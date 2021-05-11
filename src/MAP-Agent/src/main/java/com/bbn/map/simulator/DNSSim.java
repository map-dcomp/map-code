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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.BiConsumer;

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
import com.bbn.map.utils.WeightedRoundRobin;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableCollection;

/**
 * Simulated DNS.
 */
@ThreadSafe
public abstract class DNSSim implements DNSUpdateService {

    private final Object lock = new Object();

    /**
     * 
     * @return lock to use to protect internal variables
     */
    protected final Object getLock() {
        return lock;
    }

    private final Logger logger;

    private final DNSSim parent;

    /**
     * 
     * @return parent DNS
     */
    protected final DNSSim getParent() {
        return parent;
    }

    private final VirtualClock clock;

    /**
     * 
     * @return clock to use to report time
     */
    protected final VirtualClock getClock() {
        return clock;
    }

    // used to delegate DNS servers
    private final Simulation simulation;

    /**
     * 
     * @return simulation object for getting regional DNS objects
     */
    protected final Simulation getSimulation() {
        return simulation;
    }

    private final RegionIdentifier region;

    /**
     * 
     * @return region of the DNS
     */
    protected final RegionIdentifier getRegion() {
        return region;
    }

    /**
     * Simulate a DNS that has no parent to delegate to.
     * 
     * @param region
     *            {@link #getRegion()}
     * @param simulation
     *            used to get the clock for clock expiration and delegate DNS
     *            servers
     */
    public DNSSim(@Nonnull final Simulation simulation, @Nonnull final RegionIdentifier region) {
        this.region = region;
        this.parent = null;
        this.simulation = simulation;
        this.clock = simulation.getClock();
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + this.region.getName());
    }

    /**
     * Simulate a DNS that has a parent that can be asked about names not stored
     * in this DNS.
     * 
     * @param region
     *            {@link #getRegion()}
     * @param parent
     *            the parent DNS server
     * @param simulation
     *            used to get the clock for clock expiration and delegate DNS
     *            servers
     */
    public DNSSim(@Nonnull final Simulation simulation,
            @Nonnull final RegionIdentifier region,
            @Nonnull final DNSSim parent) {
        this.region = region;
        this.simulation = simulation;
        this.clock = simulation.getClock();
        this.parent = parent;
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + this.region.getName());
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
        logger.info("{}: Simulation time {} - adding a record {} with weight {}", this.region.getName(),
                clock.getCurrentTime(), record, weight);

        synchronized (lock) {
            internalAddRecord(record, weight);
        }
    }

    /**
     * Add a record, the lock is already held.
     * 
     * @param record
     *            the record to add
     * @param weight
     *            the weight for the record
     */
    protected abstract void internalAddRecord(@Nonnull DnsRecord record, double weight);

    /**
     * @param visitor
     *            executed for each record in the DNS
     */
    public abstract void foreachRecord(@Nonnull BiConsumer<DnsRecord, Double> visitor);

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
    public abstract boolean replaceAllRecords(@Nonnull ImmutableCollection<Pair<DnsRecord, Double>> records);

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
    /* package */ abstract DnsRecord lookup(String clientName, @Nonnull ServiceIdentifier<?> service);

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
    public abstract NodeIdentifier resolveService(String clientName, ServiceIdentifier<?> service)
            throws DNSLoopException;

    private final Object logLock = new Object();

    /**
     * @param clientName
     *            the client
     * @param service
     *            the service
     * @param retRecord
     *            the record that was resolved
     */
    protected final void logServiceResolution(final String clientName,
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

                final Path path = outputDirectory.resolve(String.format("dns-%s.csv", this.region.getName()));
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
    protected static final class DnsRecordList extends WeightedRoundRobin<DnsRecord> {
    }

    @Override
    public String toString() {
        return "DNSSim [" + this.region + "]";
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
