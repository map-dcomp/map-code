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
package com.bbn.map.rlg;import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;


/**
 * Uses the priority assigned to each service to determine when a service within a region
 * is entitled to more allocated capacity upon request and decides if a service is to loose allocated capacity
 * for the purpose of satisfying another service's request.
 * 
 * Some important questions to answer when designing a priority policy are:
 * 1. Does priority proportion matter in addition to order?
 * 2. Are priority targets (service resource reservation amounts) fixed for a given capacity and priority set, 
 *    or do factors that vary for each iteration such as load affect targets? 
 *    This affects how the notion of priority can adapt and be applied to different circumstances.
 *    For example, a high priority service with low load will not be utilizing its maximum possible reservation
 *    of resources, and the lower priority service targets could exploit this fact.
 * 3. 
 *    a. Is allocation/deallocation order among services of different priorities fixed or dependent on factors
 *       such as load, distance to targets, or percent progress to targets?
 *    b. Is the order for services of same priority fixed or varying dependent on factors
 *       such as load, distance to targets, or percent progress to targets? 
 *    These affect the manner in which RLG transitions between current allocation and the latest service targets.
 *    This includes stability. The services that requested containers and have not met their current priority 
 *    targets must be given a chance to allocate on the next iteration before other services have a chance
 *    to allocate. Otherwise, services may repeatedly deallocate and reallocate when some other service
 *    yet to reach its target makes a request. The transition of a resource from one service to another
 *    due to priority will ideally be as smooth as possible.
 * 
 * 
 * @author awald
 * 
 */
public interface ServicePriorityManager
{
    /**
     * Performs initialization for handling priority during an RLG iteration.
     * 
     * @param services
     *          the current {@link Set} of services within the region
     * @param resourceReports
     *          the most recent {@link ResourceReport}s for the region
     * @param loadPercentages load per service and per node
     */
    void beginIteration(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports, LoadPercentages loadPercentages);
    
    /**
     * Makes a request to allocate a certain amount of capacity for a service. 
     * 
     * @param service
     *          the service to which capacity is being allocated
     * @param request
     *          the amount of capacity to be allocated now or to be made available for future allocation
     * @return the amount of capacity to be immediately allocated for the service
     */
    double requestAllocation(ServiceIdentifier<?> service, double request);
    
    /**
     * Obtains the maximum amount of allocated capacity to be deallocated from the given service
     * for the purpose of satisfying priority criteria.
     * 
     * @param service
     *          the service for which a priority deallocation is being considered
     * @return the current maximum amount of capacity that can be deallocated from the 
     *          given service's allocated capacity
     */
    double getRemainingPriorityDeallocations(ServiceIdentifier<?> service);
    
    /**
     * Notifies the {@link ServicePriorityManager} that a container was deallocated for any reason.
     * Calling this method even for non priority related deallocations prevents extraneous allocations from occurring
     * and enables some amount of requested capacity to be considered satisfied whenever a deallocation actually occurs.
     * 
     * @param service
     *          the service from which containers were deallocated
     * @param amount
     *          the amount of capacity deallocated from the service's allocated capacity
     */
    void notifyDeallocation(ServiceIdentifier<?> service, double amount);
    
    /**
     * Decides the order in which services can either either available capacity or request excess containers from other services.
     * Determines the order in which services can request (and possibly perform) an allocation.
     * 
     * @return a sorted {@link List} of services
     */
    List<ServiceIdentifier<?>> getPriorityServiceAllocationList();
    
    
    /**
     * Decides the order in which services perform deallocations and potentially loose excess containers
     * Determines the order in which services should call {@link checkForMaxPriorityDeallocation}
     * and {@link notifyDeallocation}.
     * 
     * @return a sorted {@link List} of services
     */
    List<ServiceIdentifier<?>> getPriorityServiceDeallocationList();
}
