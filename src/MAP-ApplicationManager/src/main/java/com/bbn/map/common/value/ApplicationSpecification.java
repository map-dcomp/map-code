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

import java.util.Objects;

import javax.annotation.Nonnull;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

/**
 * Application Specification value class.
 * 
 * @author jmcettrick
 * 
 */
@NodeEntity
public class ApplicationSpecification extends Dependency {

    private static final long serialVersionUID = 6441635210042977099L;

    /**
     * Default constructor, all values set to null.
     */
    public ApplicationSpecification() {
    }

    /**
     * 
     * @param coordinates
     *            see {@link #getCoordinates()}
     */
    public ApplicationSpecification(final ApplicationCoordinates coordinates) {
        this();

        setCoordinates(coordinates);
    }

    @Relationship(type = "profile", direction = Relationship.OUTGOING)
    private ApplicationProfile profile = new ApplicationProfile();

    /**
     * The default value uses {@link #ApplicationSpecification()}.
     * 
     * @return the profile for the application
     */
    @Nonnull
    public ApplicationProfile getProfile() {
        return profile;
    }

    /**
     * 
     * @param profile
     *            see {@link #getProfile()}
     */
    public void setProfile(@Nonnull final ApplicationProfile profile) {
        this.profile = profile;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((profile == null) ? 0 : profile.hashCode());
        return result;
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
            return Objects.equals(profile, other.profile);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ApplicationSpecification [profile=");
        builder.append(profile);
        builder.append(", profile=");
        builder.append(getProfile());
        builder.append(", coordinates=");
        builder.append(getCoordinates());
        builder.append(", dependencies=");
        builder.append(getDependencies());
        builder.append("]");
        return builder.toString();
    }
}
