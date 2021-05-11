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
package com.bbn.map.rlg;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;



/**
 * Uses greedy priority groups and demand predictions to assign target allocations to services.
 * 
 * @author awald
 *
 */
public class GreedyGroupServicePriorityManager extends AbstractPercentAllocationToTargetSPM
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GreedyGroupServicePriorityManager.class);
    private static final double TARGET_LOAD_PERCENTAGE = AgentConfiguration.getInstance().getRlgLoadThreshold();
    
    
    /**
     * @param nodeAttribute
     *          the {@link NodeAttribute} to use for allocation targets and load and allocated container comparison
     */
    public GreedyGroupServicePriorityManager(NodeAttribute nodeAttribute)
    {
        super(nodeAttribute);
        LOGGER.info("Using greedy group priority policy.");
    }

    @Override
    public void beginIteration(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports, LoadPercentages loadPercentages)
    {
        super.beginIteration(services, resourceReports, loadPercentages);
        predictServiceContainerDemands();
        
        Map<Integer, Set<ServiceIdentifier<?>>> servicePriorityGroups = new HashMap<>();
        getServicePriorityStatuses().forEach((service, status) ->
        {
            servicePriorityGroups.computeIfAbsent(status.getPriority(), k -> new HashSet<>()).add(service);
        });
        
        LOGGER.debug("beginIteration: servicePriorityGroups: {}", servicePriorityGroups);
        
        List<Integer> descendingPriorityGroupValues = new LinkedList<>(servicePriorityGroups.keySet());
        Collections.sort(descendingPriorityGroupValues, Collections.reverseOrder());
        
        
        int remainingCapacity = (int)Math.floor(getRegionCapacity());
        
        // set target for each service to minimum allocation of 1 container
        for (ServiceIdentifier<?> service : services)
        {
            getServicePriorityStatus(service).setPriorityTarget(1);

            LOGGER.debug("service: {}, getPriorityTarget: {}", service, getServicePriorityStatus(service).getPriorityTarget());
            remainingCapacity -= 1;
        }
        
        // add to target values for each service in descending order of priority
        for (Integer priorityGroupValue : descendingPriorityGroupValues)
        {
            // place services within group in descending order of predicted demand
            Set<ServiceIdentifier<?>> groupServices = servicePriorityGroups.get(priorityGroupValue);
            List<ServiceIdentifier<?>> descendingPredictedDemandGroupServices = new LinkedList<>(groupServices);
            Collections.sort(descendingPredictedDemandGroupServices, servicePredictedDemandComparator);
            
            double predictedGroupDemand = 0.0;
            for (ServiceIdentifier<?> groupService : descendingPredictedDemandGroupServices)
            {
                predictedGroupDemand += getServicePriorityStatus(groupService).getPredictedDemand();
            }
            
            // This value ensures that if a high priority service loses a container to low priority service
            // due to the high priority service having demand / allocation below the overload threshold
            // that the deallocation won't cause demand / allocation to jump above the threshold and
            // trigger an immediate reallocation for the high priority service.
            final double maxTargetIncreaseForPredictedGroupDemand = predictedGroupDemand / TARGET_LOAD_PERCENTAGE + 1;
            
            // Determine the proportion of remaining capacity to allow this group to take.
            // Currently set to 1 for greedy allocation. If lower than 1
            // (for example, [sum of group service priorities / sum of all service priorities]), some containers
            // may be forced to drop down to lower priority groups to limit greediness
            final double maxGroupAllocationProportion = 1;
//          final double maxGroupAllocationProportion = getPriorityProportion(groupServices, remaining service groups yet to be assigned a target);
            final int groupTargetIncrease = (int)Math.min(Math.ceil(remainingCapacity * maxGroupAllocationProportion),
                                                 Math.floor(maxTargetIncreaseForPredictedGroupDemand));
            LOGGER.debug("priorityGroupValue: {}, remainingCapacity: {}, maxGroupAllocationProportion: {}, remainingCapacity * maxGroupAllocationProportion: {}",
                    priorityGroupValue, remainingCapacity, maxGroupAllocationProportion, remainingCapacity * maxGroupAllocationProportion);
            LOGGER.debug("priorityGroupValue: {}, groupServices: {}, predictedGroupDemand: {}, groupTargetIncrease: {}",
                    priorityGroupValue, groupServices, predictedGroupDemand, groupTargetIncrease);
            
            
            int remainingGroupCapacity = groupTargetIncrease;
            double remainingPredictedGroupDemand = predictedGroupDemand;
            
            // give each service in this priority group a load based proportion of the group's target
            for (ServiceIdentifier<?> groupService : descendingPredictedDemandGroupServices)
            {
                ServicePriorityStatusWithPredictions serviceStatus = getServicePriorityStatus(groupService);
                double predictedServiceDemand = serviceStatus.getPredictedDemand();
                
                final int targetIncrease;
                
                if (predictedGroupDemand > 0.0)
                {
                    targetIncrease = (int)Math.ceil((predictedServiceDemand / remainingPredictedGroupDemand) * remainingGroupCapacity);
                }
                else
                {
                    targetIncrease = 0;
                }
                
                LOGGER.debug("priorityGroupValue: {}, groupService: {}, predictedServiceDemand: {}, predictedGroupDemand: {}, targetIncrease: {}",
                        priorityGroupValue, groupService, predictedServiceDemand, predictedGroupDemand, targetIncrease);
                
                serviceStatus.addToPriorityTarget(targetIncrease);
                
                // subtract the demand prediction for the current service
                // to keep track of the the remaining predicted demand for the remaining services in the group
                remainingPredictedGroupDemand -= predictedServiceDemand;
                
                // lower predicted demand services in this groups will later be assigned targets
                // based on the new lower remainingGroupCapacity
                remainingGroupCapacity -= targetIncrease;
                
                LOGGER.debug("priorityGroupValue: {}, remainingPredictedGroupDemand: {}, remainingGroupCapacity: {}",
                        priorityGroupValue, remainingPredictedGroupDemand, remainingGroupCapacity);
            }
            
            // lower priority services will later be assigned targets based on the new lower remainingCapacity
            remainingCapacity -= groupTargetIncrease;
        }
        
        LOGGER.debug("beginIteration: {}", getServicesStatusString(services));
    }
    
    
    
    
    private final ServicePredictedDemandComparator servicePredictedDemandComparator =
            new ServicePredictedDemandComparator(this);
    
    private static final class ServicePredictedDemandComparator implements Comparator<ServiceIdentifier<?>>
    {
        private GreedyGroupServicePriorityManager spm;
        
        ServicePredictedDemandComparator(GreedyGroupServicePriorityManager spm)
        {
            this.spm = spm;
        }
        
        @Override
        public int compare(ServiceIdentifier<?> a, ServiceIdentifier<?> b)
        {
            final int comparison = Double.compare(spm.getServicePriorityStatus(b).getPredictedDemand(),
                    spm.getServicePriorityStatus(a).getPredictedDemand());
            
            if (comparison != 0)
            {
                return comparison;
            }
            else
            {
                // if the demands of the services are equal, compare them by String representation
                return a.getIdentifier().toString().compareTo(b.getIdentifier().toString());
            }
        }
    }
    
    
    private void predictServiceContainerDemands()
    {
        LOGGER.debug("predictServiceContainerDemands: getServices {}: ", getServices());
        
        for (ServiceIdentifier<?> service : getServices())
        {
            final double predictedDemand = getServicePriorityStatus(service).getComputeLoad();
            getServicePriorityStatus(service).setPredictedDemand(predictedDemand);
            LOGGER.debug("predictServiceContainerDemands: Predicted demand for service {}: {}", service, predictedDemand);
        }
    }    
    
    
    @Override
    protected ServicePriorityStatusWithPredictions getServicePriorityStatus(ServiceIdentifier<?> service)
    {
        return (ServicePriorityStatusWithPredictions) super.getServicePriorityStatus(service);
    }
    
    @Override
    protected ServicePriorityStatus newServicePriorityStatus()
    {
        return new ServicePriorityStatusWithPredictions();
    }
    
    private static class ServicePriorityStatusWithPredictions extends ServicePriorityStatus
    {
        private double predictedDemand = Double.NaN;
        
        ServicePriorityStatusWithPredictions()
        {
            LOGGER.debug("Constructing new {}", ServicePriorityStatusWithPredictions.class);
        }  
        
        /**
         * Sets the demand prediction for this service.
         * 
         * @param value
         *          the demand prediction for the service
         */
        void setPredictedDemand(double value)
        {
            predictedDemand = value;
        }
        
        /**
         * @return the demand predicted for the service
         */
        double getPredictedDemand()
        {
            return predictedDemand;
        }
    }
}