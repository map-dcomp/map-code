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
package com.bbn.map.dns;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Translate {@link RegionPlan} and {@link LoadBalancerPlan} to
 * {@link DnsRecord}s. The object can be shared across multiple nodes if
 * desired. The object itself is immutable and does not contain any region
 * specific information.
 */
public class PlanTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTranslator.class);

    private final int ttl;

    /**
     * Create the translator. It is assumed that defaultMappings contains an
     * entry for all services known to the system.
     * 
     * @param ttl
     *            the TTL to use for created DNS records
     */
    public PlanTranslator(final int ttl) {
        this.ttl = ttl;
    }

    /**
     * Convert a network plan for a region into DNS records that will implement
     * the plan.
     * 
     * @param loadBalancerPlan
     *            which services should be running on each node and how much
     *            traffic to send to neighboring regions
     * @param regionServiceState
     *            which services are running in which containers
     * @return the DNS records and corresponding weights that represent the
     *         inputs, null if there is a problem computing the records and no
     *         changes should be made
     */
    public ImmutableCollection<Pair<DnsRecord, Double>> convertToDns(@Nonnull final LoadBalancerPlan loadBalancerPlan,
            @Nonnull final RegionServiceState regionServiceState) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("convertToDNS loadBalancerPlan: {} serviceState: {}", loadBalancerPlan, regionServiceState);
        }

        final RegionIdentifier localRegion = loadBalancerPlan.getRegion();
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowDetails = loadBalancerPlan
                .getOverflowPlan();

        // if a container is running, but told to stop traffic or to shutdown,
        // don't create a DNS entry for it
        // node -> containers
        final Map<NodeIdentifier, Set<NodeIdentifier>> containersToIgnore = new HashMap<>();

        loadBalancerPlan.getServicePlan().forEach((node, containers) -> {
            containers.forEach(info -> {
                if (info.isStop() || info.isStopTrafficTo()) {
                    containersToIgnore.computeIfAbsent(node, k -> new HashSet<>()).add(info.getId());
                }
            });
        });

        final Map<ServiceIdentifier<?>, Set<NodeIdentifier>> containersRunningServices = new HashMap<>();
        regionServiceState.getServiceReports().forEach(serviceReport -> {
            final NodeIdentifier node = serviceReport.getNodeName();

            final Set<NodeIdentifier> nodeContainersToIgnore = containersToIgnore.getOrDefault(node,
                    Collections.emptySet());
            serviceReport.getServiceState().forEach((containerId, serviceState) -> {
                final ServiceState.Status status = serviceState.getStatus();
                // TODO check if this service is synchronous or asynchronous -
                // in the application manager. If synchronous then only add the
                // record if RUNNING.
                if (ServiceState.Status.STARTING.equals(status) || ServiceState.Status.RUNNING.equals(status)) {
                    if (!nodeContainersToIgnore.contains(containerId)) {
                        final ServiceIdentifier<?> service = serviceState.getService();
                        final Set<NodeIdentifier> containers = containersRunningServices.computeIfAbsent(service,
                                k -> new HashSet<>());
                        containers.add(containerId);
                    } else {
                        LOGGER.trace("Ignoring running container {} because it's in {}", containerId,
                                containersToIgnore);
                    }
                } else {
                    LOGGER.trace("Ignoring running container {} with state {}", containerId, status);
                }

            });
        });

        LOGGER.trace("Containers running services {}", containersRunningServices);

        final ImmutableCollection.Builder<Pair<DnsRecord, Double>> dnsRecords = ImmutableList.builder();

        // determine which containers are running which services and should be
        // entered into DNS
        final ApplicationManagerApi appManager = AppMgrUtils.getApplicationManager();
        for (final ApplicationSpecification spec : appManager.getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            final RegionIdentifier defaultNodeRegion = spec.getServiceDefaultRegion();

            final Set<NodeIdentifier> serviceNodes = containersRunningServices.getOrDefault(service,
                    Collections.emptySet());

            final ImmutableMap<RegionIdentifier, Double> regionServicePlan = overflowDetails.get(service);
            LOGGER.trace("Region service plan: {}", regionServicePlan);
            if (null == regionServicePlan) {
                // no region plan for this service

                if (serviceNodes.isEmpty()) {
                    // no local nodes for this service, just add the default
                    if (localRegion.equals(defaultNodeRegion)) {
                        LOGGER.error(
                                "Attempting to add a delegate to the current region ({}). This means that all of the nodes for service {} are stopped in this region and it is the default region for the service.",
                                localRegion, service);
                        // don't make DNS changes here, just leave things in place as they are
                        return null;
                    } else {
                        if (null == defaultNodeRegion) {
                            LOGGER.warn("Default region for service {} is null, cannot add a delegate record", service);
                        } else {
                            addDelegateRecord(dnsRecords, service, defaultNodeRegion, 1);
                        }
                    }
                } else {
                    // add a record for each node that should be running the
                    // service
                    serviceNodes.forEach(containerId -> {
                        addNameRecord(dnsRecords, service, containerId, 1);
                    });
                }
            } else {
                createRecordsForService(localRegion, service, defaultNodeRegion, regionServicePlan, serviceNodes,
                        dnsRecords);
            }

        } // foreach known service

        return dnsRecords.build();
    }

    private void createRecordsForService(@Nonnull final RegionIdentifier localRegion,
            @Nonnull final ServiceIdentifier<?> service,
            final RegionIdentifier serviceDefaultRegion,
            @Nonnull final ImmutableMap<RegionIdentifier, Double> regionServicePlan,
            @Nonnull final Set<NodeIdentifier> serviceNodes,
            @Nonnull final ImmutableCollection.Builder<Pair<DnsRecord, Double>> dnsRecords) {

        final int numServicesInLocalRegion = serviceNodes.size();

        // multiple the weight by the number of services to balance things out
        // if there are no services, then multiple by 1
        final double weightMultiplier = Math.max(1D, numServicesInLocalRegion);

        for (final ImmutableMap.Entry<RegionIdentifier, Double> regionEntry : regionServicePlan.entrySet()) {
            final RegionIdentifier destRegion = regionEntry.getKey();
            final double destRegionWeight = regionEntry.getValue();

            if (destRegion.equals(localRegion)) {
                // need to add entries to specific nodes
                if (serviceNodes.isEmpty()) {
                    // delegate to the region that contains the default node,
                    // that DNS will know which container to talk to
                    if (null == serviceDefaultRegion) {
                        LOGGER.warn("Default region for service {} is null, cannot add a delegate record", service);
                    } else {
                        // only add a single record since there are no other
                        // nodes to round robin with
                        addDelegateRecord(dnsRecords, service, serviceDefaultRegion, 1);
                    }
                } else {
                    serviceNodes.forEach(containerId -> {
                        addNameRecord(dnsRecords, service, containerId, 1);
                    });
                }
            } else {
                // need to add delegate entries
                addDelegateRecord(dnsRecords, service, destRegion, destRegionWeight * weightMultiplier);
            }
        } // foreach regionServicePlan entry
    }

    private void addDelegateRecord(@Nonnull final ImmutableCollection.Builder<Pair<DnsRecord, Double>> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final RegionIdentifier delegateRegion,
            final double weight) {
        if (null != delegateRegion) {
            // for now the source region is always null. If we decide to do
            // source based routing, then we'll need to add in the region
            // information and defaults for the null region
            final DelegateRecord record = new DelegateRecord(null, ttl, service, delegateRegion);
            dnsRecords.add(Pair.of(record, weight));
        } else {
            LOGGER.warn("Skipping adding delegate record for service because it's default region is null", service);
        }
    }

    private void addNameRecord(@Nonnull final ImmutableCollection.Builder<Pair<DnsRecord, Double>> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final NodeIdentifier nodeId,
            final double weight) {

        if (null != service) {
            // for now the source region is always null. If we decide to do
            // source based routing, then we'll need to add in the region
            // information and defaults for the null region
            final NameRecord record = new NameRecord(null, ttl, service, nodeId);
            dnsRecords.add(Pair.of(record, weight));
        } else {
            LOGGER.warn("Not adding name record for service to container {} because it's FQDN is null", nodeId);
        }
    }

}
