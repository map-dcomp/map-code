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
package com.bbn.map.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
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
     * @param maximumServiceContainers
     *            see {@link #getMaximumServiceContainers()}
     * @param dcompConfig
     *            see {@link #getDcompConfig()}
     * @param emulabConfig
     *            see {@link #emulabConfig}
     * @throws IllegalArgumentException
     *             if dcompConfig starts or ends with a comma
     */
    public HardwareConfiguration(@Nonnull final String name,
            @Nonnull final ImmutableMap<NodeAttribute, Double> capacity,
            final int maximumServiceContainers,
            final String dcompConfig,
            final String emulabConfig) {
        this.name = Objects.requireNonNull(name);
        this.capacity = Objects.requireNonNull(capacity);
        this.dcompConfig = null == dcompConfig ? null : dcompConfig.trim();
        this.emulabConfig = null == emulabConfig ? null : emulabConfig.trim();

        if (null != this.dcompConfig && (this.dcompConfig.startsWith(",") || this.dcompConfig.endsWith(","))) {
            throw new IllegalArgumentException(
                    "DCOMP config may not start or end with a comma: '" + this.dcompConfig + "'");
        }

        // if the JSON file doesn't specify the maximum containers attribute,
        // find it in the capacity
        if (0 == maximumServiceContainers) {
            this.maximumServiceContainers = capacity.getOrDefault(NodeAttribute.TASK_CONTAINERS, 0D).intValue();
        } else {
            this.maximumServiceContainers = maximumServiceContainers;
        }
    }

    private final String dcompConfig;

    /**
     * This string may be null. If it's not null it will not start with a comma.
     * The value of this field is added to the device configuration call.
     * 
     * @return the configuration string for the DCOMP testbed
     */
    public String getDcompConfig() {
        return dcompConfig;
    }

    private final String emulabConfig;

    /**
     * This string may be null. If it is not null it must be the name of a
     * hardware configuration known to the emulab testbed.
     * 
     * @return the configuration string for the Emulab testbed
     */
    public String getEmulabConfig() {
        return emulabConfig;
    }

    private final String name;

    /**
     * @return the name of the hardware configuration
     */
    @Nonnull
    public String getName() {
        return name;
    }

    private final ImmutableMap<NodeAttribute, Double> capacity;

    /**
     * 
     * @return the capacity of the server
     */
    @Nonnull
    public ImmutableMap<NodeAttribute, Double> getCapacity() {
        return capacity;
    }

    private final int maximumServiceContainers;

    /**
     * 
     * @return the maximum number of service containers that can run on this
     *         hardware.
     */
    public int getMaximumServiceContainers() {
        return maximumServiceContainers;
    }

    /**
     * Name of the file to read inside the scenario for hardware configuration
     * information. If more than 1 hardware configuration has the same name, the
     * last one is used.
     */
    public static final String HARDWARE_CONFIG_FILENAME = "hardware-configurations.json";

    /**
     * Parse the hardware configuration information. If the file does not exist
     * and empty result is returned.
     * 
     * @param path
     *            The path to the hardware configuration to read
     * @return name - configuration, empty if the path does not exist
     * @throws IOException
     *             if there is a problem reading the file
     */
    @Nonnull
    public static ImmutableMap<String, HardwareConfiguration> parseHardwareConfigurations(@Nonnull final Path path)
            throws IOException {
        if (!Files.exists(path)) {
            // no hardware configs
            return ImmutableMap.of();
        }

        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(new GuavaModule());

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final ImmutableList<HardwareConfiguration.Parsed> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<HardwareConfiguration.Parsed>>() {
                    });
            final ImmutableMap.Builder<String, HardwareConfiguration> map = ImmutableMap.builder();
            list.forEach(parsed -> {

                final HardwareConfiguration config = new HardwareConfiguration(parsed.name, //
                        parsed.capacity == null ? ImmutableMap.of() : parsed.capacity, //
                        parsed.maximumServiceContainers, //
                        parsed.dcompConfig, //
                        parsed.emulabConfig);
                map.put(config.getName(), config);
            });

            return map.build();
        }
    }

    // CHECKSTYLE:OFF - data class for JSON
    /**
     * Used by JSON parsing and converted to {@link HardwareConfiguration} after
     * parsing. This ensures that {@link HardwareConfiguration} is immutable,
     * but allows for each JSON parsing and can be used to handle different
     * versions of the JSON, if needed.
     */
    private static final class Parsed {
        public String name;
        public ImmutableMap<NodeAttribute, Double> capacity;
        public int maximumServiceContainers;
        public String emulabConfig;
        public String dcompConfig;
    }
    // CHECKSTYLE:ON
}
