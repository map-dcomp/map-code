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
package com.bbn.map;

import java.time.Duration;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;

/**
 * Global configuration information for MAP Agent.
 */
public final class AgentConfiguration {

    private AgentConfiguration() {
    }

    private static final Object INSTANCE_LOCK = new Object();

    private static AgentConfiguration instance = null;

    /**
     *
     * @return the singleton instance
     */
    public static AgentConfiguration getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (null == instance) {
                instance = new AgentConfiguration();
            }
            return instance;
        }
    }

    /**
     * Reset all values back to their defaults. This is done by replacing the
     * value returned from {@link #getInstance()}. So any cases where the
     * instance is held will not see the new values.
     */
    public static void resetToDefaults() {
        synchronized (INSTANCE_LOCK) {
            // set to null, the next access will create a new instance with the
            // defaults
            instance = null;
        }
    }

    private String apProgram = "/protelis/com/bbn/map/resourcetracker.pt";
    private boolean apProgramAnonymous = false;

    /**
     * @return the program to run in AP
     */
    @Nonnull
    public String getApProgram() {
        return apProgram;
    }

    /**
     *
     * @param v
     *            see {@link #getApProgram()}
     */
    public void setApProgram(@Nonnull final String v) {
        apProgram = v;
    }

    /**
     * @return if the AP program is anonymous
     * @see #getApProgram()
     */
    public boolean isApProgramAnonymous() {
        return apProgramAnonymous;
    }

    /**
     *
     * @param v
     *            see {@link #isApProgramAnonymous()}
     */
    public void setApProgramAnonymous(final boolean v) {
        apProgramAnonymous = v;
    }

    private static final int DEFAULT_AP_ROUND_DURATION_SECONDS = 2;
    private Duration apRoundDuration = Duration.ofSeconds(DEFAULT_AP_ROUND_DURATION_SECONDS);

    /**
     * @return how long between rounds of AP
     */
    @Nonnull
    public Duration getApRoundDuration() {
        return apRoundDuration;
    }

    /**
     *
     * @param v
     *            see {@link #getApRoundDuration()}
     */
    public void setApRoundDuration(@Nonnull final Duration v) {
        apRoundDuration = v;
    }

    private static final int DEFAULT_RLG_ESTIMATION_WINDOW_SECONDS = 3;
    private Duration rlgEstimationWindow = Duration.ofSeconds(DEFAULT_RLG_ESTIMATION_WINDOW_SECONDS);

    /**
     *
     * @return the time period over which estimates for the
     *         {@link ResourceReport} objects passed to RLG is computed. This
     *         should match {@link #getRlgRoundDuration()}.
     * @see ResourceReport#getDemandEstimationWindow()
     */
    @Nonnull
    public Duration getRlgEstimationWindow() {
        return rlgEstimationWindow;
    }

    /**
     *
     * @param v
     *            see {@link #getRlgEstimationWindow()}
     */
    public void setRlgEstimationWindow(@Nonnull final Duration v) {
        rlgEstimationWindow = v;
    }

    private Duration rlgRoundDuration = rlgEstimationWindow;

    /**
     *
     * @return how long between rounds of RLG
     */
    @Nonnull
    public Duration getRlgRoundDuration() {
        return rlgRoundDuration;
    }

    /**
     *
     * @param v
     *            see {@link #getRlgRoundDuration()}
     */
    public void setRlgRoundDuration(@Nonnull final Duration v) {
        rlgRoundDuration = v;
    }

    private static final int DEFAULT_DCOP_ESTIMATION_WINDOW_MINUTES = 1;
    private Duration dcopEstimationWindow = Duration.ofMinutes(DEFAULT_DCOP_ESTIMATION_WINDOW_MINUTES);

    /**
     *
     * @return the time period over which estimates for the
     *         {@link ResourceReport} objects passed to DCOP is computed. This
     *         should match {@link #getDcopRoundDuration()}.
     * @see ResourceReport#getDemandEstimationWindow()
     */
    @Nonnull
    public Duration getDcopEstimationWindow() {
        return dcopEstimationWindow;
    }

    /**
     *
     * @param v
     *            see {@link #getDcopEstimationWindow()}
     */
    public void setDcopEstimationWindow(@Nonnull final Duration v) {
        dcopEstimationWindow = v;
    }

    private Duration dcopRoundDuration = dcopEstimationWindow;

    /**
     *
     * @return how long between rounds of DCOP
     */
    @Nonnull
    public Duration getDcopRoundDuration() {
        return dcopRoundDuration;
    }

    /**
     * @param v
     *            see {@link #getDcopRoundDuration()}
     */
    public void setDcopRoundDuration(@Nonnull final Duration v) {
        dcopRoundDuration = v;
    }

    private static final double DEFAULT_DNS_WEIGHT_PRECISION = 0.05;
    private double dnsWeightPrecision = DEFAULT_DNS_WEIGHT_PRECISION;

    /**
     * Any weight difference less than this is considered equal. This defaults
     * to 0.05 (5%), which means that if the difference between two weights in
     * {@link RegionPlan#getPlan()} is less than this value, then the weights
     * are considered equal.
     *
     * @return The weight that is equivalent to one DNS record.
     */
    public double getDnsRecordWeightPrecision() {
        return dnsWeightPrecision;
    }

    /**
     *
     * @param v
     *            see {@link #getDnsRecordWeightPrecision()}
     */
    public void setDnsRecordWeightPrecision(final double v) {
        dnsWeightPrecision = v;
    }

    /**
     * When shutting down an {@link AbstractService}, how long to wait for the
     * thread to exit before moving on.
     *
     * @return how long to wait for a service to shutdown
     */
    @Nonnull
    public Duration getServiceShutdownWaitTime() {
        return serviceShutdownWaitTime;
    }

    /**
     *
     * @param v
     *            see {@link #getServiceShutdownWaitTime()}
     */
    public void setServiceShutdownWaitTime(@Nonnull final Duration v) {
        serviceShutdownWaitTime = v;
    }

    private static final long DEFAULT_SERVICE_SHUTDOWN_WAIT_TIME_SECONDS = 30;
    private Duration serviceShutdownWaitTime = Duration.ofSeconds(DEFAULT_SERVICE_SHUTDOWN_WAIT_TIME_SECONDS);

    private static final int DEFAULT_DCOP_ITERATION_LIMIT = 10;
    private int dcopIterationLimit = DEFAULT_DCOP_ITERATION_LIMIT;

    /**
     * @return How many iterations to run the DCOP algorithm each round
     */
    public int getDcopIterationLimit() {
        return dcopIterationLimit;
    }

    /**
     *
     * @param v
     *            see {@link #getDcopIterationLimit()}
     */
    public void setDcopIterationLimit(final int v) {
        dcopIterationLimit = v;
    }

    private static final double DEFAULT_DCOP_CAPACITY_THRESHOLD = 0.8;
    private double dcopCapacityThreshold = DEFAULT_DCOP_CAPACITY_THRESHOLD;

    /**
     * DCOP should not fill regions to their capacity, so this threshold is used
     * to specify how full a region should get. This number is between 0 and 1.
     *
     * @return the percentage of capacity that is considered full
     */
    public double getDcopCapacityThreshold() {
        return dcopCapacityThreshold;
    }

    /**
     *
     * @param v
     *            see {@link #getDcopCapacityThreshold()}
     */
    public void setDcopCapacityThreshold(final double v) {
        dcopCapacityThreshold = v;
    }
}
