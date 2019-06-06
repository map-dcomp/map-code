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
package com.bbn.map.ChartGeneration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringServiceIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test that we can deserialize {@link DnsRecord} objects.
 * 
 * @author jschewe
 *
 */
public class DnsRecordDeserializationTest {

    private ObjectMapper mapper;

    /**
     * Setup an {@link ObjectMapper} for deserializing {@link DnsRecord} objects.
     */
    @Before
    public void setup() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    /**
     * Check that {@link NameRecord} objects can be deserialized.
     * 
     * @throws IOException
     *             on internal test error
     */
    @Test
    public void nameRecord() throws IOException {
        final RegionIdentifier sourceRegion = new StringRegionIdentifier("test-region");
        final int ttl = 1;
        final ServiceIdentifier<?> service = new StringServiceIdentifier("test-service");
        final NodeIdentifier node = new DnsNameIdentifier("test-node");

        final DnsRecord record = new NameRecord(sourceRegion, ttl, service, node);

        final String json = mapper.writeValueAsString(record);

        final DnsRecord actual = mapper.readValue(json, DnsRecord.class);
        assertThat(actual, is(record));
    }

    /**
     * Check that {@link DelegateRecord} objects can be deserialized.
     * 
     * @throws IOException
     *             on internal test error
     */
    @Test
    public void delegateRecord() throws IOException {
        final RegionIdentifier sourceRegion = new StringRegionIdentifier("test-region");
        final int ttl = 1;
        final ServiceIdentifier<?> service = new StringServiceIdentifier("test-service");
        final RegionIdentifier delegateRegion = new StringRegionIdentifier("delegate-region");

        final DnsRecord record = new DelegateRecord(sourceRegion, ttl, service, delegateRegion);

        final String json = mapper.writeValueAsString(record);

        final DnsRecord actual = mapper.readValue(json, DnsRecord.class);
        assertThat(actual, is(record));
    }
}
