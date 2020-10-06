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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.utils.WeightedRoundRobin;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableCollection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Simulated DNS.
 */
@ThreadSafe
public class DNSSim implements DNSUpdateService {

    private final Object lock = new Object();

    private final Logger logger;

    private final DNSSim parent;
    /**
     * fqdn -> region -> state
     */
    private final Map<ServiceIdentifier<?>, DnsRecordList> entries = new HashMap<>();
    private final VirtualClock clock;
    // used to get the clock and delegate DNS servers
    private final Simulation simulation;
    private final String name;

    /**
     * Simulate a DNS that has no parent to delegate to.
     * 
     * @param name
     *            of the DNS for logging
     * @param simulation
     *            used to get the clock for clock expiration and delegate DNS
     *            servers
     */
    public DNSSim(@Nonnull final Simulation simulation, @Nonnull final String name) {
        this.name = name;
        this.parent = null;
        this.simulation = simulation;
        this.clock = simulation.getClock();
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + name);
    }

    /**
     * Simulate a DNS that has a parent that can be asked about names not stored
     * in this DNS.
     * 
     * @param name
     *            of the DNS for logging
     * @param parent
     *            the parent DNS server
     * @param simulation
     *            used to get the clock for clock expiration and delegate DNS
     *            servers
     */
    public DNSSim(@Nonnull final Simulation simulation, @Nonnull final String name, @Nonnull final DNSSim parent) {
        this.name = name;
        this.simulation = simulation;
        this.clock = simulation.getClock();
        this.parent = parent;
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + name);
    }

    /**
     * Add a DNS record. Calling {@link #replaceAllRecords(ImmutableCollection)}
     * on this instance will replace this record.
     * 
     * @param record
     *            the record to add
     * @param weight
     *            the weight of the record
     */
    public void addRecord(@Nonnull final DnsRecord record, final double weight) {
        logger.info("{}: Simulation time {} - adding a record {} with weight {}", name, clock.getCurrentTime(), record,
                weight);

        synchronized (lock) {
            internalAddRecord(record, weight);
        }
    }

    private void internalAddRecord(@Nonnull final DnsRecord record, final double weight) {
        final ServiceIdentifier<?> service = record.getService();
        final DnsRecordList state = entries.computeIfAbsent(service, v -> new DnsRecordList());
        state.addRecord(record, weight);
    }

    /**
     * @param visitor
     *            executed for each record in the DNS
     */
    @SuppressFBWarnings(value = "UC_USELESS_OBJECT", justification = "Copying map to avoid long synchronization section")
    public void foreachRecord(@Nonnull final BiConsumer<DnsRecord, Double> visitor) {
        final Map<ServiceIdentifier<?>, DnsRecordList> entriesCopy;
        synchronized (lock) {
            // copy to avoid the visitor from blocking
            entriesCopy = new HashMap<>(entries);
        }

        entriesCopy.forEach((fqdn, recordList) -> {
            recordList.foreachRecord(visitor);
        });
    }

    /**
     * 
     * @return the current list of records as a string.
     */
    public String recordsToString() {
        final StringBuilder builder = new StringBuilder();
        foreachRecord((fqdn, recordList) -> {
            builder.append(fqdn + " -> " + recordList);
        });
        return builder.toString();
    }

    @Override
    public boolean replaceAllRecords(@Nonnull final ImmutableCollection<Pair<DnsRecord, Double>> records) {
        synchronized (lock) {
            logger.info("{}: simulation time {} - Replacing all records with {}", name, clock.getCurrentTime(),
                    records);

            entries.clear();
            records.forEach(rec -> internalAddRecord(rec.getLeft(), rec.getRight()));

            logger.trace("Finished with replacement of records entries {}", entries);
        }
        return true;
    }

    /**
     * Find a DNS record. This will check the parent DNS if not found locally.
     *
     * Package visible for testing.
     * 
     * @param clientName
     *            the client that originated the DNS request, used to determine
     *            which record to return
     * @param service
     *            the service to lookup
     * @return the record found, will be null if not found in this DNS or it's
     *         parent
     */
    /* package */ DnsRecord lookup(final String clientName, @Nonnull final ServiceIdentifier<?> service) {
        synchronized (lock) {
            final DnsRecordList state = entries.get(service);
            if (null != state) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Finding record for {} in {}", service, state);
                }
                final DnsRecord record = state.getNextRecord();

                return record;
            }
        }

        // didn't find it locally, check the parent if one exists
        if (null != parent) {
            logger.trace("Checking parent for for {} entries: {}", service, entries);

            final DnsRecord record = parent.lookup(clientName, service);
            return record;
        } else {
            return null;
        }
    }

    /**
     * Map FQDNs to MAP nodes. This method will follow {@link DelegateRecord}s
     * until a {@link NameRecord} is found.
     * 
     * @param clientName
     *            passed to {@link #lookup(String, String)}
     * @param service
     *            the service to find the node for
     * @return the node for this fqdn or null if not found
     * @throws DNSLoopException
     *             if there is a loop in the DNS setup
     */
    public NodeIdentifier resolveService(final String clientName, final ServiceIdentifier<?> service)
            throws DNSLoopException {
        logger.trace("Top of resolve service for {}", service);

        final List<DNSSim> checked = new LinkedList<>();

        NameRecord retRecord = null;
        DNSSim dns = this;
        while (null == retRecord) {
            if (checked.contains(dns)) {
                throw new DNSLoopException("DNS Loop detected for service: " + service + " checked entries: " + checked
                        + " most recent dns: " + dns);
            }

            final DnsRecord record = dns.lookup(clientName, service);
            if (null == record) {
                // can't continue checking
                break;
            }
            checked.add(dns);

            if (record instanceof NameRecord) {
                retRecord = (NameRecord) record;
            } else if (record instanceof DelegateRecord) {
                final DelegateRecord delegateRecord = (DelegateRecord) record;
                dns = simulation.getRegionalDNS(delegateRecord.getDelegateRegion());
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

    private final Object logLock = new Object();

    private void logServiceResolution(final String clientName,
            final ServiceIdentifier<?> service,
            final NameRecord retRecord) {
        // synchronize to keep log consistent
        synchronized (logLock) {
            if (null != outputDirectory) {
                final long now = clock.getCurrentTime();

                final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                        .getApplicationSpecification((ApplicationCoordinates) service);
                Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

                final String row = String.format("%d,%s,%s,%s", now, clientName, appSpec.getServiceHostname(),
                        retRecord.getNode().getName());

                final Path path = outputDirectory.resolve(String.format("dns-%s.csv", name));
                final boolean writeHeader = !Files.exists(path);

                try (BufferedWriter logFileWriter = Files.newBufferedWriter(path, Charset.defaultCharset(),
                        StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                    if (writeHeader) {
                        logFileWriter.write("timestamp, clientAddress, name_to_resolve, resolved_name");
                        logFileWriter.newLine();
                    }

                    logFileWriter.write(row);
                    logFileWriter.newLine();
                } catch (final IOException e) {
                    logger.error("Error writing row '{}' to logfile: {}", row, e.getMessage(), e);
                }
            }
        }
    }

    private Path outputDirectory = null;

    /**
     * Specify the output directory for dns logs.
     * 
     * @param outputDirectory
     *            the new output directory, null for don't write logs
     */
    public synchronized void setBaseOutputDirectory(final Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Store information about multiple records that may exist for a name. Each
     * record has a weight and the record to use is chosen from the list based
     * on the weight.
     */
    private static final class DnsRecordList extends WeightedRoundRobin<DnsRecord> {
        DnsRecordList() {
            super(AgentConfiguration.getInstance().getDnsWeightPrecision());
        }
    }

    @Override
    public String toString() {
        return "DNSSim [" + name + "]";
    }

    /**
     * Thrown when a DNS loop is detected.
     * 
     * @author jschewe
     *
     */
    public static class DNSLoopException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * 
         * @param message
         *            passed to the parent
         */
        public DNSLoopException(final String message) {
            super(message);
        }
    }
}
