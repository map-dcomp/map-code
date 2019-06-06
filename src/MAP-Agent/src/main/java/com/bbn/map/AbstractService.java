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
package com.bbn.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for services. The service executes {@link #executeService()} in a
 * thread that is interrupted when {@link #stopService(long)} is called.
 */
public abstract class AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractService.class);

    private final Object serviceThreadLock = new Object();
    private final Object statusLock = new Object();

    /**
     * Track the status of a service.
     */
    public enum Status {
        /**
         * The service is running.
         */
        RUNNING,
        /**
         * The service is in the process of being stopped.
         */
        STOPPING,
        /**
         * The service is stopped.
         */
        STOPPED
    }

    private Status status = Status.STOPPED;

    private final String name;

    /**
     * 
     * @return the name of the service
     */
    public final String getName() {
        return name;
    }

    /**
     * 
     * @param name
     *            see {@link #getName()}
     */
    public AbstractService(final String name) {
        this.name = name;
    }

    /**
     * {@link Status#STOPPING} once {@link AbstractService#stopService(long)}
     * has been called until the thread exits. Initial state is
     * {@link Status#STOPPED}.
     * 
     * @return the status of this service
     */
    public final Status getStatus() {
        synchronized (statusLock) {
            return status;
        }
    }

    private void setStatus(final Status s) {
        synchronized (statusLock) {
            status = s;
        }
    }

    private Thread serviceThread = null;

    /**
     * Start the service.
     * 
     * @throws IllegalStateException
     *             if the service is already running.
     */
    public final void startService() {
        if (Status.STOPPED != getStatus()) {
            throw new IllegalStateException("Cannot start the service when it's already running");
        } else {
            setStatus(Status.RUNNING);
            synchronized (serviceThreadLock) {
                serviceThread = new Thread(() -> executeService(), name);
                serviceThread.start();
            }
        }
    }

    /**
     * Stop the service by setting status to {@link Status#STOPPING},
     * interrupting the read and then waiting until the thread has exited.
     * 
     * @param timeout
     *            how long to wait for the thread to shutdown in milliseconds
     * @see Thread#join(long)
     */
    public final void stopService(final long timeout) {
        setStatus(Status.STOPPING);

        synchronized (serviceThreadLock) {
            if (null != serviceThread) {
                serviceThread.interrupt();
                try {
                    serviceThread.join(timeout);
                } catch (final InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Interrupted while waiting for service to exit. Assuming we're shutting down", e);
                    }
                }
                if (serviceThread.isAlive()) {
                    LOGGER.warn("Service " + name + " didn't stop, assuming it will eventually");
                }
                serviceThread = null;
            }
        }

        setStatus(Status.STOPPED);
    }

    /**
     * Subclasses override this method to do the actual work of the service.
     * This method should not return until the service is stopped. The service
     * will be stopped by it's thread being interrupted.
     * 
     * @see Thread#interrupt()
     */
    protected abstract void executeService();

}
