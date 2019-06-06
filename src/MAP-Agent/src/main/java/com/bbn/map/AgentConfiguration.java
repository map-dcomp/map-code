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
package com.bbn.map;

import java.time.Duration;

import javax.annotation.Nonnull;

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

    private static final int DEFAULT_AP_ROUND_DURATION_MS = 500;
    private Duration apRoundDuration = Duration.ofMillis(DEFAULT_AP_ROUND_DURATION_MS);

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

    private static final DcopAlgorithm DEFAULT_DCOP_ALGORITHM = DcopAlgorithm.DISTRIBUTED_PRIORITY_ROUTING_DIFFUSION;
    private DcopAlgorithm dcopAlgorithm = DEFAULT_DCOP_ALGORITHM;

    /**
     * @return the current DCOP algorithm in use
     */
    @Nonnull
    public DcopAlgorithm getDcopAlgorithm() {
        return dcopAlgorithm;
    }

    /**
     * @param algorithm
     *            specify the DCOP algorithm to use
     */
    public void setDcopAlgorithm(@Nonnull final DcopAlgorithm algorithm) {
        dcopAlgorithm = algorithm;
    }

    /**
     * Available DCOP algorithms to be run.
     * 
     * @author jschewe
     *
     */
    public enum DcopAlgorithm {
        /**
         * Distributed Constraint-based Diffusion Algorithm. Migrates out from
         * the data center to neighbors.
         */
        DISTRIBUTED_CONSTRAINT_DIFFUSION,

        /** Distributed Routing-based Diffusion Algorithm. */
        DISTRIBUTED_ROUTING_DIFFUSION,
        /** Distributed Priority Routing-based Diffusion Algorithm. */
        DISTRIBUTED_PRIORITY_ROUTING_DIFFUSION
        
    }

    private boolean rlgNullOverflowPlan = false;

    /**
     * 
     * @return if true, then RLG will ignore the DCOP plan and always return an
     *         empty overflow plan
     */
    public boolean isRlgNullOverflowPlan() {
        return rlgNullOverflowPlan;
    }

    /**
     * 
     * @param v
     *            see {@link #isRlgNullOverflowPlan()}
     */
    public void setRlgNullOverflowPlan(final boolean v) {
        rlgNullOverflowPlan = v;
    }

    private static final RlgAlgorithm DEFAULT_RLG_ALGORITHM = RlgAlgorithm.BIN_PACKING;
    private RlgAlgorithm rlgAlgorithm = DEFAULT_RLG_ALGORITHM;

    /**
     * @return the current RLG algorithm in use
     */
    @Nonnull
    public RlgAlgorithm getRlgAlgorithm() {
        return rlgAlgorithm;
    }

    /**
     * @param algorithm
     *            see {@link #getRlgAlgorithm()}
     */
    public void setRlgAlgorithm(@Nonnull final RlgAlgorithm algorithm) {
        rlgAlgorithm = algorithm;
    }

    /**
     * Available RLG algorithms to be run.
     * 
     * @author jschewe
     *
     */
    public enum RlgAlgorithm {
        /**
         * Stub implementation by BBN.
         */
        STUB,

        /** Bin packing algorithm. */
        BIN_PACKING;
    }

    private boolean useLeaderElection = false;

    /**
     * Determine if the leader election algorithm should be used to find a
     * global leader. If this is true, then the global leader will be determined
     * by the Protelis program. If this value is false, then the global leader
     * is determined by {@link Controller#isGlobalLeader()}.
     * 
     * @return if the global leader election algorithm should be used
     */
    public boolean isUseLeaderElection() {
        return useLeaderElection;
    }

    /**
     * Set the value for use leader election. This needs to be set before the
     * protelis nodes start up.
     * 
     * @param v
     *            the new value
     * @see #isUseLeaderElection()
     */
    public void setUseLeaderElection(final boolean v) {
        useLeaderElection = v;
    }

    /**
     * Options for choosing the NCP to offload services onto in the RLG stub.
     */
    public enum RlgStubChooseNcp {
        /**
         * Find NCP with the greatest number of available containers.
         */
        MOST_AVAILABLE_CONTAINERS,
        /**
         * Pick an NCP randomly from those with available containers.
         */
        RANDOM,
        /**
         * Pick an NCP already running the service.
         */
        CURRENTLY_RUNNING_SERVICE,
        /**
         * Pick an NCP not running the service.
         */
        CURRENTLY_NOT_RUNNING_SERIVCE,
        /**
         * Pick the NCP with the lowest load percentage.
         */
        LOWEST_LOAD_PERCENTAGE;
    }

    private static final RlgStubChooseNcp DEFAULT_RLG_STUB_CHOOSE = RlgStubChooseNcp.MOST_AVAILABLE_CONTAINERS;

    private RlgStubChooseNcp rlgStubChoose = DEFAULT_RLG_STUB_CHOOSE;

    /**
     * @return the algorithm to use for choosing an NCP in the RLG Stub
     */
    @Nonnull
    public RlgStubChooseNcp getRlgStubChooseNcp() {
        return rlgStubChoose;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgStubChooseNcp()}
     */
    public void setRlgStubChooseNcp(@Nonnull final RlgStubChooseNcp v) {
        rlgStubChoose = v;
    }

    private static final double DEFAULT_RLG_LOAD_THRESHOLD = 0.75;
    private double rlgLoadThreshold = DEFAULT_RLG_LOAD_THRESHOLD;

    /**
     * @return The threshold used by RLG to determine when to allocate more
     *         services
     */
    public double getRlgLoadThreshold() {
        return rlgLoadThreshold;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgLoadThreshold()}
     */
    public void setRlgLoadThreshold(final double v) {
        rlgLoadThreshold = v;
    }
}
