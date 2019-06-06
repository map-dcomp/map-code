/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.protelis.lang.datatype.DeviceUID;

import com.bbn.map.Controller;
import com.bbn.map.ap.ReportUtils;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.simulator.SimUtils;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that RLG keeps all NCPs under threshold given load in the region is
 * under threshold.
 *
 * @author sohaib Note: Some code re-used from RegionalOverloadTest.java.
 *
 */
public class RlgThresholdTest {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Test using rlg-example. The test will pass if every NCP is below
     * threshold when the region is also below threshold. If demand in region is
     * above threshold, RLG does not guarantee every NCP to be below threshold.
     * 
     * @throws IOException
     *             if an error occurs in reading test files
     * @throws URISyntaxException
     *             if test files cannot be converted to URI
     */
    @Test
    public void test() throws IOException, URISyntaxException {
        final double threshold = 0.75;

        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/rlg-example");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("rlg_overload_1");

        /*
         * How to run test?
         * 
         * sum capacities of all NCPs in region sum total load in region
         * 
         * if total load <= total capacities * threshold then check every NCP to
         * see if its individual load is below threshold else nothing to check
         * in this case, pass test
         */

        final int numApRoundsToWaitForResults = 20;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                AppMgrUtils::getContainerParameters)) {

            sim.startSimulation();
            sim.startClients();
            SimUtils.waitForApRounds(sim, numApRoundsToWaitForResults);
            clock.stopClock();

            double totalCapacity = 0.0;
            double totalLoad = 0.0;

            // iterate over the list of servers
            Map<DeviceUID, Controller> servers = sim.getScenario().getServers();
            Iterator<Map.Entry<DeviceUID, Controller>> itr = servers.entrySet().iterator();

            while (itr.hasNext()) {
                Map.Entry<DeviceUID, Controller> currentServer = itr.next();
                final ResourceManager<?> manager = sim.getResourceManager(currentServer.getValue());

                // final double nodeCapacity =
                // manager.getComputeCapacity().get(NodeMetricName.TASK_CONTAINERS);
                final double nodeCapacity = manager.getComputeCapacity().getOrDefault(NodeMetricName.TASK_CONTAINERS,
                        0D);

                // add capacity of NCP to total capacity
                totalCapacity += nodeCapacity;

                // we're just checking load, so the estimation window doesn't
                // matter, so just use short
                final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);
                final double loadPercentage = ReportUtils.computeServerLoadPercentage(report);

                // add load from NCP to total load
                totalLoad += nodeCapacity * loadPercentage;
            }

            // RLG will guarantee every NCP to be below threshold if load is
            // below threshold
            if (totalLoad <= totalCapacity * threshold) {
                // now check the resource utilization
                sim.getScenario().getServers().forEach((deviceUid, controller) -> {
                    final ResourceManager<?> manager = sim.getResourceManager(controller);

                    // we're just checking load, so the estimation window
                    // doesn't
                    // matter, so just use short
                    final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);

                    final double loadPercentage = ReportUtils.computeServerLoadPercentage(report);

                    Assert.assertTrue(String.format("Load percentage (%.2f) for node %s is greater than %.2f",
                            loadPercentage, controller.getName(), threshold), loadPercentage < threshold);
                });
            } else {
                // do nothing, or assert true?
                fail("The region total load (" + totalLoad + ") should not exceed capacity(" + totalCapacity
                        + ") * threshold (" + threshold + ")");
            }
        } // use simulation
    }
}
