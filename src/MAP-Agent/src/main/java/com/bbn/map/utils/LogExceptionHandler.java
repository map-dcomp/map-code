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
package com.bbn.map.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log uncaught exceptions through the logging framework.
 * 
 * @author jschewe
 *
 */
public class LogExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogExceptionHandler.class);

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
        handle(e);
    }

    /**
     * Needed for catuching awt exceptions.
     * 
     * @param throwable
     *            the thrown exception
     */
    public void handle(final Throwable throwable) {
        try {
            LOGGER.error("Unhandled exception: " + throwable.getMessage(), throwable);
        } catch (final Throwable t) {
            LOGGER.error("Unhandled exception trying to log an exception: " + t.getMessage(), t);
        }
    }

    /**
     * Register this class to catch uncaught exceptions.
     */
    public static void registerExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new LogExceptionHandler());
        System.setProperty("sun.awt.exception.handler", LogExceptionHandler.class.getName());
    }
}
