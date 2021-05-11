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
package com.bbn.map.simulator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;

/**
 * Base class for client load and background traffic load.
 * 
 * @author jschewe
 *
 */
public abstract class BaseNetworkLoad implements Serializable {

    private static final long serialVersionUID = 3L;

    private static final LinkAttribute OLD_DATARATE = new LinkAttribute("DATARATE");

    /**
     * Take a network capacity map and make sure that if the old datarate is
     * there convert it to rx and tx. Don't overwrite rx and tx values if they
     * are present.
     * 
     * @param networkLoad
     *            the value to be migrated
     * @return the new value with {@link LinkAttribute#DATARATE_RX} and
     *         {@link LinkAttribute#DATARATE_TX} and without DATARATE. May be
     *         the same as the original value.
     */
    private static ImmutableMap<LinkAttribute, Double> migrateDatarate(
            final ImmutableMap<LinkAttribute, Double> networkLoad) {
        if (networkLoad.containsKey(OLD_DATARATE)) {
            final double datarate = networkLoad.get(OLD_DATARATE);
            final Map<LinkAttribute, Double> newMap = new HashMap<>(networkLoad);

            if (!newMap.containsKey(LinkAttribute.DATARATE_RX)) {
                newMap.put(LinkAttribute.DATARATE_RX, datarate);
            }

            if (!newMap.containsKey(LinkAttribute.DATARATE_TX)) {
                newMap.put(LinkAttribute.DATARATE_TX, datarate);
            }

            newMap.remove(OLD_DATARATE);

            return ImmutableMap.copyOf(newMap);
        } else {
            return networkLoad;
        }
    }
    
    /**
     * Used by JSON deserialization for construction.
     */
    public BaseNetworkLoad() {        
    }

    /**
     * @param startTime
     *            see {@link #getStartTime()}
     * @param networkDuration
     *            see {@link #getNetworkDuration()}
     * @param service
     *            see {@link #getService()}
     * @param networkLoad
     *            see {@link #getNetworkLoad()}
     */
    public BaseNetworkLoad(final long startTime,
            final long networkDuration,
            final ApplicationCoordinates service,
            final ImmutableMap<LinkAttribute, Double> networkLoad) {
        this.startTime = startTime;
        this.networkDuration = networkDuration;
        this.service = service;
        this.networkLoad = migrateDatarate(networkLoad);
    }

    private long startTime;

    /**
     * 
     * @param v
     *            see {@link #getStartTime()}
     */
    public void setStartTime(final long v) {
        startTime = v;
    }

    /**
     * @return when the request starts
     */
    public long getStartTime() {
        return startTime;
    }

    private long networkDuration;

    /**
     * 
     * @param v
     *            see {@link #getNetworkDuration()}
     */
    public void setNetworkDuration(final long v) {
        networkDuration = v;
    }

    /**
     * This duration is the minimum duration for the request to effect the
     * network. Depending on how busy the network is the actual network
     * processing time may be longer.
     * 
     * @return how long the request is active for
     */
    public long getNetworkDuration() {
        return networkDuration;
    }

    private ApplicationCoordinates service;

    /**
     * 
     * @param v
     *            see {@link #getService()}
     */
    public void setService(final ApplicationCoordinates v) {
        service = v;
    }

    /**
     * @return which service is being used
     */
    public ApplicationCoordinates getService() {
        return service;
    }

    private ImmutableMap<LinkAttribute, Double> networkLoad;

    /**
     * 
     * @param v
     *            see {@link #getNetworkLoad()}
     */
    public void setNetworkLoad(final ImmutableMap<LinkAttribute, Double> v) {
        networkLoad = v;
    }

    /**
     * 
     * @return how much load there is on the network
     */
    public ImmutableMap<LinkAttribute, Double> getNetworkLoad() {
        return networkLoad;
    }

    private transient ImmutableMap<LinkAttribute, Double> netLoadAsAttribute = null;

    /**
     * This is cached so subsequent calls are fast.
     * 
     * @return the network load as {@link LinkAttribute}
     * @see #getNetworkLoad()
     */
    @JsonIgnore
    public ImmutableMap<LinkAttribute, Double> getNetworkLoadAsAttribute() {
        if (null == netLoadAsAttribute) {
            final ImmutableMap.Builder<LinkAttribute, Double> networkLoad = ImmutableMap.builder();

            getNetworkLoad().forEach((attr, value) -> {
                networkLoad.put(attr, value);
            });

            netLoadAsAttribute = networkLoad.build();
        }
        return netLoadAsAttribute;
    }

    private transient ImmutableMap<LinkAttribute, Double> netLoadAsAttributeFlipped = null;

    /**
     * This is cached so subsequent calls are fast.
     * 
     * @return the network load as {@link LinkAttribute} with TX and RX flipped
     * @see #getNetworkLoad()
     */
    @JsonIgnore
    public ImmutableMap<LinkAttribute, Double> getNetworkLoadAsAttributeFlipped() {
        if (null == netLoadAsAttributeFlipped) {
            final ImmutableMap.Builder<LinkAttribute, Double> networkLoad = ImmutableMap.builder();

            getNetworkLoad().forEach((attr, value) -> {
                final LinkAttribute toInsert;
                if (LinkAttribute.DATARATE_RX.equals(attr)) {
                    toInsert = LinkAttribute.DATARATE_TX;
                } else if (LinkAttribute.DATARATE_TX.equals(attr)) {
                    toInsert = LinkAttribute.DATARATE_RX;
                } else {
                    toInsert = attr;
                }
                networkLoad.put(toInsert, value);
            });

            netLoadAsAttributeFlipped = networkLoad.build();
        }
        return netLoadAsAttributeFlipped;
    }

}
