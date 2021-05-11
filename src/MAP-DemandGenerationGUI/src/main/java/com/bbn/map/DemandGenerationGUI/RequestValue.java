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
package com.bbn.map.DemandGenerationGUI;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;

/**
 * Stores intermediate information about a group of requests while it is being generated.
 * 
 * @author awald
 *
 */
public class RequestValue
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestValue.class);
    
    
    private long duration;
    
    private int numClients;
    private Map<NodeAttribute, Double> nodeLoad = new HashMap<>();
    private Map<LinkAttribute, Double> networkLoad = new HashMap<>();
//    private double totalComputeLoad;
//    private double networkLoadRx;
//    private double networkLoadTx;
    
    /**
     * 
     * @param duration
     *          the duration of the request in milliseconds
//     * @param totalComputeLoad
//     *          the total amount of compute load that this group will put on the system
//     * @param networkLoadRx
//     *          the RX network load for requests in this group
//     * @param networkLoadTx
//     *          the TX network load for requests in this group    
     * @param numClients
     *          the total number of clients in this group of requests
     * @param nodeLoad
     *          attribute-value pairs for node load
     * @param networkLoad
     *          attribute-value pairs for network load
     */
    public RequestValue(long duration,
//            double totalComputeLoad, double networkLoadRx, double networkLoadTx,
            int numClients,
            Map<NodeAttribute, Double> nodeLoad,
            Map<LinkAttribute, Double> networkLoad)
    {
        this.duration = duration;
//        this.totalComputeLoad = totalComputeLoad;
//        this.networkLoadRx = networkLoadRx;
//        this.networkLoadTx = networkLoadTx;
        this.numClients = numClients;
        this.nodeLoad = nodeLoad;
        this.networkLoad = networkLoad;
        
        LOGGER.debug("Constructing RequestValue: {}", this);
    }
    
    
    /**
     * @return the duration of the request in milliseconds
     */
    public long getDuration()
    {
        return duration;
    }
    
//    /**
//     * @return the total amount of compute load that this group will put on the system
//     */
//    public double getTotalComputeLoad()
//    {
//        return totalComputeLoad;
//    }
//    
//    /**
//     * @return the RX network load for requests in this group
//     */
//    public double getNetworkLoadRx()
//    {
//        return networkLoadRx;
//    }
//    
//    /**
//     * @return the TX network load for requests in this group
//     */
//    public double getNetworkLoadTx()
//    {
//        return networkLoadTx;
//    }
    
    /**
     * @return the total number of clients in this group of requests
     */
    public int getNumClients()
    {
        return numClients;
    }
    
    
    /**
     * @return the node load attribute values for this group of requests
     */
    public Map<NodeAttribute, Double> getNodeLoad()
    {
        return nodeLoad;
    }

    /**
     * @return the network load attribute values for this group of requests
     */
    public Map<LinkAttribute, Double> getNetworkLoad()
    {
        return networkLoad;
    }

    /**
     * Sum the load and number of clients of two {@link RequestValue}s together. The duration of the {@link RequestValue}s is averaged.
     * 
     * @param a
     *          the first {@link RequestValue} to sum
     * @param b
     *          the second {@link RequestValue} to sum
     * @return a new {@link RequestValue} that represents the sum of the given {@link RequestValue}s
     */
    public static RequestValue sum(RequestValue a, RequestValue b)
    {
        long duration = (a.getDuration() + b.getDuration()) / 2;
        int numClients = a.getNumClients() + b.getNumClients();
        
        Map<NodeAttribute, Double> nodeLoad = new HashMap<>();
        
        a.getNodeLoad().forEach((attr, value) ->
        {
            nodeLoad.merge(attr, value, Double::sum);
        });
        
        b.getNodeLoad().forEach((attr, value) ->
        {
            nodeLoad.merge(attr, value, Double::sum);
        });
        
        
        Map<LinkAttribute, Double> networkLoad = new HashMap<>();
        
        a.getNetworkLoad().forEach((attr, value) ->
        {
            networkLoad.merge(attr, value, Double::sum);
        });
        
        b.getNetworkLoad().forEach((attr, value) ->
        {
            networkLoad.merge(attr, value, Double::sum);
        });
        
        
        return new RequestValue(duration, numClients, nodeLoad, networkLoad);
    }
    
    @Override
    public String toString()
    {
        return ("[" + "duration: " + duration + ", nodeLoad: " + nodeLoad +
                ", networkLoad: " + networkLoad + ", numClients: " + numClients + "]");
    }
}
