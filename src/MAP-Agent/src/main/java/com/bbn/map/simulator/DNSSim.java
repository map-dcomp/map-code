/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
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
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, DnsRecordList>> entries = new HashMap<>();
    private final Map<ServiceIdentifier<?>, CacheEntry> cache = new HashMap<>();
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

    @Override
    public synchronized void addRecord(@Nonnull final DnsRecord record) {
        getLogger().info("{}: Simulation time {} - adding a record {}", name, clock.getCurrentTime(), record);

        internalAddRecord(record);
    }

    private void internalAddRecord(@Nonnull final DnsRecord record) {
        final ServiceIdentifier<?> service = record.getService();
        final RegionIdentifier sourceRegion = record.getSourceRegion();
        final Map<RegionIdentifier, DnsRecordList> stateMap = entries.computeIfAbsent(service,
                v -> new HashMap<RegionIdentifier, DnsRecordList>());
        final DnsRecordList state = stateMap.computeIfAbsent(sourceRegion, v -> new DnsRecordList());
        state.addRecord(record);
    }

    /**
     * Used to visit records in {@link DNSSim#foreachRecord(RecordVisitor)} and
     * {@link DNSSim#foreachCachedRecord(RecordVisitor)}.
     * 
     * @author jschewe
     *
     */
    public interface RecordVisitor {
        /**
         * 
         * @param record
         *            the record being visited
         */
        void visit(DnsRecord record);
    }

    /**
     * @param visitor
     *            executed for each record in the DNS
     */
    public synchronized void foreachRecord(@Nonnull final RecordVisitor visitor) {
        entries.forEach((fqdn, stateMap) -> {
            stateMap.forEach((sourceRegion, recordList) -> {
                recordList.getAllRecords().forEach(record -> {
                    visitor.visit(record);
                });
            });
        });
    }

    /**
     * @param visitor
     *            executed for each record in the cache
     */
    public synchronized void foreachCachedRecord(@Nonnull final RecordVisitor visitor) {
        cache.forEach((fqdn, cacheEntry) -> {
            visitor.visit(cacheEntry.getRecord());
        });
    }

    @Override
    public synchronized void removeRecord(@Nonnull final DnsRecord record) {
        final ServiceIdentifier<?> service = record.getService();
        final RegionIdentifier sourceRegion = record.getSourceRegion();
        final Map<RegionIdentifier, DnsRecordList> stateMap = entries.get(service);
        if (null != stateMap) {
            final DnsRecordList state = stateMap.get(sourceRegion);
            if (null != state) {
                state.removeRecord(record);
                if (state.getNumRecords() < 1) {
                    stateMap.remove(sourceRegion);
                }
            }
            if (stateMap.isEmpty()) {
                entries.remove(service);
            }
        }
    }

    @Override
    public synchronized void replaceAllRecords(@Nonnull final ImmutableCollection<DnsRecord> records) {
        getLogger().info("{}: simulation time {} - Replacing all records with {}", name, clock.getCurrentTime(),
                records);

        entries.clear();
        records.forEach(rec -> internalAddRecord(rec));
    }

    /**
     * Find a DNS record. This will check the parent DNS if not found locally.
     * The cache is first checked and then the entries and then the parent.
     * 
     * @param clientRegion
     *            the region where the DNS request originated, used to determine
     *            which record to return
     * @param service
     *            the service to lookup
     * @return the record found, will be null if not found in this DNS or it's
     *         parent
     */
    public synchronized DnsRecord lookup(final RegionIdentifier clientRegion,
            @Nonnull final ServiceIdentifier<?> service) {
        final CacheEntry cacheEntry = cache.get(service);
        if (null != cacheEntry) {
            final long now = clock.getCurrentTime();
            if (now > cacheEntry.getExpiration()) {
                if (getLogger().isTraceEnabled()) {
                    getLogger().trace("Removing record from cache: {}", cacheEntry.getRecord());
                }

                cache.remove(service);
            } else {
                return cacheEntry.getRecord();
            }
        }

        final Map<RegionIdentifier, DnsRecordList> stateMap = entries.get(service);
        if (null != stateMap) {
            final DnsRecordList state = stateMap.getOrDefault(clientRegion, stateMap.get(null));
            if (null != state) {
                if (getLogger().isTraceEnabled()) {
                    getLogger().trace("Finding record for {} in {}", service, state);
                }
                return state.getNextRecord();
            }
        }

        // didn't find it locally, check the parent if one exists
        if (null != parent) {
            final DnsRecord record = parent.lookup(clientRegion, service);

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
     * @param clientRegion
     *            passed to {@link #lookup(RegionIdentifier, String)}
     * @param service
     *            the service to find the node for
     * @return the node for this fqdn or null if not found
     * @throws DNSLoopException
     *             if there is a loop in the DNS setup
     */
    public synchronized NodeIdentifier resolveService(final RegionIdentifier clientRegion,
            final ServiceIdentifier<?> service) throws DNSLoopException {
        final List<DNSSim> checked = new LinkedList<>();

        NameRecord retRecord = null;
        DNSSim dns = this;
        while (null == retRecord) {
            if (checked.contains(dns)) {
                throw new DNSLoopException("DNS Loop detected for service: " + service + " checked entries: " + checked
                        + " most recent dns: " + dns);
            }

            final DnsRecord record = dns.lookup(clientRegion, service);
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
            return retRecord.getNode();
        }

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
     * DNS cache entry.
     */
    private static final class CacheEntry {
        /**
         * @param record
         *            the record to cache
         * @param expiration
         *            when this cache entry expires, uses the
         *            {@link VirtualClock}
         */
        CacheEntry(final DnsRecord record, final long expiration) {
            this.record = record;
            this.expiration = expiration;
        }

        private final DnsRecord record;

        /**
         * @return the cached record
         */
        @Nonnull
        public DnsRecord getRecord() {
            return record;
        }

        private final long expiration;

        /**
         * 
         * @return when this cache entry expires
         */
        public long getExpiration() {
            return expiration;
        }

        @Override
        public String toString() {
            return "CacheEntry [" + " record: " + record + " expiration: " + expiration + " ]";
        }
    }

    /**
     * Store information about multiple records that may exist for a name and
     * which one was returned last. This implements a simple round robin DNS.
     */
    @ThreadSafe
    private static final class DnsRecordList {
        DnsRecordList() {
            this.recordIndex = 0;
        }

        private int recordIndex;

        private final List<DnsRecord> records = new LinkedList<>();

        /**
         * @param record
         *            record to add to the list for this fqdn
         */
        public synchronized void addRecord(final DnsRecord record) {
            records.add(record);
        }

        /**
         * Remove a single occurrence of this record.
         * 
         * @param record
         *            the record to remove
         * @see List#remove(Object)
         */
        public synchronized void removeRecord(final DnsRecord record) {
            records.remove(record);
            if (recordIndex >= records.size()) {
                recordIndex = 0;
            }
        }

        /**
         * @return the number of records stored
         */
        public synchronized int getNumRecords() {
            return records.size();
        }

        /**
         * 
         * @return the list of all records, used for display
         */
        public synchronized ImmutableList<DnsRecord> getAllRecords() {
            return ImmutableList.copyOf(records);
        }

        /**
         * @return the next record in the sequence, will be null if there are no
         *         records
         */
        public synchronized DnsRecord getNextRecord() {
            if (records.isEmpty()) {
                return null;
            } else {
                final DnsRecord record = records.get(recordIndex);
                if (LoggerFactory.getLogger(DnsRecordList.class).isTraceEnabled()) {
                    LoggerFactory.getLogger(DnsRecordList.class)
                            .trace("DnsRecordList returning record with index {} -> {}", recordIndex, record);
                }

                ++recordIndex;
                if (recordIndex >= records.size()) {
                    recordIndex = 0;
                }
                return record;
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
