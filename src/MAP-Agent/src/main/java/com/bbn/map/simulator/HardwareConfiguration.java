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

import javax.annotation.Nonnull;

import com.bbn.map.common.value.NodeMetricName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Configuration of some hardware. This is used to represent the hardware for
 * servers specified in the NS2 file.
 */
public class HardwareConfiguration {

    /**
     * Construct an instance of a hardware configuration.
     * 
     * @param name
     *            see {@link #getName()}
     * @param capacity
     *            see {@link #getCapacity()}
     */
    public HardwareConfiguration(@JsonProperty("name") @Nonnull final String name,
            @JsonProperty("capacity") @Nonnull final ImmutableMap<NodeMetricName, Double> capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    private final String name;

    /**
     * @return the name of the hardware configuration
     */
    @Nonnull
    public String getName() {
        return name;
    }

    private final ImmutableMap<NodeMetricName, Double> capacity;

    /**
     * 
     * @return the capacity of the server
     */
    @Nonnull
    public ImmutableMap<NodeMetricName, Double> getCapacity() {
        return capacity;
    }

}
