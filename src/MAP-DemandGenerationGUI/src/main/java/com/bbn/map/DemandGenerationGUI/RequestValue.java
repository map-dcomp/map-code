/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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


/**
 * Stores intermediate information about a group of requests while it is being generated.
 * 
 * @author awald
 *
 */
public class RequestValue
{
    private long duration;
    
    private double totalComputeLoad;
    private double networkLoadRx;
    private double networkLoadTx;
    
    private int numClients;
    
    /**
     * 
     * @param duration
     *          the duration of the request in milliseconds
     * @param totalComputeLoad
     *          the total amount of compute load that this group will put on the system
     * @param networkLoadRx
     *          the RX network load for requests in this group
     * @param networkLoadTx
     *          the TX network load for requests in this group
     * @param numClients
     *          the total number of clients in this group of requests
     */
    public RequestValue(long duration, double totalComputeLoad, double networkLoadRx, double networkLoadTx, int numClients)
    {
        this.duration = duration;
        this.totalComputeLoad = totalComputeLoad;
        this.networkLoadRx = networkLoadRx;
        this.networkLoadTx = networkLoadTx;
        this.numClients = numClients;
    }
    
    
    /**
     * @return the duration of the request in milliseconds
     */
    public long getDuration()
    {
        return duration;
    }
    
    /**
     * @return the total amount of compute load that this group will put on the system
     */
    public double getTotalComputeLoad()
    {
        return totalComputeLoad;
    }
    
    /**
     * @return the RX network load for requests in this group
     */
    public double getNetworkLoadRx()
    {
        return networkLoadRx;
    }
    
    /**
     * @return the TX network load for requests in this group
     */
    public double getNetworkLoadTx()
    {
        return networkLoadTx;
    }
    
    /**
     * @return the total number of clients in this group of requests
     */
    public int getNumClients()
    {
        return numClients;
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
        return new RequestValue((a.duration + b.duration) / 2,
                a.totalComputeLoad + b.totalComputeLoad,
                a.networkLoadRx + b.networkLoadRx,
                a.networkLoadTx + b.networkLoadTx,
                a.numClients + b.numClients);
    }
    
    @Override
    public String toString()
    {
        return ("[" + "duration: " + duration + ", totalComputeLoad: " + totalComputeLoad +
                ", networkLoad RX,TX: " + networkLoadRx + "," + networkLoadTx + ", numClients: " + numClients + "]");
    }
}
