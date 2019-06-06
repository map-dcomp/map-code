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
package com.bbn.map.rlg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * Some utilities used by {@link RLGService}.
 * 
 * @author jschewe
 *
 */
/* package */ final class RlgUtils {

    private RlgUtils() {
    }

    /**
     * For each service, compute the load percentage. The load percentage is
     * load divided by allocated capacity for the service. This is computed on
     * the server attributes only.
     * 
     * @param reports
     *            the reports for the current region
     * @return service -> attribute -> load percentage
     */
    public static LoadPercentages computeServiceLoadPercentages(
            @Nonnull final Map<NodeIdentifier, ResourceReport> reports) {
        final Map<ServiceIdentifier<?>, Map<NodeAttribute<?>, Double>> regionServiceAllocatedCapacity = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<NodeAttribute<?>, Double>> regionServiceLoad = new HashMap<>();

        final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> regionNodeAllocatedCapacity = new HashMap<>();
        final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> regionNodeLoad = new HashMap<>();

        reports.forEach((node, report) -> {
            final Map<NodeAttribute<?>, Double> nodeAllocatedCapacity = regionNodeAllocatedCapacity.computeIfAbsent(node,
                    k -> new HashMap<>());
            final Map<NodeAttribute<?>, Double> nodeLoad = regionNodeLoad.computeIfAbsent(node, k -> new HashMap<>());

            report.getContainerReports().forEach((container, creport) -> {
                final ServiceIdentifier<?> service = creport.getService();

                final Map<NodeAttribute<?>, Double> serviceAllocatedCapacity = regionServiceAllocatedCapacity
                        .computeIfAbsent(service, k -> new HashMap<>());
                creport.getComputeCapacity().forEach((cattr, cvalue) -> {
                    serviceAllocatedCapacity.merge(cattr, cvalue, Double::sum);

                    nodeAllocatedCapacity.merge(cattr, cvalue, Double::sum);
                });

                final Map<NodeAttribute<?>, Double> serviceLoad = regionServiceLoad.computeIfAbsent(service,
                        k -> new HashMap<>());
                creport.getComputeLoad().forEach((source, sourceLoad) -> {
                    sourceLoad.forEach((cattr, cvalue) -> {
                        serviceLoad.merge(cattr, cvalue, Double::sum);

                        nodeLoad.merge(cattr, cvalue, Double::sum);
                    });
                });

            }); // foreach container report

        }); // foreach report

        // compute percentages
        final LoadPercentages retval = new LoadPercentages();
        regionServiceAllocatedCapacity.forEach((service, serviceCapacity) -> {
            final Map<NodeAttribute<?>, Double> serviceLoad = regionServiceLoad.getOrDefault(service, Collections.emptyMap());

            final Map<NodeAttribute<?>, Double> serviceLoadPercentage = retval.allocatedLoadPercentagePerService
                    .computeIfAbsent(service, k -> new HashMap<>());

            serviceCapacity.forEach((attr, capacityValue) -> {
                final double loadValue = serviceLoad.getOrDefault(attr, 0D);
                final double percentage = loadValue / capacityValue;

                serviceLoadPercentage.put(attr, percentage);
            });
        });

        regionNodeAllocatedCapacity.forEach((node, nodeCapacity) -> {
            final Map<NodeAttribute<?>, Double> nodeLoad = regionNodeLoad.getOrDefault(node, Collections.emptyMap());

            final Map<NodeAttribute<?>, Double> nodeLoadPercentage = retval.allocatedLoadPercentagePerNode
                    .computeIfAbsent(node, k -> new HashMap<>());

            nodeCapacity.forEach((attr, capacityValue) -> {
                final double loadValue = nodeLoad.getOrDefault(attr, 0D);
                final double percentage = loadValue / capacityValue;

                nodeLoadPercentage.put(attr, percentage);
            });
        });

        return retval;
    }

    // CHECKSTYLE:OFF value class
    /**
     * Results of computing the load percentage different ways.
     * 
     * @author jschewe
     *
     */
    public static final class LoadPercentages {
        /**
         * Load percentage per service based on allocated capacity.
         */
        public final Map<ServiceIdentifier<?>, Map<NodeAttribute<?>, Double>> allocatedLoadPercentagePerService = new HashMap<>();

        /**
         * Load percentage per node based on allocated capacity.
         */
        public final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> allocatedLoadPercentagePerNode = new HashMap<>();

    }
    // CHECKSTYLE:ON
}
