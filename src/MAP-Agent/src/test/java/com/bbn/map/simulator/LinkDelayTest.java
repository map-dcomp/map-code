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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

import org.junit.Test;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.LinkDelayAlgorithm;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for the link delay attribute.
 * 
 * @author jschewe
 *
 */
public class LinkDelayTest {

    /**
     * Test that link delay is working for resource reports.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testLinkDelayReport() throws URISyntaxException, IOException {
        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/link-delay");
        final Path baseDirectory = Paths.get(baseu.toURI());
        final Path scenarioDirectory = baseDirectory.resolve("scenario");
        final VirtualClock clock = new SimpleClock();
        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;
        final EstimationWindow window = EstimationWindow.SHORT;
        final double fuzz = 1E06;
        final double a1b0ExpectedDelay = 10;
        final double a1b1ExpectedDelay = 20;

        try (Simulation sim = new Simulation("link-delay", scenarioDirectory, null, clock, pollingInterval,
                dnsTtlSeconds, AppMgrUtils::getContainerParameters)) {
            final Controller a0 = sim.getControllerById(new DnsNameIdentifier("A0"));
            assertThat(a0, notNullValue());

            final Controller a1 = sim.getControllerById(new DnsNameIdentifier("A1"));
            assertThat(a1, notNullValue());

            final Controller b0 = sim.getControllerById(new DnsNameIdentifier("B0"));
            assertThat(b0, notNullValue());

            final Controller b1 = sim.getControllerById(new DnsNameIdentifier("B1"));
            assertThat(b1, notNullValue());

            // update reports
            sim.getResourceManager(a0).updateResourceReports();
            final ResourceReport a0Report = a0.getResourceReport(window);
            assertThat(a0Report, notNullValue());

            sim.getResourceManager(a1).updateResourceReports();
            final ResourceReport a1Report = a1.getResourceReport(window);
            assertThat(a1Report, notNullValue());

            sim.getResourceManager(b0).updateResourceReports();
            final ResourceReport b0Report = b0.getResourceReport(window);
            assertThat(b0Report, notNullValue());

            sim.getResourceManager(b1).updateResourceReports();
            final ResourceReport b1Report = b1.getResourceReport(window);
            assertThat(b1Report, notNullValue());

            // check values
            final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> a0NetworkCapacity = a0Report
                    .getNetworkCapacity();
            // all links on a0 should have no delay
            assertThat(a0NetworkCapacity, aMapWithSize(1));
            a0NetworkCapacity.forEach((ifce, ifceData) -> {
                final double value = ifceData.getOrDefault(LinkAttribute.DELAY, 0D);
                assertThat(value, closeTo(0, fuzz));
            });

            final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> a1NetworkCapacity = a1Report
                    .getNetworkCapacity();
            assertThat(a1NetworkCapacity, aMapWithSize(3));

            // a1 - a0 should have no delay
            final ImmutableMap<LinkAttribute, Double> a1a0 = a1NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(a0.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(a1a0, notNullValue());
            assertThat(a1a0.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));

            // a1 - b0
            final ImmutableMap<LinkAttribute, Double> a1b0 = a1NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(b0.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(a1b0, notNullValue());
            assertThat(a1b0.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(a1b0ExpectedDelay, fuzz));

            // a1 - b1
            final ImmutableMap<LinkAttribute, Double> a1b1 = a1NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(b1.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(a1b1, notNullValue());
            assertThat(a1b1.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(a1b1ExpectedDelay, fuzz));

            final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> b0NetworkCapacity = b0Report
                    .getNetworkCapacity();
            assertThat(b0NetworkCapacity, aMapWithSize(2));

            // b0 - a1
            final ImmutableMap<LinkAttribute, Double> b0a1 = b0NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(a1.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(b0a1, notNullValue());
            assertThat(b0a1.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(a1b0ExpectedDelay, fuzz));

            // b0 - b1
            final ImmutableMap<LinkAttribute, Double> b0b1 = b0NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(b1.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(b0b1, notNullValue());
            assertThat(b0b1.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));

            final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> b1NetworkCapacity = b1Report
                    .getNetworkCapacity();
            assertThat(b1NetworkCapacity, aMapWithSize(2));

            // b1 - b0
            final ImmutableMap<LinkAttribute, Double> b1b0 = b1NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(b0.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(b1b0, notNullValue());
            assertThat(b1b0.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));

            // b1 - a1
            final ImmutableMap<LinkAttribute, Double> b1a1 = b1NetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().getNeighbors().contains(a1.getNodeIdentifier())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            assertThat(b1a1, notNullValue());
            assertThat(b1a1.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));
        }
    }

    /**
     * 
     * @throws URISyntaxException test error
     * @throws IOException test error
     */
    @Test
    public void testMultiLinkSummaryMin() throws URISyntaxException, IOException {
        AgentConfiguration.getInstance().setLinkDelayAlgorithm(LinkDelayAlgorithm.MINIMUM);
        final double abExpectedDelay = 10;
        multiLinkHelper(abExpectedDelay);
    }

