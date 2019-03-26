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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.annotation.Nonnull;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Transient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.google.common.collect.ComparisonChain;

/**
 * A value class representing an application profile.
 * 
 */
@NodeEntity
public class ApplicationProfile implements Serializable, Comparable<ApplicationProfile> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationProfile.class);

    private static final long serialVersionUID = -4736137934468851734L;

    @GraphId
    private Long id;

    /**
     * Default constructor, all fields set to null.
     */
    public ApplicationProfile() {
    }

    /**
     * 
     * @param demandToLoadRatio
     *            see {@link #getDemandToLoadRatio()}
     */
    public ApplicationProfile(final double demandToLoadRatio) {
        this.demandToLoadRatio = demandToLoadRatio;
    }

    private double dataSizePerLoadUnit = 1.0;

    /**
     * 
     * @return the data size for each load unit, defaults to 1.
     */
    public double getDataSizePerLoadUnit() {
        return dataSizePerLoadUnit;
    }

    /**
     * 
     * @param dataSizePerLoadUnit
     *            see {@link #getDataSizePerLoadUnit()}
     */
    public void setDataSizePerLoadUnit(final double dataSizePerLoadUnit) {
        this.dataSizePerLoadUnit = dataSizePerLoadUnit;
    }

    private double demandToLoadRatio = 1.0;

    /**
     * 
     * @return a value modeling the relationship between load and demand in
     *         terms of units of work. Defaults to 1.
     */
    public Double getDemandToLoadRatio() {
        return demandToLoadRatio;
    }

    /**
     * 
     * @param demandToLoadRatio
     *            see {@link #getDemandToLoadRatio()}
     */
    public void setDemandToLoadRatio(final Double demandToLoadRatio) {
        this.demandToLoadRatio = demandToLoadRatio;
    }

    private String clientPressureLambda = "";

    /**
     * If set allow the client pressure to depend on a dynamic value.
     * 
     * @return scala code to execute that models client pressure or null to use
     *         a constant
     */
    public String getClientPressureLambda() {
        return clientPressureLambda;
    }

    /**
     * 
     * @param clientPressureLambda
     *            see {@link #getClientPressureLambda()}
     */
    public void setClientPressureLambda(final String clientPressureLambda) {
        this.clientPressureLambda = clientPressureLambda;
    }

    /**
     * 
     * @return The parameters to use when creating the container that this
     *         application executes in.
     */
    public ContainerParameters getContainerParameters() {
        if (null == containerParameters && null != containerParametersStored) {
            try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(containerParametersStored))) {
                containerParameters = (ContainerParameters) is.readObject();
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.error("Error reading container paramters", e);
                throw new RuntimeException("Internal error reading container parameters", e);
            }
        }

        return containerParameters;
    }

    /**
     * @param v
     *            the new container parameters
     * @see #getContainerParameters()
     */
    public void setContainerParameters(@Nonnull final ContainerParameters v) {
        containerParameters = v;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(baos)) {
            os.writeObject(containerParameters);
            containerParametersStored = baos.toByteArray();
        } catch (final IOException e) {
            LOGGER.error("Error storing container paramters", e);
            throw new RuntimeException("Internal error storing container parameters", e);
        }
    }

    @Transient
    private ContainerParameters containerParameters = null;
    private byte[] containerParametersStored = null;

    private String serviceHostname = null;

    /**
     * This is the base name that clients use to connect to the service. This
     * starts at null, but should not be null when MAP is executing.
     * 
     * @return the hostname for the service, must be a valid hostname
     */
    public String getServiceHostname() {
        if (null != serviceHostname && serviceHostname.contains(".")) {
            LOGGER.warn("Stripping domain from service hostname {}. You should update your configuration files",
                    serviceHostname);
            final int dotIdx = serviceHostname.indexOf('.');
            serviceHostname = serviceHostname.substring(0, dotIdx);
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

    @Transient
    private RegionIdentifier serviceDefaultRegion = null;
    private byte[] serviceDefaultRegionStored = null;

    /**
     * This is the region that the service runs in by default. This starts at
     * null, but should not be null when MAP is executing.
     * 
     * @return the default region for the service
     */
    public RegionIdentifier getServiceDefaultRegion() {
        if (null == serviceDefaultRegion && null != serviceDefaultRegionStored) {
            try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(serviceDefaultRegionStored))) {
                serviceDefaultRegion = (RegionIdentifier) is.readObject();
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.error("Error reading service default region", e);
                throw new RuntimeException("Internal error reading service default region", e);
            }
        }
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

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(baos)) {
            os.writeObject(serviceDefaultRegion);
            serviceDefaultRegionStored = baos.toByteArray();
        } catch (final IOException e) {
            LOGGER.error("Error storing service default region", e);
            throw new RuntimeException("Internal error storing service default region", e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Double.hashCode(demandToLoadRatio);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ApplicationProfile other = (ApplicationProfile) obj;
        if (demandToLoadRatio != other.demandToLoadRatio) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(final ApplicationProfile that) {
        return ComparisonChain.start().compare(this.getDemandToLoadRatio(), that.getDemandToLoadRatio())
                .compare(this.getDataSizePerLoadUnit(), that.getDataSizePerLoadUnit()).result();
    }

}