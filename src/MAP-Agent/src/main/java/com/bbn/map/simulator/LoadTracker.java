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

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for tracking load.
 * 
 * @author jschewe
 *
 * @param T
 *            the type of {@link LoadEntry} being tracked.
 */
/* package */ abstract class LoadTracker<T extends LoadEntry> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTracker.class);

    private final PriorityQueue<T> loadQueue = new PriorityQueue<>(LoadEntryEndTime.INSTANCE);

    /**
     * Remove any entries that expired before now.
     * 
     * @param now
     *            the current time
     * @param callback
     *            called for each entry that is removed
     */
    public final void removeExpiredEntries(final long now, Consumer<T> callback) {
        boolean done = false;
        while (!done) {
            final T entry = loadQueue.peek();
            if (null != entry) {
                final long endTime = entry.getStartTime() + entry.getDuration();
                if (endTime <= now) {
                    LOGGER.trace("Expiring entry now: {} end: {} start: {} duration: {}", now, endTime,
                            entry.getStartTime(), entry.getDuration());
                    removeLoad(entry);
                    callback.accept(entry);
                } else {
                    // nothing else to check
                    done = true;
                }
            } else {
                // queue is empty
                done = true;
            }
        }
    }

    /**
     * @param entry
     *            the entry to add
     */
    public final void addLoad(final T entry) {
        loadQueue.add(entry);
        postAddLoad(entry);
    }

    /**
     * Called after {@link #addLoad(T)}.
     * 
     * @param entry
     *            the entry that was added
     */
    protected abstract void postAddLoad(T entry);

    /**
     * 
     * @param entry
     *            the entry to remove. If this entry has not been added nothing
     *            happens.
     */
    public final void removeLoad(final T entry) {
        if (loadQueue.contains(entry)) {
            LOGGER.trace("Removing load entry start: {} duration: {}", entry.getStartTime(), entry.getDuration());
            loadQueue.remove(entry);
            postRemoveLoad(entry);
        } else {
            LOGGER.warn("Trying to remove load entry that wasn't added: {}", entry);
        }
    }

    /**
     * Called after {@link #removeLoad(T)}.
     * 
     * @param entry
     *            the entry that was removed
     */
    protected abstract void postRemoveLoad(T entry);

    private static final class LoadEntryEndTime implements Comparator<LoadEntry> {
        public static final LoadEntryEndTime INSTANCE = new LoadEntryEndTime();

        @Override
        public int compare(final LoadEntry o1, final LoadEntry o2) {
            final long o1End = o1.getStartTime() + o1.getDuration();
            final long o2End = o2.getStartTime() + o2.getDuration();
            return Long.compare(o1End, o2End);
        }

    }
}
