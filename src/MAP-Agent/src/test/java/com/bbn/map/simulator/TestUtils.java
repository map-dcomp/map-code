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

import java.net.UnknownHostException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.ThreadContext;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.ApplicationManagerMain;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Some utilities to help in testing simulations.
 * 
 * @author jschewe
 *
 */
public final class TestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    /**
     * How many times do the AP network tests need to fail to be considered a
     * failure.
     */
    public static final int DEFAULT_RETRY_COUNT = 3;

    private TestUtils() {
    }

    /**
     * Wait for the simulation to complete a number of AP iterations. When this
     * method returns the {@link Controller#getExecutionCount()} will be greater
     * than the current value by at least <code>numRounds</code>
     * 
     * @param sim
     *            the simulation that is being run
     * @param numRounds
     *            the number of rounds to wait
     */
    public static void waitForApRounds(@Nonnull final Simulation sim, final int numRounds) {
        final long timeToWait = AgentConfiguration.getInstance().getApRoundDuration().toMillis();

        // get current max number of executions so we know how long to wait
        final Map<Controller, Long> currentMaxExecutions = sim.getScenario().getServers().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, e -> e.getValue().getExecutionCount()));

        // poll the simulation until we have enough executions
        boolean doneWaiting = false;
        while (!doneWaiting) {
            sim.getClock().waitForDuration(timeToWait);

            doneWaiting = currentMaxExecutions.entrySet().stream().allMatch(e -> {
                return e.getKey().getExecutionCount() >= e.getValue() + numRounds;
            });
        }
    }

    /**
     * Add the test name to the logging {@link ThreadContext}.
     */
    public static class AddTestNameToLogContext extends TestWatcher {
        @Override
        protected void starting(final Description description) {
            ThreadContext.push(description.getMethodName());
            LOGGER.info("Starting test {} in {}", description.getMethodName(), description.getClassName());
        }

        @Override
        protected void finished(final Description description) {
            LOGGER.info("Finished test {} in {}", description.getMethodName(), description.getClassName());
            ThreadContext.pop();
        }
    }

    /**
     * JUnit rule that starts and stops the {@link ApplicationManagerMain}
     * around a test case.
     * 
     * @author jschewe
     *
     */
    public static class UseMapApplicationManager extends ExternalResource {
        private ConfigurableApplicationContext context = null;

        @Override
        protected void before() throws Throwable {
            context = launchAppManager(new String[0]);
        }

        @Override
        protected void after() {
            // make sure that nothing is left hanging around
            AppMgrUtils.getApplicationManager().clear();

            if (null != context) {
                context.close();
                context = null;
            }
        }

        private static ConfigurableApplicationContext launchAppManager(final String[] args)
                throws UnknownHostException {
            final SpringApplication app = new SpringApplication(ApplicationManagerMain.class);
            final SimpleCommandLinePropertySource source = new SimpleCommandLinePropertySource(args);
            ApplicationManagerMain.addDefaultProfile(app, source);
            final ConfigurableApplicationContext ctx = app.run(args);

            return ctx;
        }
    }

    /**
     * JUnit test rule that reruns failed tests some number of times.
     * 
     * @author jschewe
     *
     */
    public static class Retry implements TestRule {
        private final int retryCount;

        /**
         * 
         * @param retryCount
         *            how many times to retry the test
         */
        public Retry(final int retryCount) {
            this.retryCount = retryCount;
        }

        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Throwable caughtThrowable = null;

                    // implement retry logic here
                    for (int i = 0; i < retryCount; i++) {
                        try {
                            base.evaluate();
                            return;
                        } catch (final Throwable t) {
                            caughtThrowable = t;
                            LOGGER.error(description.getDisplayName() + ": run " + (i + 1) + " failed");
                        }
                    }
                    LOGGER.error(description.getDisplayName() + ": giving up after " + retryCount + " failures");
                    throw caughtThrowable;
                }
            };
        }
    }

    /**
     * Each C/G operation in the AP program can take up to 2 times the network
     * diameter to complete. As of 10/25/2017 our program is G -> C -> G
     * (measure, collect, broadcast), so the multiplier is 6.
     */
    private static final int AP_ROUNDS_MULTIPLIER = 6;
    /**
     * Extra rounds to wait to handle differences in systems.
     */
    private static final int AP_ROUNDS_FUZZ = 10;

    /**
     * Compute the number of rounds it will take AP to stabilize. This is based
     * on the AP program that we are running and the diameter of the network. If
     * the network diameter cannot be determined an assertion violation occurs.
     * 
     * @param sim
     *            the simulation containing the network
     * @return the number of rounds for AP to stabilize
     */
    public static int computeRoundsToStabilize(@Nonnull final Simulation sim) {
        final double networkDiameter = sim.getNetworkDiameter();
        Assert.assertTrue("Cannot find the network diameter", Double.isFinite(networkDiameter));

        final int numApRoundsToStabilize = (int) Math.ceil(networkDiameter * AP_ROUNDS_MULTIPLIER) + AP_ROUNDS_FUZZ;

        return numApRoundsToStabilize;
    }

    /**
     * Default link rate to use for container parameters when testing.
     */
    public static final double DEFAULT_LINK_DATARATE = 100;

    /**
     * Default container capacity to use for container parameters when testing.
     */
    public static final double DEFAULT_CONTAINER_CAPACITY = 1;

    /**
     * Get some dummy container parameters for testing. Always return 1
     * container and 100Mbps.
     * 
     * @param service
     *            ignored
     * @return the dummy container parameters
     * @see #DEFAULT_CONTAINER_CAPACITY
     * @see #DEFAULT_LINK_DATARATE
     */
    public static ContainerParameters dummyContainerParameters(@Nonnull final ServiceIdentifier<?> service) {
        final ImmutableMap.Builder<NodeAttribute<?>, Double> computeCapacity = ImmutableMap.builder();
        computeCapacity.put(NodeMetricName.TASK_CONTAINERS, DEFAULT_CONTAINER_CAPACITY);

        final ImmutableMap.Builder<LinkAttribute<?>, Double> networkCapacity = ImmutableMap.builder();
        networkCapacity.put(LinkMetricName.DATARATE, DEFAULT_LINK_DATARATE);

        return new ContainerParameters(computeCapacity.build(), networkCapacity.build());
    }

}
