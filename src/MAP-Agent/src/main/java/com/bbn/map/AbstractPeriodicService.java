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
package com.bbn.map;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execute a method at regular intervals. Call {@link #execute()} based on the
 * period passed into the constructor. If the {@link #execute()} method takes
 * longer than the period, then there is no sleep, otherwise the service sleeps
 * the remaining duration.
 * 
 * @author jschewe
 *
 */
public abstract class AbstractPeriodicService extends AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPeriodicService.class);

    private final Duration period;

    /**
     * 
     * @param name
     *            passed to the parent constructor
     * @param executePeriod
     *            see {@link #getPeriod()}
     */
    public AbstractPeriodicService(final String name, @Nonnull final Duration executePeriod) {
        super(name);
        this.period = executePeriod;
    }

    /**
     * This is the amount of time that should elapse between calls to
     * {@link #execute()}.
     * 
     * @return the execution period
     */
    public final Duration getPeriod() {
        return period;
    }

    @Override
    protected final void executeService() {
        LOGGER.debug("Started service {}", getName());

        while (Status.RUNNING == getStatus()) {
            final LocalDateTime beforePlan = LocalDateTime.now();

            execute();

            // check before sleep in case computePlan is slow
            if (Status.RUNNING == getStatus()) {
                try {
                    // sleep if the computation didn't take too long
                    final Duration timeSinceStart = Duration.between(beforePlan, LocalDateTime.now());
                    final Duration sleepDuration = period.minus(timeSinceStart);
                    if (!sleepDuration.isZero() && !sleepDuration.isNegative()) {
                        Thread.sleep(sleepDuration.toMillis());
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Sleep duration was not positive, no sleep: {}", sleepDuration);
                        }
                    }
                } catch (final InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Got interrupted, likely time to shutdown, top of while loop will confirm.");
                    }
                }
            }
        }

        LOGGER.debug("Stopped service {}", getName());
    }

    /**
     * Called by {@link #executeService()} at regular intervals.
     */
    protected abstract void execute();

}
