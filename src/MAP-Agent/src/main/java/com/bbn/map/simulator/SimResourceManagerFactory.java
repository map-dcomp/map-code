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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceManagerFactory;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;

/**
 * Create {@link SimResourceManager} objects.
 *
 */
public class SimResourceManagerFactory implements ResourceManagerFactory<NetworkServer> {

    private final Simulation simulation;
    private final long pollingInterval;

    private final Map<NetworkServer, SimResourceManager> resourceManagers = new HashMap<>();

    /**
     * 
     * @param simulation
     *            the simulation
     * @param pollingInterval
     *            milliseconds between creating new {@link ResourceReport}
     *            objects
     */
    public SimResourceManagerFactory(@Nonnull final Simulation simulation, final long pollingInterval) {
        this.simulation = simulation;
        this.pollingInterval = pollingInterval;
    }

    @Override
    @Nonnull
    public ResourceManager createResourceManager(@Nonnull final NetworkServer node,
            @Nonnull final Map<String, Object> ignored) {
        final SimResourceManager manager = new SimResourceManager(simulation, node, pollingInterval);

        resourceManagers.put(node, manager);

        return manager;
    }

    /**
     * Get a resource manager that was created with
     * {@link #createResourceManager(NetworkServer, Map)}.
     * 
     * @param node
     *            the node to find the resource manager for
     * @return the resource manager or null if not found
     */
    public SimResourceManager getResourceManager(@Nonnull final NetworkServer node) {
        return resourceManagers.get(node);
    }
}
