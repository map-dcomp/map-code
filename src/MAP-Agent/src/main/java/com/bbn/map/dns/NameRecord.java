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
package com.bbn.map.dns;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

/**
 * DNS 'A' record. This may also be implemented by a 'CNAME' record pointing to
 * the FQDN assigned to the node.
 */
public final class NameRecord extends DnsRecord {
    /**
     * 
     * @param sourceRegion
     *            passed to parent, {@link DnsRecord#DnsRecord(RegionIdentifier,
     * int, ServiceIdentifier<?>)
     * @param ttl
     *            passed to parent, {@link DnsRecord#DnsRecord(RegionIdentifier,
     * int, ServiceIdentifier<?>)
     * @param service
     *            passed to parent, {@link DnsRecord#DnsRecord(RegionIdentifier,
     * int, ServiceIdentifier<?>)
     * @param node
     *            node to map name to
     */
    public NameRecord(@JsonProperty("sourceRegion") final RegionIdentifier sourceRegion,
            @JsonProperty("ttl") final int ttl,
            @JsonProperty("service") @Nonnull final ServiceIdentifier<?> service,
            @JsonProperty("node") @Nonnull final NodeIdentifier node) {
        super(sourceRegion, ttl, service);
        this.node = node;
        this.hashCode = getService().hashCode();
    }

    private final NodeIdentifier node;

    /**
     * @return the node that the name maps to.
     */
    @Nonnull
    public NodeIdentifier getNode() {
        return node;
    }

    private final int hashCode;

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (null == o) {
            return false;
        } else if (getClass().equals(o.getClass())) {
            final NameRecord other = (NameRecord) o;
            if (this.hashCode != other.hashCode) {
                return false;
            } else {
                return getTtl() == other.getTtl() //
                        && Objects.equal(getService(), other.getService()) //
                        && Objects.equal(getNode(), other.getNode()) //
                        && Objects.equal(getSourceRegion(), other.getSourceRegion());
            }
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "NameRecord[" + getService() + " -> " + getNode() + "]";
    }

}