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

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.simulator.HardwareConfiguration;
import com.bbn.map.simulator.TestUtils;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for JSON serialization and deserialization of
 * {@link HardwareConfiguration}.
 * 
 * @author jschewe
 *
 */
public class HardwareConfigurationSerialization {

    /**
     * Rules for running tests.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Test that a hardware configuration before dcomp and emulab properties
     * were added can be parsed.
     * 
     * @throws URISyntaxException
     *             Test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testVersion0() throws URISyntaxException, IOException {
        final URL url = HardwareConfigurationSerialization.class
                .getResource("data/hardware-configuration_version0.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<String, HardwareConfiguration> actual = HardwareConfiguration
                .parseHardwareConfigurations(path);
        assertThat(actual, notNullValue());

        assertThat(actual, not(anEmptyMap()));
    }

    /**
     * Test that specifying the dcomp and emulab properties work.
     * 
     * @throws URISyntaxException
     *             Test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testVersion1() throws URISyntaxException, IOException {
        final URL url = HardwareConfigurationSerialization.class
                .getResource("data/hardware-configuration_version1.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<String, HardwareConfiguration> actual = HardwareConfiguration
                .parseHardwareConfigurations(path);
        assertThat(actual, notNullValue());

        assertThat(actual, not(anEmptyMap()));
    }

    /**
     * Test that it's OK to skip specifying the capacity.
     * 
     * @throws URISyntaxException
     *             Test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testVersion1NoCapacity() throws URISyntaxException, IOException {
        final URL url = HardwareConfigurationSerialization.class
                .getResource("data/hardware-configuration_version1_missing-capacity.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<String, HardwareConfiguration> actual = HardwareConfiguration
                .parseHardwareConfigurations(path);
        assertThat(actual, notNullValue());

        assertThat(actual, not(anEmptyMap()));
    }

    /**
     * Ensure that DCOMP starting with a comma causes a failure.
     * 
     * @throws URISyntaxException
     *             Test error
     * @throws IOException
     *             test error
     */
    @Test(expected = IllegalArgumentException.class)
    public void testVersion1StartComma() throws URISyntaxException, IOException {
        final URL url = HardwareConfigurationSerialization.class
                .getResource("data/hardware-configuration_version1_start-comma.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<String, HardwareConfiguration> actual = HardwareConfiguration
                .parseHardwareConfigurations(path);
        assertThat(actual, notNullValue());

        assertThat(actual, not(anEmptyMap()));
    }

    /**
     * Ensure that DCOMP ending with a comma causes a failure.
     * 
     * @throws URISyntaxException
     *             Test error
     * @throws IOException
     *             test error
     */
    @Test(expected = IllegalArgumentException.class)
    public void testVersion1EndComma() throws URISyntaxException, IOException {
        final URL url = HardwareConfigurationSerialization.class
                .getResource("data/hardware-configuration_version1_end-comma.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<String, HardwareConfiguration> actual = HardwareConfiguration
                .parseHardwareConfigurations(path);
        assertThat(actual, notNullValue());

        assertThat(actual, not(anEmptyMap()));
    }

}
