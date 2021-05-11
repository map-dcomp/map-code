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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import com.bbn.map.simulator.ClientSim;
import com.bbn.protelis.networkresourcemanagement.visualizer.ScenarioVisualizer;

/**
 * Display information about a {@link ClientSim} object.
 * 
 * @author jschewe
 *
 */
/* package */ class ClientInformation extends JPanel {

    private static final long serialVersionUID = 1L;
    private final ClientSim client;
    private final JLabel requestsAttempted;
    private final JLabel requestsSlowNetwork;
    private final JLabel requestsSlowServer;
    private final JLabel requestsSlowBoth;
    private final JLabel requestsFailNetwork;
    private final JLabel requestsFailServer;
    private final JLabel requestsSucceeded;
    private final Timer updateTimer;

    /* package */ ClientInformation(@Nonnull final ClientSim client) {
        super(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
        this.client = client;

        final JPanel detailPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc;

        // client properties
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests Attempted: "), gbc);

        requestsAttempted = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsAttempted, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests Succeeded: "), gbc);

        requestsSucceeded = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsSucceeded, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests Slow for Network: "), gbc);

        requestsSlowNetwork = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsSlowNetwork, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests Slow for Server: "), gbc);

        requestsSlowServer = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsSlowServer, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests Slow for BOTH: "), gbc);

        requestsSlowBoth = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsSlowBoth, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests FAIL for Network: "), gbc);

        requestsFailNetwork = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsFailNetwork, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        detailPanel.add(new JLabel("Requests FAIL for Server: "), gbc);

        requestsFailServer = new JLabel("0");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        detailPanel.add(requestsFailServer, gbc);

        // requests table
        final ClientLoadModel model = new ClientLoadModel(client.getClientRequests());
        final JTable table = new JTable(model);
        // table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        final Box tableContainer = Box.createVerticalBox();
        tableContainer.add(table.getTableHeader());
        tableContainer.add(table);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        detailPanel.add(tableContainer, gbc);

        resizeColumnWidth(table);

        final ExpandablePanel expand = new ExpandablePanel(client.getSimName(), detailPanel);
        add(expand, BorderLayout.CENTER);

        updateTimer = new Timer(ScenarioVisualizer.REFRESH_RATE, e -> update());
        updateTimer.start();
    }

    private static final int MINIMUM_COLUMN_WIDTH = 15;
    private static final int MAXIMUM_COLUMN_WIDTH = 300;

    /**
     * Make all columns the width of the widest element that is currently in
     * that column of the table.
     * 
     * @param table
     *            the table to modify
     * @see #MINIMUM_COLUMN_WIDTH
     * @see #MAXIMUM_COLUMN_WIDTH
     */
    private static void resizeColumnWidth(final JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = MINIMUM_COLUMN_WIDTH;
            for (int row = 0; row < table.getRowCount(); row++) {
                final TableCellRenderer renderer = table.getCellRenderer(row, column);
                final Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 1, width);
            }
            width = Math.min(width, MAXIMUM_COLUMN_WIDTH);
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    private void update() {
        requestsAttempted.setText(Objects.toString(client.getNumRequestsAttempted()));
        requestsSucceeded.setText(Objects.toString(client.getNumRequestsSucceeded()));
        requestsSlowNetwork.setText(Objects.toString(client.getNumRequestsSlowForNetworkLoad()));
        requestsSlowServer.setText(Objects.toString(client.getNumRequestsSlowForServerLoad()));
        requestsSlowBoth.setText(Objects.toString(client.getNumRequestsSlowForNetworkAndServerLoad()));
        requestsFailNetwork.setText(Objects.toString(client.getNumRequestsFailedForNetworkLoad()));
        requestsFailServer.setText(Objects.toString(client.getNumRequestsFailedForServerLoad()));
    }

}
