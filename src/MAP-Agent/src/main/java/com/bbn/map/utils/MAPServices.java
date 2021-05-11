/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
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

import com.bbn.map.common.value.ApplicationCoordinates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

/**
 * Service identifiers that MAP uses.
 * 
 * @author jschewe
 *
 */
public final class MAPServices {

    private MAPServices() {
    }

    /**
     * Track load associated with docker.
     */
    public static final ApplicationCoordinates DOCKER = new ApplicationCoordinates("com.bbn", "docker", "1.0");

    /**
     * Track load associated with DNS queries.
     */
    public static final ApplicationCoordinates DNS_QUERY = new ApplicationCoordinates("com.bbn", "dns-query", "1.0");

    /**
     * Track load associated with updates to the MAP DNS server.
     */
    public static final ApplicationCoordinates DNS_UPDATE = new ApplicationCoordinates("com.bbn", "dns-update", "1.0");

    /**
     * Track load associated with OSPF network configuration.
     */
    public static final ApplicationCoordinates OSPF = new ApplicationCoordinates("com.bbn", "ospf", "1.0");

    /**
     * Track load associated with PIM network configuration.
     */
    public static final ApplicationCoordinates PIM = new ApplicationCoordinates("com.bbn", "pim", "1.0");

    /**
     * Track load associated with multicast network configuration.
     */
    public static final ApplicationCoordinates MULTICAST_MANAGEMENT = new ApplicationCoordinates("com.bbn",
            "multicast-management", "1.0");

    /**
     * Track load associated with simulation driver.
     */
    public static final ApplicationCoordinates SIMULATION_DRIVER = new ApplicationCoordinates("com.bbn",
            "simulation-driver", "1.0");

    /**
     * Collection of services that should not be planned by RLG or DCOP. These
     * are services that MAP reports information on, but doesn't control.
     */
    public static final ImmutableCollection<ApplicationCoordinates> UNPLANNED_SERVICES = ImmutableList.of(
            ApplicationCoordinates.UNMANAGED, ApplicationCoordinates.AP, DOCKER, DNS_QUERY, DNS_UPDATE,
            MULTICAST_MANAGEMENT, OSPF, PIM, SIMULATION_DRIVER);

}
