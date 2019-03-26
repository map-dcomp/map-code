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
package com.bbn.map.dns;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationProfile;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
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
     * @return the DNS records that represent the inputs
     */
    @Nonnull
    public ImmutableCollection<DnsRecord> convertToDns(@Nonnull final LoadBalancerPlan loadBalancerPlan,
            @Nonnull final RegionServiceState regionServiceState) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("convertToDNS loadBalancerPlan: {} serviceState: {}", loadBalancerPlan, regionServiceState);
        }

        final RegionIdentifier localRegion = loadBalancerPlan.getRegion();
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowDetails = loadBalancerPlan
                .getOverflowPlan();

        final Set<ContainerIdentifier> containersToIgnore = loadBalancerPlan.getStopTrafficTo().entrySet().stream()
                .map(Map.Entry::getValue).flatMap(Set::stream).collect(Collectors.toSet());

        final Map<ServiceIdentifier<?>, Set<ContainerIdentifier>> containersRunningServices = new HashMap<>();
        regionServiceState.getServiceReports().forEach(serviceReport -> {
            serviceReport.getServiceState().forEach((containerId, serviceState) -> {
                final ServiceState.Status status = serviceState.getStatus();
                // TODO check if this service is synchronous or asynchronous -
                // in the application manager. If synchronous then only add the
                // record if RUNNING.
                if (ServiceState.Status.STARTING.equals(status) || ServiceState.Status.RUNNING.equals(status)) {
                    if (!containersToIgnore.contains(containerId)) {
                        final ServiceIdentifier<?> service = serviceState.getService();
                        final Set<ContainerIdentifier> containers = containersRunningServices.computeIfAbsent(service,
                                k -> new HashSet<>());
                        containers.add(containerId);
                    }
                }

            });
        });

        final ImmutableCollection.Builder<DnsRecord> dnsRecords = ImmutableList.builder();

        // determine which containers are running which services and should be
        // entered into DNS
        final ApplicationManagerApi appManager = AppMgrUtils.getApplicationManager();
        // TODO: should this be looking at dependencies as well?
        for (final ApplicationSpecification spec : appManager.getAllApplicationSpecifications()) {
            final ApplicationProfile profile = spec.getProfile();
            final ApplicationCoordinates service = spec.getCoordinates();
            final RegionIdentifier defaultNodeRegion = profile.getServiceDefaultRegion();

            final Set<ContainerIdentifier> serviceNodes = containersRunningServices.getOrDefault(service,
                    Collections.emptySet());

            final ImmutableMap<RegionIdentifier, Double> regionServicePlan = overflowDetails.get(service);
            if (null == regionServicePlan) {
                // no region plan for this service

                if (serviceNodes.isEmpty()) {
                    // no local nodes for this service, just add the default
                    if (localRegion.equals(defaultNodeRegion)) {
                        LOGGER.error(
                                "Attempting to add a delegate to the current region ({}). This means that all of the nodes for service {} are stopped in the default region",
                                localRegion, service);
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
            @Nonnull final Set<ContainerIdentifier> serviceNodes,
            @Nonnull final ImmutableCollection.Builder<DnsRecord> dnsRecords) {

        final double weightPrecision = AgentConfiguration.getInstance().getDnsRecordWeightPrecision();

        final double totalOfWeights = regionServicePlan.values().stream().mapToDouble(Number::doubleValue).sum();

        // If there are 3 services running in the current region, then each 5%
        // gets 3 records
        final int numServicesInRegion = serviceNodes.size();
        final int minPrecisionMultiplier = Math.max(1, numServicesInRegion);

        for (final ImmutableMap.Entry<RegionIdentifier, Double> regionEntry : regionServicePlan.entrySet()) {
            final RegionIdentifier destRegion = regionEntry.getKey();
            final double destRegionWeight = regionEntry.getValue();
            final double destRegionPercentage = destRegionWeight / totalOfWeights;
            final double rawNumRecords = destRegionPercentage / weightPrecision;
            final int numRecords = (int) Math.round(minPrecisionMultiplier * rawNumRecords);

            if (destRegion.equals(localRegion)) {
                // need to add entries to specific nodes

                if (serviceNodes.isEmpty()) {
                    // delegate to the region that contains the default node,
                    // that DNS will know which container to talk to
                    if (null == serviceDefaultRegion) {
                        LOGGER.warn("Default region for service {} is null, cannot add a delegate record", service);
                    } else {
                        addDelegateRecord(dnsRecords, service, serviceDefaultRegion, numRecords);
                    }
                } else {
                    serviceNodes.forEach(containerId -> {
                        LOGGER.trace("Adding name record for service {} -> {}", service, containerId);

                        addNameRecord(dnsRecords, service, containerId, numRecords);
                    });
                }
            } else {
                // need to add delegate entries
                addDelegateRecord(dnsRecords, service, destRegion, numRecords);
            }
        } // foreach regionServicePlan entry
    }

    private void addDelegateRecord(@Nonnull final ImmutableCollection.Builder<DnsRecord> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final RegionIdentifier delegateRegion,
            final int numRecordsToAdd) {
        if (null != delegateRegion) {
            // for now the source region is always null. If we decide to do
            // source based routing, then we'll need to add in the region
            // information and defaults for the null region
            final DelegateRecord record = new DelegateRecord(null, ttl, service, delegateRegion);
            dnsRecords.addAll(Collections.nCopies(numRecordsToAdd, record));
        } else {
            LOGGER.warn("Skipping adding delegate record for service because it's default region is null", service);
        }
    }

    private void addNameRecord(@Nonnull final ImmutableCollection.Builder<DnsRecord> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final ContainerIdentifier nodeId,
            final int numRecordsToAdd) {

        if (null != service) {
            // for now the source region is always null. If we decide to do
            // source based routing, then we'll need to add in the region
            // information and defaults for the null region
            final NameRecord record = new NameRecord(null, ttl, service, nodeId);
            dnsRecords.addAll(Collections.nCopies(numRecordsToAdd, record));
        } else {
            LOGGER.warn("Not adding name record for service to container {} because it's FQDN is null", nodeId);
        }
    }

}
