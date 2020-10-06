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
package com.bbn.map.ap;

import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.protelis.lang.ProtelisLoader;
import org.protelis.vm.ProtelisProgram;

import com.bbn.map.Controller;
import com.bbn.map.NetworkServices;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkFactory;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceManagerFactory;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;

/**
 * Implementation of {@link NetworkFactory} for MAP.
 */
public class MapNetworkFactory implements NetworkFactory<Controller, NetworkLink, NetworkClient>,
        MapUtils.GraphFactory<NetworkNode, NetworkLink> {

    private final NodeLookupService nodeLookupService;
    private final RegionLookupService regionLookupService;
    private final String program;
    private final boolean anonymousProgram;
    private final ResourceManagerFactory<Controller> managerFactory;
    private final NetworkServices networkServices;
    private final boolean allowDnsChanges;
    private final boolean enableDcop;
    private final boolean enableRlg;
    private final Function<String, NodeIdentifier> createNodeIdentifier;

    /**
     * Create a MAP factory.
     * 
     * @param nodeLookupService
     *            how to find other nodes
     * @param regionLookupService
     *            how to find regions for nodes
     * @param program
     *            the program to put in all nodes
     * @param anonymous
     *            if true, parse as main expression; if false, treat as a module
     *            reference
     * @param managerFactory
     *            used to create {@link ResourceManagers}
     * @param networkServices
     *            passed to
     *            {@link Controller#Controller(NodeLookupService, ProtelisProgram, String, NetworkServices)}
     * @param allowDnsChanges
     *            if true (default) allow changes to the DNS based on the DCOP
     *            and RLG plans. This should only be false during testing.
     *            Passed to
     *            {@link Controller#Controller(NodeLookupService, ProtelisProgram, String, NetworkServices, boolean)}
     * @param enableDcop
     *            set to false for testing where DCOP should not run
     * @param enableRlg
     *            set to false for testing where RLG should not run
     * @param createNodeIdentifier
     *            function for creating node identifiers from strings
     */
    public MapNetworkFactory(@Nonnull final NodeLookupService nodeLookupService,
            @Nonnull final RegionLookupService regionLookupService,
            @Nonnull final ResourceManagerFactory<Controller> managerFactory,
            @Nonnull final String program,
            final boolean anonymous,
            @Nonnull final NetworkServices networkServices,
            final boolean allowDnsChanges,
            final boolean enableDcop,
            final boolean enableRlg,
            @Nonnull final Function<String, NodeIdentifier> createNodeIdentifier) {
        this.nodeLookupService = nodeLookupService;
        this.regionLookupService = regionLookupService;
        this.program = program;
        this.anonymousProgram = anonymous;
        this.managerFactory = managerFactory;
        this.networkServices = networkServices;
        this.allowDnsChanges = allowDnsChanges;
        this.enableDcop = enableDcop;
        this.enableRlg = enableRlg;
        this.createNodeIdentifier = createNodeIdentifier;
    }

    @Override
    @Nonnull
    public NetworkLink createLink(final String name,
            final NetworkNode left,
            final NetworkNode right,
            final double bandwidth, double delay) {
        final NetworkLink link = new NetworkLink(name, left, right, bandwidth, delay);
        left.addNeighbor(right, bandwidth);
        right.addNeighbor(left, bandwidth);
        return link;
    }

    @Override
    @Nonnull
    public Controller createServer(final NodeIdentifier name, final Map<String, Object> extraData) {
        final ProtelisProgram instance;
        if (anonymousProgram) {
            instance = ProtelisLoader.parseAnonymousModule(program);
        } else {
            instance = ProtelisLoader.parse(program);
        }

        final ResourceManager<Controller> manager = managerFactory.createResourceManager();
        final Controller controller = new Controller(nodeLookupService, regionLookupService, instance, name, manager,
                extraData, networkServices, allowDnsChanges, enableDcop, enableRlg);
        manager.init(controller, extraData);

        return controller;
    }

    @Override
    @Nonnull
    public NetworkClient createClient(@Nonnull final NodeIdentifier name,
            @Nonnull final Map<String, Object> extraData) {
        final NetworkClient client = new NetworkClient(name, extraData);

        return client;
    }

    @Override
    @Nonnull
    public NetworkNode createVertex(@Nonnull final Node node) {
        final NodeIdentifier id = createNodeIdentifier.apply(node.getName());
        if (node.isClient()) {
            final NetworkClient c = createClient(id, node.getExtraData());
            return c;
        } else {
            final Controller s = createServer(id, node.getExtraData());
            s.setHardware(node.getHardware());
            return s;
        }
    }

    @Override
    @Nonnull
    public NetworkLink createEdge(@Nonnull final Link link, @Nonnull final NetworkNode left, final NetworkNode right) {
        return createLink(link.getName(), left, right, link.getBandwidth(), link.getDelay());
    }

}
