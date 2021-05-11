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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.protelis.lang.datatype.DeviceUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.MapOracle;
import com.bbn.map.ap.ImmutableDcopSharedInformation;
import com.bbn.map.ta2.RegionalTopology;
import com.bbn.map.ta2.TA2Interface;
import com.bbn.protelis.networkresourcemanagement.HelloMessage;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkManager;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * Network manager for doing DCOP communications outside of AP.
 * 
 * @author jschewe
 *
 */
public class DcopDirectCommunicator {

    private final Logger logger;

    private final TA2Interface ta2;
    private final MapOracle oracle;
    private final NodeLookupService nodeLookup;
    private final Controller controller;
    private static final Random RANDOM = new Random();
    private final Object lock = new Object();

    /** neighbor -> connection */
    private final Map<NodeIdentifier, DcopNeighbor> nbrs = new HashMap<>();

    /**
     * 
     * @param controller
     *            used to get and set DCOP shared information
     * @param nodeLookup
     *            used to determine how to connect to the nodes. This is
     *            different from the instance to lookup nodes for AP.
     * @param oracle
     *            used to find DCOP leaders
     * @param ta2
     *            used to get neighboring regions
     */
    public DcopDirectCommunicator(final @Nonnull Controller controller,
            final @Nonnull NodeLookupService nodeLookup,
            final @Nonnull TA2Interface ta2,
            final @Nonnull MapOracle oracle) {
        this.controller = controller;
        this.nodeLookup = nodeLookup;
        this.ta2 = ta2;
        this.oracle = oracle;
        logger = LoggerFactory.getLogger(this.getClass().getName() + "." + controller.getName());
    }

    private Set<RegionIdentifier> getNeighboringRegions() {
        final RegionalTopology topology = ta2.getRegionTopology();
        return topology.getNeighboringRegions(controller.getRegion());
    }

    /**
     * Start communicating with neighbors.
     * 
     * @throws IllegalStateException
     *             if the network manager is already running
     */
    public void start() {
        synchronized (lock) {
            if (running) {
                throw new IllegalStateException(
                        "Cannot start network manager when it's already running. Node: " + controller.getName());
            }

            running = true;

            listenForNeighbors();
        }
    }

    /**
     * Connect to all neighbors that aren't currently connected and remove any
     * neighbors that aren't running. This should be called at regular intervals
     * to ensure that all neighbors are connected.
     */
    public void updateNeighbors() {
        // copy the list so that we don't hold the lock while sending all of
        // the messages and to ensure we don't end up with a
        // concurrent modification exception below
        final Map<DeviceUID, DcopNeighbor> nbrsCopy = new HashMap<>();
        synchronized (lock) {
            // remove any disconnected neighbors
            nbrs.entrySet().removeIf(e -> !e.getValue().isRunning());

            nbrsCopy.putAll(nbrs);
        }

        getNeighboringRegions().parallelStream() //
                .map(oracle::getDcopForRegion) //
                .forEach(neighborUID -> {
                    if (!nbrsCopy.containsKey(neighborUID)) {
                        connectToNeighbor(neighborUID);
                    }
                });
    }

    private boolean running = false;

    /**
     * Stop the manager.
     */
    public void stop() {
        synchronized (lock) {
            running = false;

            // stop talking to neighbors
            nbrs.forEach((k, v) -> {
                v.terminate();
                try {
                    logger.trace("Waiting on {}", v.getName());
                    v.join();
                } catch (final InterruptedException e) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Interrupted during join", e);
                    }
                }
            });

