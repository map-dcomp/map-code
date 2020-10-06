/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

/**
 * A DNS record that specifies that the lookup should be delegated to a DNS
 * server in another region.
 */
public class DelegateRecord extends DnsRecord {

    /**
     * 
     * @param sourceRegion
     *            passed to {@link DnsRecord#DnsRecord(RegionIdentifier, int,
     *            ServiceIdentifier<?>)}
     * @param ttl
     *            passed to {@link DnsRecord#DnsRecord(RegionIdentifier, int,
     *            ServiceIdentifier<?>)}
     * @param service
     *            passed to {@link DnsRecord#DnsRecord(RegionIdentifier, int,
     *            ServiceIdentifier<?>)}
     * @param delegateRegion
     *            see {@link #getDelegateRegion()}
     */
    public DelegateRecord(@JsonProperty("sourceRegion") final RegionIdentifier sourceRegion,
            @JsonProperty("ttl") final int ttl,
            @JsonProperty("service") @Nonnull final ServiceIdentifier<?> service,
            @JsonProperty("delegateRegion") @Nonnull final RegionIdentifier delegateRegion) {
        super(sourceRegion, ttl, service);
        this.delegateRegion = delegateRegion;
    }

    private final RegionIdentifier delegateRegion;

    /**
     * 
     * @return the region to delegate lookups for this FQDN to.
     */
    @Nonnull
    public RegionIdentifier getDelegateRegion() {
        return delegateRegion;
    }

    @Override
    public String toString() {
        return "DelegateRecord[" + getService() + " -> " + getDelegateRegion() + "]";
    }

    @Override
    public int hashCode() {
        return getService().hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (null == o) {
            return false;
        } else if (getClass().equals(o.getClass())) {
            final DelegateRecord other = (DelegateRecord) o;
            return Objects.equal(getDelegateRegion(), other.getDelegateRegion())
                    && Objects.equal(getSourceRegion(), other.getSourceRegion()) && getTtl() == other.getTtl();
        } else {
            return false;
        }
    }
}
