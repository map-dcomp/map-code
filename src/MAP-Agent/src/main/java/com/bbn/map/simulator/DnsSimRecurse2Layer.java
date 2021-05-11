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
package com.bbn.map.simulator;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.dns.PlanTranslatorRecurse2Layer;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableCollection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implementation of {@link DNSSim} used with
 * {@link PlanTranslatorRecurse2Layer}.
 * 
 * @author jschewe
 *
 */
public class DnsSimRecurse2Layer extends DNSSim {

    private final Logger logger;

    private final Map<ServiceIdentifier<?>, DnsRecordList> regionEntries = new HashMap<>();
    private final Map<ServiceIdentifier<?>, DnsRecordList> containerEntries = new HashMap<>();

    /**
     * @param region
     *            see {@link DNSSim#DNSSim(Simulation, RegionIdentifier)}
     * @param simulation
     *            see {@link DNSSim#DNSSim(Simulation, RegionIdentifier)}
     */
    public DnsSimRecurse2Layer(@Nonnull final Simulation simulation, @Nonnull final RegionIdentifier region) {
        super(simulation, region);
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + region.getName());
    }

    /**
     * @param region
     *            see
     *            {@link DNSSim#DNSSim(Simulation, RegionIdentifier, DNSSim)}
     * @param parent
     *            see
     *            {@link DNSSim#DNSSim(Simulation, RegionIdentifier, DNSSim)}
     * @param simulation
     *            see
     *            {@link DNSSim#DNSSim(Simulation, RegionIdentifier, DNSSim)}
     */
    public DnsSimRecurse2Layer(@Nonnull final Simulation simulation,
            @Nonnull final RegionIdentifier region,
            @Nonnull final DNSSim parent) {
        super(simulation, region, parent);
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + region.getName());
    }

    /**
     * If the number of times that a DNS server is touched trying to resolve a
     * service gets greater than this number, throw a {@link DNSLoopException}.
     */
    private static final int DNS_SERVER_LOOP_LIMIT = 10;

    @Override
    public NodeIdentifier resolveService(final String clientName, final ServiceIdentifier<?> service)
            throws DNSLoopException {
        logger.trace("Top of resolve service for {}", service);

        final Map<DNSSim, Integer> checked = new HashMap<>();
        final LinkedList<DNSSim> serversChecked = new LinkedList<>();

        NameRecord retRecord = null;
        DNSSim dns = this;
        while (null == retRecord) {
            final int dnsLoopValue = checked.merge(dns, 1, Integer::sum);
            serversChecked.add(dns);
            if (dnsLoopValue > DNS_SERVER_LOOP_LIMIT) {
                throw new DNSLoopException("DNS Loop detected for service: " + service + " counts per server: "
                        + checked + " most recent dns: " + dns + " order of DNS servers checked: " + serversChecked);
            }

            final DnsRecord record = dns.lookup(clientName, service);
            if (null == record) {
                // can't continue checking
                break;
            }

            if (record instanceof NameRecord) {
                retRecord = (NameRecord) record;
            } else if (record instanceof DelegateRecord) {
                final DelegateRecord delegateRecord = (DelegateRecord) record;
                dns = getSimulation().getRegionalDNS(delegateRecord.getDelegateRegion());
                logger.trace("Delegating to {} for {}", delegateRecord.getDelegateRegion(), service);
            } else {
                throw new IllegalArgumentException("Unknown DNS record type: " + record.getClass().getName());
            }
        }

        if (null == retRecord) {
            // cannot resolve the name
            return null;
        } else {
            logServiceResolution(clientName, service, retRecord);

            return retRecord.getNode();
        }

    }

    @Override
    protected DnsRecord lookup(final String clientName, @Nonnull final ServiceIdentifier<?> service) {
        synchronized (getLock()) {
            // check the region delegates first
            final DnsRecordList regionState = regionEntries.get(service);
            if (null != regionState) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Finding region record for {} in {}", service, regionState);
                }

                final DnsRecord regionRecord = regionState.getNextRecord();
                if (regionRecord instanceof DelegateRecord) {
                    final DelegateRecord delegate = (DelegateRecord) regionRecord;
                    if (delegate.getDelegateRegion().equals(getRegion())) {
                        // return a container in this region
                        final DnsRecordList containerState = containerEntries.get(service);

                        if (null != containerState) {
                            if (logger.isTraceEnabled()) {
                                logger.trace("Finding container record for {} in {}", service, regionState);
                            }
                            final DnsRecord containerRecord = containerState.getNextRecord();
                            return containerRecord;
                        } else {
                            logger.warn(
                                    "Found region record for the local region, but no container record. service: {} regionEntries: {}[ containerEntries: {}",
                                    service, regionEntries, containerEntries);
                        }

                    } else {
                        // delegate to another region
                        return regionRecord;
                    }
                } else {
                    logger.warn("Found non-delegate record in the region record lookups, this is unexpected");
                    return regionRecord;
                }
            } else {
                // check the list of containers
                final DnsRecordList containerState = containerEntries.get(service);

                if (null != containerState) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Finding container record for {}", service);
                    }
                    final DnsRecord containerRecord = containerState.getNextRecord();
                    return containerRecord;
                }
            }
        }

        // didn't find it locally, check the parent if one exists
        if (null != getParent()) {
            logger.trace("Checking parent for for {} region entries: {} container entries: {}", service, regionEntries,
                    containerEntries);

            final DnsRecord record = getParent().lookup(clientName, service);
            return record;
        } else {
            return null;
        }
    }

    @Override
    @SuppressFBWarnings(value = "UC_USELESS_OBJECT", justification = "Copying map to avoid long synchronization section")
    public void foreachRecord(@Nonnull final BiConsumer<DnsRecord, Double> visitor) {
        final Map<ServiceIdentifier<?>, DnsRecordList> regionEntriesCopy;
        final Map<ServiceIdentifier<?>, DnsRecordList> containerEntriesCopy;
        synchronized (getLock()) {
            // copy to avoid the visitor from blocking
            regionEntriesCopy = new HashMap<>(regionEntries);
            containerEntriesCopy = new HashMap<>(containerEntries);
        }

        regionEntriesCopy.forEach((fqdn, recordList) -> {
            recordList.foreachRecord(visitor);
        });
        containerEntriesCopy.forEach((fqdn, recordList) -> {
            recordList.foreachRecord(visitor);
        });
    }

    @Override
    protected void internalAddRecord(@Nonnull final DnsRecord record, final double weight) {
        final ServiceIdentifier<?> service = record.getService();

        final Map<ServiceIdentifier<?>, DnsRecordList> entries;
        if (record instanceof DelegateRecord) {
            entries = regionEntries;
        } else {
            entries = containerEntries;
        }
        final DnsRecordList state = entries.computeIfAbsent(service, v -> new DnsRecordList());
        state.addRecord(record, weight);
    }

    @Override
    public boolean replaceAllRecords(@Nonnull final ImmutableCollection<Pair<DnsRecord, Double>> records) {
        synchronized (getLock()) {
            logger.info("{}: simulation time {} - Replacing all records with {}", getRegion(),
                    getClock().getCurrentTime(), records);

            regionEntries.clear();
            containerEntries.clear();
            records.forEach(rec -> internalAddRecord(rec.getLeft(), rec.getRight()));

            logger.trace("Finished with replacement of records region entries {} container entries {}", regionEntries,
                    containerEntries);
        }
        return true;
    }

}
