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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

import java8.util.Lists;

/**
 * Provides priority target numbers of containers (reservations) to services, which are the numbers of
 * containers that each service is entitled to based on relative priority values.
 * 
 * Allows any service to claim unused containers at any time.
 * 
 * Provides ability for a service with a number of containers below its reservation to take containers from
 * a service with a number of containers above its reservation.
 * 
 * Places services in ascending order of progress towards reaching targets (filling reservations) for allocation
 * and descending order of progress towards reaching targets for deallocation. Therefore, services with low progress towards
 * their targets will be the first to allocate containers and the services with high progress towards their targets
 * or containers above their targets will be first to deallocate containers.
 * 
 * @author awald
 *
 */
public class SimpleServicePriorityManager implements ServicePriorityManager
{   
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleServicePriorityManager.class);
    
    private Collection<ServiceIdentifier<?>> services = new HashSet<>();
    private Map<ServiceIdentifier<?>, ServicePriorityStatus> servicePriorityStatuses = new HashMap<>();
    private double regionAvailableContainers = Double.NaN;

    private final NodeAttribute computeLoadAttribute;
    
    
    
    private static class ServicePriorityStatus
    {
        private double priority = 0.0;
        private double priorityTarget = 1.0;
        private double allocatedContainers = 0.0;
        private double computeLoad = 0.0;
        private double allocatedCapacity = 0.0;

        private double requestedAvailable = 0.0;
        private double requested = 0.0;
        private double plannedDeallocation = 0.0;
        
        
        /**
         * Constructs a new {@link SimpleServicePriorityManager}.
         */
        ServicePriorityStatus()
        {
            LOGGER.trace("Constructing new ServicePriorityStatus");
        }

        
        private void initialize(double priority, double priorityTarget, double allocatedContainers, double computeLoad, double allocatedCapacity)
        {
            this.priority = priority;
            this.priorityTarget = priorityTarget;
            this.allocatedContainers = allocatedContainers;
            this.computeLoad = computeLoad;
            this.allocatedCapacity = allocatedCapacity;
            
            this.requested = 0.0;
            this.requestedAvailable = 0.0;
            this.plannedDeallocation = 0.0;
        }
        
        private void addRequested(double request)
        {
            requested += request;
        }
        
        private void addRequestedAvailable(double request)
        {
            requestedAvailable += request;
        }
        
        private void addPlannedDeallocation(double deallocation)
        {
            plannedDeallocation += deallocation;
        }
        
        /**
         * @return the total amount of containers requested
         */
        private double getRequested()
        {
            return requested;
        }
        
        /**
         * @return the total amount of containers requested that were immediately available
         */
        private double getRequestedAvailable()
        {
            return requestedAvailable;
        }
        
        /**
         * @return the amount of containers allocated
         */
        private double getAllocated()
        {
            return allocatedContainers;
        }
        
        /**
         * @return the amount of containers that this service is entitled to
         */
        private double getPriorityTarget()
        {
            return priorityTarget;
        }
        
        /**
         * @return the amount of containers to be deallocated
         */
        private double getPlannedDeallocation()
        {
            return plannedDeallocation;
        }
        
        /**
         * @return the current total compute load of the service in the region
         */
        private double getComputeLoad()
        {
            return computeLoad;
        }
        
        /**
         * @return the current total allocated capacity of the service in the region
         */
        private double getAllocatedCapacity()
        {
            return allocatedCapacity;
        }
        
        /**
         * @return the predicted allocation for this service after immediate allocations are performed
         *          and priority requests are fulfilled in the future
         */
        private double getProjectedAllocation()
        {
            return (getAllocated() + getRequestedAvailable() + getRequested());
        }
        
        private double getAmountAboveTarget()
        {
            return Math.max(0.0, compareAllocationToTarget());
        }
        
        private double compareAllocationToTarget()
        {
            return (getAllocated() - getPriorityTarget());
        }
        
        private double getPercentProgressToTarget()
        {
            return (getAllocated() / getPriorityTarget());
        }
        
        private double getPercentLoaded()
        {
            return (getComputeLoad() / getAllocatedCapacity());
        }
        
        
        @Override
        public String toString()
        {
            StringBuilder b = new StringBuilder();
            
            b.append("[");
            
            b.append("priority: ").append(priority);
            b.append(", ");
            b.append("priorityTarget: ").append(priorityTarget);
            b.append(", ");
            
            b.append("allocatedContainers: ").append(allocatedContainers);
            b.append(", ");
            b.append("requestedAvailable: ").append(requestedAvailable);
            b.append(", ");
            b.append("requested: ").append(requested);
            b.append(", ");
            b.append("plannedDeallocation: ").append(plannedDeallocation);
            b.append(", ");
            
            b.append("computeLoad: ").append(computeLoad);
            b.append(", ");
            b.append("allocatedCapacity: ").append(allocatedCapacity);
            
            b.append("]");
            
            return b.toString();
        }
    }
    
    
    /**
     * Constructs new {@link SimpleServicePriorityManager}, which can be used throughout an experiment.
     * 
     * @param nodeAttribute
     *          the attribute to use for load and allocated capacity
     */
    public SimpleServicePriorityManager(NodeAttribute nodeAttribute)
    {
        computeLoadAttribute = nodeAttribute;
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
    public void beginIteration(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports, LoadPercentages loadPercentages)
    {
        this.services = services;
        LOGGER.debug("beginIteration: services: {}, resourceReports: {}", services, resourceReports);
        computeInitialServicePriorityStatuses(this.services, resourceReports);
    }

    private void computeInitialServicePriorityStatuses(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports)
    {
        Map<ServiceIdentifier<?>, Double> servicePriorities = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> servicePriorityTargets = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> serviceAllocatedContainers = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> serviceComputeLoads = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> serviceAllocatedCapacities = new HashMap<>();
        
        double regionCapacity = 0.0;
        double regionAvailableContainers = 0.0;
        
        // compute total region capacity
        for (ResourceReport report : resourceReports)
        {            
            regionCapacity += report.getMaximumServiceContainers();
            regionAvailableContainers += report.getAvailableServiceContainers();
            
            // get reports for all services in region
            for (ContainerResourceReport containerReport : report.getContainerReports().values())
            {
                ServiceIdentifier<?> service = containerReport.getService();
                
                serviceAllocatedContainers.merge(service, 1.0, Double::sum);
                
                RlgUtils.getConfiguredLoadInput(containerReport).forEach((sourceNode, load) ->
                {
                    serviceComputeLoads.merge(service, load.getOrDefault(computeLoadAttribute, 0.0), Double::sum);
                });
                
                serviceAllocatedCapacities.merge(service, containerReport.getComputeCapacity()
                        .getOrDefault(computeLoadAttribute, 0.0), Double::sum);
            }
        }
        
        
        // find total priority in region
        double sum = 0.0;
        for (ServiceIdentifier<?> service : services)
        {
            double priority = ((double) AppMgrUtils.getApplicationSpecification(service).getPriority());
            servicePriorities.put(service, priority);
            sum += priority;
        }
        final double totalPriority = sum;
        
        // assign amount of priority target containers to each service according to its priority
        for (Map.Entry<ServiceIdentifier<?>, Double> entry : servicePriorities.entrySet())
        {
            final double capacity = regionCapacity;
            final double priorityProportion = entry.getValue() / totalPriority;
            servicePriorityTargets.put(entry.getKey(), Math.max(1.0, capacity * priorityProportion));
        }
        
        this.regionAvailableContainers = regionAvailableContainers;
        
        // add new priority objects
        for (ServiceIdentifier<?> service : services)
        {
            servicePriorityStatuses.computeIfAbsent(service, k -> new ServicePriorityStatus())
                    .initialize(servicePriorities.getOrDefault(service, 0.0),
                                servicePriorityTargets.getOrDefault(service, 0.0),
                                serviceAllocatedContainers.getOrDefault(service, 0.0),
                                serviceComputeLoads.getOrDefault(service, 0.0),
                                serviceAllocatedCapacities.getOrDefault(service, 0.0));
        }
        
        // remove any unused ServicePriorityStatus objects from servicePriorityStatuses 
        for (Iterator<ServiceIdentifier<?>> iter = servicePriorityStatuses.keySet().iterator(); iter.hasNext();)
        {
            if (!services.contains(iter.next()))
            {
                iter.remove();
            }
        }
        
        LOGGER.debug("regionAvailableContainers: {}", this.regionAvailableContainers);
        LOGGER.debug("computeInitialServicePriorityStatuses: {}", getServicesStatusString(services));
    }
    
    
    @Override
    public double requestAllocation(ServiceIdentifier<?> service, final double requestAmount)
    {
        ServicePriorityStatus ps = servicePriorityStatuses.get(service);
        
        // if the service is not being managed by the SimpleServicePriorityManager, do not prevent the full requestAmount from being allocated
        if (ps == null)
        {
            LOGGER.warn("requestAllocation: Service '{}' made a request but is not being managed by the priority manager. Returning full request amount of {}.", service, requestAmount);
            return requestAmount;
        }
        
        double totalRequestedAvailableContainers = getTotalRequestedAvailableContainers();
        double projectedAllocation = ps.getProjectedAllocation();
        double priorityTarget = ps.getPriorityTarget();
        
        LOGGER.debug("requestAllocation: service = '{}', request = {}, getTotalRequestedAvailableContainers() = {}, regionAvailableContainers = {}, getProjectedAllocation() = {}, getPriorityTarget() = {}",
                service, requestAmount, totalRequestedAvailableContainers, regionAvailableContainers, projectedAllocation, priorityTarget);
        
        double requestAvailableFulfilled = 0;
        double requestAmountRemaining = requestAmount;
        
        
        
        // determine the amount requested that is immediately available for allocation
        final double amountAvailable = Math.max(0.0, regionAvailableContainers - totalRequestedAvailableContainers);
        requestAvailableFulfilled = Math.min(requestAmountRemaining, amountAvailable);
        requestAmountRemaining -= requestAvailableFulfilled;
        ps.addRequestedAvailable(requestAvailableFulfilled);
        
        LOGGER.debug("requestAllocation: Request amount of {} for service '{}' could be immediately fulfilled. getTotalRequestedAvailableContainers() = {}, regionAvailableContainers = {}",
                requestAvailableFulfilled, service, totalRequestedAvailableContainers, regionAvailableContainers);
        
        
        // request the amount of additional containers to which the service is entitled that are not immediately available 
        final double amountToTarget = Math.max(0.0, priorityTarget - projectedAllocation);
        int requested = (int) Math.floor(Math.min(requestAmountRemaining, amountToTarget));
        requestAmountRemaining -= requested;
        ps.addRequested(requested);
        
        LOGGER.debug("requestAllocation: Request amount of {} for service '{}' is being saved for a priority deallocation of another service. getProjectedAllocation() = {}, getPriorityTarget() = {}",
                requested, service, projectedAllocation, priorityTarget);

        
        // return the amount of containers immediately available
        LOGGER.debug("requestAllocation: Request amount of {} is remaining (could not be immediately fulfilled or used to request a deallocation).", requestAmountRemaining);
        return requestAvailableFulfilled;
    }
    

    

    @Override
    public double getRemainingPriorityDeallocations(ServiceIdentifier<?> service)
    {
        ServicePriorityStatus ps = servicePriorityStatuses.get(service);
        
        if (ps == null)
        {
            LOGGER.warn("getRemainingPriorityDeallocations: Service '{}' is not being manager by the priority manager.", service);
            return 0.0;
        }
        
        double totalRequestedContainers = getTotalRequestedContainers();
        double totalPlannedDeallocationContainers = getTotalPlannedDeallocationContainers();
        double amountAboveTarget = ps.getAmountAboveTarget();
        double plannedDeallocation = ps.getPlannedDeallocation();
        
        double remainingDeallocation = Math.max(0, Math.min(totalRequestedContainers - totalPlannedDeallocationContainers,
                amountAboveTarget - plannedDeallocation));
        
        LOGGER.debug("getRemainingPriorityDeallocations: For service '{}', remaining deallocation is {}: getTotalRequestedContainers() = {}, getTotalPlannedDeallocationContainers() = {}, getAmountAboveTarget() = {}, getPlannedDeallocation = {}",
                service, remainingDeallocation, totalRequestedContainers, totalPlannedDeallocationContainers, amountAboveTarget, plannedDeallocation);
        
        return remainingDeallocation;
    }
    
    private double getTotalRequestedContainers()
    {
        double requested = 0.0;
        
        for (ServicePriorityStatus ps : servicePriorityStatuses.values())
        {
            requested += ps.getRequested();
        }
        
        return requested;
    }
    
    private double getTotalRequestedAvailableContainers()
    {
        double requested = 0.0;
        
        for (ServicePriorityStatus ps : servicePriorityStatuses.values())
        {
            requested += ps.getRequestedAvailable();
        }
        
        return requested;
    }
    
    private double getTotalPlannedDeallocationContainers()
    {
        double deallocation = 0.0;
        
        for (ServicePriorityStatus ps : servicePriorityStatuses.values())
        {
            deallocation += ps.getPlannedDeallocation();
        }
        
        return deallocation;
    }

    @Override
    public void notifyDeallocation(ServiceIdentifier<?> service, double amount)
    {        
        ServicePriorityStatus ps = servicePriorityStatuses.computeIfAbsent(service, k -> new ServicePriorityStatus());
        
        if (ps != null)
        {
            LOGGER.debug("notifyDeallocation: For service '{}', adding {} to current planned deallocation of {}.", service, amount, ps.getPlannedDeallocation());
            ps.addPlannedDeallocation(amount);
        }
        else
        {
            LOGGER.warn("notifyDeallocation: {} containers for service {} are being deallocated, but the service is not being managed by the priority manager.");
        }
    }

    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceAllocationList()
    {
        List<ServiceIdentifier<?>> prioritizedServices = new LinkedList<>(getServices());
        Collections.sort(prioritizedServices, new Comparator<ServiceIdentifier<?>>()
        {          
            @Override
            public int compare(ServiceIdentifier<?> s1, ServiceIdentifier<?> s2)
            {
                ServicePriorityStatus ps1 = servicePriorityStatuses.get(s1);
                ServicePriorityStatus ps2 = servicePriorityStatuses.get(s2);
                
                // 1. prioritize the service with the lower amount of progress to the target
                int relativeProgressToTarget = Double.compare(ps1.getPercentProgressToTarget(), ps2.getPercentProgressToTarget());
                
                if (relativeProgressToTarget != 0)
                {
                    return relativeProgressToTarget;
                }
                
                // 2. prioritize the service with the higher regional percent load
                int relativePercentLoaded = Double.compare(ps2.getPercentLoaded(), ps1.getPercentLoaded());
                
                if (relativePercentLoaded != 0)
                {
                    return relativePercentLoaded;
                }
                
                // compare the String representations of the services as a tie breaker
                return ps1.toString().compareTo(ps2.toString());
            }
        });
        
        LOGGER.debug("getPriorityServiceAllocationList: {}", getServicesStatusString(prioritizedServices));
        
        return prioritizedServices;
    }

    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceDeallocationList()
    {
        List<ServiceIdentifier<?>> prioritizedServices = new LinkedList<>(getServices());
        Collections.sort(prioritizedServices, new Comparator<ServiceIdentifier<?>>()
        {        
            @Override
            public int compare(ServiceIdentifier<?> s1, ServiceIdentifier<?> s2)
            {
                ServicePriorityStatus ps1 = servicePriorityStatuses.get(s1);
                ServicePriorityStatus ps2 = servicePriorityStatuses.get(s2);
                
                // 1. prioritize the service with the higher amount of progress to the target
                int relativeProgressToTarget = Double.compare(ps2.getPercentProgressToTarget(), ps1.getPercentProgressToTarget());
                
                if (relativeProgressToTarget != 0)
                {
                    return relativeProgressToTarget;
                }
                
                // 2. prioritize the service with the lower regional percent load
                int relativePercentLoaded = Double.compare(ps1.getPercentLoaded(), ps2.getPercentLoaded());
                
                if (relativePercentLoaded != 0)
                {
                    return relativePercentLoaded;
                }
                
                // compare the String representations of the services as a tie breaker
                return ps1.toString().compareTo(ps2.toString());
            }
        });
        
        LOGGER.debug("getPriorityServiceDeallocationList: {}", getServicesStatusString(prioritizedServices));
        
        return prioritizedServices;
    }
    
    
    
    private String getServicesStatusString(Collection<ServiceIdentifier<?>> services)
    {
        return getServicesStatusString(Lists.copyOf(services));
    }
    
    private String getServicesStatusString(List<ServiceIdentifier<?>> services)
    {
        StringBuilder b = new StringBuilder();
        
        for (int s = 0; s < services.size(); s++)
        {
            ServiceIdentifier<?> service = services.get(s);
            
            b.append(s > 0 ? ", " : "");
            b.append(service.toString());
            b.append(" : ");
            b.append(servicePriorityStatuses.get(service));
        }
        
        return b.toString();
    }
    
    private Collection<ServiceIdentifier<?>> getServices()
    {
        return services;
    }

}
