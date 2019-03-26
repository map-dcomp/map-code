package com.bbn.map.dcop;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsNull;
import org.hamcrest.number.OrderingComparison;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.ap.ApplicationManagerUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that DCOP is able to send data to another region.
 * 
 * @author jschewe
 *
 */
public class DcopOverflowTest {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.UseMapApplicationManager())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Test using the topology test-dcop-overflow. Initially all traffic goes to
     * the single server in region A. There is 1 more request than the server
     * can handle. DCOP should recognize that region A is fully loaded and
     * distribute some of the traffic to region B. More demand shows up 300
     * seconds in and some of that load should get sent over to region B.
     * 
     * @throws IOException
     *             on error reading the test files
     * @throws URISyntaxException
     *             on internal error
     */
    @Test
    public void test() throws IOException, URISyntaxException {
        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;
        final int dcopRoundSeconds = 30;
        final Duration dcopRoundDuration = Duration.ofSeconds(dcopRoundSeconds);
        final int rlgRoundSeconds = 10;
        final Duration rlgRoundDuration = Duration.ofSeconds(rlgRoundSeconds);

        final String clientNodeName = "clientPoolA";
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
        AgentConfiguration.resetToDefaults();
        AgentConfiguration.getInstance().setDcopRoundDuration(dcopRoundDuration);
        AgentConfiguration.getInstance().setDcopEstimationWindow(dcopRoundDuration);
        AgentConfiguration.getInstance().setRlgRoundDuration(rlgRoundDuration);
        AgentConfiguration.getInstance().setRlgEstimationWindow(rlgRoundDuration);

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                ApplicationManagerUtils::getContainerParameters)) {

            sim.startSimulation();
            clock.waitForDuration(dcopRoundDuration.toMillis() * 3);

            // check for 1 failed request on clientPoolA
            final Optional<ClientSim> clientPoolAOptional = sim.getClientSimulators().stream()
                    .filter(csim -> csim.getClientName().equals(clientNodeName)).findFirst();
            Assert.assertTrue(clientPoolAOptional.isPresent());
            final ClientSim clientPoolA = clientPoolAOptional.get();

            Assert.assertThat("Num requests attempted. Failure in the test framework.",
                    clientPoolA.getNumRequestsAttempted(), OrderingComparison.comparesEqualTo(3));
            Assert.assertThat("Num requests succeeded. Something unexpected happened with the initial client requests.",
                    clientPoolA.getNumRequestsSucceeded(), OrderingComparison.comparesEqualTo(2));
            Assert.assertThat(
                    "Num requests failed for server. Something unexpected happened with the initial client requests..",
                    clientPoolA.getNumRequestsFailedForServerLoad(), OrderingComparison.comparesEqualTo(1));
            Assert.assertThat(
                    "Num requests failed for network. No requests should have failed because of network load.",
                    clientPoolA.getNumRequestsFailedForNetworkLoad(), OrderingComparison.comparesEqualTo(0));

            clock.waitForDuration(dcopRoundDuration.toMillis() * 4);
            sim.stopSimulation();

            // check that there is load on a service in region B
            final Controller nodeB0 = sim.getControllerById(nodeB0identifier);
            Assert.assertThat("Cannot find nodeB0. Failure in the test framework.", nodeB0,
                    CoreMatchers.is(IsNull.notNullValue()));

            final ResourceReport nodeB0report = nodeB0.getResourceReport(EstimationWindow.SHORT);
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> nodeB0load = nodeB0report
                    .getComputeLoad();
            final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceLoad = nodeB0load
                    .get(serviceIdentifier);
            Assert.assertThat("No load for the service in region B, DCOP must not have sent any traffic there",
                    serviceLoad, CoreMatchers.is(IsNull.notNullValue()));
        } // simulation allocation

    }

}
