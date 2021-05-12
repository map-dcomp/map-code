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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.GlobalNetworkConfiguration;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        if (null == instance) {
            synchronized (INSTANCE_LOCK) {
                if (null == instance) {
                    instance = new AgentConfiguration();
                }
            }
        }
        return instance;
    }

    /**
     * Read the configuration from the specified JSON file. Any properties not
     * specified in the file will have default values.
     * 
     * @param path
     *            the path to the file to read
     * @throws IOException
     *             if there is an error reading the file
     */
    public static void readFromFile(@Nonnull final Path path) throws IOException {
        synchronized (INSTANCE_LOCK) {
            final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                final AgentConfiguration value = mapper.readValue(reader, AgentConfiguration.class);
                instance = value;
            }
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

    private static final int DEFAULT_RESOURCE_REPORT_INTERVAL_MS = 500;
    private Duration resourceReportInterval = Duration.ofMillis(DEFAULT_RESOURCE_REPORT_INTERVAL_MS);

    /**
     * @return the time interval between building of new {@link ResourceReport}
     *         objects
     */
    @Nonnull
    public Duration getResourceReportInterval() {
        return resourceReportInterval;
    }

    /**
     *
     * @param v
     *            see {@link #getResourceReportInterval()}
     */
    public void setResourceReportInterval(@Nonnull final Duration v) {
        resourceReportInterval = v;
    }

    private static final int DEFAULT_RLG_ESTIMATION_WINDOW_SECONDS = 3;
    private Duration rlgEstimationWindow = Duration.ofSeconds(DEFAULT_RLG_ESTIMATION_WINDOW_SECONDS);

    /**
     *
     * @return the time period over which estimates for the
     *         {@link ResourceReport} objects passed to RLG is computed when
     *         using
     *         {@link AgentConfiguration.DemandComputationAlgorithm#MOVING_AVERAGE}.
     * @see ResourceReport#getDemandEstimationWindow()
     * @see #getDemandComputationAlgorithm()
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

    private static final int DEFAULT_DCOP_ESTIMATION_WINDOW_MINUTES = 3;
    private Duration dcopEstimationWindow = Duration.ofMinutes(DEFAULT_DCOP_ESTIMATION_WINDOW_MINUTES);

    /**
     *
     * @return the time period over which estimates for the
     *         {@link ResourceReport} objects passed to DCOP is computed when
     *         using
     *         {@link AgentConfiguration.DemandComputationAlgorithm#MOVING_AVERAGE}.
     * @see ResourceReport#getDemandEstimationWindow()
     * @see #getDemandComputationAlgorithm()
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

    private static final int DEFAULT_DCOP_ROUND_DURATION_MINUTES = 5;
    private Duration dcopRoundDuration = Duration.ofMinutes(DEFAULT_DCOP_ROUND_DURATION_MINUTES);

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

    private static final Duration DEFAULT_DCOP_ACDIFF_TIME_OUT = Duration.ofSeconds(45);

    private Duration dcopAcdiffTimeOut = DEFAULT_DCOP_ACDIFF_TIME_OUT;

    /**
     * 
     * @return how long the DCOP ACDIFF will run before computing the DCOP plan.
     */
    public Duration getDcopAcdiffTimeOut() {
        return dcopAcdiffTimeOut;
    }

    /**
     * 
     * @param v
     *            see {@Link #getDcopAcdiffTimeOut()}
     */
    public void setDcopAcdiffTimeOut(final Duration v) {
        dcopAcdiffTimeOut = v;
    }

    private static final boolean DEFAULT_DCOP_ACDIFF_SIMULATE_MESSAGE_DROPS = false;

    private boolean dcopAcdiffSimulateMessageDrops = DEFAULT_DCOP_ACDIFF_SIMULATE_MESSAGE_DROPS;

    /**
     * @return if messages in the DCOP ACDIFF algorithm are dropped
     * @see #getDcopAcdiffSimulateMessageDropRate()
     */
    public boolean getDcopAcdiffSimulateMessageDrops() {
        return dcopAcdiffSimulateMessageDrops;
    }

    /**
     * 
     * @param v
     *            see {@link #getDcopAcdiffSimulateMessageDrops()}
     */
    public void setDcopAcdiffSimulateMessageDrops(final boolean v) {
        dcopAcdiffSimulateMessageDrops = v;
    }

    private static final double DEFAULT_DCOP_ACDIFF_SIMULATE_MESSAGE_DROP_RATE = 0;

    private double dcopAcdiffSimulateMessageDropRate = DEFAULT_DCOP_ACDIFF_SIMULATE_MESSAGE_DROP_RATE;

    /**
     * When testing the {@link DcopAlgorithm#ASYNCHRONOUS_CDIFF} algorithm this
     * value specifies the percentage of messages that are dropped. This is only
     * used if {@link #getDcopAcdiffSimulateMessageDrops()} is true.
     * 
     * @return the percentage of top to not send messages
     * @see #getDcopAcdiffSimulateMessageDrops()
     */
    public double getDcopAcdiffSimulateMessageDropRate() {
        return dcopAcdiffSimulateMessageDropRate;
    }

    /**
     * 
     * @param v
     *            see {@link #getDcopAcdiffSimulateMessageDropRate()}
     */
    public void setDcopAcdiffSimulateMessageDropRate(final double v) {
        dcopAcdiffSimulateMessageDropRate = v;
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

    private static final DcopAlgorithm DEFAULT_DCOP_ALGORITHM = DcopAlgorithm.FINAL_RCDIFF;
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
         * Distributed Constraint-based Diffusion Algorithm.
         * <p>
         * 
         * Migrates out from the data center to neighbors.
         * <p>
         * 
         * Each overloaded region sends out messages to neighbors to ask for
         * help.
         * <p>
         * Each message is propagated until reaching either a region with
         * sufficient capacity or a region that doesn't have any neighbor that
         * can help.
         * <p>
         * The process forms a number of disjoint trees, each rooted at an
         * overloaded region. It means a region can belong to at most one tree
         * at a time.
         * <p>
         * Each region then waits for capacity information from all children and
         * send the aggregated information up to the parent.
         * <p>
         * The root then determines the amount of load to shed to each child in
         * a plan message.
         * <p>
         * Each region serves as much as possible and creates a plan for each
         * child based on the excess load.
         * 
         */
        DISTRIBUTED_CONSTRAINT_DIFFUSION,

        /**
         * Distributed Routing-based Diffusion Algorithm.
         * <p>
         * 
         * The client region serves and shed excess load out towards the root.
         * <p>
         * 
         * Each region with non-empty server load sends out load information
         * towards region clients based on the request path.
         * <p>
         * Each client region serves as much as possible and shed the excess
         * load to regions in the request path toward the server.
         */
        DISTRIBUTED_ROUTING_DIFFUSION,

        /**
         * CDIFF algorithm written in protelis to spread from the client
         * regions.
         */
        CDIFF_PLUS,

        /**
         * Asynchronous CDIFF.
         * <p>
         * 
         * Migrates out from the data center to neighbors.
         * <p>
         * 
         * Instead of waiting for capacity information from all children, each
         * region sends up the information as long as it receives from a child.
         */
        ASYNCHRONOUS_CDIFF,
        /**
         * This algorithm should output same DCOP plans as those of Asynchronous
         * CDIFF.
         * <p>
         * 
         * The only difference is that MODULAR_ACDIFF is implemented with
         * modular design in C, G and Clear blocks.
         * 
         * Still under development.
         */
        MODULAR_ACDIFF,
        /**
         * Combination of asynchronous CDIFF and (asynchronous) RDIFF.
         * <p>
         * 
         * Same as RDIFF, load information is propagated from server to client
         * regions.
         * <p>
         * Same as CDIFF, each client region shed excess load to neighbors (each
         * tree is not rooted at a client region).
         * <p>
         * 
         * Still under development.
         *
         */
        RC_DIFF,
        /**
         * Modular_RCDIFF is the RC_DIFF algorithm but implemented following
         * block design.
         * 
         * Still under development.
         */
        MODULAR_RCDIFF,
        /**
         * RCDIFF in block designed.
         */
        FINAL_RCDIFF,
        /**
         * Return default plans in all cases. Used for comparison.
         */
        DEFAULT_PLAN,
    }

    private static final long DEFAULT_DCOP_SYNCHRONOUS_MESSAGE_TIMEOUT_MINUTES = 30;
    private Duration dcopSynchronousMessageTimeout = Duration
            .ofMinutes(DEFAULT_DCOP_SYNCHRONOUS_MESSAGE_TIMEOUT_MINUTES);

    /**
     * @return The maximum amount of time that the synchronous DCOP algorithms
     *         will wait for messages from neighboring DCOP processes
     */
    public Duration getDcopSynchronousMessageTimeout() {
        return dcopSynchronousMessageTimeout;
    }

    /**
     * 
     * @param v
     *            see {@link #getDcopSynchronousMessageTimeout()}
     */
    public void setDcopSynchronousMessageTimeout(final Duration v) {
        dcopSynchronousMessageTimeout = v;
    }

    private static final Duration DEFAULT_RCDIFF_TIMER_THRESHOLD = Duration.ofSeconds(5);

    private Duration rcdiffTimerThreshold = DEFAULT_RCDIFF_TIMER_THRESHOLD;

    /**
     * 
     * @return how long RC-DIFF will wait for messages from neighboring regions
     */
    public Duration getRcdiffTimerThreshold() {
        return rcdiffTimerThreshold;
    }

    /**
     * 
     * @param v
     *            {@link #getRcdiffTimerThreshold()}
     */
    public void setRcdiffTimerThreshold(final Duration v) {
        rcdiffTimerThreshold = v;
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

    private static final RlgAlgorithm DEFAULT_RLG_ALGORITHM = RlgAlgorithm.STUB;
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
        BIN_PACKING,

        /**
         * Run as if MAP isn't running.
         */
        NO_MAP;
    }

    private static final RlgAlgorithmLoadInput DEFAULT_RLG_ALGORITHM_LOAD_INPUT = RlgAlgorithmLoadInput.DEMAND;
    private RlgAlgorithmLoadInput rlgAlgorithmLoadInput = DEFAULT_RLG_ALGORITHM_LOAD_INPUT;

    /**
     * @return the current RLG algorithm in use
     */
    @Nonnull
    public RlgAlgorithmLoadInput getRlgAlgorithmLoadInput() {
        return rlgAlgorithmLoadInput;
    }

    /**
     * @param loadInput
     *            see {@link #getRlgAlgorithmLoadInput()}
     */
    public void setRlgAlgorithmLoadInput(@Nonnull final RlgAlgorithmLoadInput loadInput) {
        rlgAlgorithmLoadInput = loadInput;
    }

    /**
     * Available RLG algorithm load inputs.
     * 
     * @author awald
     *
     */
    public enum RlgAlgorithmLoadInput {
        /**
         * Raw load.
         */
        LOAD,

        /** Demand estimate. */
        DEMAND;
    }

    private static final RlgPriorityPolicy DEFAULT_RLG_PRIORITY_POLICY = RlgPriorityPolicy.GREEDY_GROUP;
    private RlgPriorityPolicy rlgPriorityPolicy = DEFAULT_RLG_PRIORITY_POLICY;

    /**
     * @return the current RLG priority policy to uses
     */
    @Nonnull
    public RlgPriorityPolicy getRlgPriorityPolicy() {
        return rlgPriorityPolicy;
    }

    /**
     * @param policy
     *            see {@link #getRlgPriorityPolicy()}
     */
    public void setRlgPriorityPolicy(@Nonnull final RlgPriorityPolicy policy) {
        rlgPriorityPolicy = policy;
    }

    /**
     * Available RLG priority policies to use.
     * 
     * @author awald
     *
     */
    public enum RlgPriorityPolicy {
        /**
         * Greedily assign allocation targets in descending order of priority
         * groups.
         */
        GREEDY_GROUP,

        /**
         * Use a priority with fixed reservation sizes that are determined from
         * priority based proportions.
         */
        FIXED_TARGET,

        /**
         * Use a Null priority implementation, which functions as if the
         * priority does not exist.
         */
        NO_PRIORITY;
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

    private static final double DEFAULT_RLG_LOAD_THRESHOLD = 0.6;
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

    private static final double DEFAULT_RLG_UNDERLOAD_ENDED_THRESHOLD = 0.35;
    private double rlgUnderloadEndedThreshold = DEFAULT_RLG_UNDERLOAD_ENDED_THRESHOLD;

    /**
     * @return The threshold used by RLG to determine when to cancel scheduled
     *         service container deallocations
     */
    public double getRlgUnderloadEndedThreshold() {
        return rlgUnderloadEndedThreshold;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgUnderloadEndedThreshold()}
     */
    public void setRlgUnderloadEndedThreshold(final double v) {
        rlgUnderloadEndedThreshold = v;
    }

    private static final double DEFAULT_RLG_UNDERLOAD_THRESHOLD = 0.25;
    private double rlgUnderloadThreshold = DEFAULT_RLG_UNDERLOAD_THRESHOLD;

    /**
     * @return The threshold used by RLG to determine when to deallocate service
     *         containers
     */
    public double getRlgUnderloadThreshold() {
        return rlgUnderloadThreshold;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgUnderloadThreshold()}
     */
    public void setRlgUnderloadThreshold(final double v) {
        rlgUnderloadThreshold = v;
    }

    private static final int DEFAULT_RLG_ALLOCATIONS_FOR_DCOP = 1;
    private int rlgAllocationsForDcop = DEFAULT_RLG_ALLOCATIONS_FOR_DCOP;

    /**
     * @return the number of containers to start in a region when DCOP specifies
     *         a new service for the region
     */
    public int getRlgAllocationsForDcop() {
        return rlgAllocationsForDcop;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgAllocationsForDcop()}
     */
    public void setRlgAllocationsForDcop(final int v) {
        rlgAllocationsForDcop = v;
    }

    private static final int DEFAULT_RLG_MAX_ALLOCATIONS_PER_ROUND_PER_SERVICE = 1;
    private int rlgMaxAllocationsPerRoundPerService = DEFAULT_RLG_MAX_ALLOCATIONS_PER_ROUND_PER_SERVICE;

    /**
     * @return the maximum number of containers to allocate per round per
     *         service
     */
    public int getRlgMaxAllocationsPerRoundPerService() {
        return rlgMaxAllocationsPerRoundPerService;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgMaxAllocationsPerRoundPerService()}
     */
    public void setRlgMaxAllocationsPerRoundPerService(final int v) {
        rlgMaxAllocationsPerRoundPerService = v;
    }

    private static final int DEFAULT_RLG_MAX_SHUTDOWNS_PER_ROUND_PER_SERVICE = 1;
    private int rlgMaxShutdownsPerRoundPerService = DEFAULT_RLG_MAX_SHUTDOWNS_PER_ROUND_PER_SERVICE;

    /**
     * @return the maximum number of shutdowns to allow per RLG round per
     *         service
     */
    public int getRlgMaxShutdownsPerRoundPerService() {
        return rlgMaxShutdownsPerRoundPerService;
    }

    /**
     * 
     * @param v
     *            see {@link #getRlgMaxShutdownsPerRoundPerService()}
     */
    public void setRlgMaxShutdownsPerRoundPerService(final int v) {
        rlgMaxShutdownsPerRoundPerService = v;
    }

    private static final long DEFAULT_RLG_SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY = 3;
    private Duration rlgServiceUnderloadedToContainerShutdownDelay = Duration
            .ofSeconds(DEFAULT_RLG_SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY);

    /**
     * @return the delay between a container being scheduled for shutdown due to
     *         service underload and a container being shutdown
     */
    public Duration getRlgServiceUnderloadedToContainerShutdownDelay() {
        return rlgServiceUnderloadedToContainerShutdownDelay;
    }

    /**
     * 
     * @param v
     *            see
     *            {@link #getRlgServiceUnderloadedToContainerShutdownDelay()}
     */
    public void setRlgServiceUnderloadedToContainerShutdownDelay(final Duration v) {
        rlgServiceUnderloadedToContainerShutdownDelay = v;
    }

    private static final long DEFAULT_DNS_WEIGHT_PRECISION = 100;
    private long dnsWeightPrecision = DEFAULT_DNS_WEIGHT_PRECISION;

    /**
     * @return The precision used by the dns weighting mechanism
     * @see com.bbn.map.utils.WeightedRoundRobin#WeightedRoundRobin(long)
     */
    public long getDnsWeightPrecision() {
        return dnsWeightPrecision;
    }

    /**
     * 
     * @param v
     *            see {@link #getDnsWeightPrecision()}
     */
    public void setDnsWeightPrecision(final long v) {
        dnsWeightPrecision = v;
    }

    private static final int DEFAULT_MAX_PULL_ATTEMPTS = 10;

    private int maxPullAttemps = DEFAULT_MAX_PULL_ATTEMPTS;

    /**
     * 
     * @return maximum number of times that one should attempt to pull a docker
     *         image
     */
    public int getMaxPullAttemps() {
        return maxPullAttemps;
    }

    /**
     * 
     * @param v
     *            see {@Link #getMaxPullAttemps()}
     */
    public void setMaxPullAttempts(final int v) {
        maxPullAttemps = v;
    }

    private static final int DEFAULT_PULL_MIN_BACKOFF_SECONDS = 1;
    private int pullMinBackoffSeconds = DEFAULT_PULL_MIN_BACKOFF_SECONDS;

    /**
     * @return minimum number of seconds to wait before attempting a pull.
     */
    public int getPullMinBackoffSeconds() {
        return pullMinBackoffSeconds;
    }

    /**
     * 
     * @param v
     *            seee {@Link #getPullMinBackoffSeconds()}
     */
    public void setPullMinBackoffSeconds(final int v) {
        pullMinBackoffSeconds = v;
    }

    private static final int DEFAULT_PULL_MAX_BACKOFF_SECONDS = 30;
    private int pullMaxBackoffSeconds = DEFAULT_PULL_MAX_BACKOFF_SECONDS;

    /**
     * @return maximum number of seconds to wait before attempting a pull.
     */
    public int getPullMaxBackoffSeconds() {
        return pullMaxBackoffSeconds;
    }

    /**
     * 
     * @param v
     *            seee {@Link #getPullMaxBackoffSeconds()}
     */
    public void setPullMaxBackoffSeconds(final int v) {
        pullMaxBackoffSeconds = v;
    }

    // functions replicated so that they can all be set through the same JSON
    // file
    /**
     * Delegates to {@link GlobalNetworkConfiguration}. Note that this property
     * is not reset on a call to {@link #resetToDefaults()}. One needs to call
     * {@link GlobalNetworkConfiguration#resetToDefaults()} for that to happen.
     * 
     * @return see {@link GlobalNetworkConfiguration#getMessageDropPercentage()}
     */
    public double getMessageDropPercentage() {
        return GlobalNetworkConfiguration.getInstance().getMessageDropPercentage();
    }

    /**
     * 
     * @param v
     *            see {@link #getMessageDropPercentage()}
     * @throws IllegalArgumentException
     *             if the value is not between 0 and 1.
     */
    public void setMessageDropPercentage(final double v) throws IllegalArgumentException {
        GlobalNetworkConfiguration.getInstance().setMessageDropPercentage(v);
    }
    // end replicated methods

    private boolean simulateLinkLatencies = false;

    /**
     * 
     * @return if true, then during lo-fi simulation simulate link latencies.
     *         Default is false.
     */
    public boolean isSimulateLinkLatencies() {
        return simulateLinkLatencies;
    }

    /**
     * 
     * @param v
     *            see {@Link #isSimulateLinkLatencies()}
     */
    public void setSimulateLinkLatencies(final boolean v) {
        simulateLinkLatencies = v;
    }

    /**
     * What algorithm to use when fetching service images.
     * 
     * @author jschewe
     *
     */
    public enum ImageFetchAlgorithm {
        /**
         * Fetch the images once says that the service will be running in the
         * current region. This will cause more nodes than necessary to get the
         * image, but will start fetching the image earlier.
         */
        DCOP,
        /**
         * Fetch the images when RLG specifies that the service will run on a
         * node. This ensures that only the nodes that need the image get it,
         * however the service will need to wait until the image arrives.
         */
        RLG;
    }

    private static final ImageFetchAlgorithm DEFAULT_IMAGE_FETCH_ALGORITHM = ImageFetchAlgorithm.RLG;

    private ImageFetchAlgorithm imageFetchAlgorithm = DEFAULT_IMAGE_FETCH_ALGORITHM;

    /**
     * 
     * @return the algorithm used to fetch service images
     * @see ImageFetchAlgorithm
     */
    @Nonnull
    public ImageFetchAlgorithm getImageFetchAlgorithm() {
        return imageFetchAlgorithm;
    }

    /**
     * 
     * @param v
     *            see {@Link #getImageFetchAlgorithm()}
     */
    public void setImageFetchAlgorithm(@Nonnull final ImageFetchAlgorithm v) {
        imageFetchAlgorithm = v;
    }

    /**
     * The algorithm to use when weighting containers for DNS resolution.
     * 
     * @author awald
     *
     */
    public enum ContainerWeightAlgorithm {
        /**
         * Standard round robin policy, which weights all containers in the
         * region equally.
         */
        ROUND_ROBIN,

        /**
         * Weights each container in the region as a proportion of its remaining
         * capacity among all containers in the region. Each container is
         * assigned a weight proportional to (container capacity - container
         * load).
         */
        PROPORTIONAL,

        /**
         * Weights each container in the region as an exponential decay of its
         * current load.
         */
        EXPONENTIAL_DECAY
    }

    private static final ContainerWeightAlgorithm DEFAULT_CONTAINER_WEIGHT_ALGORITHM = ContainerWeightAlgorithm.ROUND_ROBIN;

    private ContainerWeightAlgorithm containerWeightAlgorithm = DEFAULT_CONTAINER_WEIGHT_ALGORITHM;

    /**
     * 
     * @return the algorithm used to assign weights to containers
     * @see ContainerWeightAlgorithm
     */
    @Nonnull
    public ContainerWeightAlgorithm getContainerWeightAlgorithm() {
        return containerWeightAlgorithm;
    }

    /**
     * 
     * @param v
     *            see {@Link #getContainerWeightAlgorithm()}
     */
    public void setContainerWeightAlgorithm(@Nonnull final ContainerWeightAlgorithm v) {
        containerWeightAlgorithm = v;
    }

    private boolean skipNetworkData = false;

    /**
     * This is meant only as a debugging tool and should not be turned on in
     * production. DCOP RDIFF will not behave with this set to true.
     * 
     * @return true if the network information should not be reported in
     *         resource reports
     */
    public boolean getSkipNetworkData() {
        return skipNetworkData;
    }

    /**
     * 
     * @param value
     *            see {@link #getSkipNetworkData()}
     */
    public void setSkipNetworkData(final boolean value) {
        skipNetworkData = value;
    }

    private static final double RLG_FIXED_TARGET_ACTIVE_SERVICE_LOAD_PERCENTAGE_THRESHOLD = 0.01;
    private double rlgFixedTargetActiveServiceLoadPercentageThreshold = RLG_FIXED_TARGET_ACTIVE_SERVICE_LOAD_PERCENTAGE_THRESHOLD;

    /**
     * When using {@link RlgPriorityPolicy#FIXED_TARGET} any service with load
     * greater than or equal to this percentage is considered active and will be
     * used when computing the container reservations.
     * 
     * @return percentage threshold
     */
    public double getRlgFixedTargetActiveServiceLoadPercentageThreshold() {
        return rlgFixedTargetActiveServiceLoadPercentageThreshold;
    }

    /**
     * 
     * @param v
     *            see
     *            {@link #getRlgFixedTargetActiveServiceLoadPercentageThreshold()}
     */
    public void setRlgFixedTargetActiveServiceLoadPercentageThreshold(final double v) {
        rlgFixedTargetActiveServiceLoadPercentageThreshold = v;
    }

    /**
     * Types of DNS implementations.
     * 
     * @author jschewe
     *
     */
    public enum DnsResolutionType {
        /**
         * A delegation to a region can result in a delegation to another
         * region.
         */
        RECURSIVE,
        /**
         * Once a delegation to a region is seen, the request must be resolved
         * in that region.
         */
        NON_RECURSIVE,

        /**
         * Like {@link #RECURSIVE}, except the region and containers are
         * separate lookups.
         */
        RECURSIVE_TWO_LAYER;
    }

    private static final DnsResolutionType DEFAULT_DNS_RESOLUTION_TYPE = DnsResolutionType.RECURSIVE;

    private DnsResolutionType dnsResolutionType = DEFAULT_DNS_RESOLUTION_TYPE;

    /**
     * 
     * @return how delegate DNS records are handled
     */
    public DnsResolutionType getDnsResolutionType() {
        return dnsResolutionType;
    }

    /**
     * 
     * @param v
     *            see {@link #getDnsResolutionType()}
     */
    public void setDnsResolutionType(final DnsResolutionType v) {
        dnsResolutionType = v;
    }

    private static final boolean DEFAULT_RANDOMIZE_DNS_RECORDS = false;

    private boolean randomizeDnsRecords = DEFAULT_RANDOMIZE_DNS_RECORDS;

    /**
     * @return true if the list of DNS records should be randomized before
     *         sending to the DNS server
     */
    public boolean getRandomizeDnsRecords() {
        return randomizeDnsRecords;
    }

    /**
     * 
     * @param v
     *            see {@link #getRandomizeDnsRecords()}
     */
    public void setRandomizeDnsRecords(final boolean v) {
        randomizeDnsRecords = v;
    }

    private static final boolean DEFAULT_AP_CONTROL_NETWORK = true;
    private boolean apControlNetwork = DEFAULT_AP_CONTROL_NETWORK;

    /**
     * 
     * @return if true, use the control network for AP traffic, otherwise use
     *         the experiment network
     */
    public boolean getApUsesControlNetwork() {
        return apControlNetwork;
    }

    /**
     * @param v
     *            see {@link #getApUsesControlNetwork()}
     */
    public void setApUsesControlNetwork(final boolean v) {
        apControlNetwork = v;
    }

    private static final int MIN_IFTOP_PRIORITY = -20;
    private static final int MAX_IFTOP_PRIORITY = 19;
    private static final int DEFAULT_IFTOP_PRIORITY = -10;
    private int iftopPriority = DEFAULT_IFTOP_PRIORITY;

    /**
     * 
     * @return the Linux scheduler priority to run iftop at. Valid range is -20
     *         (high priority) to 19 (low priority), per the documentation on
     *         the nice command.
     */
    public int getIftopPriority() {
        return iftopPriority;
    }

    /**
     * 
     * @param v
     *            see {@link #getIftopPriority()}, clamped to a valid value
     */
    public void setIftopPriority(final int v) {
        iftopPriority = Math.max(Math.min(v, MAX_IFTOP_PRIORITY), MIN_IFTOP_PRIORITY);
    }

    private final List<String> extraIftopArguments = new LinkedList<>();

    /**
     * This is mostly used for debugging.
     * 
     * @return extra arguments to pass to iftop
     */
    public List<String> getExtraIftopArguments() {
        return extraIftopArguments;
    }

    /**
     * 
     * @param v
     *            see {@link #getExtraIftopArguments()}
     */
    public void setExtraIftopArguments(final List<String> v) {
        extraIftopArguments.clear();
        extraIftopArguments.addAll(v);
    }

    private static final boolean DCOP_SHARE_DIRECT_DEFAULT = false;

    private boolean dcopShareDirect = DCOP_SHARE_DIRECT_DEFAULT;

    /**
     * 
     * @return true if DCOP should bypass AP and share information directly
     */
    public boolean getDcopShareDirect() {
        return dcopShareDirect;
    }

    /**
     * 
     * @param v
     *            {@link #getDcopShareDirect()}
     */
    public void setDcopShareDirect(final boolean v) {
        dcopShareDirect = v;
    }

    /**
     * 
     * @return {@link GlobalNetworkConfiguration#getUseJavaSerialization()}
     */
    public boolean getUseJavaSerialization() {
        return GlobalNetworkConfiguration.getInstance().getUseJavaSerialization();
    }

    /**
     * 
     * @param v
     *            {@link #getUseJavaSerialization()}
     */
    public void setUseJavaSerialization(final boolean v) {
        GlobalNetworkConfiguration.getInstance().setUseJavaSerialization(v);
    }

    /**
     * Algorithms to use for the round robin implementation.
     * 
     * @author jschewe
     *
     */
    public enum RoundRobinAlgorithm {
        /**
         * Use a counter in each record based on the
         * {@link AgentConfiguration#getDnsWeightPrecision()}.
         */
        COUNTERS,

        /** Used multiple records in a randomized list. */
        RANDOM_RECORDS;
    }

    private static final RoundRobinAlgorithm DEFAULT_ROUND_ROBIN_ALGORITHM = RoundRobinAlgorithm.RANDOM_RECORDS;

    private RoundRobinAlgorithm roundRobinAlgorithm = DEFAULT_ROUND_ROBIN_ALGORITHM;

    /**
     * 
     * @return algorithm to use for weighted round robin
     */
    public RoundRobinAlgorithm getRoundRobinAlgorithm() {
        return roundRobinAlgorithm;
    }

    /**
     * 
     * @param v
     *            {@link #getRoundRobinAlgorithm()}
     */
    public void setRoundRobinAlgorithm(final RoundRobinAlgorithm v) {
        this.roundRobinAlgorithm = v;
    }

    /**
     * 
     * @return {@link GlobalNetworkConfiguration#getUseCompression()}
     */
    public boolean getApUseGzipCompression() {
        return GlobalNetworkConfiguration.getInstance().getUseCompression();
    }

    /**
     * 
     * @param v
     *            {@link #getApUseGzipCompression}
     */
    public void setApUseGzipCompression(final boolean v) {
        GlobalNetworkConfiguration.getInstance().setUseCompression(v);
    }

    /**
     * 
     * @return {@link GlobalNetworkConfiguration#getUseDeltaCompression()}
     */
    public boolean getApUseDeltaCompression() {
        return GlobalNetworkConfiguration.getInstance().getUseDeltaCompression();
    }

    /**
     * 
     * @param v
     *            {@link #getApUseDeltaCompression}
     */
    public void setApUseDeltaCompression(final boolean v) {
        GlobalNetworkConfiguration.getInstance().setUseDeltaCompression(v);
    }

    private static final boolean DEFAULT_MONITOR_TESTBED_CONTROL_NETWORK = false;
    private boolean monitorTestbedControlNetwork = DEFAULT_MONITOR_TESTBED_CONTROL_NETWORK;

    /**
     * 
     * @return if true, then monitor the testbed control network for network
     *         traffic
     */
    public boolean getMonitorTestbedControlNetwork() {
        return monitorTestbedControlNetwork;
    }

    /**
     * 
     * @param v
     *            {@Link #getMonitorTestbedControlNetwork()}
     */
    public void setMonitorTestbedControlNetwork(final boolean v) {
        monitorTestbedControlNetwork = v;
    }

    private static final boolean DEFAULT_MONITOR_MAP_CONTROL_NETWORK = false;
    private boolean monitorMapControlNetwork = DEFAULT_MONITOR_MAP_CONTROL_NETWORK;

    /**
     * 
     * @return if true, then monitor the MAP control network for network traffic
     */
    public boolean getMonitorMapControlNetwork() {
        return monitorMapControlNetwork;
    }

    /**
     * 
     * @param v
     *            {@Link #getMonitorMapControlNetwork()}
     */
    public void setMonitorMapControlNetwork(final boolean v) {
        monitorMapControlNetwork = v;
    }

    private static final boolean DEFAULT_RANDOM_ROUND_ROBIN_PREFER_UNUSED = true;
    private boolean randomRoundRobinPreferUnused = DEFAULT_RANDOM_ROUND_ROBIN_PREFER_UNUSED;

    /**
     * When using randomized round robin, prefer the records that were not used
     * in the previous cycle.
     * 
     * @return if {@link #getRandomizeDnsRecords()} is true, then prefer records
     *         that were not used in the previous cycle
     */
    public boolean getRandomRoundRobinPreferUnused() {
        return randomRoundRobinPreferUnused;
    }

    /**
     * 
     * @param v
     *            {@link #getRandomRoundRobinPreferUnused()}
     */
    public void setRandomRoundRobinPreferUnused(final boolean v) {
        randomRoundRobinPreferUnused = v;
    }

    private static final int DEFAULT_RANDOM_ROUND_ROBIN_NUM_SHUFFLES = 3;
    private int randomRoundRobinNumShuffles = DEFAULT_RANDOM_ROUND_ROBIN_NUM_SHUFFLES;

    /**
     * @return if {@link #getRandomizeDnsRecords()} is true, the number of time
     *         to find the "best" sequence
     */
    public int getRandomRoundRobinNumShuffles() {
        return randomRoundRobinNumShuffles;
    }

    /**
     * 
     * @param v
     *            {@link #getRandomRoundRobinNumShuffles()}
     */
    public void setRandomRoundRobinNumShuffles(final int v) {
        randomRoundRobinNumShuffles = v;
    }

    private static final boolean DEFAULT_USE_FAILED_REQUESTS_IN_DEMAND = true;

    private boolean useFailedRequestsInDemand = DEFAULT_USE_FAILED_REQUESTS_IN_DEMAND;

    /**
     * If {code true} the computation of server and network demand will take
     * into account information about failed requests, if the service provides
     * this information.
     * 
     * @return if failed requests are used in the demand computation
     */
    public boolean getUseFailedRequestsInDemand() {
        return useFailedRequestsInDemand;
    }

    /**
     * 
     * @param v
     *            {@Link #getUseFailedRequestsInDemand()}
     */
    public void setUseFailedRequestsInDemand(final boolean v) {
        useFailedRequestsInDemand = v;
    }

    private static final boolean DEFAULT_DNS_DELEGATION_USE_TCP = true;
    private boolean dnsDelegationUseTcp = DEFAULT_DNS_DELEGATION_USE_TCP;

    /**
     * 
     * @return true if DNS delegation should use TCP instead of UDP
     */
    public boolean getDnsDelegationUseTcp() {
        return dnsDelegationUseTcp;
    }

    /**
     * 
     * @param v
     *            {@link #getDnsDelegationUseTcp()}
     */
    public void setDnsDelegationUseTcp(final boolean v) {
        dnsDelegationUseTcp = v;
    }

    private static final boolean DEFAULT_RLG_STOP_FOR_DCOP = true;
    private boolean rlgStopForDcop = DEFAULT_RLG_STOP_FOR_DCOP;

    /**
     * 
     * @return true if RLG should stop all containers in regions where DCOP is
     *         not sending traffic.
     */
    public boolean getRlgStopForDcop() {
        return rlgStopForDcop;
    }

    /**
     * 
     * @param v
     *            {@link #getDnsDelegationUseTcp()}
     */
    public void setRlgStopForDcop(final boolean v) {
        rlgStopForDcop = v;
    }

    /**
     * Algorithm to use to compute link delay.
     * 
     * @author jschewe
     *
     */
    public enum LinkDelayAlgorithm {

        /**
         * Compute the average link delay for links between the same region.
         */
        AVERAGE,
        /**
         * Use the minimum link delay for links between the same region.
         */
        MINIMUM;
    }

    private static final LinkDelayAlgorithm DEFAULT_LINK_DELAY_ALGORITHM = LinkDelayAlgorithm.AVERAGE;

    private LinkDelayAlgorithm linkDelayAlgorithm = DEFAULT_LINK_DELAY_ALGORITHM;

    /**
     * 
     * @return the algorithm to use for computing link delay
     */
    public LinkDelayAlgorithm getLinkDelayAlgorithm() {
        return linkDelayAlgorithm;
    }

    /**
     * 
     * @param v
     *            {@link #getLinkDelayAlgorithm()}
     */
    public void setLinkDelayAlgorithm(final LinkDelayAlgorithm v) {
        linkDelayAlgorithm = v;
    }

    private static final boolean DEFAULT_IFTOP_USE_CUSTOM = true;

    private boolean iftopUseCustom = DEFAULT_IFTOP_USE_CUSTOM;

    /**
     * 
     * @return if true, use the custom iftop binary
     */
    public boolean getIftopUseCustom() {
        return iftopUseCustom;
    }

    /**
     * 
     * @param v
     *            {@link #getIftopUseCustom()}
     */
    public void setIftopUseCustom(final boolean v) {
        iftopUseCustom = v;
    }

    /**
     * Demand computation algorithms.
     */
    public enum DemandComputationAlgorithm {
        /**
         * Simple moving average using {@link EstimationWindow} to determine the
         * duration over which to compute the average.
         * 
         * @see AgentConfiguration#getRlgEstimationWindow()
         * @see AgentConfiguration#getDcopEstimationWindow()
         */
        MOVING_AVERAGE,
        /**
         * Exponential decay algorithm. At each sample the new value is equal to
         * {@code oldValue + alpha * (value - oldValue)}.
         * 
         * @see AgentConfiguration#getRlgExponentialDemandDecay()
         * @see AgentConfiguration#getDcopExponentialDemandDecay()
         */
        EXPONENTIAL_DECAY,
    }

    private static final DemandComputationAlgorithm DEFAULT_DEMAND_COMPUTATION_ALGORITHM = DemandComputationAlgorithm.EXPONENTIAL_DECAY;
    private DemandComputationAlgorithm demandComputationAlgorithm = DEFAULT_DEMAND_COMPUTATION_ALGORITHM;

    /**
     * 
     * @return the algorithm used to compute demand.
     */
    public DemandComputationAlgorithm getDemandComputationAlgorithm() {
        return demandComputationAlgorithm;
    }

    /**
     * 
     * @param v
     *            {@link #getDemandComputationAlgorithm()}
     */
    public void setDemandComputationAlgorithm(final DemandComputationAlgorithm v) {
        demandComputationAlgorithm = v;
    }

    private static final double DEFAULT_RLG_EXPONENTIAL_DEMAND_DECAY = 0.45;
    private double rlgExponentialDemandDecay = DEFAULT_RLG_EXPONENTIAL_DEMAND_DECAY;

    /**
     * 
     * @return the decay value to use for RLG when the
     *         {@link #getDemandComputationAlgorithm()} is
     *         {@link AgentConfiguration.DemandComputationAlgorithm#EXPONENTIAL_DECAY}.
     */
    public double getRlgExponentialDemandDecay() {
        return rlgExponentialDemandDecay;
    }

    /**
     * 
     * @param v
     *            {@link #getRlgExponentialDemandDecay()}
     */
    public void setRlgExponentialDemandDecay(final double v) {
        rlgExponentialDemandDecay = v;
    }

    private static final double DEFAULT_DCOP_EXPONENTIAL_DEMAND_DECAY = 0.01;
    private double dcopExponentialDemandDecay = DEFAULT_DCOP_EXPONENTIAL_DEMAND_DECAY;

    /**
     * 
     * @return the decay value to use for DCOP when the
     *         {@link #getDemandComputationAlgorithm()} is
     *         {@link AgentConfiguration.DemandComputationAlgorithm#EXPONENTIAL_DECAY}.
     */
    public double getDcopExponentialDemandDecay() {
        return dcopExponentialDemandDecay;
    }

    /**
     * 
     * @param v
     *            {@link #getDcopExponentialDemandDecay()}
     */
    public void setDcopExponentialDemandDecay(final double v) {
        dcopExponentialDemandDecay = v;
    }

}
