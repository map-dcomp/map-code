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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;



/**
 * Adds additional functionality to the {@link AbstractAllocationTargetSPM} that supplies services ordered
 * by percent progress to target for allocation and deallocation in RLG.
 * 
 * Places services in ascending order of progress towards reaching targets (filling reservations) for allocation
 * and descending order of progress towards reaching targets for deallocation. Therefore, services with low progress
 * towards their targets will be the first to allocate containers and the services with high progress
 * towards their targets or containers above their targets will be first to deallocate containers.
 * 
 * See {@link AbstractAllocationTargetSPM} for additional information.
 * 
 * @author awald
 *
 */
public abstract class AbstractPercentAllocationToTargetSPM extends AbstractAllocationTargetSPM
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPercentAllocationToTargetSPM.class);
    
    
    
    /**
     * @param nodeAttribute
     *          the {@link NodeAttribute} to use for allocation targets and load and allocated container comparison
     */
    public AbstractPercentAllocationToTargetSPM(NodeAttribute nodeAttribute)
    {
        super(nodeAttribute);
    }

    
    
    @Override
    public void beginIteration(Collection<ServiceIdentifier<?>> services, Set<ResourceReport> resourceReports, LoadPercentages loadPercentages)
    {
        super.beginIteration(services, resourceReports, loadPercentages);
    }

    
    
    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceAllocationList()
    {
        List<ServiceIdentifier<?>> prioritizedServices = new LinkedList<>();
        prioritizedServices.addAll(getServices());
        Collections.sort(prioritizedServices, new Comparator<ServiceIdentifier<?>>()
        {          
            @Override
            public int compare(ServiceIdentifier<?> s1, ServiceIdentifier<?> s2)
            {
                ServicePriorityStatus ps1 = getServicePriorityStatuses().get(s1);
                ServicePriorityStatus ps2 = getServicePriorityStatuses().get(s2);
                
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
        List<ServiceIdentifier<?>> prioritizedServices = new LinkedList<>();
        prioritizedServices.addAll(getServices());
        Collections.sort(prioritizedServices, new Comparator<ServiceIdentifier<?>>()
        {        
            @Override
            public int compare(ServiceIdentifier<?> s1, ServiceIdentifier<?> s2)
            {
                ServicePriorityStatus ps1 = getServicePriorityStatuses().get(s1);
                ServicePriorityStatus ps2 = getServicePriorityStatuses().get(s2);
                
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
}
