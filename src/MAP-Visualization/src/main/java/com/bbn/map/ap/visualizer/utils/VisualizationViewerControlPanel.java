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
package com.bbn.map.ap.visualizer.utils;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.bbn.map.ap.visualizer.DisplayController;
import com.bbn.protelis.networkresourcemanagement.visualizer.DisplayEdge;

import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ScalingControl;

/**
 * Panel to control a {@link VisualizationViewer}. Sets up the controls for
 * changing mouse modes and installs the mouse handler.
 * 
 * @author jschewe
 *
 */
public class VisualizationViewerControlPanel extends JPanel {

    private static final long serialVersionUID = 4592319128449449389L;

    private final DefaultModalGraphMouse<DisplayController, DisplayEdge> graphMouse;

    private static final float GRAPH_SCALE_FACTOR = 1.1f;

    private static final String INSTRUCTIONS = "<html>" //
            + "<b><h2><center>Instructions for Mouse Interactions</center></h2></b>" //
            + "<p>There are two modes, Transforming and Picking.</p>" //
            + "<p>The modes are selected with a combo box.</p>"//

            + "<p><p><b>Transforming Mode:</b></p>"//
            + "<ul>"//
            + "<li>Mouse1+drag pans the graph</li>"//
            + "<li>Mouse1+Shift+drag rotates the graph</li>"//
            + "<li>Mouse1+CTRL(or Command)+drag shears the graph</li>"//
            + "</ul>"//

            + "<b>Picking Mode:</b>"//
            + "<ul>"//
            + "<li>Mouse1 on a Vertex selects the vertex</li>"//
            + "<li>Mouse1 elsewhere unselects all Vertices</li>"//
            + "<li>Mouse1+Shift on a Vertex adds/removes Vertex selection</li>"//
            + "<li>Mouse1+drag on a Vertex moves all selected Vertices</li>"//
            + "<li>Mouse1+drag elsewhere selects Vertices in a region</li>"//
            + "<li>Mouse1+Shift+drag adds selection of Vertices in a new region</li>"//
            + "<li>Mouse1+CTRL on a Vertex selects the vertex and centers the display on it</li>"//
            + "</ul>"//
            + "<b>Both Modes:</b>"//
            + "<ul>"//
            + "<li>Mousewheel scales with a crossover value of " + Float.toString(GRAPH_SCALE_FACTOR) + "."//
            + "<ul>"//
            + "     <li> scales the graph layout when the combined scale is greater than "
            + Float.toString(GRAPH_SCALE_FACTOR) + "</li>"//
            + "     <li> scales the graph view when the combined scale is less than "
            + Float.toString(GRAPH_SCALE_FACTOR) + "</li>"//
            + "</ul></li>" //
            + "<li>Mouse1 on a node displays information about that node</li>"//
            + "</html>";

    /**
     * Create a new panel.
     */
    public VisualizationViewerControlPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        this.graphMouse = new DefaultModalGraphMouse<>(GRAPH_SCALE_FACTOR, 1 / GRAPH_SCALE_FACTOR);
        this.graphMouse.setMode(ModalGraphMouse.Mode.TRANSFORMING);

        add(new JLabel("Zoom: "));
        final ScalingControl scaler = new CrossoverScalingControl();
        final JButton minus = new JButton("-");
        add(minus);
        minus.addActionListener(e -> {
            if (null != viewer) {
                scaler.scale(viewer, 1 / GRAPH_SCALE_FACTOR, viewer.getCenter());
            }
        });
        final JButton plus = new JButton("+");
        add(plus);
        plus.addActionListener(e -> {
            if (null != viewer) {
                scaler.scale(viewer, GRAPH_SCALE_FACTOR, viewer.getCenter());
            }
        });

        add(new JLabel("Mouse mode: "));
        final JComboBox<?> modeBox = graphMouse.getModeComboBox();
        add(modeBox);
        modeBox.setMaximumSize(modeBox.getPreferredSize());

        // help button
        final JDialog helpDialog = new JDialog();
        helpDialog.getContentPane().add(new JLabel(INSTRUCTIONS));
        final JButton help = new JButton("Help");
        add(help);
        help.addActionListener(e -> {
            helpDialog.pack();
            helpDialog.setVisible(true);
        });
    }

    private VisualizationViewer<?, ?> viewer = null;

    /**
     * Attach a viewer to the controls. This also takes care of setting the
     * graph mouse on the viewer
     * 
     * @param viewer
     *            the viewer to connect to the control panel
     * @see VisualizationViewer#setGraphMouse(edu.uci.ics.jung.visualization.VisualizationViewer.GraphMouse)
     */
    public void setViewer(final VisualizationViewer<?, ?> viewer) {
        this.viewer = viewer;

        if (null != this.viewer) {
            // use our custom graph mouse
            this.viewer.setGraphMouse(graphMouse);

            // allow one to switch modes with the keyboard
            this.viewer.addKeyListener(graphMouse.getModeKeyListener());
        }
    }

}
