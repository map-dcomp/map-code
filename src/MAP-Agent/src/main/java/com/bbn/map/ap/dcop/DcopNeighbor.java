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
import java.io.OptionalDataException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.protelis.networkresourcemanagement.ApMessage;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkManager;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StreamSyncLostException;

/**
 * Network link between 2 DCOP leaders.
 */
/* package */
final class DcopNeighbor extends Thread {

    private final Logger logger;

    private final Controller selfNode;

    private ImmutablePair<RegionIdentifier, DcopSharedInformation> sharedData;

    /**
     * @return The data most recently shared from the remote DCOP leader. Not
     *         null.
     */
    public ImmutablePair<RegionIdentifier, DcopSharedInformation> getSharedValues() {
        synchronized (lock) {
            if (null == sharedData) {
                return ImmutablePair.of(selfNode.getRegion(), new DcopSharedInformation());
            } else {
                return sharedData;
            }
        }
    }

    /**
     * Used to keep from having parallel connections.
     * 
     * @return the value that was passed into the constructor.
     */
    /* package */ int getNonce() {
        return this.nonce;
    }

    private final int nonce;

    private final DataInputStream input;
    private final DataOutputStream output;
    private final Socket socket;

    // use separate object rather than this to keep other objects from blocking
    // us
    private final Object lock = new Object();

    /* package */ DcopNeighbor(final Controller selfNode,
            final NodeIdentifier neighborUid,
            final int nonce,
            final InetSocketAddress addr,
            final Socket s,
            final DataInputStream input,
            final DataOutputStream output) {
        super(String.format("%s_to_%s_port_%d", selfNode.getNodeIdentifier(), neighborUid, addr.getPort()));
        logger = LoggerFactory.getLogger(DcopNeighbor.class.getName() + "." + getName());

        this.selfNode = selfNode;
        this.input = input;
        this.output = output;
        this.socket = s;
        this.nonce = nonce;
    }

    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 
     * @return is this object still running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Listen for incoming packets
     */
    @Override
    public void run() {
        running.set(true);
        try {
            while (running.get()) {
                final byte messageType = input.readByte();
                if (DcopDirectCommunicator.MESSAGE_TYPE_DCOP_SHARE == messageType) {
                    final DcopShareMessage msg = DcopShareMessage.readMessage(input);

                    selfNode.setDcopSharedInformation(msg.getRegion(), msg.getData());
                } else if (NodeNetworkManager.MESSAGE_TYPE_CLOSE == messageType) {
                    logger.debug("Received close connection message, exiting");
                    break;
                } else {
                    logger.error("Received unexpected message type ({}), assuming corrupted stream and exiting",
                            String.format("%02x", messageType));
                    break;
                }
            }
        } catch (final StreamSyncLostException e) {
            logger.error("Lost sync of the AP stream", e);
        } catch (final OptionalDataException e) {
            logger.error(getName() + ": failed to read data from neighbor. eof: " + e.eof + " length: " + e.length, e);
        } catch (final IOException e) {
            if (!running.get()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(getName() + ": failed to receive from neighbor (in shutdown)", e);
                }
            } else {
                logger.error(getName() + ": failed to receive from neighbor", e);
            }
        } catch (final Exception e) {
            logger.error(getName() + ": unexpected exception", e);
        } finally {
            terminate();
        }
    }

    /**
     * Terminate the connection.
     */
    public void terminate() {
        logger.debug("Terminating connection");

        try {
            NodeNetworkManager.writeCloseConnection(output);
        } catch (final IOException e) {
            logger.debug("Got error writing close message, ignoring", e);
        } catch (final Exception e) {
            logger.error("Unexpected exception writing close message, ignoring", e);
        }

        running.set(false);
        interrupt();

        try {
            input.close();
        } catch (final IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Got error closing input stream on shutdown, ignoring.", e);
            }
        }

        try {
            output.close();
        } catch (final IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Got error closing output stream on shutdown, ignoring.", e);
            }
        }

        try {
            socket.close();
        } catch (final IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Got error closing socket on shutdown, ignoring.", e);
            }
        }
    }

    /**
     * Send a message to this neighbor.
     * 
     * @param toSend
     *            what to send
     * @throws IOException
     *             when there is an error writing
     */
    public void sendMessage(final byte messageType, final ApMessage msg) throws IOException {
        if (!isInterrupted() && running.get()) {
            synchronized (lock) {
                output.writeByte(messageType);
                msg.writeMessage(output);

                logger.trace("sendMessage is calling flush");
                output.flush();

                logger.trace("sendMessage finished");
            }
        }
    }

}
