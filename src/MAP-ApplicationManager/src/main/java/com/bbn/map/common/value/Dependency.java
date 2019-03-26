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
import java.util.HashSet;
import java.util.Set;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import com.google.common.collect.ComparisonChain;

/**
 * Dependency value class.
 * 
 * @author jmcettrick
 * 
 */
@NodeEntity
public class Dependency implements Serializable, Comparable<Dependency> {

    private static final long serialVersionUID = -7229346529151764397L;
    
    @GraphId
    private Long id;
    
    /**
     * Default constructor, all values set to null.
     */
    public Dependency() { }
    
    /**
     * 
     * @param coordinates
     *            see {@link #getCoordinates()}
     */
    public Dependency(final ApplicationCoordinates coordinates) {
        this();
        
        this.coordinates = coordinates;
    }

    @Relationship(type = "coordinates", direction = Relationship.OUTGOING)
    private ApplicationCoordinates coordinates;

    /**
     * 
     * @return the application that this specification is for
     */
    public ApplicationCoordinates getCoordinates() {
        return coordinates;
    }

    /**
     * 
     * @param coordinates
     *            see {@link #getCoordinates()}
     */
    public void setCoordinates(final ApplicationCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    @Relationship(type = "dependency", direction = Relationship.OUTGOING)
    private Set<Dependency> dependencies = new HashSet<>();

    /**
     * 
     * @return the applications that this application depends upon
     */
    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    /**
     * 
     * @param dependencies
     *            see {@link #getDependencies()}
     */
    public void setDependencies(final Set<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((coordinates == null) ? 0 : coordinates.hashCode());
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
        final Dependency other = (Dependency) obj;
        if (coordinates == null) {
            if (other.coordinates != null) {
                return false;
            }
        } else if (!coordinates.equals(other.coordinates)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Dependency [coordinates=");
        builder.append(coordinates);
        builder.append(", dependencies=");
        builder.append(dependencies);
        builder.append("]");
        return builder.toString();
    }

    // Comparisons and equality are based on the value of the coordinates
    @Override
    public int compareTo(final Dependency that) {
        return ComparisonChain.start().compare(this.getCoordinates(), that.getCoordinates()).result();
    }
}
