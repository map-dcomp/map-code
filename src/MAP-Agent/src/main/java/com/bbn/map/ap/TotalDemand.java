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
package com.bbn.map.ap;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Server demand for a service attributed to clients across the network.
 * 
 * @author jschewe
 *
 */
public final class TotalDemand implements Serializable {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final long serialVersionUID = 1L;

    private final ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> serverDemandWithClientAllocation;

    private static final double NON_TRIVIAL_NEGATIVE_DEMAND_VALUE_THRESHOLD = -1E-15;

    /**
     * Server demand with allocation to clients based on network demand.
     * 
     * source region - attribute - value.
     * 
     * @return server demand with allocation
     */
    public ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> getServerDemand() {
        return serverDemandWithClientAllocation;
    }

    /**
     * Represents null.
     */
    public static final TotalDemand NULL_TOTAL_DEMAND = new TotalDemand();

    /**
     * Create null object.
     */
    private TotalDemand() {
        serverDemandWithClientAllocation = ImmutableMap.of();
    }

    /**
     * 
     * @param serverDemandWithClientAllocation
     *            see {@link #getServerDemand()}
     */
    public TotalDemand(
            @JsonProperty("serverDemand") final ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> serverDemandWithClientAllocation) {
        this.serverDemandWithClientAllocation = serverDemandWithClientAllocation;
    }

