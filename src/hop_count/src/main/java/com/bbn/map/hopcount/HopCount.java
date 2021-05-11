/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
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
package com.bbn.map.hopcount;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.simulator.SimUtils;
import com.bbn.map.ta2.RegionalLink;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.map.utils.MapLoggingConfigurationFactory;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

/**
 * Generate CSV containing the number of hops from each client region to each
 * other region.
 * 
 * @author jschewe
 *
 */
public final class HopCount {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }

    private HopCount() {
    }

    private static final Logger LOGGER = LogManager.getLogger(HopCount.class);

    private static final String HELP_OPT = "help";
    private static final String SCENARIO_OPT = "scenario";
    private static final String OUTPUT_OPT = "output";

    /**
     * 
     * @param args
     *            see --help for arguments
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        final Options options = new Options();
        options.addRequiredOption("s", SCENARIO_OPT, true, "The directory where the scenario is stored (required)");
        options.addRequiredOption("o", OUTPUT_OPT, true, "File to write the output to (required)");

        options.addOption("h", HELP_OPT, false, "Show the help");

        final CommandLineParser parser = new DefaultParser();

        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            }

            final Path scenarioPath = Paths.get(cmd.getOptionValue(SCENARIO_OPT));
            final Path output = Paths.get(cmd.getOptionValue(OUTPUT_OPT));

            final Topology topology = NS2Parser.parse("dummy", scenarioPath);

            final Factory factory = new Factory();
            final Graph<Vertex, Edge> graph = MapUtils.parseTopology(topology, factory);

            final Set<RegionIdentifier> clientRegions = graph.getVertices().stream() //
                    .filter(Vertex::isClient) //
                    .map(Vertex::getRegion) //
                    .collect(Collectors.toSet());

            final Set<RegionIdentifier> allRegions = graph.getVertices().stream() //
                    .map(Vertex::getRegion) //
                    .collect(Collectors.toSet());

            final Graph<RegionIdentifier, RegionalLink> regionGraph = SimUtils.computeRegionGraph(graph,
                    Vertex::getRegion);

            final DijkstraShortestPath<RegionIdentifier, RegionalLink> pathFinder = new DijkstraShortestPath<>(
                    regionGraph);

            final CSVFormat format = CSVFormat.EXCEL.withHeader("from", "to", "hop count");

            try (Writer writer = Files.newBufferedWriter(output); CSVPrinter printer = new CSVPrinter(writer, format)) {

                for (final RegionIdentifier source : clientRegions) {
                    for (final RegionIdentifier dest : allRegions) {
                        if (!source.equals(dest)) {
                            final List<RegionalLink> path = pathFinder.getPath(source, dest);
                            printer.printRecord(source.getName(), dest.getName(), path.size());
                        }
                    }
                }

            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
            }

            LOGGER.info("Done");
        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            printUsage(options);
            System.exit(1);
        } catch (final IOException e) {
            LOGGER.error("Error parsing files", e);
            System.exit(1);
        }
    }

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp(HopCount.class.getSimpleName(), options);
    }

    private static final class Factory implements MapUtils.GraphFactory<Vertex, Edge> {

        @Override
        public Vertex createVertex(final Node node) {
            return new Vertex(node);
        }

        @Override
        public Edge createEdge(final Link link, final Vertex left, final Vertex right) {
            if (left.getNode().getName().equals(right.getNode().getName())) {
                throw new RuntimeException("Cannot create edge between a node and itself");
            }
            return new Edge(left, right);
        }
    }

    private static final class Vertex {
        /* package */ Vertex(final Node node) {
            this.node = node;
        }

        private final Node node;

        public Node getNode() {
            return node;
        }

        public RegionIdentifier getRegion() {
            return new StringRegionIdentifier(NetworkServerProperties.parseRegionName(node.getExtraData()));
        }

        public boolean isClient() {
            return node.isClient();
        }

        @Override
        public boolean equals(final Object o) {
            if (null == o) {
                return false;
            } else if (this == o) {
                return true;
            } else if (o.getClass().equals(this.getClass())) {
                return getNode().getName().equals(((Vertex) o).getNode().getName());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getNode().hashCode();
        }
    }

    private static final class Edge {
        /* package */ Edge(final Vertex left, final Vertex right) {
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

        private final Vertex left;

        public Vertex getLeft() {
            return left;
        }

        private final Vertex right;

        public Vertex getRight() {
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
                final Edge other = (Edge) o;
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
}
