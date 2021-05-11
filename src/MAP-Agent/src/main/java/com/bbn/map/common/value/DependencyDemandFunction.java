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
package com.bbn.map.common.value;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;

/**
 * Specification of how load on one service creates load on another service.
 * 
 * @author jschewe
 *
 */
public class DependencyDemandFunction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor, all values set to defaults.
     */
    public DependencyDemandFunction() {
    }

    private final Map<NodeAttribute, Double> nodeAttributeMultipliers = new HashMap<>();

    /**
     * The multipliers for each node attribute. These are used by
     * {@link #computeDependencyNodeLoad(Map)} to determine the load on the
     * dependent service.
     * 
     * @return a non-null value
     */
    @Nonnull
    public Map<NodeAttribute, Double> getNodeAttributeMultiplers() {
        return nodeAttributeMultipliers;
    }

    /**
     * 
     * @param v
     *            see {@link #getNodeAttributeMultiplers()}
     */
    public void setNodeAttributeMultiplier(final Map<NodeAttribute, Double> v) {
        nodeAttributeMultipliers.clear();
        if (null != v) {
            nodeAttributeMultipliers.putAll(v);
        }
    }

    /**
     * Use {@link #getNodeAttributeMultiplers()} to compute the load on the
     * dependent service. Each attribute in inputLoad is multiplied by the value
     * in {@link #getNodeAttributeMultiplers()}. If there is no corresponding
     * value, the attribute is not present in the output value.
     * 
     * 
     * @param inputLoad
     *            the load on the service with the dependency
     * @return the load on the dependent service
     */
    public Map<NodeAttribute, Double> computeDependencyNodeLoad(@Nonnull final Map<NodeAttribute, Double> inputLoad) {
        final Map<NodeAttribute, Double> outputLoad = new HashMap<>();
        inputLoad.forEach((attr, inputValue) -> {
            final double outputValue = inputValue * nodeAttributeMultipliers.getOrDefault(attr, 0D);
            outputLoad.put(attr, outputValue);
        });
        return outputLoad;
    }

    private final Map<LinkAttribute, Double> linkAttributeMultipliers = new HashMap<>();

    /**
     * The multipliers for each link attribute. These are used by
     * {@link #computeDependencyLinkLoad(Map)} to determine the load on the
     * dependent service.
     * 
     * @return a non-null value
     */
    @Nonnull
    public Map<LinkAttribute, Double> getLinkAttributeMultiplers() {
        return linkAttributeMultipliers;
    }

    /**
     * 
     * @param v
     *            see {@link #getLinkAttributeMultiplers()}
     */
    public void setLinkAttributeMultiplier(final Map<LinkAttribute, Double> v) {
        linkAttributeMultipliers.clear();
        if (null != v) {
            linkAttributeMultipliers.putAll(v);
        }
    }

    /**
     * Use {@link #getLinkAttributeMultiplers()} to compute the load on the
     * dependent service. Each attribute in inputLoad is multiplied by the value
     * in {@link #getLinkAttributeMultiplers()}, except for
     * {@link LinkAttribute#DATARATE_TX} and
     * {@link LinkAttribute#DATARATE_RX}, they are flipped so that traffic
     * received by the first service is used to determine traffic being sent to
     * the dependent service. If there is no corresponding value, the attribute
     * is not present in the output value.
     * 
     * @param inputLoad
     *            the load on the service with the dependency
     * @return the load on the dependent service
     */
    public Map<LinkAttribute, Double> computeDependencyLinkLoad(final Map<LinkAttribute, Double> inputLoad) {
        final Map<LinkAttribute, Double> outputLoad = new HashMap<>();
        inputLoad.forEach((attr, inputValue) -> {
            final double multiplier;
            if (LinkAttribute.DATARATE_RX.equals(attr)) {
                multiplier = linkAttributeMultipliers.getOrDefault(LinkAttribute.DATARATE_TX, 0D);
            } else if (LinkAttribute.DATARATE_TX.equals(attr)) {
                multiplier = linkAttributeMultipliers.getOrDefault(LinkAttribute.DATARATE_RX, 0D);
            } else {
                multiplier = linkAttributeMultipliers.getOrDefault(attr, 0D);
            }

            final double outputValue = inputValue * multiplier;
            outputLoad.put(attr, outputValue);
        });
        return outputLoad;
    }

    private double startStartMultiplier;
    private double startServerDurationMultiplier;
    private double startNetworkDurationMultiplier;
    private long startConstant;

    /**
     * Specify how the start time of the induced demand is computed. The start
     * time of the induced demand is:
     * 
     * <pre>
     * request start * startMultiplier 
     *   + request server duration * serverDurationMutiplier 
     *   + request network duration * networkDurationMutiplier
     *   + constant
     * </pre>
     * 
     * The result is rounded to get to an integer.
     * 
     * @param startMultiplier
     *            multiply this by the request start time
     * @param serverDurationMultiplier
     *            multiply this by the request server duration
     * @param networkDurationMultiplier
     *            multiply this by the request network duration
     * @param constant
     *            add this to the result
     */
    public void setStartComputationParameters(final double startMultiplier,
            final double serverDurationMultiplier,
            final double networkDurationMultiplier,
            final long constant) {
        this.startStartMultiplier = startMultiplier;
        this.startServerDurationMultiplier = serverDurationMultiplier;
        this.startNetworkDurationMultiplier = networkDurationMultiplier;
        this.startConstant = constant;
    }

    /**
     * Compute the start time of the created demand.
     * 
     * @param requestStart
     *            the start of the client request
     * @param requestServerDuration
     *            the server duration of the client request
     * @param requestNetworkDuration
     *            the network duration of the client request
     * @return the start time of the new request
     * @see #setStartComputationParameters(double, double, double, long)
     */
    public long computeStartTime(final long requestStart,
            final long requestServerDuration,
            final long requestNetworkDuration) {
        final long start = Math.round(
                this.startStartMultiplier * requestStart + this.startServerDurationMultiplier * requestServerDuration
                        + this.startNetworkDurationMultiplier * requestNetworkDuration + startConstant);
        return start;
    }

    private double networkDurationServerMultiplier;
    private double networkDurationNetworkMultiplier;
    private long networkDurationConstant;

    /**
     * Specify how the network duration induced demand is computed.
     * 
     * <pre>
     *   + request server duration * serverMutiplier 
     *   + request network duration * networkMutiplier 
     *   + constant
     * </pre>
     * 
     * The result is rounded to get to an integer.
     * 
     * @param serverMultiplier
     *            multiply this by the request server duration
     * @param networkMultiplier
     *            multiply this by the request network duration
     * @param constant
     *            add this to the result
     */
    public void setNetworkDurationComputationParameters(final double serverMultiplier,
            final double networkMultiplier,
            final long constant) {
        this.networkDurationServerMultiplier = serverMultiplier;
        this.networkDurationNetworkMultiplier = networkMultiplier;
        this.networkDurationConstant = constant;
    }

    /**
     * Compute the network duration of the created demand.
     * 
     * @param requestServerDuration
     *            the server duration of the client request
     * @param requestNetworkDuration
     *            the network duration of the client request
     * @return the network duration of the new request
     * @see #setNetworkDurationComputationParameters(double, double, long)
     */
    public long computeNetworkDuration(final long requestServerDuration, final long requestNetworkDuration) {
        final long value = Math.round(this.networkDurationServerMultiplier * requestServerDuration
                + this.networkDurationNetworkMultiplier * requestNetworkDuration + networkDurationConstant);
        return value;
    }

    private double serverDurationServerMultiplier;
    private double serverDurationNetworkMultiplier;
    private double serverDurationConstant;

    /**
     * Specify how the server duration induced demand is computed.
     * 
     * <pre>
     *   + request server duration * serverMutiplier 
     *   + request network duration * networkMutiplier 
     *   + constant
     * </pre>
     * 
     * The result is rounded to get to an integer.
     * 
     * @param serverMultiplier
     *            multiply this by the request server duration
     * @param networkMultiplier
     *            multiply this by the request network duration
     * @param constant
     *            add this to the result
     */
    public void setServerDurationComputationParameters(final double serverMultiplier,
            final double networkMultiplier,
            final long constant) {
        this.serverDurationServerMultiplier = serverMultiplier;
        this.serverDurationNetworkMultiplier = networkMultiplier;
        this.serverDurationConstant = constant;
    }

    /**
     * Compute the server duration of the created demand.
     * 
     * @param requestServerDuration
     *            the server duration of the client request
     * @param requestNetworkDuration
     *            the network duration of the client request
     * @return the network duration of the new request
     * @see #setServerDurationComputationParameters(double, double, long)
     */
    public long computeServerDuration(final long requestServerDuration, final long requestNetworkDuration) {
        final long value = Math.round(this.serverDurationServerMultiplier * requestServerDuration
                + this.serverDurationNetworkMultiplier * requestNetworkDuration + serverDurationConstant);
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeAttributeMultipliers, linkAttributeMultipliers);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (null == o) {
            return false;
        } else if (getClass().equals(o.getClass())) {
            final DependencyDemandFunction other = (DependencyDemandFunction) o;
            return Objects.equals(getLinkAttributeMultiplers(), other.getLinkAttributeMultiplers())
                    && Objects.equals(getNodeAttributeMultiplers(), other.getNodeAttributeMultiplers())
                    && Objects.equals(serverDurationConstant, other.serverDurationConstant)
                    && Objects.equals(serverDurationNetworkMultiplier, other.serverDurationNetworkMultiplier)
                    && Objects.equals(serverDurationServerMultiplier, other.serverDurationServerMultiplier)
                    && Objects.equals(startConstant, other.startConstant)
                    && Objects.equals(startNetworkDurationMultiplier, other.startNetworkDurationMultiplier)
                    && Objects.equals(startServerDurationMultiplier, other.startServerDurationMultiplier)
                    && Objects.equals(startStartMultiplier, other.startStartMultiplier);
        } else {
            return false;
        }
    }

}
