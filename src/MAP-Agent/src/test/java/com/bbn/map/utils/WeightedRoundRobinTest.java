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
package com.bbn.map.utils;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.RoundRobinAlgorithm;
import com.bbn.map.dns.PlanTranslatorTest;
import com.bbn.map.simulator.TestUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for {@link WeightedRoundRobin}.
 * 
 * @author awald
 *
 */
public class WeightedRoundRobinTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTranslatorTest.class);

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Tests weighted round robin by adding weighted records and comparing
     * expected percentages to occurrence count percentages.
     */
    @Test
    public void test() {
        final long precision = 1000;
        weightedRoundRobinTest(precision, RoundRobinAlgorithm.COUNTERS, false, 0);
    }

    private void weightedRoundRobinTest(final long precision,
            final RoundRobinAlgorithm rrAlgorithm,
            final boolean preferUnused,
            final int numShuffles) {
        AgentConfiguration.getInstance().setRoundRobinAlgorithm(rrAlgorithm);
        AgentConfiguration.getInstance().setRandomRoundRobinPreferUnused(preferUnused);
        AgentConfiguration.getInstance().setRandomRoundRobinNumShuffles(numShuffles);

        final double tolerance = 2.0 / precision;
        final long numRecordsToGather = 2 * precision;

        final WeightedRoundRobin<Object> wrr = new WeightedRoundRobin<Object>();

        final Map<Object, Double> expectedWeights = new HashMap<>();

        double totalWeight = 0;

        final Random random = new Random();

        for (int n = 0; n < precision; n++) {
            final double weight = random.nextDouble();
            final Object record = Integer.valueOf(n);
            expectedWeights.put(record, weight);
            totalWeight += weight;

            wrr.addRecord(record, weight);
        }

        final Map<Object, Double> actualRecordProportions = new HashMap<>();

        for (long n = 0; n < numRecordsToGather; n++) {
            actualRecordProportions.merge(wrr.getNextRecord(), 1.0, Double::sum);
        }

        for (final Map.Entry<Object, Double> entry : actualRecordProportions.entrySet()) {
            final double expectedValue = expectedWeights.get(entry.getKey()) / totalWeight;
            final double actualValue = entry.getValue() / numRecordsToGather;
            LOGGER.info("expected: {}, actual: {}, tolerance: {}", expectedValue, actualValue, tolerance);
            assertThat(actualValue, closeTo(expectedValue, tolerance));
        }
    }

    /**
     * Tests weighted round robin by adding weighted records and comparing
     * expected percentages to occurrence count percentages. This uses the
     * random record list.
     */
    @Test
    public void testRandomRecordList() {
        final long precision = 1000;
        weightedRoundRobinTest(precision, RoundRobinAlgorithm.RANDOM_RECORDS, true, 3);
    }
}
