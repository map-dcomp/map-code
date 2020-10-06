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
package com.bbn.map.simulator;

/**
 * Global configuration information for MAP simulation.
 */
public final class SimulationConfiguration {

    // eventually there should be set methods or a method to read the values in
    // from a file.

    private SimulationConfiguration() {
    }

    private static final Object INSTANCE_LOCK = new Object();

    private static SimulationConfiguration instance = null;

    /**
     * 
     * @return the singleton instance
     */
    public static SimulationConfiguration getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (null == instance) {
                instance = new SimulationConfiguration();
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

    private static final double SLOW_NETWORK_THRESHOLD_DEFAULT = 0.8;
    private double slowNetworkThreshold = SLOW_NETWORK_THRESHOLD_DEFAULT;

    /**
     * This value should be between 0 and 1 as it represents a percentage.
     * 
     * @return the slow threshold for network links, defaults to 0.8.
     * @see RequestResult#SLOW
     */
    public double getSlowNetworkThreshold() {
        return slowNetworkThreshold;
    }

    /**
     * 
     * @param v
     *            the new threshold
     * @throws IllegalArgumentException
     *             if the value is not between 0 and 1
     * @see #getSlowNetworkThreshold()
     */
    public void setSlowNetworkThreshold(final double v) {
        if (v < 0 || 1 < v) {
            throw new IllegalArgumentException("Slow network threshold must be between 0 and 1");
        }

        slowNetworkThreshold = v;
    }

    private static final double SLOW_SERVER_THRESHOLD_DEFAULT = 0.8;
    private double slowServerThreshold = SLOW_SERVER_THRESHOLD_DEFAULT;

    /**
     * This value should be between 0 and 1 as it represents a percentage.
     * 
     * @return the slow threshold for servers, defaults to 0.8.
     * @see RequestResult#SLOW
     */
    public double getSlowServerThreshold() {
        return slowServerThreshold;
    }

    /**
     * 
     * @param v
     *            the new threshold
     * @throws IllegalArgumentException
     *             if the value is not between 0 and 1
     * @see #getSlowServerThreshold()
     */
    public void setSlowServerThreshold(final double v) {
        if (v < 0 || 1 < v) {
            throw new IllegalArgumentException("Slow server threshold must be between 0 and 1");
        }

        slowServerThreshold = v;
    }

}
