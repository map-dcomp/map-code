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
package com.bbn.map.ap.dcop;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.ap.ImmutableDcopSharedInformation;
import com.bbn.protelis.networkresourcemanagement.ApMessage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StreamSyncLostException;

/**
 * Message for sharing DCOP information.
 * 
 * @author jschewe
 *
 */
public class DcopShareMessage extends ApMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcopShareMessage.class);

    private final byte[] encodedData;
    private final byte[] encodedRegion;

    private final RegionIdentifier region;

    /**
     * 
     * @return the sending region
     */
    public RegionIdentifier getRegion() {
        return region;
    }

    private final ImmutableDcopSharedInformation data;

    /**
     * 
     * @return the data being shared
     */
    public ImmutableDcopSharedInformation getData() {
        return data;
    }

    /**
     * 
     * @param region
     *            see {@link #getRegion()}
     * @param data
     *            see {@link #getData()}
     * @throws IOException
     *             if there is an error encoding the data
     */
    public DcopShareMessage(final RegionIdentifier region, final ImmutableDcopSharedInformation data)
            throws IOException {
        this.region = region;
        this.data = data;

        this.encodedData = encodeData(this.data);
        this.encodedRegion = encodeData(this.region);
    }

    @Override
    public void writeMessage(final DataOutputStream stream) throws IOException {
        LOGGER.trace("Sending message of size {}", encodedData.length);

        stream.writeInt(encodedRegion.length);
        stream.write(encodedRegion);

        stream.writeInt(encodedData.length);
        stream.write(encodedData);

    }

    private static final int MINIMUM_DATA_SIZE = 1;

    /**
     * 
     * @param stream
     *            where to read from
     * @return the message that was read
     * @throws IOException
     *             if there is an error reading from the stream
     * @throws StreamSyncLostException
     *             if the message is too small, signaling that the stream should
     *             be restarted
     */
    public static DcopShareMessage readMessage(final DataInputStream stream)
            throws IOException, StreamSyncLostException {
        final int regionSize = stream.readInt();
        if (regionSize < MINIMUM_DATA_SIZE) {
            throw new StreamSyncLostException("Message size (region) is too small: " + regionSize);
        }

        final byte[] regionBytes = new byte[regionSize];
        stream.readFully(regionBytes);

        final RegionIdentifier region = decodeData(RegionIdentifier.class, regionBytes);

        final int dataSize = stream.readInt();
        if (dataSize < MINIMUM_DATA_SIZE) {
            throw new StreamSyncLostException("Message size (data) is too small: " + regionSize);
        }

        final byte[] dataBytes = new byte[dataSize];
        stream.readFully(dataBytes);

        final ImmutableDcopSharedInformation data = decodeData(ImmutableDcopSharedInformation.class, dataBytes);

        return new DcopShareMessage(region, data);
    }

}
