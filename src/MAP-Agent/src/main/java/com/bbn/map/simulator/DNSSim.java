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
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

/**
 * Simulated DNS.
 */
@ThreadSafe
public class DNSSim implements DNSUpdateService {

    /**
     * @return a logger that shows the name of the DNS
     */
    @Nonnull
    private Logger getLogger() {
        final String loggerName = DNSSim.class.getName() + "." + name;
        return LoggerFactory.getLogger(loggerName);
    }

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
    public synchronized void addRecord(@Nonnull final DnsRecord record, final double weight) {
        getLogger().info("{}: Simulation time {} - adding a record {} with weight {}", name, clock.getCurrentTime(),
                record, weight);

        internalAddRecord(record, weight);
    }

    private void internalAddRecord(@Nonnull final DnsRecord record, final double weight) {
        final ServiceIdentifier<?> service = record.getService();
        final DnsRecordList state = entries.computeIfAbsent(service, v -> new DnsRecordList());
        state.addRecord(record, weight);
    }

    /**
     * Used to visit records in {@link DNSSim#foreachRecord(DNSSim.RecordVisitor)} and
     * {@link DNSSim#foreachCachedRecord(DNSSim.RecordVisitor)}.
     * 
     * @author jschewe
     *
     */
    public interface RecordVisitor {
        /**
         * 
         * @param record
         *            the record being visited
         * @param weight
         *            the weight of the record
         */
        void visit(DnsRecord record, double weight);
    }

    /**
     * @param visitor
     *            executed for each record in the DNS
     */
    public synchronized void foreachRecord(@Nonnull final RecordVisitor visitor) {
        entries.forEach((fqdn, recordList) -> {
            recordList.getAllRecords().forEach(pair -> {
                visitor.visit(pair.getKey(), pair.getValue());
            });
        });
    }

    @Override
    public synchronized boolean replaceAllRecords(@Nonnull final ImmutableCollection<Pair<DnsRecord, Double>> records) {
        getLogger().info("{}: simulation time {} - Replacing all records with {}", name, clock.getCurrentTime(),
                records);

        entries.clear();
        records.forEach(rec -> internalAddRecord(rec.getLeft(), rec.getRight()));
        return true;
    }

    /**
     * Find a DNS record. This will check the parent DNS if not found locally.
     * The cache is first checked and then the entries and then the parent.
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
        final DnsRecordList state = entries.get(service);
        if (null != state) {
            if (getLogger().isTraceEnabled()) {
                getLogger().trace("Finding record for {} in {}", service, state);
            }
            final DnsRecord record = state.getNextRecord();

            return record;
        }

        // didn't find it locally, check the parent if one exists
        if (null != parent) {
            final DnsRecord record = parent.lookup(clientName, service);

            // If we needed to contact another DNS server to get the record,
            // cache it
            addToCache(record);

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
    public synchronized NodeIdentifier resolveService(final String clientName, final ServiceIdentifier<?> service)
            throws DNSLoopException {
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

            if (dns != this) {
                // If we needed to contact another DNS server to get the record,
                // cache it
                addToCache(record);
            }

            if (record instanceof NameRecord) {
                retRecord = (NameRecord) record;
            } else if (record instanceof DelegateRecord) {
                final DelegateRecord delegateRecord = (DelegateRecord) record;
                dns = simulation.getRegionalDNS(delegateRecord.getDelegateRegion());
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

    private void logServiceResolution(final String clientName,
            final ServiceIdentifier<?> service,
            final NameRecord retRecord) {
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
                getLogger().error("Error writing row '{}' to logfile: {}", row, e.getMessage(), e);
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

    private void addToCache(final DnsRecord record) {
        // final long now = clock.getCurrentTime();
        // final long expiration = now +
        // Duration.ofSeconds(record.getTtl()).toMillis();
        // if (getLogger().isTraceEnabled()) {
        // getLogger().trace("Adding record to cache: {}", record);
        // }
        // cache.put(record.getFqdn(), new CacheEntry(record, expiration));
        getLogger().trace("Skipping cache for now as this isn't working like we want");
    }

    /**
     * Store information about multiple records that may exist for a name. Each
     * record has a weight and the record to use is randomly chosen from the
     * list based on the weight.
     */
    @ThreadSafe
    private static final class DnsRecordList {
        DnsRecordList() {
        }

        private final List<Pair<DnsRecord, Double>> records = new LinkedList<>();
        private double totalWeight = 0;

        /**
         * @param record
         *            record to add to the list for this fqdn
         * @param weight
         *            the weight of the record
         */
        public synchronized void addRecord(final DnsRecord record, final double weight) {
            records.add(Pair.of(record, weight));
            totalWeight += weight;
            recomputeUseWeight();
        }

        private List<AtomicLong> recordUseWeight = new LinkedList<>();
        private int recordUseWeightIndex = 0;

        private void recomputeUseWeight() {
            boolean foundNonZeroWeight = false;
            recordUseWeight.clear();
            for (final Pair<DnsRecord, Double> pair : records) {
                //FIXME move 100 to AgentConfiguration
                final long w = Math.round(pair.getRight() / totalWeight * 100);
                if (w > 0) {
                    foundNonZeroWeight = true;
                }
                recordUseWeight.add(new AtomicLong(w));
            }
            if (!foundNonZeroWeight) {
                throw new RuntimeException("Internal error, computed all zero weights!");
            }
        }

        /**
         * 
         * @return the list of all records, used for display
         */
        public synchronized ImmutableList<Pair<DnsRecord, Double>> getAllRecords() {
            return ImmutableList.copyOf(records);
        }

        /**
         * @return the next record to use, will be null if there are no records
         */
        public synchronized DnsRecord getNextRecord() {
            if (records.isEmpty()) {
                return null;
            } else {
                final int startingIndex = recordUseWeightIndex;

                // loop until we find a use value that is greater than 0
                while (true) {
                    final int recordIndex = recordUseWeightIndex;
                    final AtomicLong use = recordUseWeight.get(recordIndex);

                    // always increment index
                    incrementRecordIndex();

                    if (use.get() > 0) {
                        // found record to use
                        final DnsRecord record = records.get(recordIndex).getLeft();

                        // note that it's been used
                        use.decrementAndGet();
                        return record;
                    }

                    if (startingIndex == recordUseWeightIndex) {
                        // looped, everything must be zero, reset the data
                        recomputeUseWeight();
                    }
                }
            }
        }

        private void incrementRecordIndex() {
            ++recordUseWeightIndex;
            if (recordUseWeightIndex >= records.size()) {
                recordUseWeightIndex = 0;
            }
        }

        @Override
        public String toString() {
            return "DnsRecordList [" + " records: " + records + " ]";
        }

    }

    @Override
    public String toString() {
        // return "DNSSim [" + name + " " + " cache: " + cache + " entries: " +
        // entries + " parent: " + parent + " ]";
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
