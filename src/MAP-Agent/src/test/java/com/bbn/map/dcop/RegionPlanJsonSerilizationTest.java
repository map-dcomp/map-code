package com.bbn.map.dcop;

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
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Ensures that a {@link RegionPlan} with a timestamp is serialized to JSON and
 * deserializes from Json properly.
 * 
 * @author awald
 *
 */
public class RegionPlanJsonSerilizationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionPlanJsonSerilizationTest.class);

    private static final ObjectMapper JSON_MAPPER = JsonUtils.getStandardMapObjectMapper();

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Check that a {@link RegionPlan} can be serialized and deserialized
     * properly and that two plans with identical content but differing
     * timestamps are considered equal.
     */
    @Test
    public void test() {
        long time = System.currentTimeMillis();
        RegionIdentifier region = new StringRegionIdentifier("X");
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> plan = ImmutableMap.of(
                new ApplicationCoordinates("com.bbn", "app", "1.0"),
                ImmutableMap.of(new StringRegionIdentifier("A"), 0.5));

        RegionPlan regionPlan = new RegionPlan(region, time, plan);
        LOGGER.info("regionPlan: {}", regionPlan);

        try {
            String serializedRegionPlan = JSON_MAPPER.writeValueAsString(regionPlan);

            try {
                RegionPlan deserializedRegionPlan = JSON_MAPPER.readValue(serializedRegionPlan, RegionPlan.class);
                LOGGER.info("deserializedRegionPlan: {}", deserializedRegionPlan);

                Assert.assertEquals(time, deserializedRegionPlan.getTimestamp());
                Assert.assertEquals(region, deserializedRegionPlan.getRegion());
                Assert.assertEquals(plan, deserializedRegionPlan.getPlan());

                Assert.assertEquals(regionPlan, deserializedRegionPlan);

                // ensure that plans with differing timestamps are still
                // considered equal
                deserializedRegionPlan.setTimestamp(System.currentTimeMillis());
                Assert.assertEquals(regionPlan, deserializedRegionPlan);
            } catch (IOException | RuntimeException e) {
                Assert.fail("Deserialization of " + RegionPlan.class.getSimpleName() + " String '"
                        + serializedRegionPlan + "' failed:\n" + e);
            }
        } catch (IOException | RuntimeException e) {
            Assert.fail("Serialization of " + plan + " failed:\n" + e);
        }
    }
}
