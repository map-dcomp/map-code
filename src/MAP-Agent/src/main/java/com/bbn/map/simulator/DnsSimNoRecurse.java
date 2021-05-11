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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.dns.PlanTranslatorNoRecurse;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableCollection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implementation of {@link DNSSim} used with {@link PlanTranslatorNoRecurse}.
 * 
 * @author jschewe
 *
 */
public class DnsSimNoRecurse extends DNSSim {

    private final Logger logger;

    /**
     * @param region
     *            see {@link DNSSim#DNSSim(Simulation, RegionIdentifier)}
     * @param simulation
     *            see {@link DNSSim#DNSSim(Simulation, RegionIdentifier)}
     */
    public DnsSimNoRecurse(@Nonnull final Simulation simulation, @Nonnull final RegionIdentifier region) {
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
    public DnsSimNoRecurse(@Nonnull final Simulation simulation,
            @Nonnull final RegionIdentifier region,
            @Nonnull final DNSSim parent) {
        super(simulation, region, parent);
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + region.getName());
    }

    /**
     * Delegate to another region.
     */
    private final Map<ServiceIdentifier<?>, DnsRecordList> delegateEntries = new HashMap<>();

    /**
     * Lookup in the current region.
     */
    private final Map<ServiceIdentifier<?>, DnsRecordList> nameEntries = new HashMap<>();

    @Override
    protected DnsRecord lookup(final String clientName, @Nonnull final ServiceIdentifier<?> service) {
        synchronized (getLock()) {
            final DnsRecordList delegateState = delegateEntries.get(service);
            if (null != delegateState) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Finding delegate record for {} in {}", service, delegateState);
                }
                final DnsRecord record = delegateState.getNextRecord();

                return record;
            }
        }

        // no delegate, use the names
        final DnsRecord nameRecord = lookupDirect(clientName, service);
        if (null != nameRecord) {
            return nameRecord;
        }

        // didn't find it locally, check the parent if one exists
        if (null != getParent()) {
            logger.trace("Checking parent for {} delegate entries: {} name entries: {}", service, delegateEntries,
                    nameEntries);

            final DnsRecord record = getParent().lookup(clientName, service);
            return record;
        } else {
            return null;
        }
    }

    @Override
    protected void internalAddRecord(@Nonnull final DnsRecord record, final double weight) {
        final ServiceIdentifier<?> service = record.getService();

        final DnsRecordList state;
        if (record instanceof DelegateRecord) {
            state = delegateEntries.computeIfAbsent(service, v -> new DnsRecordList());
        } else if (record instanceof NameRecord) {
            state = nameEntries.computeIfAbsent(service, v -> new DnsRecordList());
        } else {
            throw new RuntimeException("Unknown type of DNS record: " + record.getClass());
        }

        state.addRecord(record, weight);
    }

    @Override
    @SuppressFBWarnings(value = "UC_USELESS_OBJECT", justification = "Copying map to avoid long synchronization section")
    public void foreachRecord(@Nonnull final BiConsumer<DnsRecord, Double> visitor) {
        final Map<ServiceIdentifier<?>, DnsRecordList> nameCopy;
        final Map<ServiceIdentifier<?>, DnsRecordList> delegateCopy;
        synchronized (getLock()) {
            // copy to avoid the visitor from blocking
            nameCopy = new HashMap<>(nameEntries);
            delegateCopy = new HashMap<>(delegateEntries);
        }

        Stream.concat(nameCopy.entrySet().stream(), delegateCopy.entrySet().stream()).forEach(e -> {
            e.getValue().foreachRecord(visitor);
        });
    }

    @Override
    public boolean replaceAllRecords(@Nonnull final ImmutableCollection<Pair<DnsRecord, Double>> records) {
        synchronized (getLock()) {
            logger.info("{}: simulation time {} - Replacing all records with {}", getRegion(),
                    getClock().getCurrentTime(), records);

            delegateEntries.clear();
            nameEntries.clear();
            records.forEach(rec -> internalAddRecord(rec.getLeft(), rec.getRight()));

            logger.trace("Finished with replacement of records delegate entries {} name entries {}", delegateEntries,
                    nameEntries);
        }
        return true;
    }

    @Override
    public NodeIdentifier resolveService(final String clientName, final ServiceIdentifier<?> service)
            throws DNSLoopException {
        logger.trace("Top of resolve service for {}", service);

        NameRecord retRecord = null;
        final DnsRecord record = lookup(clientName, service);
        if (null == record) {
            // cannot resolve
            logger.debug("Cannot resolve " + service);
            return null;
        }

        if (record instanceof NameRecord) {
            retRecord = (NameRecord) record;
        } else if (record instanceof DelegateRecord) {
            final DelegateRecord delegateRecord = (DelegateRecord) record;
            final DnsSimNoRecurse delegateDns = (DnsSimNoRecurse) getSimulation()
                    .getRegionalDNS(delegateRecord.getDelegateRegion());
            logger.trace("Delegating to {} for {}", delegateRecord.getDelegateRegion(), service);
            final DnsRecord delegateLookup = delegateDns.lookupDirect(clientName, service);
            if (delegateLookup instanceof NameRecord) {
                retRecord = (NameRecord) delegateLookup;
            } else if (null == delegateLookup) {
                // cannot resolve
                logger.debug("Cannot resolve " + service + " using delegate " + delegateDns);
                return null;
            } else {
                throw new RuntimeException(
                        "Internal error a delegate record should always resolve to a NameRecord service: " + service
                                + " misbehaving DNS: " + delegateDns);
            }
        } else {
            throw new IllegalArgumentException("Unknown DNS record type: " + record.getClass().getName());
        }

        logServiceResolution(clientName, service, retRecord);

        return retRecord.getNode();
    }

    /**
     * Find a DNS record for a service without delegating to another region.
     * 
     * @param clientName
     *            see {@link #lookup(String, ServiceIdentifier)}
     * @param service
     *            see {@link #lookup(String, ServiceIdentifier)}
     * @return the record found, will be null if there is no entry for the
     *         service in this DNS or it's parent
     */
    /* package */ DnsRecord lookupDirect(final String clientName, @Nonnull final ServiceIdentifier<?> service) {
        synchronized (getLock()) {
            final DnsRecordList nameState = nameEntries.get(service);
            if (null != nameState) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Finding name record for {} in {}", service, nameState);
                }
                final DnsRecord record = nameState.getNextRecord();

                return record;
            }
        }

        // didn't find it locally, check the parent if one exists
        if (null != getParent()) {
            logger.trace("Checking parent for {} directly. Name entries: {}", service, delegateEntries, nameEntries);

            final DnsRecord record = ((DnsSimNoRecurse) getParent()).lookupDirect(clientName, service);
            return record;
        } else {
            return null;
        }
    }
}
