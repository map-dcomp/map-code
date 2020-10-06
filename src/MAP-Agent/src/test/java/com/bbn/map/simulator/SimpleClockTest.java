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
package com.bbn.map.simulator;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.number.OrderingComparison;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that {@link SimpleClock} follows the contract of {@link VirtualClock}.
 * The tests provided here should be replicated for any other clock
 * implementation.
 * 
 * @author jschewe
 *
 */
public class SimpleClockTest {

    /**
     * Add test name to logging.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    private VirtualClock clock;

    /**
     * Make sure that the clock is shutdown.
     */
    @After
    public void tearDown() {
        clock.shutdown();
    }

    /**
     * Create an instance of {@link SimpleClock}.
     */
    @Before
    public void setup() {
        clock = new SimpleClock();
    }

    /**
     * Check the state of the clock.
     */
    @Test
    public void testStartState() {
        Assert.assertFalse(clock.isStarted());
        Assert.assertFalse(clock.isShutdown());

        clock.startClock();
        Assert.assertTrue(clock.isStarted());
        Assert.assertFalse(clock.isShutdown());

        clock.stopClock();
        Assert.assertFalse(clock.isStarted());
        Assert.assertFalse(clock.isShutdown());

        clock.shutdown();
        Assert.assertFalse(clock.isStarted());
        Assert.assertTrue(clock.isShutdown());
    }

    /**
     * Test that the clock is reasonably close to wall time.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted
     */
    @Test
    public void testDurationAccuracy() throws InterruptedException {
        final Duration sleepTime = Duration.ofSeconds(5);
        final long toleranceMillis = 500;

        clock.startClock();

        Thread.sleep(sleepTime.toMillis());

        final long actual = clock.getCurrentTime();

        Assert.assertTrue(Math.abs(actual - sleepTime.toMillis()) < toleranceMillis);
    }

    /**
     * Test that the value is constant after stop and that no exceptions are
     * thrown reading the value after the clock is stopped.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted
     */
    @Test
    public void testStop() throws InterruptedException {
        final Duration sleepTime = Duration.ofSeconds(1);

        clock.startClock();
        Thread.sleep(sleepTime.toMillis());
        clock.stopClock();

        final long baseTime = clock.getCurrentTime();

        Thread.sleep(sleepTime.toMillis());

        final long actual = clock.getCurrentTime();

        Assert.assertEquals(baseTime, actual);
    }

    /**
     * Test that no exception is thrown after shutting down the clock.
     */
    public void testShutdown() {
        clock.startClock();
        clock.shutdown();
        clock.getCurrentTime();
    }

    /**
     * Test that waiting until a given time actually waits until that time.
     */
    @Test
    public void testWaitUntil() {
        final long waitUntil = Duration.ofSeconds(1).toMillis();

        clock.startClock();

        clock.waitUntilTime(waitUntil);
        final long actual = clock.getCurrentTime();

        Assert.assertTrue(actual >= waitUntil);
    }

