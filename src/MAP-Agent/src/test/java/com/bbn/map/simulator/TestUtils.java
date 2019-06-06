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
package com.bbn.map.simulator;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.ThreadContext;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * The polling interval to use with the simulation for tests.
     * 
     * If this value is at 10 ms, then things start falling behind and the CPU
     * usage ramps way up
     */
    public static final long POLLING_INTERVAL_MS = 500;

    /**
     * DNS TTL for tests.
     */
    public static final int DNS_TTL = 1;

    private TestUtils() {
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
     * @see LinkMetricName#DATARATE_RX
     * @see LinkMetricName#DATARATE_RX
     */
    public static ContainerParameters dummyContainerParameters(@Nonnull final ServiceIdentifier<?> service) {
        final ImmutableMap.Builder<NodeAttribute<?>, Double> computeCapacity = ImmutableMap.builder();
        computeCapacity.put(NodeMetricName.TASK_CONTAINERS, DEFAULT_CONTAINER_CAPACITY);

        final ImmutableMap.Builder<LinkAttribute<?>, Double> networkCapacity = ImmutableMap.builder();
        networkCapacity.put(LinkMetricName.DATARATE_TX, DEFAULT_LINK_DATARATE);
        networkCapacity.put(LinkMetricName.DATARATE_RX, DEFAULT_LINK_DATARATE);

        return new ContainerParameters(computeCapacity.build(), networkCapacity.build());
    }

}
