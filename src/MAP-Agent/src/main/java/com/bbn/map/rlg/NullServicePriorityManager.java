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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * The {@link NullServicePriorityManager} ignores service priority and is intended to cause RLG to behave 
 * as though there is no {@link PriorityManager} in effect.
 * 
 * If there are available containers in the region, any service can take any of the containers.
 * If there are no containers available in a region, allocation requests will return 0, and all services must wait 
 * for a deallocation to occur for reasons other than priority before making an allocation.
 * 
 * @author awald
 */
public class NullServicePriorityManager implements ServicePriorityManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NullServicePriorityManager.class);
    
    private Set<ServiceIdentifier<?>> services = new HashSet<>();
    private double regionCapacity = Double.NaN;
    private double regionAvailableContainers = Double.NaN;
    private double requestedAvailable = 0;
    private double plannedDeallocation = 0;
    
    
    /**
     * Constructor for no priority policy.
     */
    public NullServicePriorityManager()
    {
        LOGGER.info("Using no priority in RLG.");
    }
    
    @Override
    public void beginIteration(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports, LoadPercentages loadPercentages)
    {    
        this.services.clear();
        int regionCapacity = 0;
        int regionAvailableContainers = 0;
        
        // compute total region capacity
        for (ResourceReport report : resourceReports)
        {            
            regionCapacity += report.getMaximumServiceContainers();
            regionAvailableContainers += report.getAvailableServiceContainers();
        }
        
        this.regionCapacity = regionCapacity;
        this.regionAvailableContainers = regionAvailableContainers;
        this.requestedAvailable = 0.0;
        this.plannedDeallocation = 0.0;

        this.services.addAll(services);
        
        LOGGER.debug("beginIteration: services: {}, regionCapacity: {}, regionAvailableContainers: {}, resourceReports: {}",
                this.services, this.regionCapacity, this.regionAvailableContainers, resourceReports);   
    }

    @Override
    public double requestAllocation(ServiceIdentifier<?> service, double requestAmount)
    {
        double requestAmountRemaining = requestAmount;
        
        LOGGER.debug("requestAllocation: service = '{}', request = {}, regionAvailableContainers = {}, requestedAvailable = {}, plannedDeallocation = {}",
                service, requestAmountRemaining, regionAvailableContainers, requestedAvailable, plannedDeallocation);
        
        // determine the amount requested that is immediately available for allocation
        double requestAmountFulfilled = Math.min(requestAmountRemaining, regionAvailableContainers - requestedAvailable + plannedDeallocation);
        requestAmountRemaining -= requestAmountFulfilled;
        requestedAvailable += requestAmountFulfilled;

        // return the amount of containers immediately available
        LOGGER.debug("requestAllocation: Request amount of {} was fulfilled and {} is remaining (could not be immediately fulfilled).", requestAmountFulfilled, requestAmountRemaining);
        return requestAmountFulfilled;
    }

    @Override
    public double getRemainingPriorityDeallocations(ServiceIdentifier<?> service)
    {
        LOGGER.debug("getRemainingPriorityDeallocations: No service including '{}' can make priority deallocations in {} policy.", service, getClass());
        return 0;
    }

    @Override
    public void notifyDeallocation(ServiceIdentifier<?> service, double amount)
    {
        LOGGER.debug("notifyDeallocation: service: '{}', adding {} to total planned deallocation of {}.", service, amount, plannedDeallocation);
        plannedDeallocation += amount;
    }

    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceAllocationList()
    {
        List<ServiceIdentifier<?>> servicesList = new LinkedList<>(services);
        
        LOGGER.debug("getPriorityServiceAllocationList: {}", servicesList);
        
        return servicesList;
    }

    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceDeallocationList()
    {
        List<ServiceIdentifier<?>> servicesList = new LinkedList<>(services);
        
        LOGGER.debug("getPriorityServiceDeallocationList: {}", servicesList);
        
        return servicesList;
    }
}