    /**
     * Convert a summary to the {@link TotalDemand} objects per service.
     * 
     * @param summary
     *            summary object to convert
     * @return service, total demand
     */
    public static ImmutableMap<ServiceIdentifier<?>, TotalDemand> fromSummary(final ResourceSummary summary) {
        final RegionIdentifier thisRegion = summary.getRegion();

        // service -> source region -> data
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serverDemandWithClientAllocation = allocateComputeBasedOnNetwork(
                thisRegion, summary.getServerDemand(), summary.getNetworkDemand());

        return ImmutableMap.copyOf(serverDemandWithClientAllocation.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new TotalDemand(e.getValue()))));
    }

    /**
     * Sum 2 objects.
     * 
     * @param one
     *            First object
     * @param two
     *            Second object
     * @return see {@link #TotalDemand(TotalDemand, TotalDemand)}
     */
    public static TotalDemand sumTotalDemand(final TotalDemand one, final TotalDemand two) {
        if (null == one && null == two) {
            return nullTotalDemand();
        } else if (null == one) {
            return two;
        } else if (null == two) {
            return one;
        } else {
            final Map<RegionIdentifier, Map<NodeAttribute, Double>> sum = new HashMap<>();

            one.getServerDemand().forEach((region, regionData) -> {
                final Map<NodeAttribute, Double> mergedData = new HashMap<>(regionData);
                sum.put(region, mergedData);
            });

            two.getServerDemand().forEach((region, regionData) -> {
                final Map<NodeAttribute, Double> mergedData = sum.computeIfAbsent(region, k -> new HashMap<>());
                regionData.forEach((attr, value) -> {
                    mergedData.merge(attr, value, Double::sum);
                });
            });

            final ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> immutableSum = ImmutableUtils
                    .makeImmutableMap2(sum);

            final TotalDemand merged = new TotalDemand(immutableSum);
            return merged;
        } // non-null objects to sum
    }

    /**
     * 
     * @return object Protelis will use for null
     */
    public static TotalDemand nullTotalDemand() {
        return NULL_TOTAL_DEMAND;
    }

    /**
     * 
     * @param rawCompute
     *            the raw compute value
     * @param network
     *            the network value used to determine what percentage of the raw
     *            compute value belongs to each client
     * @return the compute demand using the network demand to determine
     *         allocation per client
     */
    private static ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> allocateComputeBasedOnNetwork(
            final RegionIdentifier thisRegion,
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> rawCompute,
            final ImmutableMap<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> network) {

        // ignore the "source" of the compute numbers
        final Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> aggregatedCompute = new HashMap<>();
        rawCompute.forEach((service, data) -> {
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                final Map<NodeAttribute, Double> aggregateData = aggregatedCompute.computeIfAbsent(service,
                        k -> new HashMap<>());
                data.forEach((ignore, attrData) -> {
                    attrData.forEach((attr, value) -> {
                        aggregateData.merge(attr, value, Double::sum);
                    });
                });
            } // not AP or UNMANAGED
        });

        // compute sum of RX and TX for each service from each source by service
        // TODO: make sure if the network demand is zero, then the inferred
        // demand is zero as well
        final Map<ServiceIdentifier<?>, Double> networkPerService = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> networkPerServicePerSource = new HashMap<>();
        network.forEach((neighbor, sourceData) -> {
            sourceData.forEach((flow, flowData) -> {
                final RegionIdentifier client;
                final RegionIdentifier server;
                if (flow.getSource().equals(flow.getServer())) {
                    client = flow.getDestination();
                    server = flow.getSource();
                } else if (flow.getDestination().equals(flow.getServer())) {
                    client = flow.getSource();
                    server = flow.getDestination();
                } else {
                    client = null;
                    server = flow.getServer();
                }

                final Map<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> plannedServiceFlowData = flowData
                        .entrySet().stream().filter(entry -> !MAPServices.UNPLANNED_SERVICES.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (null == client && !plannedServiceFlowData.isEmpty()) {
                    // only warn when there are planned services for which we
                    // cannot find the client
                    LOGGER.warn(
                            "Unable to find client in flow {} <-> {} with server {}. Skipping computation of inferred demand for this flow",
                            flow.getSource(), flow.getDestination(), flow.getServer());
                }

                if (null != client && thisRegion.equals(server)) {
                    plannedServiceFlowData.forEach((service, serviceData) -> {
                        final double rx = serviceData.getOrDefault(LinkAttribute.DATARATE_RX, 0D);
                        final double tx = serviceData.getOrDefault(LinkAttribute.DATARATE_TX, 0D);
                        final double serviceNetworkDemand = (Double.isFinite(rx) && rx >= 0.0 ? rx : 0.0)
                                + (Double.isFinite(tx) && tx >= 0.0 ? tx : 0.0);
                        networkPerService.merge(service, serviceNetworkDemand, Double::sum);

                        if (!Double.isFinite(rx) || !Double.isFinite(tx)) {
                            LOGGER.error("Found NaN or infinite network demand value when computing inferred demand. "
                                    + "thisRegion: {}, neighbor: {}, flow: {}, service: {}, serviceData: {}", 
                                    thisRegion, neighbor, flow, server, serviceData);
                        }

                        if (rx < NON_TRIVIAL_NEGATIVE_DEMAND_VALUE_THRESHOLD || tx < NON_TRIVIAL_NEGATIVE_DEMAND_VALUE_THRESHOLD) {
                            LOGGER.error("Found non trivial negative network demand value with magnitude > |{}| "
                                    + "when computing inferred demand. thisRegion: {}, neighbor: {}, flow: {}, "
                                    + "service: {}, serviceData: {}", NON_TRIVIAL_NEGATIVE_DEMAND_VALUE_THRESHOLD,
                                    thisRegion, neighbor, flow, server, serviceData);
                        }

                        final Map<RegionIdentifier, Double> networkPerSource = networkPerServicePerSource
                                .computeIfAbsent(service, k -> new HashMap<>());
                        networkPerSource.merge(client, serviceNetworkDemand, Double::sum);
                    });
                } // client not null and server is in this region
            });
        });

        // compute server value based on percentage of aggregatedCompute and
        // percentage of network from each source for a service
        final ImmutableMap.Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> finalCompute = ImmutableMap
                .builder();
        networkPerServicePerSource.forEach((service, networkPerSource) -> {
            if (aggregatedCompute.containsKey(service)) {
                final ImmutableMap.Builder<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> finalComputePerSource = ImmutableMap
                        .builder();

                final Map<NodeAttribute, Double> aggregateComputeForService = aggregatedCompute.get(service);
                final double serviceValue = networkPerService.get(service);
                networkPerSource.forEach((source, sourceValue) -> {
                    final double rawPercentage = sourceValue / serviceValue;
                    // Can get NaN if sourceValue and serviceValue are both
                    // zero.
                    /// If sourceValue is non-zero, then serviceValue cannot be
                    // zero because of how it's computed, therefore we will not
                    // see Infinity.
                    final double percentage = Double.isNaN(rawPercentage) ? 0 : rawPercentage;

                    final ImmutableMap.Builder<NodeAttribute, Double> finalComputeForSource = ImmutableMap.builder();
                    aggregateComputeForService.forEach((attr, value) -> {
                        final double sourceComputeValue = value * percentage;
                        finalComputeForSource.put(attr, sourceComputeValue);
                        // finalComputeForSource.put(attr, 6.0);
                    });

                    finalComputePerSource.put(source, finalComputeForSource.build());
                }); // foreach network source

                finalCompute.put(service, finalComputePerSource.build());
            } // have compute values for this service
        }); // foreach service with network data

        return finalCompute.build();
    }
}
