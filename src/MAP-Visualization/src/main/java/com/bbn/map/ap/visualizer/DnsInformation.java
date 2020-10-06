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
package com.bbn.map.ap.visualizer;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.simulator.DNSSim;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.visualizer.ScenarioVisualizer;

/**
 * Display information about a {@link DNSSim} object.
 * 
 * @author jschewe
 *
 */
/* package */ class DnsInformation extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DNSSim dns;
    private final Timer updateTimer;
    private final DefaultListModel<String> entriesModel = new DefaultListModel<>();
    private List<String> prevEntries = new LinkedList<>();

    /* package */ DnsInformation(@Nonnull final RegionIdentifier region, @Nonnull final DNSSim dns) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.dns = dns;

        add(new JLabel("Region: " + region.getName()));

        final JList<String> entriesList = new JList<>(entriesModel);
        final ExpandablePanel entriesExpand = new ExpandablePanel("Entries", entriesList);
        add(entriesExpand);

        updateTimer = new Timer(ScenarioVisualizer.REFRESH_RATE, e -> update());
        updateTimer.start();
    }

    /**
     * Stop the refresh of the panel. Once stopped, it cannot be restarted.
     */
    public void shutdown() {
        updateTimer.stop();
    }

    private void update() {
        final List<String> newEntries = new LinkedList<>();
        dns.foreachRecord((record, weight) -> {
            newEntries.add(recordToString(record));
        });

        if (!newEntries.equals(prevEntries)) {
            // only update the display if something changed
            entriesModel.clear();
            newEntries.forEach(s -> entriesModel.addElement(s));
            prevEntries = newEntries;
        }

    }

    private String recordToString(final DnsRecord record) {
        final StringBuilder builder = new StringBuilder();
        builder.append(record.getService());
        builder.append(" -> ");
        if (record instanceof NameRecord) {
            final NameRecord nrecord = (NameRecord) record;
            builder.append(nrecord.getNode().getName());
        } else if (record instanceof DelegateRecord) {
            final DelegateRecord drecord = (DelegateRecord) record;
            builder.append("region ");
            builder.append(drecord.getDelegateRegion().getName());
        } else {
            builder.append("Unknown record type");
        }
        return builder.toString();
    }

}
