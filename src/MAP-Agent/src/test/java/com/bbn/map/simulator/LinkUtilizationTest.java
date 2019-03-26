/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.hamcrest.number.IsCloseTo;
import org.hamcrest.number.OrderingComparison;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.Controller;
import com.bbn.map.ap.ApplicationManagerUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.simulator.ClientSim.RequestResult;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Check that link utilization is properly reported.
 * 
 * @author jschewe
 *
 */
public class LinkUtilizationTest {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.UseMapApplicationManager());

    /**
     * Add information to one side of the link and make sure that both sides see
     * it.
     * 
     * @throws URISyntaxException
     *             on an internal test error
     * @throws IOException
     *             if the test data cannot be loaded
     */
    @Test
    public void bothSidesUpdated() throws URISyntaxException, IOException {
        final LinkMetricName datarateAttribute = LinkMetricName.DATARATE;
        // how much error we are allowing on our double comparisons
        final double error = 1E-6;
        final String srcControllerName = "nodeA0";
        final String destControllerName = "nodeA1";

        final long requestStart = 0;
        final long requestDuration = 10;
        final int requestNumClients = 1;
        final String serviceName = "test";
        final double nodeLoad = 1;

        final ClientRequest req = new ClientRequest(requestStart, requestDuration, requestDuration, requestNumClients,
                new ApplicationCoordinates(serviceName, serviceName, serviceName), ImmutableMap.of(),
                ImmutableMap.of(LinkMetricName.DATARATE, nodeLoad));

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/simple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final Controller srcController = sim.getControllerById(new DnsNameIdentifier(srcControllerName));
            final Controller destController = sim.getControllerById(new DnsNameIdentifier(destControllerName));

            final SimResourceManager destManager = sim.getResourceManager(destController);
            Assert.assertThat(destManager, CoreMatchers.is(IsNull.notNullValue()));

            // apply some load
            final LinkResourceManager lmgr = sim.getLinkResourceManager(srcController.getNodeIdentifier(),
                    destController.getNodeIdentifier());
            final Pair<RequestResult, LinkResourceManager.LinkLoadEntry> linkResult = lmgr
                    .addLinkLoad(destController.getNodeIdentifier(), req);
            Assert.assertThat("Link load should be added without a problem", linkResult.getLeft(),
                    IsEqual.equalTo(RequestResult.SUCCESS));

            // get the reports and check that they match
            destManager.updateResourceReports();
            final ResourceReport destReport = destManager.getCurrentResourceReport(EstimationWindow.SHORT);
            Assert.assertThat(destReport, CoreMatchers.is(IsNull.notNullValue()));

            final SimResourceManager srcManager = sim.getResourceManager(srcController);
            Assert.assertThat(srcManager, CoreMatchers.is(IsNull.notNullValue()));

            srcManager.updateResourceReports();
            final ResourceReport srcReport = srcManager.getCurrentResourceReport(EstimationWindow.SHORT);
            Assert.assertThat(srcReport, CoreMatchers.is(IsNull.notNullValue()));

            // test neighbor traffic as that should show this
            final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> srcNetLoad = srcReport
                    .getNodeNeighborNetworkLoad();
            Assert.assertThat(srcNetLoad, CoreMatchers.is(IsNull.notNullValue()));
            final ImmutableMap<LinkAttribute<?>, Double> srcNetNeighborLoad = srcNetLoad
                    .get(destController.getNodeIdentifier());
            Assert.assertThat(srcNetNeighborLoad, CoreMatchers.is(IsNull.notNullValue()));
            final Double srcNetNeighborLoadValue = srcNetNeighborLoad.get(datarateAttribute);
            Assert.assertThat(srcNetNeighborLoadValue, CoreMatchers.is(IsNull.notNullValue()));

            final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> destNetLoad = destReport
                    .getNodeNeighborNetworkLoad();
            Assert.assertThat(destNetLoad, CoreMatchers.is(IsNull.notNullValue()));
            final ImmutableMap<LinkAttribute<?>, Double> destNetNeighborLoad = destNetLoad
                    .get(srcController.getNodeIdentifier());
            Assert.assertThat(destNetNeighborLoad, CoreMatchers.is(IsNull.notNullValue()));
            final Double destNetNeighborLoadValue = destNetNeighborLoad.get(datarateAttribute);
            Assert.assertThat(destNetNeighborLoadValue, CoreMatchers.is(IsNull.notNullValue()));

            Assert.assertThat("Must have load greater than 0", srcNetNeighborLoadValue,
                    OrderingComparison.greaterThan(0D));

            Assert.assertThat("Both sides of the link should return the same load value", destNetNeighborLoadValue,
                    IsCloseTo.closeTo(srcNetNeighborLoadValue, error));

        } // sim allocation

    }
}
