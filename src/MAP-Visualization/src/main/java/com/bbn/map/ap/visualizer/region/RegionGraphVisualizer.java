/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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
package com.bbn.map.ap.visualizer.region;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.apache.commons.collections15.Transformer;

import com.bbn.map.Controller;
import com.bbn.map.ap.visualizer.utils.VisualizationViewerControlPanel;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.simulator.Simulation;
import com.bbn.protelis.common.visualizer.MultiVertexRenderer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

import edu.uci.ics.jung.algorithms.layout.ISOMLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;

/**
 * Graph panel that displays client pools and regional data. The client pools
 * are sized based on the number of clients they represent. The regions are
 * sized based on their capacity and are filled based on their load percentage.
 * 
 * @author jschewe
 *
 */
public class RegionGraphVisualizer extends JPanel {

    private static final long serialVersionUID = 2788026958055409237L;

    private VisualizationViewer<AbstractRegionDisplayNode, RegionDisplayEdge> viewer;

    private final Graph<AbstractRegionDisplayNode, RegionDisplayEdge> graph = new SparseMultigraph<>();

    private Simulation sim = null;

    private final VisualizationViewerControlPanel viewerControl;

    /**
     * The {@link NodeAttribute} that we are going to report on.
     */
    public static final NodeMetricName RELEVANT_NODE_ATTRIBUTE = NodeMetricName.TASK_CONTAINERS;

    private double maxRegionCapacity = 0;
    private int maxClientPoolSize = 1;

    private final Timer refresher;
    /**
     * How often to redraw the graph in milliseconds.
     */
    public static final int REFRESH_RATE = 500;

