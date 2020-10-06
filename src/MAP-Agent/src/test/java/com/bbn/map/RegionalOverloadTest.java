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
package com.bbn.map;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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

import com.bbn.map.AgentConfiguration.RlgAlgorithm;
import com.bbn.map.AgentConfiguration.RlgStubChooseNcp;
import com.bbn.map.ap.ReportUtils;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.simulator.SimUtils;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.GlobalNetworkConfiguration;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that when a region is overloaded, MAP starts up services on other nodes
 * to keep the load on each server down.
 * 
 * @author jschewe
 *
 */
@RunWith(Theories.class)
public class RegionalOverloadTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionalOverloadTest.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain()
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

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

    // CHECKSTYLE:OFF test data class
    private static final class Parameters {
        public RlgAlgorithm algorithm;
        public RlgStubChooseNcp stubChooseAlgorithm;
    }
    // CHECKSTYLE:ON

    /**
     * @return the weights to use
     */
    @DataPoints
    public static List<Parameters> algorithms() {
        final List<Parameters> params = new LinkedList<>();
        for (final RlgAlgorithm algorithm : RlgAlgorithm.values()) {
            if (RlgAlgorithm.NO_MAP.equals(algorithm)) {
                LOGGER.trace("Skipping no map algorithm");
                continue;
            } else if (RlgAlgorithm.STUB.equals(algorithm)) {
                for (final RlgStubChooseNcp choose : RlgStubChooseNcp.values()) {
                    final Parameters p = new Parameters();
                    p.algorithm = algorithm;
                    p.stubChooseAlgorithm = choose;
                    params.add(p);
                }
            } else {
                final Parameters p = new Parameters();
                p.algorithm = algorithm;
                params.add(p);
            }
        }
        return params;
    }

    /**
     * @throws URISyntaxException
     *             if the path to the test files cannot be converted to a URI.
     *             This should not happen.
     * @throws IOException
     *             If there is an error reading the test files.
     * @see RegionalOverloadTest
     * @param params
     *            the algorithm parameters to run the test with
     */
    @Theory
    public void test(final Parameters params) throws URISyntaxException, IOException {
        AgentConfiguration.getInstance().setRlgAlgorithm(params.algorithm);
        AgentConfiguration.getInstance().setRlgStubChooseNcp(params.stubChooseAlgorithm);

        LOGGER.info("Running test with algorithm: {} stub choose: {}",
                AgentConfiguration.getInstance().getRlgAlgorithm(),
                AgentConfiguration.getInstance().getRlgStubChooseNcp());

        // nodes should not be above this value for task containers once MAP has
        // had a chance to balance things out
        final double thresholdPercentage = 0.75;

        final long pollingInterval = Duration.ofMillis(10).toMillis();
        final int dnsTtlSeconds = 60;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/rlg-example");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("rlg_overload_1");

        final int numApRoundsToWaitForResults = 20;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                AppMgrUtils::getContainerParameters)) {

            sim.startSimulation();
            SimUtils.waitForApRounds(sim, numApRoundsToWaitForResults);
            clock.stopClock();

            // check the resource utilization
            sim.getAllControllers().forEach(controller -> {
                final ResourceManager<?> manager = sim.getResourceManager(controller);

                // we're just checking load, so the estimation window doesn't
                // matter, so just use short
                final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);

                final double loadPercentage = ReportUtils.computeServerLoadPercentage(report);

                Assert.assertTrue(String.format("Load percentage (%.2f) for node %s is greater than %.2f",
                        loadPercentage, controller.getName(), thresholdPercentage),
                        loadPercentage < thresholdPercentage);
            });
        } // use simulation
    }

}
