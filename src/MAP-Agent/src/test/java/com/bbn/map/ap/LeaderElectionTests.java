/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.ap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
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
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.simulator.SimUtils;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.GlobalNetworkConfiguration;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for the leader election algorithm.
 * 
 * @author jschewe
 *
 */
public class LeaderElectionTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderElectionTests.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Multiple this value by the network diameter to get the number of AP
     * execution rounds that it will take for a global leader to be selected.
     */
    public static final int MULTIPLER_FOR_GLOBAL_LEADER_ELECTION = 3;

    /**
     * Multiple this value by the network diameter to get the number of AP
     * execution rounds that it will take for information to be gathered and
     * shared across the whole network.
     */
    public static final int MULTIPLER_FOR_GLOBAL_NETWORK_SHARING = 4;

    /**
     * Run the network sharing test multiple times.
     * 
     * @author jschewe
     *
     */
    @RunWith(Theories.class)
    public static final class TestNetworkSharing {
        /**
         * Add test name to logging and use the application manager.
         */
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
        @Rule
        public RuleChain chain = TestUtils.getStandardRuleChain();

        /**
         * Ensure that the changes made to the agent configuration are undone
         * before other tests.
         */
        @Before
        @After
        public void resetAgentConfiguration() {
            AgentConfiguration.resetToDefaults();
            GlobalNetworkConfiguration.resetToDefaults();
        }

        /**
         * Number of times to run the test.
         */
        private static final int NUM_TEST_RUNS = 5;

        /**
         * @return the indicies for each run attempt
         */
        @DataPoints
        public static int[] runIndicies() {
            return IntStream.rangeClosed(1, NUM_TEST_RUNS).toArray();
        }

        /**
         * Test that information is shared across a network. This runs the
         * multinode topology and ensures that we are able to collect the list
         * of all nodes in each region across the whole network.
         * 
         * @param runIndex
         *            which run this is
         * @throws IOException
         *             Internal test error
         * @throws URISyntaxException
         *             Internal test error
         */
        @Theory
        public void testMultinodeSharing(final int runIndex) throws IOException, URISyntaxException {
            try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(String.format("Run %s", runIndex))) {

                LOGGER.info("Starting run number {}", runIndex);

                final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/multinode");
                final Path baseDirectory = Paths.get(baseu.toURI());

                final Path demandPath = null;

                final VirtualClock clock = new SimpleClock();

                AgentConfiguration.getInstance().setApProgram("/protelis/com/bbn/map/ap/share_region_nodes.pt");
                AgentConfiguration.getInstance().setUseLeaderElection(true);

                try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock,
                        TestUtils.POLLING_INTERVAL_MS, TestUtils.DNS_TTL, false, false, false,
                        AppMgrUtils::getContainerParameters)) {
                    sim.startSimulation();

                    sim.waitForAllNodesToConnectToNeighbors();
                    LOGGER.info("All nodes connected to neighbors");

                    sim.startClients();

                    final double networkDiameter = sim.getNetworkDiameter();
                    Assert.assertTrue("Cannot find the network diameter", Double.isFinite(networkDiameter));

                    final int numRoundsToWait = (int) Math.ceil(networkDiameter
                            * (MULTIPLER_FOR_GLOBAL_LEADER_ELECTION + MULTIPLER_FOR_GLOBAL_NETWORK_SHARING));

                    LOGGER.info("Network diameter {} roundsToGatherAndBroadcast {}", networkDiameter, numRoundsToWait);

                    SimUtils.waitForApRounds(sim, numRoundsToWait);

                    // compute the expected value
                    final Map<RegionIdentifier, Set<NodeIdentifier>> allNodes = new HashMap<>();
                    sim.getAllControllers().forEach(controller -> {
                        final RegionIdentifier region = controller.getRegionIdentifier();
                        allNodes.computeIfAbsent(region, k -> new HashSet<>()).add(controller.getNodeIdentifier());
                    });

                    final Map<RegionIdentifier, RegionNodes> allRegionNodes = allNodes.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> new RegionNodes(e.getKey(), ImmutableSet.copyOf(e.getValue()))));
                    final NetworkNodes expectedNetworkNodes = new NetworkNodes(allRegionNodes);

                    for (final Controller controller : sim.getAllControllers()) {
                        // will throw ClassCastException if the program is wrong
                        final NetworkNodes localValue = (NetworkNodes) controller.getVM().getCurrentValue();

                        assertThat("Controller: " + controller.getName(), localValue, is(expectedNetworkNodes));
                    }

                } // use simulation
            } // logger context
        }

        /**
         * Collect the nodes in a region.
         * 
         * @author jschewe
         *
         */
        public static final class RegionNodes implements Serializable {

            private static final long serialVersionUID = 1L;

            /**
             * @param nodes
             *            see {@link #getNodes()}
             * @param region
             *            see {@link #getRegion()}
             */
            public RegionNodes(@Nonnull final RegionIdentifier region,
                    @Nonnull final ImmutableSet<NodeIdentifier> nodes) {
                this.region = region;
                this.nodes = nodes;
            }

            /**
             * An empty object.
             * 
             * @param region
             *            see {@link #getRegion()}.
             */
            public RegionNodes(final RegionIdentifier region) {
                this.region = region;
                this.nodes = ImmutableSet.of();
            }

            /**
             * Merge 2 objects.
             * 
             * @param one
             *            the first object
             * @param two
             *            the second object
             */
            public RegionNodes(@Nonnull final RegionNodes one, @Nonnull final RegionNodes two) {
                if (!one.getRegion().equals(two.getRegion())) {
                    throw new IllegalArgumentException("Cannot merge objects with 2 different regions");
                }
                this.region = one.getRegion();
                final ImmutableSet.Builder<NodeIdentifier> nodes = ImmutableSet.builder();
                nodes.addAll(one.getNodes());
                nodes.addAll(two.getNodes());
                this.nodes = nodes.build();
            }

            /**
             * 
             * @param controller
             *            the controller to gather information from
             * @return the region node information containing only the
             *         controller
             */
            public static RegionNodes convertToRegionNodes(@Nonnull final Controller controller) {
                return new RegionNodes(controller.getRegionIdentifier(),
                        ImmutableSet.of(controller.getNodeIdentifier()));
            }

            private final RegionIdentifier region;

            /**
             * 
             * @return the region that the information is for
             */
            @Nonnull
            public RegionIdentifier getRegion() {
                return region;
            }

            private final ImmutableSet<NodeIdentifier> nodes;

            /**
             * 
             * @return the nodes in the region
             */
            @Nonnull
            public ImmutableSet<NodeIdentifier> getNodes() {
                return nodes;
            }

            @Override
            public String toString() {
                return "Region: " + getRegion() + " nodes: " + nodes.toString();
            }

            @Override
            public boolean equals(final Object o) {
                if (null == o) {
                    return false;
                } else if (this == o) {
                    return true;
                } else if (o.getClass().equals(getClass())) {
                    final RegionNodes other = (RegionNodes) o;
                    return getNodes().equals(other.getNodes()) && getRegion().equals(other.getRegion());
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                return Objects.hash(region, nodes.hashCode());
            }
        } // class RegionNodes

        /**
         * All nodes in a network.
         * 
         * @author jschewe
         *
         */
        public static final class NetworkNodes implements Serializable {

            private static final long serialVersionUID = 1L;

            private final Map<RegionIdentifier, RegionNodes> data = new HashMap<>();

            /**
             * Create an object with no nodes.
             */
            public NetworkNodes() {
            }

            /**
             * @param regionNodes
             *            the know nodes for a region
             */
            public NetworkNodes(@Nonnull final RegionNodes regionNodes) {
                data.put(regionNodes.getRegion(), regionNodes);
            }

            /**
             * Merge 2 objects.
             * 
             * @param a
             *            the first object
             * @param b
             *            the second object
             */
            public NetworkNodes(@Nonnull final NetworkNodes a, @Nonnull final NetworkNodes b) {
                data.putAll(a.data);
                b.data.forEach((region, regionNodes) -> {
                    final RegionNodes existing = data.get(region);
                    if (null == existing) {
                        data.put(region, regionNodes);
                    } else {
                        final RegionNodes merged = new RegionNodes(regionNodes, existing);
                        data.put(region, merged);
                    }
                });
            }

            /**
             * 
             * @param data
             *            the data to put in the object
             */
            public NetworkNodes(@Nonnull final Map<RegionIdentifier, RegionNodes> data) {
                this.data.putAll(data);
            }

            /**
             * 
             * @return the null object, used byProtelis
             */
            public static NetworkNodes nullNetworkNodes() {
                return new NetworkNodes();
            }

            /**
             * Used by Protelis to merge objects.
             * 
             * @param a
             *            the first object to merge
             * @param b
             *            the second object to merge
             * @return a new object that is the merge of a and b
             */
            public static NetworkNodes mergeNetworkNodes(@Nonnull final NetworkNodes a, @Nonnull final NetworkNodes b) {
                return new NetworkNodes(a, b);
            }

            /**
             * 
             * @param regionNodes
             *            the object to convert
             * @return a new network nodes object
             */
            public static NetworkNodes convertToNetworkNodes(@Nonnull final RegionNodes regionNodes) {
                return new NetworkNodes(regionNodes);
            }

            /**
             * 
             * @param region
             *            the region to get the nodes for
             * @return the nodes in the region
             */
            @Nonnull
            public RegionNodes getNodesForRegion(@Nonnull final RegionIdentifier region) {
                return data.getOrDefault(region, new RegionNodes(region));
            }

            @Override
            public String toString() {
                return data.toString();
            }

            @Override
            public boolean equals(final Object o) {
                if (null == o) {
                    return false;
                } else if (this == o) {
                    return true;
                } else if (o.getClass().equals(getClass())) {
                    final NetworkNodes other = (NetworkNodes) o;
                    return data.equals(other.data);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                return data.hashCode();
            }

        } // class NetworkNodes
    } // class TestNetworkSharing

    /**
     * Run the test for finding a global leader multiple times.
     * 
     * @author jschewe
     *
     */
    @RunWith(Theories.class)
    public static final class TestSingleGlobalLeader {
        /**
         * Add test name to logging and use the application manager.
         */
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
        @Rule
        public RuleChain chain = TestUtils.getStandardRuleChain();

        /**
         * Ensure that the changes made to the agent configuration are undone
         * before other tests.
         */
        @Before
        @After
        public void resetAgentConfiguration() {
            AgentConfiguration.resetToDefaults();
            GlobalNetworkConfiguration.resetToDefaults();
        }

        /**
         * Number of times to run the test.
         */
        private static final int NUM_TEST_RUNS = 5;

        /**
         * @return the indicies for each run attempt
         */
        @DataPoints
        public static int[] runIndicies() {
            return IntStream.rangeClosed(1, NUM_TEST_RUNS).toArray();
        }

        /**
         * 
         * @return the topologies to test
         * @throws URISyntaxException
         *             internal error
         * @throws IOException
         *             internal error
         */
        @DataPoints
        public static List<Path> topologies() {
            try {
                final Path baseDirectory = Paths
                        .get(Thread.currentThread().getContextClassLoader().getResource("ns2/leader-election").toURI());

                final List<Path> topologyDirectories = Files.walk(baseDirectory, 1).filter(Files::isDirectory)
                        .collect(Collectors.toList());
                topologyDirectories.remove(0); // remove base leader election
                                               // folder

                // add full multi-region topology
                topologyDirectories.add(
                        Paths.get(Thread.currentThread().getContextClassLoader().getResource("ns2/multinode").toURI()));

                return topologyDirectories;
            } catch (URISyntaxException | IOException e) {
                throw new RuntimeException("Internal test error: " + e.getMessage(), e);
            }
        }

        /**
         * Test that we have a single global leader after the expected number of
         * AP rounds.
         * 
         * @param runIndex
         *            which run this is
         * @param topologyPath
         *            the path to the topology to execute
         * @throws IOException
         *             Internal test error
         */
        @Theory
        public void testGlobalLeader(final Path topologyPath, final int runIndex) throws IOException {
            try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                    .push(String.valueOf(topologyPath.getFileName())).push(String.format("Run %s", runIndex))) {

                LOGGER.info("Starting run number {} with topology {}", runIndex, topologyPath.getFileName());

                final long pollingInterval = 10;
                final int dnsTtlSeconds = 60;

                final Path baseDirectory = topologyPath;

                final Path demandPath = null;

                final VirtualClock clock = new SimpleClock();

                AgentConfiguration.getInstance().setApProgram("/protelis/com/bbn/map/ap/share_region_nodes.pt");
                AgentConfiguration.getInstance().setUseLeaderElection(true);

                try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval,
                        dnsTtlSeconds, false, false, false, AppMgrUtils::getContainerParameters)) {
                    sim.startSimulation();
                    sim.startClients();

                    sim.waitForAllNodesToConnectToNeighbors();
                    LOGGER.info("All nodes connected to neighbors");

                    final double networkDiameter = sim.getNetworkDiameter();
                    Assert.assertTrue("Cannot find the network diameter", Double.isFinite(networkDiameter));

                    final int numRoundsToWait = (int) Math.ceil(networkDiameter * MULTIPLER_FOR_GLOBAL_LEADER_ELECTION);

                    LOGGER.info("Network diameter {} roundsToGatherAndBroadcast {}", networkDiameter, numRoundsToWait);

                    SimUtils.waitForApRounds(sim, numRoundsToWait);

                    sim.getAllControllers().forEach(controller -> {
                        LOGGER.info("Node: {} AP Execution count: {}", controller.getName(),
                                controller.getExecutionCount());
                    });

                    // see how many nodes have the global leader flag set
                    final Set<Controller> leaders = sim.getAllControllers().stream().filter(c -> c.isGlobalLeader())
                            .collect(Collectors.toSet());

                    assertThat("There is not a single leader: " + leaders, leaders.size(), is(1));

                } // use simulation
            } // logger context
        }
    }

}
