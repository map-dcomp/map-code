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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
 * Provides a partial implementation of {@link ServicePriorityManager} that provides allocation targets
 * and {@link ServicePriorityStatus} along with corresponding implementations of {@code requestAllocation},
 * {@code getRemainingPriorityDeallocations}, {@code notifyDeallocation}, and helper methods.
 * 
 * Allows any service to claim unused containers at any time.
 * 
 * Provides ability for a service with a number of containers below its target to take containers from
 * a service with a number of containers above its target.
 * 
 * @author awald
 * 
 */
public abstract class AbstractAllocationTargetSPM implements ServicePriorityManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAllocationTargetSPM.class);
    
    private Map<ServiceIdentifier<?>, ServicePriorityStatus> servicePriorityStatuses = new HashMap<>();
    private double regionAvailableContainers = Double.NaN;
    private double regionCapacity = Double.NaN;

    private final NodeAttribute computeLoadAttribute;
    
    
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
        LOGGER.debug("beginIteration: services: {}, resourceReports: {}", services, resourceReports);        
        
        Map<ServiceIdentifier<?>, Integer> servicePriorities = new HashMap<>();
        Map<ServiceIdentifier<?>, Integer> serviceAllocatedContainers = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> serviceAllocatedCapacities = new HashMap<>();
        Map<ServiceIdentifier<?>, Double> serviceComputeLoads = new HashMap<>();
        
        double regionCapacity = 0.0;
        double regionAvailableContainers = 0.0;
        
        // obtain service priorities
        for (ServiceIdentifier<?> service : services)
        {
            int priority = AppMgrUtils.getApplicationSpecification(service).getPriority();
            servicePriorities.put(service, priority);
        }
        
        // compute total region capacity
        for (ResourceReport report : resourceReports)
        {            
            regionCapacity += report.getMaximumServiceContainers();
            regionAvailableContainers += report.getAvailableServiceContainers();
            
            // get reports for all services in region
            for (ContainerResourceReport containerReport : report.getContainerReports().values())
            {
                ServiceIdentifier<?> service = containerReport.getService();
                
                serviceAllocatedContainers.merge(service, 1, Integer::sum);
                
                RlgUtils.getConfiguredLoadInput(containerReport).forEach((sourceNode, load) ->
                {
                    serviceComputeLoads.merge(service, load.getOrDefault(computeLoadAttribute, 0.0), Double::sum);
                });
                
                serviceAllocatedCapacities.merge(service, containerReport.getComputeCapacity()
                        .getOrDefault(computeLoadAttribute, 0.0), Double::sum);
            }
        }
        
        this.regionAvailableContainers = regionAvailableContainers;
        this.regionCapacity = regionCapacity;
        
        // add new priority objects
        for (ServiceIdentifier<?> service : services)
        {
            servicePriorityStatuses.computeIfAbsent(service, k -> newServicePriorityStatus())
                    .initialize(servicePriorities.getOrDefault(service, 0),
                                0.0,
                                serviceAllocatedContainers.getOrDefault(service, 0),
                                serviceAllocatedCapacities.getOrDefault(service, 0.0),
                                serviceComputeLoads.getOrDefault(service, 0.0));
        }
        
        // remove any unused ServicePriorityStatus objects from servicePriorityStatuses 
        for (Iterator<ServiceIdentifier<?>> iter = servicePriorityStatuses.keySet().iterator(); iter.hasNext();)
        {
            if (!services.contains(iter.next()))
            {
                iter.remove();
            }
        }
        
        LOGGER.debug("regionCapacity: {}, regionAvailableContainers: {}", this.regionCapacity, this.regionAvailableContainers);

    }   
    

    @Override
    public abstract List<ServiceIdentifier<?>> getPriorityServiceAllocationList();

    @Override
    public abstract List<ServiceIdentifier<?>> getPriorityServiceDeallocationList();
    
    
    /**
     * Constructs and return a new service priority status.
     * 
     * @return a new {@link ServicePriorityStatus} object
     */
    protected ServicePriorityStatus newServicePriorityStatus()
    {
        return new ServicePriorityStatus();
    }
    
    /**
     * Object for tracking the current state of a service for priority functionality.
     * 
     * @author awald
     *
     */
    protected static class ServicePriorityStatus
    {
        private int priority = 0;
        private double priorityTarget = 1.0;
        private double allocatedContainers = 0.0;
        private double allocatedCapacity = 0.0;
        private double computeLoad = 0.0;

        private double requestedAvailable = 0.0;
        private double requested = 0.0;
        private double plannedDeallocation = 0.0;
        

        ServicePriorityStatus()
        {
            LOGGER.debug("Constructing new {}", ServicePriorityStatus.class);
        }

        
        void initialize(int priority, double priorityTarget, double allocatedContainers,
                double allocatedCapacity, double computeLoad)
        {
            this.priority = priority;
            this.priorityTarget = priorityTarget;
            this.allocatedContainers = allocatedContainers;
            this.allocatedCapacity = allocatedCapacity;
            this.computeLoad = computeLoad;
            
            this.requested = 0.0;
            this.requestedAvailable = 0.0;
            this.plannedDeallocation = 0.0;
        }
        
        void addRequested(double request)
        {
            requested += request;
        }
        
        void addRequestedAvailable(double request)
        {
            requestedAvailable += request;
        }
        
        void addPlannedDeallocation(double deallocation)
        {
            plannedDeallocation += deallocation;
        }
        
        /**
         * @return the total amount of containers requested
         */
        double getRequested()
        {
            return requested;
        }
        
        /**
         * @return the total amount of containers requested that were immediately available
         */
        double getRequestedAvailable()
        {
            return requestedAvailable;
        }
        
        /**
         * @return the amount of containers allocated
         */
        double getAllocatedContainers()
        {
            return allocatedContainers;
        }
        
        /**
         * @return the current total allocated capacity of the service in the region
         */
        double getAllocatedCapacity()
        {
            return allocatedCapacity;
        }
        
        /**
         * @return the priority of the service
         */
        int getPriority()
        {
            return priority;
        }
        
        /**
         * @return the amount of containers that this service is entitled to
         */
        double getPriorityTarget()
        {
            return priorityTarget;
        }
        
        /**
         * Sets the priority target for the corresponding service.
         * 
         * @param target
         *          the new value for the target
         */
        void setPriorityTarget(double target)
        {
            priorityTarget = target;
        }
        
        /**
         * Increases the priority target by the given amount.
         * 
         * @param delta
         *          the amount to add to the target
         * @return the new target value
         */
        double addToPriorityTarget(double delta)
        {
            priorityTarget += delta;
            return priorityTarget;
        }
        
        /**
         * @return the amount of containers to be deallocated
         */
        double getPlannedDeallocation()
        {
            return plannedDeallocation;
        }
        
        /**
         * @return the current total compute load of the service in the region
         */
        double getComputeLoad()
        {
            return computeLoad;
        }
    
        /**
         * Checks if the request can be applied without causing the priority target to be exceeded.
         * 
         * @param request
         *          the amount of containers being requested
         * @return true if the request can be applied without causing the priority target to be exceeded and false otherwise
         */
        boolean checkRequest(double request)
        {
            return (getProjectedAllocation() + request <= getPriorityTarget());
        }
        
        double getRemainingPriorityAllocations()
        {
            return (getPriorityTarget() - getProjectedAllocation());
        }
        
        /**
         * @return the predicted allocation for this service after immediate allocations are performed
         *          and priority requests are fulfilled in the future
         */
        double getProjectedAllocation()
        {
            return (getAllocatedContainers() + getRequestedAvailable() + getRequested());
        }
        
        double getAmountBelowTarget()
        {
            return Math.max(0.0, -compareAllocationToTarget());
        }
        
        double getAmountAboveTarget()
        {
            return Math.max(0.0, compareAllocationToTarget());
        }
        
        double compareAllocationToTarget()
        {
            return (getAllocatedContainers() - getPriorityTarget());
        }
        
        /**
         * @return the percent of the way to the target of a service
         */
        double getPercentProgressToTarget()
        {
            return (getAllocatedContainers() / getPriorityTarget());
        }
        
        /**
         * @return the percent of load of a service
         */
        double getPercentLoaded()
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
    public AbstractAllocationTargetSPM(NodeAttribute nodeAttribute)
    {
        computeLoadAttribute = nodeAttribute;
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
                service, requestAmount, totalRequestedAvailableContainers, getRegionAvailableContainers(), projectedAllocation, priorityTarget);
        
        double requestAvailableFulfilled = 0;
        double requestAmountRemaining = requestAmount;
        
        
        
        // determine the amount requested that is immediately available for allocation
        final double amountAvailable = Math.max(0.0, getRegionAvailableContainers() - totalRequestedAvailableContainers);
        requestAvailableFulfilled = Math.min(requestAmountRemaining, amountAvailable);
        requestAmountRemaining -= requestAvailableFulfilled;
        ps.addRequestedAvailable(requestAvailableFulfilled);
        
        LOGGER.debug("requestAllocation: Request amount of {} for service '{}' could be immediately fulfilled. getTotalRequestedAvailableContainers() = {}, regionAvailableContainers = {}",
                requestAvailableFulfilled, service, totalRequestedAvailableContainers, getRegionAvailableContainers());
        
        
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
        ServicePriorityStatus ps = servicePriorityStatuses.computeIfAbsent(service, k -> newServicePriorityStatus());
        
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
    
    
    /**
     * Converts the specified services to status strings.
     * 
     * @param services
     *              the collections of services to obtain the status string representation for
     * @return a string representation of the given services and their priority statuses
     */
    protected String getServicesStatusString(Collection<ServiceIdentifier<?>> services)
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
    
    /**
     * @return the collection of services being managed for priority
     */
    protected Collection<ServiceIdentifier<?>> getServices()
    {
        return servicePriorityStatuses.keySet();
    }
    
    /**
     * @return a map of services to their priority statuses
     */
    protected Map<ServiceIdentifier<?>, ServicePriorityStatus> getServicePriorityStatuses()
    {
        return servicePriorityStatuses;
    }
    
    /**
     * Obtains the priority status for a service.
     * 
     * @param service
     *          the service for which to obtain its priority status
     * @return a priority status object for the given service
     */
    protected ServicePriorityStatus getServicePriorityStatus(ServiceIdentifier<?> service)
    {
        return getServicePriorityStatuses().get(service);
    }
    
    /**
     * @return the amount of containers available for immediate allocation in the region
     */
    protected double getRegionAvailableContainers()
    {
        return regionAvailableContainers;
    }
    
    /**
     * Sets the amount of available containers in the region.
     * 
     * @param amount
     *          the amount of available containers in the region
     */
    protected void setRegionAvailableContainers(double amount)
    {
        regionAvailableContainers = amount;
    }
    
    /**
     * @return the {@link NodeAttribute} to use for allocation targets and load and allocation comparison
     */
    protected NodeAttribute getComputeLoadAttribute()
    {
        return computeLoadAttribute;
    }
    
    /**
     * @return the capacity of the region as determined from the {@link ResourceReport}s
     */
    protected double getRegionCapacity()
    {
        return regionCapacity;
    }
    
    /**
     * Finds the ratio of a single service's priority to the total priority of a group of services.
     * Divides the priority of the single service by the total priority of the group of services.
     * 
     * @param serviceA
     *          a single service
     * @param servicesB
     *          a group of services
     * @return the ratio of priority of {@code serviceA} to total priority of {@code servicesB}
     */
    protected double getPriorityProportion(ServiceIdentifier<?> serviceA,
            Collection<ServiceIdentifier<?>> servicesB)
    {
        return (getServicePriorityStatus(serviceA).getPriority() / getTotalPriority(servicesB));
    }
    
    /**
     * Finds the ratio of total priorities of two groups of services. Dives the total priority of the first
     * group by the total priority of the second group.
     * 
     * @param servicesA
     *          the first group of services
     * @param servicesB
     *          the second group of services
     * @return the ratio of total priority of {@code servicesA} to {@code servicesB}
     */
    protected double getPriorityProportion(Collection<ServiceIdentifier<?>> servicesA,
            Collection<ServiceIdentifier<?>> servicesB)
    {
        return (getTotalPriority(servicesA) / getTotalPriority(servicesB));
    }
    
    /**
     * Finds the total priority value for the given services.
     * 
     * @param services
     *          the services for which to find the total priority
     * @return the sum of the priorities of the services
     */
    protected double getTotalPriority(Collection<ServiceIdentifier<?>> services)
    {
        double total = 0.0;
        
        for (ServiceIdentifier<?> service : services)
        {
            total += getServicePriorityStatus(service).getPriority();            
        }
        
        return total;
    }
}
