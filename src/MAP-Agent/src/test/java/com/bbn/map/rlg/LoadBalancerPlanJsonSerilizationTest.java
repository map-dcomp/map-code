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
package com.bbn.map.rlg;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.TestUtils;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan.ContainerInfo;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Ensures that a {@link LoadBalancerPlan} with a timestamp is serialized to
 * JSON and deserializes from Json properly.
 * 
 * @author awald
 *
 */
public class LoadBalancerPlanJsonSerilizationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerPlanJsonSerilizationTest.class);

    private static final ObjectMapper JSON_MAPPER = JsonUtils.getStandardMapObjectMapper();

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Check that a {@link LoadBalancerPlan} can be serialized and deserialized
     * properly and that two plans with identical content but differing
     * timestamps are considered equal.
     */
    @Test
    public void test() {
        long time = System.currentTimeMillis();
        RegionIdentifier region = new StringRegionIdentifier("X");
        NodeIdentifier node = new DnsNameIdentifier("serverX1");
        ImmutableMap<NodeIdentifier, ImmutableCollection<ContainerInfo>> servicePlan = ImmutableMap.of(node,
                ImmutableList.of());
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap.of(
                new ApplicationCoordinates("com.bbn", "app", "1.0"),
                ImmutableMap.of(new StringRegionIdentifier("A"), 0.5));

        LoadBalancerPlan plan = new LoadBalancerPlan(region, time, servicePlan, overflowPlan);
        LOGGER.info("plan: {}", plan);

        try {
            String serializedPlan = JSON_MAPPER.writeValueAsString(plan);

            try {
                LoadBalancerPlan deserializedPlan = JSON_MAPPER.readValue(serializedPlan, LoadBalancerPlan.class);
                LOGGER.info("deserializedPlan: {}", deserializedPlan);

                Assert.assertEquals(time, deserializedPlan.getTimestamp());
                Assert.assertEquals(plan, deserializedPlan);
                Assert.assertEquals(region, deserializedPlan.getRegion());
                Assert.assertEquals(servicePlan, deserializedPlan.getServicePlan());
                Assert.assertEquals(overflowPlan, deserializedPlan.getOverflowPlan());

                // ensure that plans with differing timestamps are still
                // considered equal
                deserializedPlan.setTimestamp(System.currentTimeMillis());
                Assert.assertEquals(plan, deserializedPlan);
            } catch (IOException | RuntimeException e) {
                Assert.fail("Deserialization of " + LoadBalancerPlan.class.getSimpleName() + " String '"
                        + serializedPlan + "' failed:\n" + e);
            }
        } catch (IOException | RuntimeException e) {
            Assert.fail("Serialization of " + plan + " failed:\n" + e);
        }
    }
}
