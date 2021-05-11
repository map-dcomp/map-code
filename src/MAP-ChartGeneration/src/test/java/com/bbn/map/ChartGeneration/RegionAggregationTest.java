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
package com.bbn.map.ChartGeneration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;

/**
 * Test for region aggregation.
 * 
 * @author awald
 *
 */
public class RegionAggregationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionAggregationTest.class);

    /**
     * Tests if compute load values are being placed in the correct time bins
     * and if values in the same time bin and same region are being summed
     * together properly.
     */
    @Test
    public void test() {
        HashMap<String, String> nodeToRegionMap = new HashMap<>();
        nodeToRegionMap.put("nodea0", "A");
        nodeToRegionMap.put("nodea1", "A");
        nodeToRegionMap.put("nodeb0", "B");
        nodeToRegionMap.put("nodeb1", "B");

        final long firstBinCenter = 50;
        final long dataTimeInterval = 100;

        Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> nodeData = new HashMap<>();

        final double aContainers = 6;
        final double aDemand = 6;
        final double aCapacity = 0;
        Map<NodeAttribute, LoadValue> a = new HashMap<>();
        a.put(NodeAttribute.TASK_CONTAINERS, new LoadValue(aContainers, aDemand, aCapacity));

        final double bContainers = 8;
        final double bDemand = 6;
        final double bCapacity = 0;
        Map<NodeAttribute, LoadValue> b = new HashMap<>();
        b.put(NodeAttribute.TASK_CONTAINERS, new LoadValue(bContainers, bDemand, bCapacity));

        final double cContainers = 17;
        final double cDemand = 20;
        final double cCapacity = 0;
        Map<NodeAttribute, LoadValue> c = new HashMap<>();
        c.put(NodeAttribute.TASK_CONTAINERS, new LoadValue(cContainers, cDemand, cCapacity));

        final double dContainers = 21;
        final double dDemand = 21;
        final double dCapacity = 0;
        Map<NodeAttribute, LoadValue> d = new HashMap<>();
        d.put(NodeAttribute.TASK_CONTAINERS, new LoadValue(dContainers, dDemand, dCapacity));

        Map<String, Map<NodeAttribute, LoadValue>> t1Nodes = new HashMap<>();
        t1Nodes.put("nodeA0", a);

        Map<String, Map<NodeAttribute, LoadValue>> t2Nodes = new HashMap<>();
        t2Nodes.put("nodeA1", b);

        Map<String, Map<NodeAttribute, LoadValue>> t3Nodes = new HashMap<>();
        t1Nodes.put("nodeB0", c);
        t1Nodes.put("nodeB1", d);

        final long t1Time = 40;
        final long t2Time = 92;
        final long t3Time = 93;
        nodeData.put(t1Time, t1Nodes);
        nodeData.put(t2Time, t2Nodes);
        nodeData.put(t3Time, t3Nodes);

        Map<String, Map<NodeAttribute, LoadValue>> t4Nodes = new HashMap<>();
        t4Nodes.put("nodeA0", a);
        t4Nodes.put("nodeB1", d);

        Map<String, Map<NodeAttribute, LoadValue>> t5Nodes = new HashMap<>();
        t5Nodes.put("nodeB0", b);

        Map<String, Map<NodeAttribute, LoadValue>> t6Nodes = new HashMap<>();
        t6Nodes.put("nodeA1", c);

        final long t4Time = 540;
        final long t5Time = 592;
        final long t6Time = 593;
        nodeData.put(t4Time, t4Nodes);
        nodeData.put(t5Time, t5Nodes);
        nodeData.put(t6Time, t6Nodes);

        // place data in time bins, and within each bin, place data into region
        // groups
        Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> binnedData = LoadChartGenerator
                .binByLabelGroup(nodeData, nodeToRegionMap, firstBinCenter, dataTimeInterval);

        assertTrue(binnedData.get(firstBinCenter).get("A").containsKey("nodeA0"));
        assertTrue(binnedData.get(firstBinCenter).get("A").containsKey("nodeA1"));
        assertTrue(binnedData.get(firstBinCenter).get("B").containsKey("nodeB0"));
        assertTrue(binnedData.get(firstBinCenter).get("B").containsKey("nodeB1"));

        final long fifthBinCenter = firstBinCenter + 5 * dataTimeInterval;
        assertTrue(binnedData.get(fifthBinCenter).get("A").containsKey("nodeA0"));
        assertTrue(binnedData.get(fifthBinCenter).get("A").containsKey("nodeA1"));
        assertTrue(binnedData.get(fifthBinCenter).get("B").containsKey("nodeB0"));
        assertTrue(binnedData.get(fifthBinCenter).get("B").containsKey("nodeB1"));

        // sum the data for each region group within each bin
        Map<Long, Map<String, Map<NodeAttribute, SumCount>>> collapsedBinnedData = LoadChartGenerator
                .collapseBins(binnedData);

        Map<Long, Map<String, Map<NodeAttribute, SumCount>>> expectedCollapsedBinnedData = new HashMap<>();

        Map<String, Map<NodeAttribute, SumCount>> bin1 = new HashMap<>();

        Map<NodeAttribute, SumCount> regionValues1A = new HashMap<>();
        final double region1AContainerValue = 14;
        final double region1ADemandValue = 12;
        final double region1AContainerCapacity = 0;
        final int region1AContainerCount = 2;
        regionValues1A.put(NodeAttribute.TASK_CONTAINERS, new SumCount(new LoadValue(region1AContainerValue, region1ADemandValue, region1AContainerCapacity), region1AContainerCount));
        bin1.put("A", regionValues1A);

        final double region1BContainerValue = 38;
        final double region1BDemandValue = 41;
        final double region1BContainerCapacity = 0;
        final int region1BContainerCount = 2;
        Map<NodeAttribute, SumCount> regionValues1B = new HashMap<>();
        regionValues1B.put(NodeAttribute.TASK_CONTAINERS, new SumCount(new LoadValue(region1BContainerValue, region1BDemandValue, region1BContainerCapacity), region1BContainerCount));
        bin1.put("B", regionValues1B);

        expectedCollapsedBinnedData.put(firstBinCenter, bin1);

        Map<String, Map<NodeAttribute, SumCount>> bin2 = new HashMap<>();

        final double region2AContainerValue = 23;
        final double region2ADemandValue = 26;
        Map<NodeAttribute, SumCount> regionValues2A = new HashMap<>();
        regionValues2A.put(NodeAttribute.TASK_CONTAINERS, new SumCount(new LoadValue(region2AContainerValue, region2ADemandValue, region1AContainerCapacity), region1AContainerCount));
        bin2.put("A", regionValues2A);

        final double region2BContainerValue = 29;
        final double region2BDemandValue = 27;
        Map<NodeAttribute, SumCount> regionValues2B = new HashMap<>();
        regionValues2B.put(NodeAttribute.TASK_CONTAINERS, new SumCount(new LoadValue(region2BContainerValue, region2BDemandValue, region1BContainerCapacity), region1BContainerCount));
        bin2.put("B", regionValues2B);

        expectedCollapsedBinnedData.put(fifthBinCenter, bin2);

        LOGGER.info("{}", collapsedBinnedData);
        LOGGER.info("{}", expectedCollapsedBinnedData);

        assertEquals(collapsedBinnedData, expectedCollapsedBinnedData);
    }
}