    /**
     * Test that waiting until a time before clock start works.
     * 
     * @throws InterruptedException
     *             if there is a problem waiting, this is an error
     */
    @Test
    public void testWaitUntilBeforeStart() throws InterruptedException {
        final Duration waitUntilDuration = Duration.ofSeconds(1);
        final long waitUntil = waitUntilDuration.toMillis();

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Long> future = executor.submit(() -> {
            clock.waitUntilTime(waitUntil);
            return clock.getCurrentTime();
        });

        clock.startClock();

        try {
            final Long result = future.get(2 * waitUntilDuration.toMillis(), TimeUnit.MILLISECONDS);
            Assert.assertThat(result, OrderingComparison.greaterThanOrEqualTo(waitUntil));
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }

    /**
     * Test that waiting for a long time and then calling stop, stops the wait
     * and doesn't throw an exception.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted, this is an error
     */
    @Test
    public void testWaitUntilStop() throws InterruptedException {
        final long waitUntil = Duration.ofMinutes(10).toMillis();
        final Duration sleepTime = Duration.ofSeconds(1);

        clock.startClock();

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<Long> future = executor.submit(() -> {
            clock.waitUntilTime(waitUntil);
            return clock.getCurrentTime();
        });

        Thread.sleep(sleepTime.toMillis());

        clock.stopClock();

        try {
            final Long result = future.get(1, TimeUnit.SECONDS);
            Assert.assertThat(result, OrderingComparison.lessThan(waitUntil));

        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }

    /**
     * Test that waiting for a long time and then calling shutdown, stops the
     * wait and doesn't throw an exception.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted, this is an error
     */
    @Test
    public void testWaitUntilShutdown() throws InterruptedException {
        final long waitUntil = Duration.ofMinutes(10).toMillis();
        final Duration sleepTime = Duration.ofSeconds(1);

        clock.startClock();

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<?> future = executor.submit(() -> {
            clock.waitUntilTime(waitUntil);
        });

        Thread.sleep(sleepTime.toMillis());

        clock.shutdown();

        try {
            // wait for the future to finish
            future.get(1, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }

    /**
     * Test that waiting for a duration actually waits until that time.
     */
    @Test
    public void testWaitForDuration() {
        final long waitDuration = Duration.ofSeconds(1).toMillis();

        clock.startClock();

        final long start = clock.getCurrentTime();
        clock.waitForDuration(waitDuration);
        final long after = clock.getCurrentTime();

        final long actualDuration = after - start;
        Assert.assertThat(actualDuration, OrderingComparison.greaterThanOrEqualTo(waitDuration));
    }

    /**
     * Test that waiting for a duration and then calling stop, stops the wait
     * and doesn't throw an exception.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted, this is an error
     */
    @Test
    public void testWaitForDurationStop() throws InterruptedException {
        final long waitDuration = Duration.ofMinutes(10).toMillis();
        final Duration sleepTime = Duration.ofSeconds(1);

        clock.startClock();

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final long start = clock.getCurrentTime();
        final Future<Long> future = executor.submit(() -> {
            clock.waitForDuration(waitDuration);
            return clock.getCurrentTime();
        });

        Thread.sleep(sleepTime.toMillis());

        clock.stopClock();

        try {
            final Long result = future.get(1, TimeUnit.SECONDS);
            final long actualDuration = result.longValue() - start;
            Assert.assertThat(actualDuration, OrderingComparison.lessThan(waitDuration));

        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }

    /**
     * Test that waiting for a duration and then calling shutdown, stops the
     * wait and doesn't throw an exception.
     * 
     * @throws InterruptedException
     *             if the sleep is interrupted, this is an error
     */
    @Test
    public void testWaitForDurationShutdown() throws InterruptedException {
        final long waitDuration = Duration.ofMinutes(10).toMillis();
        final Duration sleepTime = Duration.ofSeconds(1);

        clock.startClock();

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<?> future = executor.submit(() -> {
            clock.waitForDuration(waitDuration);
        });

        Thread.sleep(sleepTime.toMillis());

        clock.shutdown();

        try {
            // wait for the future to finish
            future.get(1, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }

    /**
     * Test that wait for clock start returns after the clock is started.
     * 
     * @throws InterruptedException
     *             interrupted while sleeping, this is an error
     */
    @Test
    public void testWaitForClockStart() throws InterruptedException {
        final Duration sleepTime = Duration.ofSeconds(1);

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<?> future = executor.submit(() -> {
            clock.waitForClockStart();
        });

        clock.startClock();

        Thread.sleep(sleepTime.toMillis());

        try {
            // wait for the future to finish
            future.get(1, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }
    }

    /**
     * Test that wait for clock start returns after the clock is shutdown
     * without being started.
     * 
     * @throws InterruptedException
     *             interrupted while sleeping, this is an error
     */
    @Test
    public void testWaitForClockStartShutdown() throws InterruptedException {
        final Duration sleepTime = Duration.ofSeconds(1);

        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<?> future = executor.submit(() -> {
            clock.waitForClockStart();
        });

        clock.shutdown();

        Thread.sleep(sleepTime.toMillis());

        try {
            // wait for the future to finish
            future.get(1, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Assert.fail("Got exception waiting: " + e.getMessage());
        } catch (final TimeoutException e) {
            future.cancel(true);
            Assert.fail("Timed out waiting for future: " + e.getMessage());
        }

    }
}