    /**
     * Create an empty panel.
     */
    public RegionGraphVisualizer() {
        super(new BorderLayout());

        viewerControl = new VisualizationViewerControlPanel();
        add(viewerControl, BorderLayout.NORTH);

        refresher = new Timer(REFRESH_RATE, new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                // TODO: may need to refresh edges as well
                if (null != viewer) {
                    viewer.repaint();
                }
            }
        });
        refresher.start();

    }

    /**
     * Specify the simulation that is to be displayed.
     * 
     * @param sim
     *            the new simuulation to display, null to clear the display
     */
    public void setSimulation(final Simulation sim) {
        maxRegionCapacity = 0;
        maxClientPoolSize = 1;

        this.sim = sim;
        if (null != this.sim) {
            final Map<RegionIdentifier, RegionDisplayNode> regionNodes = new HashMap<>();
            final List<ClientDisplayNode> clientNodes = new LinkedList<>();

            this.sim.getScenario().getServers().forEach((k, controller) -> {
                final RegionIdentifier region = controller.getRegionIdentifier();
                final RegionDisplayNode display = regionNodes.computeIfAbsent(region,
                        k1 -> new RegionDisplayNode(this, k1, sim));
                graph.addVertex(display);
            });
            this.sim.getScenario().getClients().forEach((k, client) -> {
                final ClientDisplayNode display = new ClientDisplayNode(this, client);
                clientNodes.add(display);
                maxClientPoolSize = Math.max(maxClientPoolSize, client.getNumClients());
            });

            final Set<RegionDisplayEdge> edges = new HashSet<>();

            regionNodes.forEach((region, display) -> {
                sim.computeNeighborRegions(region).forEach(otherRegion -> {
                    final RegionDisplayNode otherDisplay = regionNodes.get(otherRegion);

                    final RegionDisplayEdge edge = new RegionDisplayEdge(display, otherDisplay);
                    if (!edges.contains(edge)) {
                        graph.addEdge(edge, display, otherDisplay);
                        edges.add(edge);
                    }

                });

            });

            clientNodes.forEach(clientDisplay -> {
                graph.addVertex(clientDisplay);

                final Set<RegionIdentifier> neighborRegions = new HashSet<>();

                // add edges for connections to regions
                clientDisplay.getClient().getNeighbors().forEach(nodeId -> {
                    final Controller controller = sim.getControllerById(nodeId);
                    if (null != controller) {
                        neighborRegions.add(controller.getRegionIdentifier());
                    }
                });

                neighborRegions.forEach(region -> {
                    final RegionDisplayNode regionDisplay = regionNodes.get(region);
                    Objects.requireNonNull(regionDisplay);

                    final RegionDisplayEdge edge = new RegionDisplayEdge(clientDisplay, regionDisplay);
                    graph.addEdge(edge, clientDisplay, regionDisplay);
                });
            });

            // compute max region capacity
            maxRegionCapacity = regionNodes.entrySet().stream().mapToDouble(e -> {
                return e.getValue().getRegionCapacity();
            }).max().orElse(0D);

            createVisualizer();

            // force refresh
            revalidate();
        }
    }

    private static final int DEFAULT_WIDTH = 1200;// 640;//1920;
    private static final int DEFAULT_HEIGHT = 800;// 480;//1080;
    private static final int LAYOUT_WIDTH = (int) (0.9 * DEFAULT_WIDTH);
    private static final int LAYOUT_HEIGHT = (int) (0.9 * DEFAULT_HEIGHT);
    private static final double REGION_NODE_MAX_DIAMETER = 50;
    private static final double REGION_NODE_MIN_DIAMETER = 5;
    private static final double CLIENT_NODE_MAX_DIAMETER = 50;
    private static final double CLIENT_NODE_MIN_DIAMETER = 5;

    /* package */ double computeRegionNodeDiameter(final RegionDisplayNode displayNode) {
        final double value = displayNode.getRegionCapacity();
        if (maxRegionCapacity <= 0) {
            return REGION_NODE_MIN_DIAMETER;
        } else {
            final double percentOfMax = value / maxRegionCapacity;
            final double diameter = percentOfMax * REGION_NODE_MAX_DIAMETER;
            final double d = Math.max(REGION_NODE_MIN_DIAMETER, diameter);
            return d;
        }
    }

    /* package */ double computeClientNodeDiameter(final ClientDisplayNode displayNode) {
        final double value = displayNode.getClient().getNumClients();
        final double percentOfMax = value / maxClientPoolSize;
        final double diameter = percentOfMax * CLIENT_NODE_MAX_DIAMETER;
        final double d = Math.max(CLIENT_NODE_MIN_DIAMETER, diameter);
        return d;
    }

    private void createVisualizer() {
        if (null != viewer) {
            remove(viewer);
            viewer = null;
        }

        final Layout<AbstractRegionDisplayNode, RegionDisplayEdge> layout = new ISOMLayout<>(graph);
        // sets the initial size of the space
        layout.setSize(new Dimension(LAYOUT_WIDTH, LAYOUT_HEIGHT));
        viewer = new VisualizationViewer<>(layout);
        viewer.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));

        viewer.getRenderer().setVertexRenderer(new MultiVertexRenderer<AbstractRegionDisplayNode, RegionDisplayEdge>());
        viewer.getRenderContext().setVertexLabelTransformer(new Transformer<AbstractRegionDisplayNode, String>() {
            @Override
            public String transform(final AbstractRegionDisplayNode dn) {
                return dn.getVertexLabel();
            }
        });
        // Place labels at bottom center
        viewer.getRenderer().getVertexLabelRenderer().setPosition(Position.S);

        viewer.getRenderContext().setVertexFillPaintTransformer(new Transformer<AbstractRegionDisplayNode, Paint>() {
            @Override
            public Paint transform(final AbstractRegionDisplayNode dn) {
                return dn.getVertexFillColor();
            }
        });

        viewer.getRenderContext().setVertexDrawPaintTransformer(new Transformer<AbstractRegionDisplayNode, Paint>() {
            @Override
            public Paint transform(final AbstractRegionDisplayNode dn) {
                return dn.getVertexColor();

            }
        });

        // viewer.getRenderContext().setVertexStrokeTransformer(new
        // Transformer<AbstractRegionDisplayNode, Stroke>() {
        // @Override
        // public Stroke transform(final AbstractRegionDisplayNode dn) {
        // return new BasicStroke(3);
        // }
        // });

        final Transformer<RegionDisplayEdge, Paint> arrowPaint = new Transformer<RegionDisplayEdge, Paint>() {
            @Override
            public Paint transform(final RegionDisplayEdge e) {
                return e.getEdgeColor();
            }
        };
        viewer.getRenderContext().setEdgeDrawPaintTransformer(arrowPaint);
        viewer.getRenderContext().setArrowDrawPaintTransformer(arrowPaint);
        viewer.getRenderContext().setArrowFillPaintTransformer(arrowPaint);

        viewer.getRenderContext().setVertexShapeTransformer(new Transformer<AbstractRegionDisplayNode, Shape>() {
            @Override
            public Shape transform(final AbstractRegionDisplayNode dn) {
                return dn.getVertexShape();
            }
        });

        viewer.getRenderContext().setVertexIconTransformer(new Transformer<AbstractRegionDisplayNode, Icon>() {
            @Override
            public Icon transform(final AbstractRegionDisplayNode dn) {
                return dn.getVertexIcon();
            }
        });

        viewer.getRenderContext().setEdgeLabelTransformer(new Transformer<RegionDisplayEdge, String>() {
            @Override
            public String transform(final RegionDisplayEdge e) {
                return e.getDisplayText();
            }
        });

        viewerControl.setViewer(viewer);

        add(viewer, BorderLayout.CENTER);
    }

}
