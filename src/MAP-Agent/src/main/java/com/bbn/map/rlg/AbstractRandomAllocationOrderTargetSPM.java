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
 * randomly for allocation and deallocation in RLG.
 * 
 * @author awald
 *
 */
public abstract class AbstractRandomAllocationOrderTargetSPM extends AbstractAllocationTargetSPM
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRandomAllocationOrderTargetSPM.class);
    
    
    
    /**
     * @param nodeAttribute
     *          the {@link NodeAttribute} to use for allocation targets and load and allocated container comparison
     */
    public AbstractRandomAllocationOrderTargetSPM(NodeAttribute nodeAttribute)
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
        Collections.shuffle(prioritizedServices);
        
        LOGGER.debug("getPriorityServiceAllocationList: {}", getServicesStatusString(prioritizedServices));
        
        return prioritizedServices;
    }

    @Override
    public List<ServiceIdentifier<?>> getPriorityServiceDeallocationList()
    {
        List<ServiceIdentifier<?>> prioritizedServices = new LinkedList<>();
        prioritizedServices.addAll(getServices());
        Collections.shuffle(prioritizedServices);
        
        LOGGER.debug("getPriorityServiceDeallocationList: {}", getServicesStatusString(prioritizedServices));
        
        return prioritizedServices;
    }
}
