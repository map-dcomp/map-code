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
package com.bbn.map.dns;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DnsResolutionType;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Translate {@link LoadBalancerPlan} to {@link DnsRecord}s. The object can be
 * shared across multiple nodes if desired. The object itself is immutable and
 * does not contain any region specific information.
 */
public abstract class PlanTranslator {

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
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> normalizedOverflowPlan = computeNormalizedOverflow(
                overflowDetails);

        // if a container is running, but told to stop traffic or to shutdown,
        // don't create a DNS entry for it
        // node -> containers
        final Map<NodeIdentifier, Set<NodeIdentifier>> containersToIgnore = new HashMap<>();

        // container -> weight
        final Map<NodeIdentifier, Double> containerWeights = new HashMap<>();

        // container -> service
        final Map<NodeIdentifier, ServiceIdentifier<?>> containerToServiceMap = new HashMap<>();

        // service -> sum of container weights
        final Map<ServiceIdentifier<?>, Double> serviceTotalContainerWeights = new HashMap<>();

        loadBalancerPlan.getServicePlan().forEach((node, containers) -> {
            containers.forEach(info -> {
                if (info.isStop() || info.isStopTrafficTo() || info.getWeight() <= 0) {
                    containersToIgnore.computeIfAbsent(node, k -> new HashSet<>()).add(info.getId());
                } else if (null != info.getId()) {
                    // ignore plan for new containers that don't exist yet
                    containerToServiceMap.put(info.getId(), info.getService());
                    containerWeights.merge(info.getId(), info.getWeight(), Double::sum);
                    serviceTotalContainerWeights.merge(info.getService(), info.getWeight(), Double::sum);
                }
            });
        });

        // container -> weight normalized by total service weight and multiplied
        // by the local region weight
        final Map<NodeIdentifier, Double> globalNormalizedContainerWeights = new HashMap<>();
        final Map<NodeIdentifier, Double> normalizedContainerWeights = new HashMap<>();
        containerWeights.forEach((container, weight) -> {
            final ServiceIdentifier<?> containerService = containerToServiceMap.get(container);
            final Map<RegionIdentifier, Double> serviceOverflow = normalizedOverflowPlan.getOrDefault(containerService,
                    ImmutableMap.of());
            final double localregionServiceWeight = serviceOverflow.getOrDefault(localRegion, 1D);

            final double normalizedWeight = weight / serviceTotalContainerWeights.get(containerService);
            normalizedContainerWeights.put(container, normalizedWeight);

            final double globalNormalizedWeight = normalizedWeight * localregionServiceWeight;
            globalNormalizedContainerWeights.put(container, globalNormalizedWeight);
        });
        LOGGER.debug("serviceTotalContainerWeights: {}", serviceTotalContainerWeights);
        LOGGER.debug("convertToDns: global normalized containerWeights: {}, loadBalancerPlan: {}",
                globalNormalizedContainerWeights, loadBalancerPlan);

