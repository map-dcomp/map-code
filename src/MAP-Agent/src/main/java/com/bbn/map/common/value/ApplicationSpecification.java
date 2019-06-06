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
package com.bbn.map.common.value;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * Application Specification value class.
 * 
 * @author jmcettrick
 * 
 */
public class ApplicationSpecification implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationSpecification.class);

    private static final long serialVersionUID = 6441635210042977099L;

    /**
     * Categories the type of traffic for a service.
     * 
     * @author jschewe
     *
     */
    public enum ServiceTrafficType {
        /**
         * The service receives large amounts of data and sends back small
         * amounts of data. An HTTP POST is an example of this.
         */
        RX_GREATER,
        /**
         * The service receives a small amount of data and sends back a large
         * amount of data. An example of this is an HTTP GET.
         */
        TX_GREATER,
        /**
         * The relationship between request and response is unknow. It may be
         * the case that both are equal.
         */
        UNKNOWN;
    }

    /**
     * 
     * @param coordinates
     *            see {@link #getCoordinates()}
     */
    public ApplicationSpecification(final ApplicationCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    private final ApplicationCoordinates coordinates;

    /**
     * 
     * @return the application that this specification is for
     */
    public ApplicationCoordinates getCoordinates() {
        return coordinates;
    }

    /**
     * 
     * @return The parameters to use when creating the container that this
     *         application executes in.
     */
    public ContainerParameters getContainerParameters() {
        return containerParameters;
    }

    /**
     * @param v
     *            the new container parameters
     * @see #getContainerParameters()
     */
    public void setContainerParameters(@Nonnull final ContainerParameters v) {
        containerParameters = v;
    }

    private ContainerParameters containerParameters = null;

    private String serviceHostname = null;

    /**
     * This is the base name that clients use to connect to the service. This
     * starts at null, but should not be null when MAP is executing.
     * 
     * @return the hostname for the service, must be a valid hostname
     */
    public String getServiceHostname() {
        final String tempHostname = serviceHostname;
        if (null != tempHostname && tempHostname.contains(".")) {
            LOGGER.warn("Stripping domain from service hostname {}. You should update your configuration files",
                    tempHostname);
            final int dotIdx = tempHostname.indexOf('.');
            serviceHostname = tempHostname.substring(0, dotIdx);
        }
        return serviceHostname;
    }

    /**
     * This is the hostname without the domain.
     * 
     * @param v
     *            the new service hostname
     * @see #getServiceHostname()
     */
    public void setServiceHostname(final String v) {
        serviceHostname = v;
    }

    private RegionIdentifier serviceDefaultRegion = null;

    /**
     * This is the region that the service runs in by default. This starts at
     * null, but should not be null when MAP is executing.
     * 
     * @return the default region for the service
     */
    public RegionIdentifier getServiceDefaultRegion() {
        return serviceDefaultRegion;
    }

    /**
     * 
     * @param v
     *            the new service default region
     * @see #getServiceDefaultRegion
     */
    public void setServiceDefaultRegion(final RegionIdentifier v) {
        serviceDefaultRegion = v;
    }

    /**
     * Default value for {@link #getPriority()}.
     */
    public static final int DEFAULT_PRIORITY = 5;

    private int priority = DEFAULT_PRIORITY;

    /**
     * The priority is a value between 1 and 10 with 1 being the lowest and 10
     * being the highest. If unset the value is {@Link #DEFAULT_PRIORITY}.
     * 
     * @return the priority for this application profile.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 
     * @param v
     *            see {@link #getPriority()}
     */
    public void setPriority(final int v) {
        priority = v;
    }

    private final Set<Dependency> dependencies = new HashSet<>();

    /**
     * 
     * @return the dependencies for this application, unmodifiable
     */
    @Nonnull
    public Set<Dependency> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    /**
     * 
     * @param v
     *            see {@link #getDependencies()}
     */
    public void setDependencies(final Set<Dependency> v) {
        dependencies.clear();
        dependencies.addAll(v);
    }

    /**
     * 
     * @param v
     *            the dependency to add
     * @return true if the dependency didn't already exist
     * @see Set#add(Object)
     */
    public boolean addDependency(final Dependency v) {
        return dependencies.add(v);
    }

    private boolean replicable = true;

    /**
     * 
     * @return true if this service can be copied, false if it should not be
     *         copied
     */
    public boolean isReplicable() {
        return replicable;
    }

    /**
     * 
     * @param v
     *            see {@link #isReplicable()}
     */
    public void setReplicable(final boolean v) {
        this.replicable = v;
    }

    private String imageName = null;

    /**
     * 
     * @return the name of the image to use for the service, default is null
     */
    public String getImageName() {
        return imageName;
    }

    /**
     * 
     * @param v
     *            see {@link #getImageName()}
     */
    public void setImageName(final String v) {
        this.imageName = v;
    }

    private ServiceTrafficType trafficType = ServiceTrafficType.UNKNOWN;

    /**
     * 
     * @return the type of traffic to the service, default is
     *         {@link ServiceTrafficType#UNKNOWN}.
     */
    @Nonnull
    public ServiceTrafficType getTrafficType() {
        return trafficType;
    }

    /**
     * 
     * @param v
     *            see {@link #getTrafficType()}
     */
    public void setTrafficType(@Nonnull final ServiceTrafficType v) {
        this.trafficType = v;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCoordinates(), getPriority(), getServiceHostname(), getServiceDefaultRegion());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!super.equals(obj)) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            final ApplicationSpecification other = (ApplicationSpecification) obj;
            return Objects.equals(getCoordinates(), other.getCoordinates()) //
                    && Objects.equals(getPriority(), other.getPriority()) //
                    && Objects.equals(getServiceHostname(), other.getServiceHostname()) //
                    && Objects.equals(getServiceDefaultRegion(), other.getServiceDefaultRegion())//
                    && Objects.equals(getContainerParameters(), other.getContainerParameters()) //
                    && Objects.equals(isReplicable(), other.isReplicable()) //
                    && Objects.equals(getImageName(), other.getImageName()) //
            ;
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());

        builder.append(" [coordinates=");
        builder.append(getCoordinates());

        builder.append(", serviceHostname=");
        builder.append(getServiceHostname());

        builder.append(", serviceDefaultRegion=");
        builder.append(getServiceDefaultRegion());

        builder.append("]");
        return builder.toString();
    }
}
