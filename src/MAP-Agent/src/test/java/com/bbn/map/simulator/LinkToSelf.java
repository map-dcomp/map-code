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
package com.bbn.map.simulator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.uci.ics.jung.graph.Graph;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test case where a link appears to going to itself and should not be.
 * 
 * @author jschewe
 *
 */
public class LinkToSelf {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkToSelf.class);

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * This scenario should not have a link to itself, but is coming up as a
     * problem.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Test
    public void test() throws URISyntaxException, IOException {
        final URL baseu = LinkToSelf.class.getResource("link-to-self");
        final Path scenarioPath = Paths.get(baseu.toURI());

        final TestFactory factory = new TestFactory();
        final Topology topology = NS2Parser.parse("link-to-self", scenarioPath);
        final Graph<TestVertex, TestEdge> graph = MapUtils.parseTopology(topology, factory);

        graph.getEdges().stream().forEach(link -> {
            if (link.getLeft().getNode().getName().equals(link.getRight().getNode().getName())) {
                throw new RuntimeException("Cannot have a link between a node and itself");
            }
        });

    }

    private static final class TestFactory implements MapUtils.GraphFactory<TestVertex, TestEdge> {

        @Override
        public TestVertex createVertex(final Node node) {
            return new TestVertex(node);
        }

        @Override
        public TestEdge createEdge(final Link link, final TestVertex left, final TestVertex right) {
            if (left.getNode().getName().equals(right.getNode().getName())) {
                throw new RuntimeException("Cannot create edge between a node and itself");
            }
            return new TestEdge(left, right);
        }
    }

    private static final class TestVertex {
        /* package */ TestVertex(final Node node) {
            this.node = node;
        }

        private final Node node;

        public Node getNode() {
            return node;
        }

        @Override
        public boolean equals(final Object o) {
            if (null == o) {
                return false;
            } else if (this == o) {
                return true;
            } else if (o.getClass().equals(this.getClass())) {
                return getNode().getName().equals(((TestVertex) o).getNode().getName());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getNode().hashCode();
        }
    }

    private static final class TestEdge {
        /* package */ TestEdge(final TestVertex left, final TestVertex right) {
            this.left = left;
            this.right = right;

            // make sure that edge(one, two) is equal to edge(two, one)
            // Objects.hash returns different values depending on the order
            if (left.hashCode() < right.hashCode()) {
                this.hashCode = Objects.hash(left, right);
            } else {
                this.hashCode = Objects.hash(right, left);
            }
        }

        private final TestVertex left;

        public TestVertex getLeft() {
            return left;
        }

        private final TestVertex right;

        public TestVertex getRight() {
            return right;
        }

        @Override
        public boolean equals(final Object o) {
            if (null == o) {
                return false;
            } else if (this == o) {
                return true;
            } else if (o.getClass().equals(this.getClass())) {
                // edges are bidirectional, so left and right don't matter for
                // equality
                final TestEdge other = (TestEdge) o;
                return (left.equals(other.getLeft()) && right.equals(other.getRight()))
                        || (left.equals(other.getRight()) && right.equals(other.getLeft()));
            } else {
                return false;
            }
        }

        private final int hashCode;

        @Override
        public int hashCode() {
            return hashCode;
        }

    }

    /**
     * Load topology into full simulation object.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testSimulation() throws URISyntaxException, IOException {
        final URL baseu = LinkToSelf.class.getResource("link-to-self");
        final Path scenarioPath = Paths.get(baseu.toURI());
        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", scenarioPath, null, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {
            LOGGER.debug("Successfully created simulation");
        }
    }
}