        // containers to add DNS records for
        final Map<ServiceIdentifier<?>, Set<NodeIdentifier>> containersRunningServices = new HashMap<>();
        regionServiceState.getServiceReports().forEach(serviceReport -> {
            final NodeIdentifier node = serviceReport.getNodeName();

            final Set<NodeIdentifier> nodeContainersToIgnore = containersToIgnore.getOrDefault(node,
                    Collections.emptySet());
            serviceReport.getServiceState().forEach((containerId, serviceState) -> {
                final ServiceStatus status = serviceState.getStatus();
                // TODO check if this service is synchronous or asynchronous -
                // in the application manager. If synchronous then only add the
                // record if RUNNING.
                if (ServiceStatus.STARTING.equals(status) || ServiceStatus.RUNNING.equals(status)) {
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
        LOGGER.trace("normalizedOverflowPlan: {}", normalizedOverflowPlan);
        LOGGER.trace("normalizedContainerWeights: {}", normalizedContainerWeights);
        LOGGER.trace("globalNormalizedContainerWeights: {}", globalNormalizedContainerWeights);

        final List<Pair<DnsRecord, Double>> records = createDnsRecords(localRegion, normalizedOverflowPlan,
                normalizedContainerWeights, globalNormalizedContainerWeights, containersRunningServices);

        LOGGER.trace("Sending records: {}", records);
        if (null == records) {
            return null;
        } else {
            if (AgentConfiguration.getInstance().getRandomizeDnsRecords()) {
                // mix name and delegate records
                Collections.shuffle(records);
            }
            return ImmutableList.copyOf(records);
        }
    }

    /**
     * Create an overflow plan that has weights normalized based on the sum of
     * all weights for the service.
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> computeNormalizedOverflow(
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan) {
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> normalized = new HashMap<>();
        overflowPlan.forEach((service, serviceOverflow) -> {

            final double sum = serviceOverflow.entrySet().stream() //
                    .mapToDouble(Map.Entry::getValue) //
                    .sum();

            final Map<RegionIdentifier, Double> normalizedServiceOverflow = serviceOverflow.entrySet().stream() //
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue() / sum));

            normalized.put(service, normalizedServiceOverflow);
        });

        return normalized;
    }

    /**
     * Create DNS records.
     * 
     * @param localRegion
     *            the local region
     * @param normalizedOverflow
     *            overflow plan normalized to sum to 1
     * @param globalNormalizedContainerWeights
     *            {@code normalizedContainerWeights} multiplied by the local
     *            region weight, need to check {@code containersRunningServices}
     *            to determine which containers goto which services
     * @param normalizedContainerWeights
     *            weights normalized based on the sum of the weights for the
     *            service, need to check {@code containersRunningServices} to
     *            determine which containers goto which services
     * @param containersRunningServices
     *            which containers are running each service
     * @return the DNS records
     */
    protected abstract List<Pair<DnsRecord, Double>> createDnsRecords(RegionIdentifier localRegion,
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> normalizedOverflow,
            Map<NodeIdentifier, Double> normalizedContainerWeights,
            Map<NodeIdentifier, Double> globalNormalizedContainerWeights,
            Map<ServiceIdentifier<?>, Set<NodeIdentifier>> containersRunningServices);

    /**
     * Add a delegate record. If the weight is less than or equal to zero the
     * method does nothing.
     * 
     * @param dnsRecords
     *            where to add the record
     * @param service
     *            the service to add the delegate for
     * @param delegateRegion
     *            the region to delegate to
     * @param weight
     *            the weight for the record.
     */
    protected final void addDelegateRecord(@Nonnull final List<Pair<DnsRecord, Double>> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final RegionIdentifier delegateRegion,
            final double weight) {
        if (weight > 0) {
            if (null != delegateRegion) {
                // for now the source region is always null. If we decide to do
                // source based routing, then we'll need to add in the region
                // information and defaults for the null region
                final DelegateRecord record = new DelegateRecord(null, ttl, service, delegateRegion);
                dnsRecords.add(Pair.of(record, weight));
            } else {
                LOGGER.warn("Skipping adding delegate record for service because it's default region is null", service);
            }
        } else {
            LOGGER.trace("Skipping delegate record with zero weight. service: {} delegateRegion: {}", service,
                    delegateRegion);
        }
    }

    /**
     * Add a name record. If the weight is less than or equal to zero the method
     * does nothing.
     * 
     * @param dnsRecords
     *            where to add the record
     * @param service
     *            the service to create the record for
     * @param nodeId
     *            the node to resolve to
     * @param weight
     *            the weight of the record
     */
    protected final void addNameRecord(@Nonnull final List<Pair<DnsRecord, Double>> dnsRecords,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final NodeIdentifier nodeId,
            final double weight) {
        if (weight > 0) {
            if (null != service) {
                // for now the source region is always null. If we decide to do
                // source based routing, then we'll need to add in the region
                // information and defaults for the null region
                final NameRecord record = new NameRecord(null, ttl, service, nodeId);
                dnsRecords.add(Pair.of(record, weight));
            } else {
                LOGGER.warn("Not adding name record for service to container {} because it's FQDN is null", nodeId);
            }
        } else {
            LOGGER.trace("Skipping name record with zero weight. service: {} node: {}", service, nodeId);
        }
    }

    /**
     * Create the appropriate {@link PlanTranslator} and throw
     * {@link RuntimeException} if the {@code dnsResolutionType} is not
     * recognized.
     * 
     * @param dnsResolutionType
     *            which type of DNS resolution to use
     * @param ttl
     *            the TTL for the DNS records
     * @return the appropriate {@link PlanTranslator{
     */
    public static PlanTranslator constructPlanTranslator(final DnsResolutionType dnsResolutionType, final int ttl) {
        switch (dnsResolutionType) {
        case RECURSIVE:
            return new PlanTranslatorRecurse(ttl);
        case NON_RECURSIVE:
            return new PlanTranslatorNoRecurse(ttl);
        case RECURSIVE_TWO_LAYER:
            return new PlanTranslatorRecurse2Layer(ttl);
        default:
            throw new RuntimeException("Unexpected DNS resolution type: " + dnsResolutionType);
        }
    }

}
