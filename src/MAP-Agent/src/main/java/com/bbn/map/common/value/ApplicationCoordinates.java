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

import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ComparisonChain;

/**
 * A value class uniquely identifying an application.
 */
public class ApplicationCoordinates
        implements Serializable, Comparable<ApplicationCoordinates>, ServiceIdentifier<ApplicationCoordinates> {

    private static final long serialVersionUID = -2057776692983341566L;

    private final String group;

    private final String artifact;

    private final String version;

    /**
     * Used to represent an unknown application or service.
     */
    public static final ApplicationCoordinates UNMANAGED = new ApplicationCoordinates("unmanaged", "unmanaged", "0.0");

    /**
     * Used to represent an load assocated with AP.
     */
    public static final ApplicationCoordinates AP = new ApplicationCoordinates("com.bbn", "AP", "1.0");

    /**
     * 
     * @param group
     *            see {@link #getGroup()}
     * @param artifact
     *            see {@link #getArtifact()}
     * @param version
     *            see {@link #getVersion()}
     */
    public ApplicationCoordinates(@JsonProperty("group") final String group,
            @JsonProperty("artifact") final String artifact,
            @JsonProperty("version") final String version) {
        super();
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    /**
     * @return the group that the application belongs to
     */
    public String getGroup() {
        return group;
    }

    /**
     * 
     * @return the artifact in the group
     */
    public String getArtifact() {
        return artifact;
    }

    /**
     * 
     * @return the version of the application
     */
    public String getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifact == null) ? 0 : artifact.hashCode());
        result = prime * result + ((group == null) ? 0 : group.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        final ApplicationCoordinates other = (ApplicationCoordinates) obj;
        if (artifact == null) {
            if (other.artifact != null) {
                return false;
            }
        } else if (!artifact.equals(other.artifact)) {
            return false;
        }
        if (group == null) {
            if (other.group != null) {
                return false;
            }
        } else if (!group.equals(other.group)) {
            return false;
        }
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AppCoordinates {" + group + ", " + artifact + ", " + version + "}";
    }

    @Override
    public int compareTo(final ApplicationCoordinates that) {
        return ComparisonChain.start().compare(this.getGroup(), that.getGroup())
                .compare(this.getArtifact(), that.getArtifact()).compare(this.getVersion(), that.getVersion()).result();
    }

    @JsonIgnore // don't return a reference to self
    @Override
    public ApplicationCoordinates getIdentifier() {
        return this;
    }

}