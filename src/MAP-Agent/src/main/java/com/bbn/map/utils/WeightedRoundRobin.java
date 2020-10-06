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
package com.bbn.map.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implements a weighted round robin.
 * 
 * @param <T>
 *            the type of object being stored. This type needs an equals method
 *            defined that can be used to find it in the list of records without
 *            considering the weight. This is used to remove a record and to
 *            change a weight.
 * 
 * @author jschewe
 *
 */
@ThreadSafe
public class WeightedRoundRobin<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedRoundRobin.class);

    /**
     * Create a weighted round robin with the specified precision. Internally
     * the weights are stored as an integer between 0 and precision. The larger
     * the number, the closer to the desired weight. However with large numbers
     * it takes a long time to get through all of the possible values.
     * 
     * @param precision
     *            the precision for the weight computation
     */
    public WeightedRoundRobin(final long precision) {
        weightPrecision = precision;
    }

    private final long weightPrecision;

    private int recordUseWeightIndex = 0;

    private void recomputeUseWeight() {
        boolean foundNonZeroWeight = false;
        for (final RecordData<T> data : records) {
            final long w = Math.round(data.weight / totalWeight * weightPrecision);
            if (w > 0) {
                foundNonZeroWeight = true;
            }
            data.useCount = w;
        }
        if (!foundNonZeroWeight) {
            throw new RuntimeException("Internal error, computed all zero weights!");
        }

        LOGGER.trace("After recomputing use weight totalWeight: {} weightPrecision: {} recordCount: {} records: {}",
                totalWeight, weightPrecision, records.size(), records);
    }

    private final Map<T, Integer> recordIndexMap = new HashMap<>();
    private final List<RecordData<T>> records = new ArrayList<>();
    private double totalWeight = 0;

    /**
     * This method will add a new record or increase the weight of an existing
     * record if one already exists.
     * 
     * @param record
     *            record to add to the list
     * @param weight
     *            the weight of the record
     */
    public synchronized void addRecord(final T record, final double weight) {
        LOGGER.trace("Adding record {} with weight {}", record, weight);
        
        final Integer recordIndex = recordIndexMap.get(record);
        if (null != recordIndex) {
            final RecordData<T> recordData = records.get(recordIndex);
            if (recordData.active) {
                recordData.weight = recordData.weight + weight;
            } else {
                recordData.weight = weight;
                recordData.active = true;
            }
            totalWeight += weight;
        } else {
            final RecordData<T> newData = new RecordData<>(record, weight);
            internalAddRecord(newData);
        }
        recomputeUseWeight();
    }

    // TODO pack method that removes inactive values from records and rebuilds
    // recordIndexMap

    /**
     * Make this match {@code toMerge} without resetting the index of which
     * record to use next. When this method finishes, the data in {@code this}
     * will be equivalent to {@code toMerge}.
     * 
     * @param newData
     *            the new data
     */
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "Bug in Spotbugs, doesn't handle lambdas")
    public synchronized void updateRecords(final WeightedRoundRobin<T> newData) {
        LOGGER.trace("Updating records with: " + newData);

        final List<RecordData<T>> toAdd = new LinkedList<>();
        final Set<T> seen = new HashSet<>();

        newData.foreachRecord((record, weight) -> {
            seen.add(record);

            final Integer idx = recordIndexMap.get(record);
            if (null == idx) {
                // add
                final RecordData<T> newRecordData = new RecordData<>(record, weight);
                toAdd.add(newRecordData);
            } else {
                // merge
                final RecordData<T> existing = records.get(idx);
                totalWeight = totalWeight - existing.weight + weight;
                existing.weight = weight;
            }
        });

        records.forEach(recordData -> {
            if (!seen.contains(recordData.record)) {
                // remove
                totalWeight = totalWeight - recordData.weight;
                recordData.active = false;
                recordData.weight = 0;
            }
        });

        // add new records
        // do this after the search for records to remove to make the above loop
        // shorter
        toAdd.forEach(this::internalAddRecord);

        // assuming something changed
        recomputeUseWeight();
    }

    private void internalAddRecord(final RecordData<T> newRecordData) {
        totalWeight = totalWeight + newRecordData.weight;
        records.add(newRecordData);
        recordIndexMap.put(newRecordData.record, records.size() - 1);
    }

    /**
     * Visit each record and weight.
     * 
     * @param visitor
     *            the function to call for each pair
     */
    public final synchronized void foreachRecord(@Nonnull BiConsumer<T, Double> visitor) {
        records.forEach(r -> {
            if (r.active) {
                visitor.accept(r.record, r.weight);
            }
        });
    }

    /**
     * @return the next record to use, will be null if there are no records
     */
    @JsonIgnore
    public final synchronized T getNextRecord() {
        if (records.isEmpty()) {
            return null;
        } else {
            final int startingIndex = recordUseWeightIndex;
            LOGGER.trace("getNextRecord starting at index {}", startingIndex);

            // loop until we find a use value that is greater than 0
            while (true) {
                final int recordIndex = recordUseWeightIndex;
                final RecordData<T> data = records.get(recordIndex);

                // always increment index
                incrementRecordIndex();

                if (data.active && data.useCount > 0) {
                    // found record to use
                    final T record = data.record;

                    LOGGER.trace("Found record to use with useCount: {} record: {}", data.useCount, record);

                    // note that it's been used
                    data.useCount = data.useCount - 1;
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
        checkRecordIndex();
    }

    /**
     * Reset the record index if it's off the end of the records list.
     */
    private void checkRecordIndex() {
        if (recordUseWeightIndex >= records.size()) {
            recordUseWeightIndex = 0;
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" + " records: " + records + " ]";
    }

    // CHECKSTYLE:OFF data class
    private static final class RecordData<T> {

        /**
         * Create a new active record.
         * 
         * @param record
         *            see {@link #record}
         * @param weight
         *            see {@link #weight}
         */
        public RecordData(final T record, final double weight) {
            this.record = record;
            this.weight = weight;
            this.active = true;
        }

        /**
         * The record.
         */
        public T record;

        /**
         * Weight of the record. Used to compute the use weight.
         */
        public double weight;

        /**
         * How many times to use this record before it's reset.
         */
        public long useCount;

        /**
         * True if active, false if it should be skipped. This allows one to
         * avoid needing to remove from the list and reset the index map.
         */
        public boolean active;

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " record: " + record + " weight: " + weight + " active: " + active;
        }
    }
    // CHECKSTYLE:ON

}