            // force one to stop listening
            try {
                if (null != server) {
                    server.close();
                }
            } catch (final IOException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Error closing server socket", e);
                }
            }
        }
    }

    private void addNeighbor(final int nonce,
            final NodeIdentifier neighborId,
            final Socket s,
            final DataInputStream input,
            final DataOutputStream output) {
        synchronized (lock) {
            // symmetry-break nonce
            // If UID isn't already linked, add a new neighbor

            final InetSocketAddress remoteAddr = new InetSocketAddress(s.getInetAddress(), s.getPort());
            final DcopNeighbor other = nbrs.get(neighborId);
            if (null == other || other.getNonce() < nonce) {
                if (null != other) {
                    logger.debug("Closing remote connection from {} because there's a connection to them", neighborId);
                    other.terminate();
                }

                final DcopNeighbor neighbor = new DcopNeighbor(controller, neighborId, nonce, remoteAddr, s, input,
                        output);
                nbrs.put(neighborId, neighbor);
                neighbor.start();
                logger.debug("Started new neighbor connection with {}", neighborId);
            } else {
                logger.debug("Closing connection to {} because we already have a connection from them", neighborId,
                        controller.getNodeIdentifier());
                try {
                    NodeNetworkManager.writeCloseConnection(output);
                } catch (final IOException e) {
                    logger.debug("Error writing close to neighbor, ignoring", e);
                } catch (final Exception e) {
                    logger.debug("Unepxected error writing close to neighbor, ignoring", e);
                }

                try {
                    input.close();
                } catch (final IOException e) {
                    logger.debug("Error closing input to neighbor, ignoring", e);
                }

                try {
                    output.close();
                } catch (final IOException e) {
                    logger.debug("Error closing output to neighbor, ignoring", e);
                }

                try {
                    s.close();
                } catch (final IOException e) {
                    logger.debug("Error closing socket to neighbor, ignoring", e);
                }
            }
        } // lock so that we don't add 2 connections to the neighbor
    }

    private ServerSocket server = null;

    /**
     * Listen for neighbor connections.
     * 
     */
    private void listenForNeighbors() {

        final InetSocketAddress addr = nodeLookup.getInetAddressForNode(controller.getNodeIdentifier());
        if (null == addr) {
            logger.error(
                    "Unable to find this node '{}' in the lookup service, unable to listen for neighbor connections",
                    controller.getNodeIdentifier());
            return;
        }

        final int port = addr.getPort();
        new Thread(() -> {

            while (running) {
                try {
                    synchronized (lock) {
                        server = new ServerSocket(port);
                    }

                    server.setReuseAddress(true);
                    logger.info("Node: " + controller.getName() + " Daemon listening for neighbors on port " + port);
                    while (running) {
                        final Socket s = server.accept();

                        logger.debug("Got a connection from {}", s.getInetAddress());

                        // don't need a thread here since addNeighbor will take
                        // care of creating a thread to service the connection.
                        try {
                            final DataOutputStream output = new DataOutputStream(s.getOutputStream());
                            final DataInputStream input = new DataInputStream(s.getInputStream());

                            // If the link connects, trade UIDs

                            // write uid for neighbor
                            logger.trace("Writing node identifier to new connection");
                            NodeNetworkManager.writeHello(output, RANDOM.nextInt(), controller.getNodeIdentifier());

                            // reads data from connectToNeighbor()
                            logger.trace("Reading node identifier and nonce from neighbor {}", s.getInetAddress());
                            final byte remoteMessageType = input.readByte();
                            if (remoteMessageType == NodeNetworkManager.MESSAGE_TYPE_HELLO) {
                                final HelloMessage remoteHello = HelloMessage.readMessage(input);

                                addNeighbor(remoteHello.getNonce(), remoteHello.getId(), s, input, output);
                                logger.trace("Received uid {} and nonce {} from {}", remoteHello.getId(),
                                        remoteHello.getNonce(), s.getInetAddress());
                            } else {
                                logger.error("Unexpected message type from neighbor: "
                                        + String.format("%02x", remoteMessageType));
                                s.close();
                            }
                        } catch (final IOException e) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Got exception creating link to neighbor that connected to us.", e);
                            }
                        }

                    } // while running accept connections
                } catch (final IOException e) {
                    if (running) {
                        logger.warn("Node: " + controller.getName()
                                + " received I/O exception accepting connections, trying to listen again on port: "
                                + port, e);
                    }
                }

                synchronized (lock) {
                    try {
                        if (null != server) {
                            server.close();
                        }
                    } catch (final IOException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Error closing server socket", e);
                        }
                    }
                    server = null;
                }
            } // while running, restart listen

            if (logger.isInfoEnabled()) {
                logger.info("Exiting thread: " + Thread.currentThread().getName());
            }
        }, "Node: " + controller.getName() + " server thread").start();

    }

    /**
     * Attempt to create a connection to a neighbor.
     * 
     * @param neighborNode
     *            the neighbor to connect to
     */
    private void connectToNeighbor(final NodeIdentifier neighborUID) {
        final InetSocketAddress addr = nodeLookup.getInetAddressForNode(neighborUID);
        if (null == addr) {
            logger.warn(neighborUID
                    + " is not found in the lookup service, not connecting to this neighbor for AP sharing");
            return;
        }

        logger.debug("Connecting to {} from {}", neighborUID, controller.getNodeIdentifier());

        try {
            // Try to link
            final Socket s = new Socket(addr.getAddress(), addr.getPort());

            final int nonce = RANDOM.nextInt();

            final DataOutputStream output = new DataOutputStream(s.getOutputStream());
            final DataInputStream input = new DataInputStream(s.getInputStream());

            // If the link connects, trade UIDs
            NodeNetworkManager.writeHello(output, nonce, controller.getNodeIdentifier());

            logger.debug("Reading identifier from neighbor {}", controller.getNodeIdentifier());
            final byte remoteMessageType = input.readByte();
            if (remoteMessageType == NodeNetworkManager.MESSAGE_TYPE_HELLO) {
                final HelloMessage remoteHello = HelloMessage.readMessage(input);

                addNeighbor(nonce, remoteHello.getId(), s, input, output);
            } else {
                logger.error("Unexpected message type connecting to neighbor {}: {}", neighborUID,
                        String.format("%02x", remoteMessageType));
                s.close();
            }
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Couldn't connect to neighbor: {}. Will try again later.", neighborUID, e);
            }
        }
    }

    /**
     * Share the {@code msg} with all neighbors.
     * 
     * @param msg
     *            the message to share
     */
    public void shareDcopMessage(final ImmutableDcopSharedInformation msg) {
        updateNeighbors();

        final Map<NodeIdentifier, DcopNeighbor> nbrsCopy = new HashMap<>();
        synchronized (lock) {
            nbrsCopy.putAll(nbrs);
        }

        logger.debug("Top of share DCOP sending from {} - neighbors: {}", controller.getName(), nbrsCopy.keySet());

        try {
            final DcopShareMessage message = new DcopShareMessage(controller.getRegion(), msg);

            final Map<NodeIdentifier, DcopNeighbor> toRemove = nbrsCopy.entrySet().parallelStream().map(entry -> {
                final DcopNeighbor neighbor = entry.getValue();
                logger.trace("Sending message to {}", entry.getKey());

                try {
                    neighbor.sendMessage(MESSAGE_TYPE_DCOP_SHARE, message);
                    return null;
                } catch (final Exception e) {
                    if (!neighbor.isRunning()) {
                        logger.debug("Neighbor has stopped, removing. Neighbor: " + entry.getKey(), e);
                    } else {
                        logger.error(
                                "Got error sending message to neighbor, removing. Neighbor: {}. See the following for the object types being sent",
                                entry.getKey(), e);
                    }
                    return entry;
                }
            }).filter(e -> null != e).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!toRemove.isEmpty()) {
                synchronized (lock) {
                    for (final Map.Entry<NodeIdentifier, DcopNeighbor> entry : toRemove.entrySet()) {
                        entry.getValue().terminate();
                        nbrs.remove(entry.getKey());
                    }
                }
            }
        } catch (final IOException e) {
            logger.error("Error encoding data to send, message not sent", e);
        }

        logger.debug("Bottom of share DCOP sending from {}", controller.getName());

    }

    /**
     * Message type for {@link DcopShareMessage}. Builds on the message types in
     * {@link NodeNetworkManager}.
     */
    public static final byte MESSAGE_TYPE_DCOP_SHARE = 4;

}
