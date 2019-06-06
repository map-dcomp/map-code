package com.bbn.map.dcop;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.hamcrest.number.OrderingComparison;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.diffplug.common.base.Errors;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that DCOP is able to send data to another region.
 * 
 * @author jschewe
 *
 */
@RunWith(Theories.class)
public class DcopOverflowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcopOverflowTest.class);

    /**
     * @return the weights to use
     */
    @DataPoints
    public static DcopAlgorithm[] algorithms() {
        return DcopAlgorithm.values();
    }

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Reset the {@link AgentConfiguration} object to default values. This is
     * done before and after all tests to ensure that other tests are not
     * effected by using the different algorithms here.
     */
    @Before
    @After
    public void resetAgentConfiguration() {
        AgentConfiguration.resetToDefaults();
    }

    /**
     * Test using the topology test-dcop-overflow. Initially all traffic goes to
     * the single server in region A. There is 1 more request than the server
     * can handle. DCOP should recognize that region A is fully loaded and
     * distribute some of the traffic to region B. More demand shows up 300
     * seconds in and some of that load should get sent over to region B.
     *
     * @param algorithm the DCOP algorithm to run the test with 
     * @throws IOException
     *             on error reading the test files
     * @throws URISyntaxException
     *             on internal error
     */
    @Theory
    public void test(final DcopAlgorithm algorithm) throws IOException, URISyntaxException {
        AgentConfiguration.getInstance().setDcopAlgorithm(algorithm);

        LOGGER.info("Running test with algorithm {}", AgentConfiguration.getInstance().getDcopAlgorithm());

        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;
        final int dcopRoundSeconds = 30;
        final Duration dcopRoundDuration = Duration.ofSeconds(dcopRoundSeconds);
        final int rlgRoundSeconds = 10;
        final Duration rlgRoundDuration = Duration.ofSeconds(rlgRoundSeconds);

        final String clientNodeName = "clientPoolB";
        final String nodeB0name = "nodeB0";
        final NodeIdentifier nodeB0identifier = new DnsNameIdentifier(nodeB0name);

        final String nodeA0name = "nodeA0";
        final NodeIdentifier nodeA0identifier = new DnsNameIdentifier(nodeA0name);

        final String serviceGroup = "com.bbn";
        final String serviceName = "image-recognition-high";
        final String serviceVersion = "1";
        final ApplicationCoordinates serviceIdentifier = new ApplicationCoordinates(serviceGroup, serviceName,
                serviceVersion);

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-overflow");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("simple-overflow");

        // setup simulation parameters
        AgentConfiguration.getInstance().setDcopRoundDuration(dcopRoundDuration);
        AgentConfiguration.getInstance().setDcopEstimationWindow(dcopRoundDuration);
        AgentConfiguration.getInstance().setRlgRoundDuration(rlgRoundDuration);
        AgentConfiguration.getInstance().setRlgEstimationWindow(rlgRoundDuration);

        final int expectedInitialRequests = 3;
        final int expectedInitialSuccess = 2;
        final int expectedInitialFailServer = 1;
        final int expectedInitialFailNetwork = 0;

        // how many plans to check before giving up
        final int maxNumPlans = 3;
        final BlockingQueue<RegionPlan> dcopPlans = new LinkedBlockingDeque<>();
        // how many minutes to wait for a plan to be created
        final int planCreationTimeoutMinutes = 5;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                AppMgrUtils::getContainerParameters)) {

            sim.startSimulation();
            
            sim.waitForAllNodesToConnectToNeighbors();
            sim.startClients();

            // dcop for region A
            final Controller nodeA0 = sim.getControllerById(nodeA0identifier);
            nodeA0.addDcopPlanListener(Errors.rethrow().wrap(dcopPlans::put));

            final Controller nodeB0 = sim.getControllerById(nodeB0identifier);
            final RegionIdentifier regionB = nodeB0.getRegionIdentifier();

            // Wait for everything to start and the clients to add load to the
            // network
            clock.waitForDuration(dcopRoundDuration.toMillis() * 3);

            // check for 1 failed request on the client pool
            final Optional<ClientSim> clientPoolOptional = sim.getClientSimulators().stream()
                    .filter(csim -> csim.getClientName().equals(clientNodeName)).findFirst();
            Assert.assertTrue(clientPoolOptional.isPresent());
            final ClientSim clientPool = clientPoolOptional.get();

            Assert.assertThat("Num requests attempted. Failure in the test framework.",
                    clientPool.getNumRequestsAttempted(), OrderingComparison.comparesEqualTo(expectedInitialRequests));
            Assert.assertThat("Num requests succeeded. Something unexpected happened with the initial client requests.",
                    clientPool.getNumRequestsSucceeded(), OrderingComparison.comparesEqualTo(expectedInitialSuccess));
            Assert.assertThat(
                    "Num requests failed for server. Something unexpected happened with the initial client requests..",
                    clientPool.getNumRequestsFailedForServerLoad(),
                    OrderingComparison.comparesEqualTo(expectedInitialFailServer));
            Assert.assertThat(
                    "Num requests failed for network. No requests should have failed because of network load.",
                    clientPool.getNumRequestsFailedForNetworkLoad(),
                    OrderingComparison.comparesEqualTo(expectedInitialFailNetwork));

            // read some number of DCOP plans and check for traffic being sent
            // to region B
            int numPlansInspected = 0;
            while (numPlansInspected < maxNumPlans) {
                try {
                    final RegionPlan plan = dcopPlans.poll(planCreationTimeoutMinutes, TimeUnit.MINUTES);
                    if (null == plan) {
                        sim.stopSimulation();

                        fail("Timeout waiting for plan. Count is " + numPlansInspected);
                        break;
                    }

                    ++numPlansInspected;

                    LOGGER.debug("Received plan {}", plan);

                    final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> detailedPlan = plan
                            .getPlan();
                    final ImmutableMap<RegionIdentifier, Double> servicePlan = detailedPlan.get(serviceIdentifier);
                    if (null != servicePlan) {
                        final Double regionBValue = servicePlan.get(regionB);
                        if (null != regionBValue) {
                            if (regionBValue > 0) {
                                // success
                                LOGGER.info("Found load to region B: {}", regionBValue);

                                sim.stopSimulation();

                                return;
                            }
                        }
                    }

                } catch (final InterruptedException e) {
                    fail("Interrupted polling for a plan: " + e.getMessage());
                }
            }

            sim.stopSimulation();
            fail("Inspected " + numPlansInspected
                    + " plans and didn't find traffic sent to region B. Dcop failed to send traffic to region B in response to excess load in region A");
        } // simulation allocation

    }

}
