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

/**
 * Base class for DNS records.
 */
public abstract class DnsRecord {
    /**
     * 
     * @param sourceRegion
     *            see {@link #getSourceRegion()}
     * @param ttl
     *            see {@link #getTtl()}
     * @param service
     *            see {@link #getService()}
     * 
     * @see #getSourceRegion()
     */
    public DnsRecord(final RegionIdentifier sourceRegion, final int ttl, @Nonnull final ServiceIdentifier<?> service) {
        this.sourceRegion = sourceRegion;
        this.ttl = ttl;
        this.service = service;
    }

    private final ServiceIdentifier<?> service;

    /**
     * 
     * @return The service that the entry is for.
     */
    @Nonnull
    public ServiceIdentifier<?> getService() {
        return service;
    }

    private final int ttl;

    /**
     * 
     * @return TTL in seconds
     */
    public int getTtl() {
        return ttl;
    }

    private final RegionIdentifier sourceRegion;

    /**
     * When a record is being looked up the region of the client is checked
     * against this field. If this field matches then the record is used for the
     * client. This field may be null to signify that the record can be used for
     * clients from any region.
     * 
     * @return the source region that should use this record
     */
    public RegionIdentifier getSourceRegion() {
        return sourceRegion;
    }
}