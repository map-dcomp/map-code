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
import java.util.LinkedList;
import java.util.List;
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
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Like {@link PlanTranslatorRecurse}, except that the lookup of the region and
 * the containers in a region are separate lookups.
 * 
 * @author jschewe
 *
 */
public class PlanTranslatorRecurse2Layer extends PlanTranslatorRecurse {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTranslatorRecurse2Layer.class);

    /**
     * @param ttl
     *            see {@link PlanTranslator#PlanTranslator(int)}
     */
    public PlanTranslatorRecurse2Layer(final int ttl) {
        super(ttl);
    }

    @Override
    protected List<Pair<DnsRecord, Double>> createDnsRecords(final RegionIdentifier localRegion,
            final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> normalizedOverflow,
            final Map<NodeIdentifier, Double> normalizedContainerWeights,
            final Map<NodeIdentifier, Double> globalNormalizedContainerWeightsUnused,
            final Map<ServiceIdentifier<?>, Set<NodeIdentifier>> containersRunningServices) {

        final List<Pair<DnsRecord, Double>> dnsRecords = new LinkedList<>();

        // determine which containers are running which services and should be
        // entered into DNS
        final ApplicationManagerApi appManager = AppMgrUtils.getApplicationManager();
        for (final ApplicationSpecification spec : appManager.getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                final RegionIdentifier defaultNodeRegion = spec.getServiceDefaultRegion();

                final Set<NodeIdentifier> serviceNodes = containersRunningServices.getOrDefault(service,
                        Collections.emptySet());
                LOGGER.trace("Containers for {} are {}", service, serviceNodes);

                final Map<RegionIdentifier, Double> serviceOverflowPlan = normalizedOverflow.get(service);
                LOGGER.trace("Region service ({}) overflow plan: {}", service, serviceOverflowPlan);
                if (null == serviceOverflowPlan || serviceOverflowPlan.isEmpty()) {
                    // no overflow plan for this service

                    if (serviceNodes.isEmpty()) {
                        // no local nodes for this service, add a delegate to
                        // the default region

                        if (localRegion.equals(defaultNodeRegion)) {
                            LOGGER.error(
                                    "Attempting to add a delegate to the current region ({}). This means that all of the nodes for service {} are stopped in this region and it is the default region for the service.",
                                    localRegion, service);
                            // don't make DNS changes here, just leave things in
                            // place as they are
                            return null;
                        } else {
                            if (null == defaultNodeRegion) {
                                LOGGER.warn("Default region for service {} is null, cannot add a delegate record",
                                        service);
                            } else {
                                LOGGER.trace("no overflow adding delegate record for {} to {}", service,
                                        defaultNodeRegion);
                                addDelegateRecord(dnsRecords, service, defaultNodeRegion, 1);
                            }
                        }
                    } else {
                        // add a record for each node that should be running the
                        // service
                        serviceNodes.forEach(containerId -> {
                            final double weight;
                            if (!normalizedContainerWeights.containsKey(containerId)) {
                                weight = 1.0;
                                LOGGER.warn(
                                        "No weight found in plan for container '{}' running service '{}'. Weight defaulting to {}.",
                                        containerId, service, weight);
                            } else {
                                weight = normalizedContainerWeights.get(containerId);
                            }

                            LOGGER.trace("no overflow adding name record for {} to {}", service, containerId);
                            addNameRecord(dnsRecords, service, containerId, weight);
                        });
                    }
                } else {
                    createRecordsForService(localRegion, service, defaultNodeRegion, serviceOverflowPlan, serviceNodes,
                            normalizedContainerWeights, dnsRecords);
                }
            } // planned service
        } // foreach known service

        return dnsRecords;
    }

    private void createRecordsForService(@Nonnull final RegionIdentifier localRegion,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final RegionIdentifier serviceDefaultRegion,
            @Nonnull final Map<RegionIdentifier, Double> normalizedServiceOverflow,
            @Nonnull final Set<NodeIdentifier> serviceNodes,
            @Nonnull final Map<NodeIdentifier, Double> normalizedContainerWeights,
            @Nonnull final List<Pair<DnsRecord, Double>> dnsRecords) {

        boolean needsDelegateToDefaultRegionForZeroServiceNodes = false;
        boolean delegateToDefaultRegionInPlan = false;
        double defaultRegionWeight = 0;
        for (final ImmutableMap.Entry<RegionIdentifier, Double> regionEntry : normalizedServiceOverflow.entrySet()) {
            final RegionIdentifier destRegion = regionEntry.getKey();
            final double destRegionWeight = regionEntry.getValue();

            if (destRegionWeight > 0) {
                if (localRegion.equals(destRegion) && serviceNodes.isEmpty()) {
                    // no containers to delegate to locally, send to the default
                    // region
                    needsDelegateToDefaultRegionForZeroServiceNodes = true;
                    defaultRegionWeight = destRegionWeight;
                } else {
                    // add delegate entry
                    LOGGER.trace("createRecordsForService adding delegate record for {} to {}", service, destRegion);
                    addDelegateRecord(dnsRecords, service, destRegion, destRegionWeight);

                    if (destRegion.equals(serviceDefaultRegion)) {
                        delegateToDefaultRegionInPlan = true;
                    }
                }
            } // region weight greater than 0
        } // foreach regionServicePlan entry

        for (final NodeIdentifier container : serviceNodes) {
            Double weight = normalizedContainerWeights.getOrDefault(container, 1D);

            if (weight == null) {
                weight = 1.0;
                LOGGER.warn("No weight found in plan for container '{}' running service '{}'. Weight defaulting to {}.",
                        container, service, weight);
            }

            if (weight > 0) {
                LOGGER.trace("createRecordsForService adding name record for {} to {}", service, container);
                addNameRecord(dnsRecords, service, container, weight);
            }
        }

        // Only add delegation to the default region if there isn't already a
        // delegate entry
        if (needsDelegateToDefaultRegionForZeroServiceNodes && !delegateToDefaultRegionInPlan
                && defaultRegionWeight > 0) {
            LOGGER.trace("createRecordsForService adding default region delegate record for {} to {}", service,
                    serviceDefaultRegion);
            addDelegateRecord(dnsRecords, service, serviceDefaultRegion, defaultRegionWeight);
        }

    }

}
