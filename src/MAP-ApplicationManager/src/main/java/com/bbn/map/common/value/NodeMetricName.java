/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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
import java.util.Objects;

import javax.annotation.Nonnull;

import org.neo4j.ogm.annotation.GraphId;

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The name of a node metric. This is used when reporting node metrics out of a
 * MAP-agent.
 * 
 */
public class NodeMetricName implements Serializable, NodeAttribute<NodeMetricName> {

    private static final long serialVersionUID = 2L;

    @GraphId
    private Long id;

    /**
     * Node attribute for measuring units of standard small containers. This
     * attribute is not application specific.
     */
    public static final NodeMetricName TASK_CONTAINERS = new NodeMetricName("TASK_CONTAINERS");

    /**
     * Example of an application specific metric.
     */
    public static final NodeMetricName QUEUE_LENGTH = new NodeMetricName("QueueLength", true);

    /**
     * Node attribute for CPU usage. This is a number between 0 and 1
     * representing the percentage of the total CPUs busy. This attribute is not
     * application specific.
     */
    public static final NodeMetricName CPU = new NodeMetricName("CPU");

    /**
     * Memory in gigabytes. This attribute is not
     * application specific.
     */
    public static final NodeMetricName MEMORY = new NodeMetricName("MEMORY");

    // NOTE: AttributeJsonSupport needs to know about any other constants
    // created

    /**
     * Calls {@link #NodeMetricName(String, boolean)} with application specific
     * set to false.
     * 
     * @param name
     *            see {@link #getName()}
     */
    public NodeMetricName(@Nonnull final String name) {
        this(name, false);
    }

    /**
     * 
     * @param name
     *            see {@link #getName()}
     * @param applicationSpecific
     *            see {@link #isApplicationSpecific()}
     */
    public NodeMetricName(@JsonProperty("name") @Nonnull final String name,
            @JsonProperty("applicationSpecific") final boolean applicationSpecific) {
        this.name = name;
        this.applicationSpecific = applicationSpecific;
    }

    private final boolean applicationSpecific;

    /**
     * 
     * @return true if this metric is specific to an application, otherwise the
     *         metric applies to all applications
     */
    public boolean isApplicationSpecific() {
        return applicationSpecific;
    }

    private final String name;

    /**
     * 
     * @return the name of the metric
     */
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationSpecific, name);
    }

    /**
     * Only the name is compared.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof NodeMetricName) {
            final NodeMetricName other = (NodeMetricName) o;
            return Objects.equals(getName(), other.getName())
                    && isApplicationSpecific() == other.isApplicationSpecific();
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "NodeMetricName {" + getName() + ", " + isApplicationSpecific() + "}";
    }

    @Override
    public NodeMetricName getAttribute() {
        return this;
    }

}
