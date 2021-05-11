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
 * Implementation of {@link PlanTranslator} where a delegation cannot do a
 * further delegation.
 * 
 * 
 * @author jschewe
 *
 */
public class PlanTranslatorNoRecurse extends PlanTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTranslatorNoRecurse.class);

    /**
     * @param ttl
     *            see {@link PlanTranslator#PlanTranslator(int)}
     */
    public PlanTranslatorNoRecurse(final int ttl) {
        super(ttl);
    }

    @Override
    protected final List<Pair<DnsRecord, Double>> createDnsRecords(final RegionIdentifier localRegion,
            final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> normalizedOverflowPlan,
            final Map<NodeIdentifier, Double> normalizedContainerWeightsUnused,
            final Map<NodeIdentifier, Double> globalNormalizedContainerWeights,
            final Map<ServiceIdentifier<?>, Set<NodeIdentifier>> containersRunningServices) {
        final List<Pair<DnsRecord, Double>> dnsRecords = new LinkedList<>();

        // determine which containers are running which services and should be
        // entered into DNS
        final ApplicationManagerApi appManager = AppMgrUtils.getApplicationManager();
        for (final ApplicationSpecification spec : appManager.getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                final Set<NodeIdentifier> serviceNodes = containersRunningServices.getOrDefault(service,
                        Collections.emptySet());

                // add a record for each node that should be running the
                // service
                serviceNodes.forEach(containerId -> {
                    Double weight = globalNormalizedContainerWeights.get(containerId);

                    if (weight == null) {
                        weight = 1.0;
                        LOGGER.warn(
                                "No weight found in plan for container '{}' running service '{}'. Weight defaulting to {}.",
                                containerId, service, weight);
                    }

                    addNameRecord(dnsRecords, service, containerId, weight);
                });

                final Map<RegionIdentifier, Double> regionServicePlan = normalizedOverflowPlan.get(service);
                LOGGER.trace("Region service plan: {}", regionServicePlan);

                final RegionIdentifier defaultNodeRegion = spec.getServiceDefaultRegion();
                final boolean success = createRecordsForRegionPlan(service, !serviceNodes.isEmpty(), regionServicePlan,
                        localRegion, defaultNodeRegion, dnsRecords);
                if (!success) {
                    return null;
                }

            } // planned service
        } // foreach known service

        return dnsRecords;
    }

    /**
     * 
     * @return false if there was an error and DNS changes should be aborted
     */
    private boolean createRecordsForRegionPlan(final ServiceIdentifier<?> service,
            final boolean hasServiceNodes,
            final Map<RegionIdentifier, Double> regionServicePlan,
            final RegionIdentifier localRegion,
            final RegionIdentifier defaultNodeRegion,
            final List<Pair<DnsRecord, Double>> dnsRecords) {
        if (null == regionServicePlan) {
            if (!hasServiceNodes) {
                // no local nodes for this service, add a pointer to the
                // default region
                if (localRegion.equals(defaultNodeRegion)) {
                    LOGGER.error(
                            "Attempting to add a delegate to the current region ({}). This means that all of the nodes for service {} are stopped in this region and it is the default region for the service.",
                            localRegion, service);
                    // don't make DNS changes here, just leave things in
                    // place as they are
                    return false;
                } else {
                    if (null == defaultNodeRegion) {
                        LOGGER.warn("Default region for service {} is null, cannot add a delegate record", service);
                    } else {
                        addDelegateRecord(dnsRecords, service, defaultNodeRegion, 1);
                    }
                }
            } else {
                // no region plan, but have service nodes, keep traffic
                // local
                addDelegateRecord(dnsRecords, service, localRegion, 1);
            }
        } else {
            for (final ImmutableMap.Entry<RegionIdentifier, Double> regionEntry : regionServicePlan.entrySet()) {
                final RegionIdentifier destRegion = regionEntry.getKey();
                if (!localRegion.equals(destRegion)) {
                    final double destRegionWeight = regionEntry.getValue();

                    addDelegateRecord(dnsRecords, service, destRegion, destRegionWeight);
                }
            } // foreach regionServicePlan entry
        }
        return true;
    }

}
