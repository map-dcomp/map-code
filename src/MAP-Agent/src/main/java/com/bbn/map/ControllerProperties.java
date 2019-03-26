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
package com.bbn.map;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Functions for parsing the data for
 * {@link Controller#processExtraData(java.util.Map)}.
 * 
 * @author jschewe
 *
 */
public final class ControllerProperties {

    private ControllerProperties() {
    }

    /**
     * The key in extra data to specify if DCOP should run on this node. If the
     * value is true, then DCOP will be run.
     * 
     * @see #processExtraData(Map)
     */
    public static final String EXTRA_DATA_DCOP_KEY = "DCOP";

    /**
     * @param extraData
     *            the data to process
     * @return true if DCOP should be run based on the extra data
     */
    public static boolean isRunningDcop(@Nonnull final Map<String, Object> extraData) {
        return Boolean.parseBoolean(Objects.toString(extraData.get(EXTRA_DATA_DCOP_KEY)));
    }

    /**
     * The key in extra data to specify if RLG should run on this node. If the
     * value is true, then RLG will be run.
     * 
     * @see #processExtraData(Map)
     */
    public static final String EXTRA_DATA_RLG_KEY = "RLG";

    /**
     * @param extraData
     *            the data to process
     * @return true if RLG should be run based on the extra data
     */
    public static boolean isRunningRlg(@Nonnull final Map<String, Object> extraData) {
        return Boolean.parseBoolean(Objects.toString(extraData.get(EXTRA_DATA_RLG_KEY)));
    }

    /**
     * The key in extra data to specify if DNS updates are handled by this node.
     * 
     * @see #processExtraData(Map)
     * @see #isHandleDnsChanges()
     */
    public static final String EXTRA_DATA_DNS_KEY = "dns";

    /**
     * @param extraData
     *            the data to process
     * @return true if DNS changes should be handled based on the extra data
     */
    public static boolean isHandlingDnsChanges(@Nonnull final Map<String, Object> extraData) {
        return Boolean.parseBoolean(Objects.toString(extraData.get(EXTRA_DATA_DNS_KEY)));
    }

}
