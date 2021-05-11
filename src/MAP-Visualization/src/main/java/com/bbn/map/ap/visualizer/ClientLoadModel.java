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

import javax.annotation.Nonnull;
import javax.swing.table.AbstractTableModel;

import com.bbn.map.simulator.ClientLoad;
import com.google.common.collect.ImmutableList;

/**
 * Model for displaying a list of {@link ClientLoad} objects.
 * 
 * @author jschewe
 *
 */
public class ClientLoadModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final ImmutableList<ClientLoad> data;

    /* package */ ClientLoadModel(@Nonnull final ImmutableList<ClientLoad> data) {
        this.data = data;
    }

    private static final int START_TIME_COLUMN_INDEX = 0;
    private static final int SERVER_DURATION_COLUMN_INDEX = 1;
    private static final int NUM_CLIENTS_COLUMN_INDEX = 2;
    private static final int SERVICE_COLUMN_INDEX = 3;
    private static final int NODE_LOAD_COLUMN_INDEX = 4;
    private static final int NETWORK_LOAD_COLUMN_INDEX = 5;
    private static final int NETWORK_DURATION_COLUMN_INDEX = 6;
    private static final int LAST_COLUMN_INDEX = NETWORK_DURATION_COLUMN_INDEX;

    @Override
    public String getColumnName(final int column) {
        switch (column) {
        case START_TIME_COLUMN_INDEX:
            return "Start Time";
        case SERVER_DURATION_COLUMN_INDEX:
            return "Server Duration";
        case NUM_CLIENTS_COLUMN_INDEX:
            return "Num Clients";
        case SERVICE_COLUMN_INDEX:
            return "Service";
        case NETWORK_DURATION_COLUMN_INDEX:
            return "Network Duration";
        case NODE_LOAD_COLUMN_INDEX:
            return "Node Load";
        case NETWORK_LOAD_COLUMN_INDEX:
            return "Network Load";
        default:
            return null;
        }
    }

    @Override
    public Class<?> getColumnClass(final int column) {
        switch (column) {
        case START_TIME_COLUMN_INDEX:
            return Long.class;
        case SERVER_DURATION_COLUMN_INDEX:
        case NETWORK_DURATION_COLUMN_INDEX:
            return Long.class;
        case NUM_CLIENTS_COLUMN_INDEX:
            return Integer.class;
        case SERVICE_COLUMN_INDEX:
            return String.class;
        case NODE_LOAD_COLUMN_INDEX:
            return String.class;
        case NETWORK_LOAD_COLUMN_INDEX:
            return String.class;
        default:
            return null;
        }

    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return LAST_COLUMN_INDEX + 1;
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        final ClientLoad request = data.get(rowIndex);
        switch (columnIndex) {
        case START_TIME_COLUMN_INDEX:
            return request.getStartTime();
        case SERVER_DURATION_COLUMN_INDEX:
            return request.getServerDuration();
        case NETWORK_DURATION_COLUMN_INDEX:
            return request.getNetworkDuration();
        case NUM_CLIENTS_COLUMN_INDEX:
            return request.getNumClients();
        case SERVICE_COLUMN_INDEX:
            return request.getService().getArtifact();
        case NODE_LOAD_COLUMN_INDEX:
            return request.getNodeLoad().toString();
        case NETWORK_LOAD_COLUMN_INDEX:
            return request.getNetworkLoad().toString();
        default:
            return null;
        }

    }

}
