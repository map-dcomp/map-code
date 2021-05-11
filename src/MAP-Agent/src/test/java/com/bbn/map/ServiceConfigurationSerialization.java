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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for JSON serialization and deserialization of
 * {@link ServiceConfiguration}.
 * 
 * @author jschewe
 *
 */
public class ServiceConfigurationSerialization {

    /**
     * Rules for running tests.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Make sure there aren't any exceptions parsing the single default node and
     * that it's parsed properly.
     * 
     * @throws URISyntaxException
     *             internal test error
     * @throws IOException
     *             internal test error
     */
    @Test
    public void testDeserializeSingleDefaultNode() throws URISyntaxException, IOException {
        final String expectedDefaultNodeName = "serverX";
        final NodeIdentifier expectedDefaultNode = new DnsNameIdentifier(expectedDefaultNodeName);
        final int expectedNumInstances = 1;
        final String expectedServiceGroup = "test";
        final String expectedServiceName = "test-service";
        final String expectedServiceVersion = "1";
        final ApplicationCoordinates expectedService = new ApplicationCoordinates(expectedServiceGroup,
                expectedServiceName, expectedServiceVersion);

        final URL url = ServiceConfigurationSerialization.class
                .getResource("data/service-configurations_defaultNode-single.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> actual = ServiceConfiguration
                .parseServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ServiceConfiguration actualConfig = actual.get(expectedService);
        assertThat(actualConfig, notNullValue());

        final ImmutableMap<NodeIdentifier, Integer> defaultNodes = actualConfig.getDefaultNodes();
        assertThat(defaultNodes.size(), is(1));

        final Integer actualInstances = defaultNodes.get(expectedDefaultNode);
        assertThat(actualInstances, notNullValue());

        assertThat(actualInstances, is(expectedNumInstances));
    }

    /**
     * Make sure there aren't any exceptions parsing the multiple node and that
     * it's parsed properly.
     * 
     * @throws URISyntaxException
     *             internal test error
     * @throws IOException
     *             internal test error
     */
    @Test
    public void testDeserializeMultipleDefaultNode() throws URISyntaxException, IOException {
        final String expectedDefaultNode1Name = "serverX";
        final NodeIdentifier expectedDefaultNode1 = new DnsNameIdentifier(expectedDefaultNode1Name);
        final int expectedNode1NumInstances = 5;

        final String expectedDefaultNode2Name = "nodeX2";
        final NodeIdentifier expectedDefaultNode2 = new DnsNameIdentifier(expectedDefaultNode2Name);
        final int expectedNode2NumInstances = 3;

        final String expectedServiceGroup = "test";
        final String expectedServiceName = "test-service";
        final String expectedServiceVersion = "1";
        final ApplicationCoordinates expectedService = new ApplicationCoordinates(expectedServiceGroup,
                expectedServiceName, expectedServiceVersion);
        final int expectedNumberOfDefaultNodes = 2;

        final URL url = ServiceConfigurationSerialization.class
                .getResource("data/service-configurations_defaultNode-multiple.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> actual = ServiceConfiguration
                .parseServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ServiceConfiguration actualConfig = actual.get(expectedService);
        assertThat(actualConfig, notNullValue());

        final ImmutableMap<NodeIdentifier, Integer> defaultNodes = actualConfig.getDefaultNodes();
        assertThat(defaultNodes.size(), is(expectedNumberOfDefaultNodes));

        final Integer actualInstancesNode1 = defaultNodes.get(expectedDefaultNode1);
        assertThat(actualInstancesNode1, notNullValue());

        assertThat(actualInstancesNode1, is(expectedNode1NumInstances));

        final Integer actualInstancesNode2 = defaultNodes.get(expectedDefaultNode2);
        assertThat(actualInstancesNode2, notNullValue());

        assertThat(actualInstancesNode2, is(expectedNode2NumInstances));
    }

    /**
     * Make sure that parsing a configuration without a specified priority
     * results in the deafult priority.
     * 
     * @throws URISyntaxException
     *             internal test error
     * @throws IOException
     *             internal test error
     */
    @Test
    public void testDefaultPriority() throws URISyntaxException, IOException {
        final String expectedServiceGroup = "test";
        final String expectedServiceName = "test-service";
        final String expectedServiceVersion = "1";
        final ApplicationCoordinates expectedService = new ApplicationCoordinates(expectedServiceGroup,
                expectedServiceName, expectedServiceVersion);

        final URL url = ServiceConfigurationSerialization.class
                .getResource("data/service-configurations_priority-default.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> actual = ServiceConfiguration
                .parseServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ServiceConfiguration actualConfig = actual.get(expectedService);
        assertThat(actualConfig, notNullValue());

        assertThat(actualConfig.getPriority(), is(ApplicationSpecification.DEFAULT_PRIORITY));
    }

    /**
     * Test that reading in a specified priority works.
     * 
     * @throws URISyntaxException
     *             internal test error
     * @throws IOException
     *             internal test error
     */
    @Test
    public void testSpecifiedPriority() throws URISyntaxException, IOException {
        final String expectedServiceGroup = "test";
        final String expectedServiceName = "test-service";
        final String expectedServiceVersion = "1";
        final ApplicationCoordinates expectedService = new ApplicationCoordinates(expectedServiceGroup,
                expectedServiceName, expectedServiceVersion);
        final int expectedPriority = 10; // needs to match the file

        final URL url = ServiceConfigurationSerialization.class
                .getResource("data/service-configurations_priority-specified.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());

        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> actual = ServiceConfiguration
                .parseServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ServiceConfiguration actualConfig = actual.get(expectedService);
        assertThat(actualConfig, notNullValue());

        assertThat(actualConfig.getPriority(), is(expectedPriority));
    }

}
