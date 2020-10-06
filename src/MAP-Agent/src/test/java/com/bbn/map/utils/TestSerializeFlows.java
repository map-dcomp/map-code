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
package com.bbn.map.utils;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test serialization and deserialization of {@link NodeNetworkFlow} and
 * {@link RegionNetworkFlow}.
 * 
 * @author jschewe
 *
 */
public class TestSerializeFlows {

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Check that we can serialize a {@link NodeNetworkFlow} as a map key and
     * deserialize it.
     * 
     * @throws IOException
     *             test error
     */
    @Test
    public void testNodeSerializeDeserialize() throws IOException {
        final NodeIdentifier src = new DnsNameIdentifier("src");
        final NodeIdentifier dest = new DnsNameIdentifier("dest");

        final NodeNetworkFlow flow = new NodeNetworkFlow(src, dest, dest);
        final ImmutableMap<NodeNetworkFlow, Double> map = ImmutableMap.of(flow, 10D);

        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
        try (StringWriter writer = new StringWriter()) {
            mapper.writeValue(writer, map);

            final String data = writer.toString();
            final ImmutableMap<NodeNetworkFlow, Double> actual = mapper.readValue(data,
                    new TypeReference<ImmutableMap<NodeNetworkFlow, Double>>() {
                    });
            assertThat(actual, equalTo(map));
        }
    }

    /**
     * Check that we can serialize a {@link RegionNetworkFlow} as a map key and
     * deserialize it.
     * 
     * @throws IOException
     *             test error
     */
    @Test
    public void testRegionSerializeDeserialize() throws IOException {
        final RegionIdentifier src = new StringRegionIdentifier("src");
        final RegionIdentifier dest = new StringRegionIdentifier("dest");

        final RegionNetworkFlow flow = new RegionNetworkFlow(src, dest, dest);
        final ImmutableMap<RegionNetworkFlow, Double> map = ImmutableMap.of(flow, 10D);

        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
        try (StringWriter writer = new StringWriter()) {
            mapper.writeValue(writer, map);

            final String data = writer.toString();
            final ImmutableMap<RegionNetworkFlow, Double> actual = mapper.readValue(data,
                    new TypeReference<ImmutableMap<RegionNetworkFlow, Double>>() {
                    });
            assertThat(actual, equalTo(map));
        }
    }
}
