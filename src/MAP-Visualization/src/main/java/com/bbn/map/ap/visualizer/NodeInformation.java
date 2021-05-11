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
package com.bbn.map.ap.visualizer;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.bbn.map.Controller;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.visualizer.ScenarioVisualizer;

/**
 * Panel to display information about a {@link NetworkNode}.
 * 
 * @author jschewe
 *
 */
/*package*/ class NodeInformation extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel name;
    private final JCheckBox isDns;
    private final JCheckBox isRlg;
    private final JCheckBox isDcop;
    private final Timer updateTimer;
    
    private NetworkNode node;

    /**
     * Default constructor that creates all of the UI components.
     */
    /*package*/ NodeInformation() {
        super(new GridBagLayout());
        this.node = null;

        GridBagConstraints gbc;

        name = new JLabel("---------");
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(name, gbc);

        isDns = new JCheckBox("Is handling DNS changes");
        isDns.setEnabled(false);
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(isDns, gbc);

        isRlg = new JCheckBox("RLG");
        isRlg.setEnabled(false);
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(isRlg, gbc);

        isDcop = new JCheckBox("DCOP");
        isDcop.setEnabled(false);
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(isDcop, gbc);

        updateTimer = new Timer(ScenarioVisualizer.REFRESH_RATE, e -> update());
        updateTimer.start();
    }

    private void update() {
        if (null == node) {
            name.setText(null);
            isDns.setSelected(false);
            isRlg.setSelected(false);
            isDcop.setSelected(false);
        } else {
            name.setText(node.getName());

            if (node instanceof Controller) {
                final Controller controller = (Controller) node;
                isDns.setSelected(controller.isHandleDnsChanges());
                isRlg.setSelected(controller.isDCOPRunning());
                isDcop.setSelected(controller.isRLGRunning());
            } else {
                isDns.setSelected(false);
                isRlg.setSelected(false);
                isDcop.setSelected(false);
            }
        }
        // revalidate();
    }

    /**
     * 
     * @param node
     *            specify which node the panel should display information for,
     *            may be null
     */
    public void setNode(final NetworkNode node) {
        this.node = node;
        update();
    }

    /**
     * 
     * @return the node that is currently being displayed, may be null
     */
    public NetworkNode getNode() {
        return node;
    }
    
    /**
     * Stop the refresh of the panel. Once stopped, it cannot be restarted.
     */
    public void shutdown() {
        updateTimer.stop();
    }

}
