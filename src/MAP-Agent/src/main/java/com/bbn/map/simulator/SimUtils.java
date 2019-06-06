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

import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;

/**
 * Some tools for working with simulations.
 * 
 * @author jschewe
 *
 */
public final class SimUtils {

    private SimUtils() {
    }

    /**
     * Each C/G operation in the AP program can take up to 2 times the network
     * diameter to complete. As of 10/25/2017 our program is G -> C -> G
     * (measure, collect, broadcast), so the multiplier is 6.
     */
    private static final int AP_ROUNDS_MULTIPLIER = 6;
    /**
     * Extra rounds to wait to handle differences in systems.
     */
    private static final int AP_ROUNDS_FUZZ = 10;

    /**
     * Compute the number of rounds it will take AP to stabilize. This is based
     * on the AP program that we are running and the diameter of the network. If
     * the network diameter cannot be determined an exception is thrown.
     * 
     * @param sim
     *            the simulation containing the network
     * @return the number of rounds for AP to stabilize
     * @throws RuntimeException
     *             if the network diameter cannot be determined
     * @see Simulation#getNetworkDiameter()
     */
    public static int computeRoundsToStabilize(@Nonnull final Simulation sim) throws RuntimeException {
        final double networkDiameter = sim.getNetworkDiameter();
        if (!Double.isFinite(networkDiameter)) {
            throw new RuntimeException("Cannot find the network diameter");
        }

        final int numApRoundsToStabilize = (int) Math.ceil(networkDiameter * AP_ROUNDS_MULTIPLIER) + AP_ROUNDS_FUZZ;

        return numApRoundsToStabilize;
    }

    /**
     * Wait for the simulation to complete a number of AP iterations. When this
     * method returns the {@link Controller#getExecutionCount()} will be greater
     * than the current value by at least <code>numRounds</code>
     * 
     * @param sim
     *            the simulation that is being run
     * @param numRounds
     *            the number of rounds to wait
     */
    public static void waitForApRounds(@Nonnull final Simulation sim, final int numRounds) {
        final long timeToWait = AgentConfiguration.getInstance().getApRoundDuration().toMillis();
    
        // get current max number of executions so we know how long to wait
        final Map<Controller, Long> currentMaxExecutions = sim.getScenario().getServers().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, e -> e.getValue().getExecutionCount()));
    
        // poll the simulation until we have enough executions
        boolean doneWaiting = false;
        while (!doneWaiting) {
            sim.getClock().waitForDuration(timeToWait);
    
            doneWaiting = currentMaxExecutions.entrySet().stream().allMatch(e -> {
                return e.getKey().getExecutionCount() >= e.getValue() + numRounds;
            });
        }
    }

}
