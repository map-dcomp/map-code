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
package com.bbn.map.rlg;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.RlgAlgorithm;
import com.bbn.map.Controller;
import com.bbn.map.ap.ReportUtils;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.simulator.SimUtils;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.GlobalNetworkConfiguration;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
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
@RunWith(Theories.class)
public class RlgThresholdTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RlgThresholdTest.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain()
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * @return the weights to use
     */
    @DataPoints
    public static List<RlgAlgorithm> algorithms() {
        final List<RlgAlgorithm> values = new LinkedList<>();
        for (RlgAlgorithm a : RlgAlgorithm.values()) {
            if (!RlgAlgorithm.NO_MAP.equals(a)) {
                values.add(a);
            }
        }
        return values;
    }

    /**
     * Reset the {@link AgentConfiguration} object to default values. This is
     * done before and after all tests to ensure that other tests are not
     * effected by using the different algorithms here.
     */
    @Before
    @After
    public void resetAgentConfiguration() {
        AgentConfiguration.resetToDefaults();
        GlobalNetworkConfiguration.resetToDefaults();
    }

    /**
     * Test using rlg-example. The test will pass if every NCP is below
     * threshold when the region is also below threshold. If demand in region is
     * above threshold, RLG does not guarantee every NCP to be below threshold.
     * 
     * @param algorithm
     *            the RLG algorithm to run the test with
     * @throws IOException
     *             if an error occurs in reading test files
     * @throws URISyntaxException
     *             if test files cannot be converted to URI
     */
    @Theory
    public void test(final RlgAlgorithm algorithm) throws IOException, URISyntaxException {
        // assumeThat("Bin packing is under development", algorithm,
        // not(equalTo(RlgAlgorithm.BIN_PACKING)));

        AgentConfiguration.getInstance().setRlgAlgorithm(algorithm);

        LOGGER.info("Running test with algorithm {}", AgentConfiguration.getInstance().getRlgAlgorithm());

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

            double regionCapacity = 0.0;
            double regionLoad = 0.0;

            // iterate over the list of servers
            final Collection<Controller> servers = sim.getAllControllers();
            Iterator<Controller> itr = servers.iterator();

            while (itr.hasNext()) {
                final Controller currentServer = itr.next();
                final ResourceManager<?> manager = sim.getResourceManager(currentServer);

                // final double nodeCapacity =
                // manager.getComputeCapacity().get(NodeAttribute.TASK_CONTAINERS);
                final double nodeCapacity = manager.getComputeCapacity().getOrDefault(NodeAttribute.TASK_CONTAINERS,
                        0D);

                // add capacity of NCP to total capacity
                regionCapacity += nodeCapacity;

                // we're just checking load, so the estimation window doesn't
                // matter, so just use short
                final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);

                // loadPercentage of NCP is the load on containers weighted by
                // the number of containers allocated
                final double loadPercentage = report.getAllocatedServiceContainers()
                        * ReportUtils.computeServerLoadPercentage(report) / nodeCapacity;

                // add load from NCP to total region load
                regionLoad += nodeCapacity * loadPercentage;
            }

            // RLG will guarantee every NCP to be below threshold if total load
            // of region is
            // below threshold
            if (regionLoad <= regionCapacity * threshold) {
                // now check the resource utilization
                sim.getAllControllers().forEach(controller -> {
                    final ResourceManager<?> manager = sim.getResourceManager(controller);

                    // we're just checking load, so the estimation window
                    // doesn't
                    // matter, so just use short
                    final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);
                    // final double loadPercentage =
                    // ReportUtils.computeServerLoadPercentage(report);
                    final double nodeCapacity = manager.getComputeCapacity().getOrDefault(NodeAttribute.TASK_CONTAINERS,
                            0D);
                    final double loadPercentage = report.getAllocatedServiceContainers()
                            * ReportUtils.computeServerLoadPercentage(report) / nodeCapacity;

                    Assert.assertTrue(String.format("Load percentage (%.2f) for node %s is greater than %.2f",
                            loadPercentage, controller.getName(), threshold), loadPercentage <= threshold);
                });
            } else {
                // do nothing, or assert true?
                fail("The region total load (" + regionLoad + ") should not exceed capacity(" + regionCapacity
                        + ") * threshold (" + threshold + ")");
            }
        } // use simulation
    }
}
