package com.bbn.map.dcop;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
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

import org.junit.Assert;
import org.junit.Assume;
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
import com.bbn.map.AgentConfiguration.RlgAlgorithm;
import com.bbn.map.AgentConfiguration.RlgPriorityPolicy;
import com.bbn.map.AgentConfiguration.RlgStubChooseNcp;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.SimulationRunner;
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
 * Test that DCOP will overflow to another region.
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
    public RuleChain chain = TestUtils.getStandardRuleChain()
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Test using the topology test-dcop-overflow. Initially all traffic goes to
     * the single server in region A. There is 1 more request than the server
     * can handle. DCOP should recognize that region A is fully loaded and
     * distribute some of the traffic to region B. More demand shows up later in
     * the test and some of that load should get sent over to region B.
     * 
     * @param algorithm
     *            the DCOP algorithm to run the test with
     * @throws IOException
     *             on error reading the test files
     * @throws URISyntaxException
     *             on internal error
     */
    @Theory
    public void test(final DcopAlgorithm algorithm) throws IOException, URISyntaxException {
        Assume.assumeTrue("DEFAULT_PLAN algorithm does not provide a plan valid for this test",
                !algorithm.equals(DcopAlgorithm.DEFAULT_PLAN));
        Assume.assumeTrue("CDIFF_PLUS is under development", !algorithm.equals(DcopAlgorithm.CDIFF_PLUS));
        Assume.assumeTrue("MODULAR_ACDIFF is under development", !algorithm.equals(DcopAlgorithm.MODULAR_ACDIFF));
        Assume.assumeTrue("RC_DIFF is under development", !algorithm.equals(DcopAlgorithm.RC_DIFF));
        Assume.assumeTrue("MODULAR_RCDIFF is under development", !algorithm.equals(DcopAlgorithm.MODULAR_RCDIFF));

        AgentConfiguration.getInstance().setDcopAlgorithm(algorithm);

        LOGGER.info("Running test with algorithm {}", AgentConfiguration.getInstance().getDcopAlgorithm());

        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;
        final int dcopRoundSeconds = 30;
        final Duration dcopRoundDuration = Duration.ofSeconds(dcopRoundSeconds);
        final int rlgRoundSeconds = 10;
        final Duration rlgRoundDuration = Duration.ofSeconds(rlgRoundSeconds);
        final double dcopThreshold = 0.5;

        final String clientNodeName = "clientPoolB";
        final String nodeB0name = "nodeB0";
        final NodeIdentifier nodeB0identifier = new DnsNameIdentifier(nodeB0name);

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
        AgentConfiguration.getInstance().setRlgAlgorithm(RlgAlgorithm.STUB);
        AgentConfiguration.getInstance().setRlgPriorityPolicy(RlgPriorityPolicy.NO_PRIORITY);
        AgentConfiguration.getInstance().setRlgStubChooseNcp(RlgStubChooseNcp.MOST_AVAILABLE_CONTAINERS);
        AgentConfiguration.getInstance().setDcopCapacityThreshold(dcopThreshold);
        // don't let RLG shutdown containers for low load
        AgentConfiguration.getInstance().setRlgUnderloadThreshold(0);

        final int expectedInitialRequests = 4;
        final int expectedInitialSuccess = 1;
        final int expectedInitialFailServer = 1;

        // how many plans to check before giving up
        final int maxNumPlans = 5;
        final BlockingQueue<RegionPlan> dcopPlans = new LinkedBlockingDeque<>();
        // how many minutes to wait for a plan to be created
        final int planCreationTimeoutMinutes = 3;

        final int numDcopRoundsToWaitForOverflow = 4;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                AppMgrUtils::getContainerParameters)) {

            sim.startSimulation();

            SimulationRunner.startAgentsAndClients(sim);

            // dcop for region B
            final Controller nodeB0 = sim.getControllerById(nodeB0identifier);
            final RegionIdentifier regionB = nodeB0.getRegionIdentifier();
            nodeB0.addDcopPlanListener(Errors.rethrow().wrap(dcopPlans::put));

            // Wait for everything to start and the clients to add load to the
            // network
            clock.waitForDuration(dcopRoundDuration.toMillis() * numDcopRoundsToWaitForOverflow);

            // check for 1 failed request on the client pool
            final Optional<ClientSim> clientPoolOptional = sim.getClientSimulators().stream()
                    .filter(csim -> csim.getSimName().equals(clientNodeName)).findFirst();
            Assert.assertTrue(clientPoolOptional.isPresent());
            final ClientSim clientPool = clientPoolOptional.get();

            Assert.assertThat("Num requests attempted. Failure in the test framework.",
                    clientPool.getNumRequestsAttempted(), greaterThanOrEqualTo(expectedInitialRequests));

            // some requests should fail
            Assert.assertThat("Num requests succeeded. Something unexpected happened with the initial client requests.",
                    clientPool.getNumRequestsSucceeded(),
                    is(both(greaterThanOrEqualTo(expectedInitialSuccess)).and(lessThan(expectedInitialRequests))));

            // the failed requests should be for server load
            Assert.assertThat(
                    "Num requests failed for server. Something unexpected happened with the initial client requests..",
                    clientPool.getNumRequestsFailedForServerLoad(), greaterThanOrEqualTo(expectedInitialFailServer));
            Assert.assertThat(
                    "Num requests failed for network. No requests should have failed because of network load.",
                    clientPool.getNumRequestsFailedForNetworkLoad(), comparesEqualTo(0));

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
                                LOGGER.info("success: Found load to region B: {}", regionBValue);

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
