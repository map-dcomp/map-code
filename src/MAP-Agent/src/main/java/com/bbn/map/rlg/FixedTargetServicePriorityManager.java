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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;



/**
 * Provides priority target numbers of containers (reservations) to services, which are the numbers of containers
 * that each service is entitled to based on priority proportions. Targets are fixed for a particular region capacity.
 * 
 * See {@link AbstractPercentAllocationToTargetSPM} for additional information.
 * 
 * @author awald
 *
 */
public class FixedTargetServicePriorityManager extends AbstractPercentAllocationToTargetSPM
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedTargetServicePriorityManager.class);
    
    
    
    /**
     * @param nodeAttribute
     *          the {@link NodeAttribute} to use for allocation targets and load and allocated container comparison
     */
    public FixedTargetServicePriorityManager(NodeAttribute nodeAttribute)
    {
        super(nodeAttribute);
        LOGGER.info("Using fixed target priority policy.");
    }
    
    private Set<ServiceIdentifier<?>> getActiveServices(final RlgUtils.LoadPercentages loadPercentages) {
        final double activeServiceLoadPercentageThreshold = AgentConfiguration.getInstance()
                .getRlgFixedTargetActiveServiceLoadPercentageThreshold();

        return loadPercentages.allocatedLoadPercentagePerService.entrySet().stream().filter(e -> e.getValue()
                .getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0D) >= activeServiceLoadPercentageThreshold)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
    
    /**
     * Uses service priorities to compute the amount of regional capacity to which each service in the region is entitled,
     * and gathers other useful information from {@link ResourceReport}s for prioritization.
     * 
     * @param services
     *          the current {@link Collection} of services within the region
     * @param resourceReports
     *          the resource reports, which contain node capacity, load, allocated containers, allocated capacity information
     */
    @Override
    public void beginIteration(final Collection<ServiceIdentifier<?>> services, final Set<ResourceReport> resourceReports, final RlgUtils.LoadPercentages loadPercentages)
    {
        super.beginIteration(services, resourceReports, loadPercentages);
        
        // We need at least one instance of each service in the region
        final int minAllocation = 1;
        
        Map<ServiceIdentifier<?>, Integer> targets = new HashMap<>();
        
        int remainingCapacity = (int)Math.floor(getRegionCapacity());
        LOGGER.debug("remainingCapacity: {}", remainingCapacity);
        
        // 0. Min Allocation for services (1st pass allocation)
        // set target for each service to minimum allocation of 1 container
        for (ServiceIdentifier<?> service : services)
        {
            targets.put(service, minAllocation);
            remainingCapacity -= minAllocation;
        }
        
        LOGGER.debug("0. targets: {}, remainingCapacity: {}", targets, remainingCapacity);
        
        
        // 1. Active Priority service list in descending order of priority
        final List<ServiceIdentifier<?>> activeServices = new ArrayList<>(getActiveServices(loadPercentages));
        Collections.sort(activeServices, new Comparator<ServiceIdentifier<?>>()
        {
            @Override
            public int compare(ServiceIdentifier<?> a, ServiceIdentifier<?> b)
            {
                ServicePriorityStatus statusA = getServicePriorityStatus(a);
                ServicePriorityStatus statusB = getServicePriorityStatus(b);

                // B is first so that we have descending order
                return Integer.compare(statusB.getPriority(), statusA.getPriority());
            }
        });

        // check for services that are considered active but are not in the services list for this priority iteration
        for (Iterator<ServiceIdentifier<?>> iter = activeServices.iterator(); iter.hasNext();) {
            ServiceIdentifier<?> activeService = iter.next();

            if (!services.contains(activeService)) {
                LOGGER.warn("Active service {} with load percentage {}, was not found in the list of services passed "
                        + "into beginIteration for this priority management iteration: {}",
                        activeService, loadPercentages.allocatedLoadPercentagePerService.get(activeService), services);
                iter.remove();
            }
        }


        int totalPriority = 0;
        for (ServiceIdentifier<?> service : activeServices)
        {
            ServicePriorityStatus priorityStatus = getServicePriorityStatus(service);
            totalPriority += priorityStatus.getPriority();
        }

        LOGGER.debug("1. services: {}, activeServices: {}, active totalPriority: {}", services, activeServices, totalPriority);

        
        // 2. Priority Weighting
        Map<ServiceIdentifier<?>, Double> priorityProportions = new HashMap<>();
        
        for (ServiceIdentifier<?> service : activeServices)
        {
            ServicePriorityStatus priorityStatus = getServicePriorityStatus(service);
            final double priorityProportion = priorityStatus.getPriority() * 1.0 / totalPriority;
            priorityProportions.put(service, priorityProportion);
        }
        
        LOGGER.debug("2. priorityProportions: {}", priorityProportions);
        
        
        // 3.  Relative Allocation Slice
        Map<ServiceIdentifier<?>, Double> relativeAllocationSlices = new HashMap<>();
        
        for (ServiceIdentifier<?> service : activeServices)
        {
            final double relativeAllocation = remainingCapacity * priorityProportions.get(service);
            relativeAllocationSlices.put(service, relativeAllocation);
        }
        
        LOGGER.debug("3. allocationSlices: {}", relativeAllocationSlices);
        
        
        // 4. Floor + min allocation (2nd pass allocation)
        Map<ServiceIdentifier<?>, Integer> floorPlusMinAllocations = new HashMap<>();
        
        for (ServiceIdentifier<?> service : activeServices)
        {
            final int floorAllocation = (int)Math.floor(relativeAllocationSlices.get(service));
            targets.merge(service, floorAllocation, Integer::sum);
            remainingCapacity -= floorAllocation;
            
            final int floorPlusMinAllocation = floorAllocation + minAllocation;
            floorPlusMinAllocations.put(service, floorPlusMinAllocation);
        }
        
        LOGGER.debug("4. floorPlusMinAllocations: {}", floorPlusMinAllocations);
        
        // 5. Ideal Rounding
        Map<ServiceIdentifier<?>, Integer> idealRoundingPlusMinAllocations = new HashMap<>();
        
        for (ServiceIdentifier<?> service : activeServices)
        {
            final int idealRoundingPlusMinAllocation = (int)Math.round(relativeAllocationSlices.get(service)) + minAllocation;
            idealRoundingPlusMinAllocations.put(service, idealRoundingPlusMinAllocation);
        }
        
        LOGGER.debug("5. idealRoundings: {}", idealRoundingPlusMinAllocations);
        
        
        // 6. Underallocated amount
        Map<ServiceIdentifier<?>, Integer> underAllocatedAmounts = new HashMap<>();
        
        for (ServiceIdentifier<?> service : activeServices)
        {
            final int idealRoundingPlusMinAllocation = idealRoundingPlusMinAllocations.get(service) - floorPlusMinAllocations.get(service);
            underAllocatedAmounts.put(service, idealRoundingPlusMinAllocation);
        }
        
        LOGGER.debug("6. underAllocatedAmount: {}", underAllocatedAmounts);
        
        
        // 7. in descending order of priority add remaining containers to underallocated groups (3rd pass allocation)        
        for (ServiceIdentifier<?> service : activeServices)
        {
            if (remainingCapacity > 0)
            {
                // if fraction >= 0.5 && < 1.0
                if (underAllocatedAmounts.get(service) > 0)
                {
                    targets.merge(service, 1, Integer::sum);
                    remainingCapacity -= 1;
                }
            }
            else
            {
                break;
            }
        }
        
        LOGGER.debug("7. targets: {}, remainingCapacity: {}", targets, remainingCapacity);
        
        
        if (remainingCapacity > 0)
        {
            // 8. get fractions > 0.0 && < .5
            List<ServiceIdentifier<?>> descendingFractionServices = new LinkedList<>();
            for (ServiceIdentifier<?> service : activeServices)
            {
                // if idealRoundingPlusMinAllocations.get(service) == floorPlusMinAllocations.get(service) and
                // the fractional part of the service's allocation slice is > 0
                if (underAllocatedAmounts.get(service) == 0 && relativeAllocationSlices.get(service) >
                        Math.floor(relativeAllocationSlices.get(service)))
                {
                    descendingFractionServices.add(service);
                }
            }
        
            LOGGER.debug("8. descendingFractionServices: {}", descendingFractionServices);
        
        
            // 9. Allocate in descending order of residual fraction until residual containers is 0 (4th pass allocation)
            // sort services in descending order of fractional part
            Collections.sort(descendingFractionServices, new Comparator<ServiceIdentifier<?>>()
            {
                @Override
                public int compare(ServiceIdentifier<?> a, ServiceIdentifier<?> b)
                {
                    double allocationFractionA = relativeAllocationSlices.get(a);
                    allocationFractionA -= Math.floor(allocationFractionA);
                    
                    double allocationFractionB = relativeAllocationSlices.get(b);
                    allocationFractionB -= Math.floor(allocationFractionB);
                    
                    return Double.compare(allocationFractionB, allocationFractionA);
                }
            });
            
            LOGGER.debug("9. after sort descendingFractionServices: {}", descendingFractionServices);
            
            while (remainingCapacity > 0 && !descendingFractionServices.isEmpty())
            {
                targets.merge(descendingFractionServices.remove(0), 1, Integer::sum);
                remainingCapacity -= 1;
            }
            
            LOGGER.debug("9. targets: {}, remainingCapacity: {}, descendingFractionServices: {}", targets,
                    remainingCapacity, descendingFractionServices);
        }
        
        
        // 10. Hand out remaining containers in descending order of priority (5th pass allocation)
        if (remainingCapacity > 0 && !activeServices.isEmpty())
        {
            LOGGER.warn("10. Unexpectedly has remaining capacity of {} at this point. targets: {}", remainingCapacity, targets);
            
            while (remainingCapacity > 0)
            {
                for (ServiceIdentifier<?> service : activeServices)
                {
                    targets.merge(service, 1, Integer::sum);
                    remainingCapacity -= 1;
                    
                    if (remainingCapacity <= 0)
                    {
                        break;
                    }
                }
            }
            
            LOGGER.debug("10. targets: {}, remainingCapacity: {}", targets, remainingCapacity);
        }
        
        
        
        
        // store targets
        getServicePriorityStatuses().forEach((s, status) ->
        {
            status.setPriorityTarget(targets.get(s));
        });
        
        
        LOGGER.debug("computeInitialServicePriorityStatuses: {}", getServicesStatusString(services));
    }
}