    /**
     * 
     * @throws URISyntaxException test error
     * @throws IOException test error
     */
    @Test
    public void testMultiLinkSummaryAverage() throws URISyntaxException, IOException {
        AgentConfiguration.getInstance().setLinkDelayAlgorithm(LinkDelayAlgorithm.AVERAGE);
        final double abExpectedDelay = 15;
        multiLinkHelper(abExpectedDelay);
    }

    private void multiLinkHelper(final double abExpectedDelay) throws URISyntaxException, IOException {
        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/link-delay");
        final Path baseDirectory = Paths.get(baseu.toURI());
        final Path scenarioDirectory = baseDirectory.resolve("scenario");
        final VirtualClock clock = new SimpleClock();
        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;
        final EstimationWindow window = EstimationWindow.SHORT;
        final double fuzz = 1E06;

        try (Simulation sim = new Simulation("link-delay", scenarioDirectory, null, clock, pollingInterval,
                dnsTtlSeconds, AppMgrUtils::getContainerParameters)) {
            final Controller a0 = sim.getControllerById(new DnsNameIdentifier("A0"));
            assertThat(a0, notNullValue());

            final Controller a1 = sim.getControllerById(new DnsNameIdentifier("A1"));
            assertThat(a1, notNullValue());

            final Controller b0 = sim.getControllerById(new DnsNameIdentifier("B0"));
            assertThat(b0, notNullValue());

            final Controller b1 = sim.getControllerById(new DnsNameIdentifier("B1"));
            assertThat(b1, notNullValue());

            // update reports
            sim.getResourceManager(a0).updateResourceReports();
            final ResourceReport a0Report = a0.getResourceReport(window);
            assertThat(a0Report, notNullValue());

            sim.getResourceManager(a1).updateResourceReports();
            final ResourceReport a1Report = a1.getResourceReport(window);
            assertThat(a1Report, notNullValue());

            sim.getResourceManager(b0).updateResourceReports();
            final ResourceReport b0Report = b0.getResourceReport(window);
            assertThat(b0Report, notNullValue());

            sim.getResourceManager(b1).updateResourceReports();
            final ResourceReport b1Report = b1.getResourceReport(window);
            assertThat(b1Report, notNullValue());

            // check summaries
            final ResourceSummary aSummary = Controller.computeResourceSummary(a0.getRegion(), sim,
                    ImmutableSet.of(a0Report, a1Report));

            final ResourceSummary bSummary = Controller.computeResourceSummary(b0.getRegion(), sim,
                    ImmutableSet.of(b0Report, b1Report));

            final ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute, Double>> aNetworkCapacity = aSummary
                    .getNetworkCapacity();
            assertThat(aNetworkCapacity, aMapWithSize(2));

            // a - a should have no delay
            final ImmutableMap<LinkAttribute, Double> aa = aNetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().equals(a0.getRegion())).map(Map.Entry::getValue).findFirst().orElse(null);
            assertThat(aa, notNullValue());
            assertThat(aa.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));

            // a - b
            final ImmutableMap<LinkAttribute, Double> ab = aNetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().equals(b0.getRegion())).map(Map.Entry::getValue).findFirst().orElse(null);
            assertThat(ab, notNullValue());
            assertThat(ab.getOrDefault(LinkAttribute.DELAY, abExpectedDelay), closeTo(0, fuzz));

            final ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute, Double>> bNetworkCapacity = bSummary
                    .getNetworkCapacity();
            assertThat(bNetworkCapacity, aMapWithSize(2));

            // b - b should have no delay
            final ImmutableMap<LinkAttribute, Double> bb = bNetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().equals(b0.getRegion())).map(Map.Entry::getValue).findFirst().orElse(null);
            assertThat(bb, notNullValue());
            assertThat(bb.getOrDefault(LinkAttribute.DELAY, 0D), closeTo(0, fuzz));

            // b - a
            final ImmutableMap<LinkAttribute, Double> ba = bNetworkCapacity.entrySet().stream()
                    .filter(e -> e.getKey().equals(a0.getRegion())).map(Map.Entry::getValue).findFirst().orElse(null);
            assertThat(ba, notNullValue());
            assertThat(ba.getOrDefault(LinkAttribute.DELAY, abExpectedDelay), closeTo(0, fuzz));

        }
    }

}
