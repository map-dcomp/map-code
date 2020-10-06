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
package com.bbn.map.rlg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Some utilities used by {@link RLGService}.
 * 
 * @author jschewe
 *
 */
/* package */ final class RlgUtils {

    private RlgUtils() {
    }
    
    
    /*
     * Interface for defining functions to compute container wieghts.
     */
    public interface ContainerWeightFunction
    {
        /**
         * Function to compute the weights for a container.
         * 
         * @param containerCapacity
         *          the container capacity
         * @param containerLoad
         *          the container load
         * @return the container weight for each attribute
         */
        Map<NodeAttribute, Double> computeWeights(Map<NodeAttribute, Double> containerCapacity, 
                Map<NodeAttribute, Double> containerLoad);
    }
    
    /**
     * Computes a weight for each container according to the provided {@link ContainerWeightFunction}.
     * This method intentionally ignores containers with null Ids because they will each 
     * automatically be assigned an Id, which will then appear in the reports when the
     * container is ready to receive requests.
     * 
     * The weight of each container is (container capacity - container load) if not normalized,
     * and (container capacity - container load) / (region capacity - region load) if normalized.
     * 
     * @param reports
     *          the reports for the current region
     * @param normalizeWeights
     *          weights are normalized to sum to 1 (divided by remaining region capacity) if true
     * @return a Map from container IDs to DNS proportional weights
     */
    public static Map<NodeIdentifier, Map<NodeAttribute, Double>> computeContainerWeights(
            @Nonnull final Map<NodeIdentifier, ResourceReport> reports, ContainerWeightFunction containerWeightFunction,
            boolean normalizeWeights)
    {
        Map<NodeIdentifier, Map<NodeAttribute, Double>> containerWeights = new HashMap<>();
        Map<NodeAttribute, Double> regionCapacity = new HashMap<>();
        Map<NodeAttribute, Double> regionLoad = new HashMap<>();
        
        reports.forEach((node, report) ->
        {
            report.getContainerReports().forEach((container, creport) ->
            {
                Map<NodeAttribute, Double> containerCapacity = creport.getComputeCapacity();
                Map<NodeAttribute, Double> containerLoad = new HashMap<>();
//                Map<NodeAttribute, Double> containerRemainingCapacity = new HashMap<>();
                Map<NodeAttribute, Double> computedWeights;
                
                containerCapacity.forEach((attr, value) ->
                {
                    // add to region capacity
                    regionCapacity.merge(attr, value, Double::sum);
                    
                    // put container capacity for attr
//                    containerRemainingCapacity.put(attr, value);
                });             
                
                getConfiguredLoadInput(creport).forEach((clientNode, load) ->
                {
                    load.forEach((attr, value) ->
                    {
                        // add region load
                        regionLoad.merge(attr, value, Double::sum);
                        
                        // add container load
                        containerLoad.merge(attr, value, Double::sum);
                        
                        // subtract load from remaining capacity for this container
//                        containerRemainingCapacity.merge(attr, -value, Double::sum);
                    });
                });
                
                computedWeights = containerWeightFunction.computeWeights(containerCapacity, containerLoad);
                
                // put the remaining capacity values that are >= 0 in containerWeights
                computedWeights.forEach((attr, value) ->
                {
                    if (value >= 0)
                    {
                        containerWeights.computeIfAbsent(container, k -> new HashMap<>()).put(attr, value);
                    }
                });
            });
        });       
        
        
        
        if (normalizeWeights)
        {
            // subtract region load from region capacity to obtain remaining region capacity
            Map<NodeAttribute, Double> regionRemainingCapacity = new HashMap<>();
            regionCapacity.forEach((attr, cap) ->
            {
                regionRemainingCapacity.put(attr, cap - regionLoad.getOrDefault(attr, 0.0));
            });

            // normalize all weights to the remaining region capacity so that they sum to 1
            containerWeights.forEach((container, weights) ->
            {
                weights.keySet().forEach((attr) ->
                {
                    weights.computeIfPresent(attr, (a, w) -> (w / (regionRemainingCapacity.get(a))));
                });
            });
        }
        
        return containerWeights;
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
        final Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> regionServiceAllocatedCapacity = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> regionServiceLoad = new HashMap<>();

        final Map<NodeIdentifier, Map<NodeAttribute, Double>> regionNodeAllocatedCapacity = new HashMap<>();
        final Map<NodeIdentifier, Map<NodeAttribute, Double>> regionNodeLoad = new HashMap<>();

        reports.forEach((node, report) -> {
            final Map<NodeAttribute, Double> nodeAllocatedCapacity = regionNodeAllocatedCapacity.computeIfAbsent(node,
                    k -> new HashMap<>());
            final Map<NodeAttribute, Double> nodeLoad = regionNodeLoad.computeIfAbsent(node, k -> new HashMap<>());

            report.getContainerReports().forEach((container, creport) -> {
                final ServiceIdentifier<?> service = creport.getService();

                final Map<NodeAttribute, Double> serviceAllocatedCapacity = regionServiceAllocatedCapacity
                        .computeIfAbsent(service, k -> new HashMap<>());
                creport.getComputeCapacity().forEach((cattr, cvalue) -> {
                    serviceAllocatedCapacity.merge(cattr, cvalue, Double::sum);

                    nodeAllocatedCapacity.merge(cattr, cvalue, Double::sum);
                });

                final Map<NodeAttribute, Double> serviceLoad = regionServiceLoad.computeIfAbsent(service,
                        k -> new HashMap<>());
                getConfiguredLoadInput(creport).forEach((source, sourceLoad) -> {
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
            final Map<NodeAttribute, Double> serviceLoad = regionServiceLoad.getOrDefault(service, Collections.emptyMap());

            final Map<NodeAttribute, Double> serviceLoadPercentage = retval.allocatedLoadPercentagePerService
                    .computeIfAbsent(service, k -> new HashMap<>());

            serviceCapacity.forEach((attr, capacityValue) -> {
                final double loadValue = serviceLoad.getOrDefault(attr, 0D);
                final double percentage = loadValue / capacityValue;

                serviceLoadPercentage.put(attr, percentage);
            });
        });

        regionNodeAllocatedCapacity.forEach((node, nodeCapacity) -> {
            final Map<NodeAttribute, Double> nodeLoad = regionNodeLoad.getOrDefault(node, Collections.emptyMap());

            final Map<NodeAttribute, Double> nodeLoadPercentage = retval.allocatedLoadPercentagePerNode
                    .computeIfAbsent(node, k -> new HashMap<>());

            nodeCapacity.forEach((attr, capacityValue) -> {
                final double loadValue = nodeLoad.getOrDefault(attr, 0D);
                final double percentage = loadValue / capacityValue;

                nodeLoadPercentage.put(attr, percentage);
            });
        });

        return retval;
    }
    
    
    /**
     * Uses service priorities to compute the amount of regional capacity to which each service in the region is entitled.
     * 
     * @param resourceReports
     *          the resource reports, which contain node capacity and information about which services are present in the region
     * @return a {@link Map} containing the amount of total regional capacity for each {@link NodeAttribute} that each service is entitled to
     */
    public static Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> computeServicePriorityTargetAllocations(Set<ResourceReport> resourceReports)
    {
        Map<ServiceIdentifier<?>, Double> servicePriorities = new HashMap<>();        
        Map<NodeAttribute, Double> regionCapacity = new HashMap<>();
        
        // compute total region capacity
        for (ResourceReport report : resourceReports)
        {
            report.getNodeComputeCapacity().forEach((attr, value) ->
            {
                regionCapacity.merge(attr, value, Double::sum);
            });
            
            for (ContainerResourceReport containerReport : report.getContainerReports().values())
            {
                ServiceIdentifier<?> service = containerReport.getService();
                servicePriorities.computeIfAbsent(service, s -> ((double) AppMgrUtils.getApplicationSpecification(s).getPriority()));
            }
        }
        
        
        double sum = 0.0;
        for (double priority : servicePriorities.values())
        {
            sum += priority;
        }
        
        final double totalPriority = sum;
        
        Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> serviceTargets = new HashMap<>();
        
        servicePriorities.forEach((service, priority) ->
        {   
            final double priorityProportion = priority / totalPriority;
            
            regionCapacity.forEach((attr, capacity) ->
            {
                serviceTargets
                        .computeIfAbsent(service, k -> new HashMap<>())
                        .computeIfAbsent(attr, a -> capacity * priorityProportion);
            });
        });
        
        
        return serviceTargets;
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
        public final Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> allocatedLoadPercentagePerService = new HashMap<>();

        /**
         * Load percentage per node based on allocated capacity.
         */
        public final Map<NodeIdentifier, Map<NodeAttribute, Double>> allocatedLoadPercentagePerNode = new HashMap<>();

    }
    // CHECKSTYLE:ON
    
    
    
    /**
     * @param report
     *          the {@link ResourceReport}
     * 
     * @return the load input (either Load or Demand) from a {@link ResourceReport} as determined by {@link AgentConfiguration}
     */
    public static ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> getConfiguredLoadInput(ResourceReport report)
    {
        switch (AgentConfiguration.getInstance().getRlgAlgorithmLoadInput())
        {
            case DEMAND:
                return report.getComputeDemand();

            case LOAD:
                return report.getComputeLoad();

            default:
                return ImmutableMap.of();
        }
    }
    
    /**
     * @param report
     *          the {@link ContainerResourceReport}
     * 
     * @return the load input (either Load or Demand) from a {@link ContainerResourceReport} as determined by {@link AgentConfiguration}
     */
    public static ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> getConfiguredLoadInput(ContainerResourceReport creport)
    {
        switch (AgentConfiguration.getInstance().getRlgAlgorithmLoadInput())
        {
            case DEMAND:
                return creport.getComputeDemand();

            case LOAD:
                return creport.getComputeLoad();

            default:
                return ImmutableMap.of();
        }
    }
}
