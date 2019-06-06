package com.bbn.map.dcop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jacop.constraints.Constraint;
import org.jacop.constraints.PrimitiveConstraint;
import org.jacop.core.Store;
import org.jacop.floats.constraints.LinearFloat;
import org.jacop.floats.constraints.Max;
import org.jacop.floats.constraints.PmulCeqR;
import org.jacop.floats.core.FloatInterval;
import org.jacop.floats.core.FloatVar;
import org.jacop.floats.search.LargestDomainFloat;
import org.jacop.floats.search.Optimize;
import org.jacop.floats.search.SplitSelectFloat;
import org.jacop.search.DepthFirstSearch;
/*import org.jacop.search.IndomainMin;
import org.jacop.search.Search;
import org.jacop.search.SelectChoicePoint;
import org.jacop.search.SimpleSelect;
import org.jacop.search.SmallestDomain;
import org.jacop.constraints.XmulCeqZ;
import org.jacop.core.IntVar;
import org.jacop.constraints.LinearInt;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractPeriodicService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.dcop.algorithm3.WeightedAlgorithm;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The main entry point for DCOP. The {@link Controller} will use this class to
 * interact with DCOP. The {@link Controller} will start this service as
 * appropriate for the node.
 * 
 */
/**
 * @author khoihd Modification by cwayllace to merge first DCOP algorithm (from
 *         data center to neighbors) with second version (data center to leaves)
 */
public class DCOPService extends AbstractPeriodicService {

    /**
     * This is all the message types sent between DCOP agents.
     */
    public enum MessageType {
    /**
     * Telling a neighbor that I'm your parent and you're my children.
     */
    PARENT_TO_CHILDREN,
    /**
     * Sending load to the parent.
     */
    LOAD_TO_PARENT,
    /**
     * Write tree information of this DCOP iteration.
     */
    TREE
    }

    /**
     * @author cwayllace Stage at which is each DCOP agent FREE does not belong
     *         to any tree STAGE1 Sent messages asking for help STAGE2 Answered
     *         with load that can help Used when
     *         DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())
     *         is false
     */
    public enum Stage {
        /**
         * When agent is not belonging to any tree.
         */
        FREE,
        /**
         * When agent is asking for help.
         */
        STAGE1_ASKING_FOR_HELP,
        /**
         * When agent has received all response from the children, and start
         * sending type2 message to its parent.
         */
        STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN,
        /**
         * Write tree information of this DCOP iteration.
         */
        TREE_WRITING
    }
    
    
    private static final double DOUBLE_TOLERANCE = Math.pow(1, -6);
    
    private static final int INIT_OVERLOADED_VALUE = -1;
    private static final int NOT_OVERLOADED = 0;
    private static final int IS_OVERLOADED = 1;

    // Common variables
    private static final int NUMBER_OF_PREVIOUS_ITERATION_TO_REMOVE_OLD_MESSAGES = 9;
    private static final int TREE_ITERATION = -1;
    private static final Logger LOGGER = LoggerFactory.getLogger(DCOPService.class);
    private final RegionIdentifier selfRegionID;
    private double regionCapacity;
    private Set<RegionIdentifier> neighborSet;
    private DcopSharedInformation inbox;

    // For new algorithm

    ///////////////////////////////
    private Map<ServiceIdentifier<?>, Double> serviceIncomingLoadMap;
    private Map<ServiceIdentifier<?>, Double> serviceLoadToKeepMap;
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceOutgoingLoadMap;
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> excessLoadMapToParent;
    private Set<ServiceIdentifier<?>> leafServices;
    ///////////////////////////////

    // For old algorithm
//    private static final boolean IS_OPTIMIZATION = false;
    private static final double DEFAULT_EFFICIENCY = 1.0;
    private Map<ServiceIdentifier<?>, Double> totalDemandMap = new HashMap<>();
    private Map<ServiceIdentifier<?>, Double> excessLoadMap = new HashMap<>();
//    private double totalExcessLoad;
    private Map<ServiceIdentifier<?>, Double> loadAgreeToReceiveFromOtherMap = new HashMap<>();
    private Map<ServiceIdentifier<?>, Double> totalIncomingLoadMap = new HashMap<>();
    private Map<ServiceIdentifier<?>, Double> overloadSharedToOtherMap = new HashMap<>();
//    private ServiceIdentifier<?> serviceID;

    private Stage currentStage;
    private RegionIdentifier parent;
    private Set<RegionIdentifier> children;
    private Set<RegionIdentifier> freeNeighbors;
    private Set<RegionIdentifier> others;
    private boolean leaf = false;
    private DcopInfoMessage messageToSend;
    private List<DcopInfoMessage> storedInfoType2List;
    private int numberOfSentType2, countingChildren;
    private DcopMessage messageToForward;
    private boolean finishCount;
    private int overloaded;
    private int leaves;
    private boolean inTransition;
    private double efficiency;
    private Map<RegionIdentifier, Double> latency;
    private int hop;
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> outgoingLoadMap = new HashMap<>();

    ////////
    /**
     * Support multiple services This is a map children -> sourceRegion ->
     * service -> load
     */
    private Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenMap;
    private Map<RegionIdentifier, Set<ServiceIdentifier<?>>> parentServicesMap;
    // private Map<RegionIdentifier, Map<RegionIdentifier,
    // ServiceIdentifier<?>>> parentServicesMap;

    private Set<RegionIdentifier> nonChildrenParentSet;

    /**
     * This is the region that has a clientPool asking for a service to some
     * remote server
     */
    private boolean isRoot;

    /**
     * Support multiple services. This is a mapping sourceRegion -> service ->
     * load
     */
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceMapToThisServer;
    private Map<ServiceIdentifier<?>, Double> totalServerDemand;
    private Map<ServiceIdentifier<?>, Double> totalNetworkDemand;
    
    ///for WRDIFF
    private int lastIteration = 0;

    /**
     * Construct a DCOP service.
     *
     * @param region
     *            the region that this DCOP is running in
     *
     * @param applicationManager
     *            source of information about applications, including
     *            specifications and profiles
     *
     * @param dcopInfoProvider
     *            how to access MAP network state
     * @param nodeName
     *            the name of the node that this service is running on (for
     *            logging)
     *
     */
    public DCOPService(@Nonnull final String nodeName,
            @Nonnull final RegionIdentifier region,
            @Nonnull final DcopInfoProvider dcopInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager) {
        super("DCOP-" + nodeName, AgentConfiguration.getInstance().getDcopRoundDuration());
        this.selfRegionID = region;

        Objects.requireNonNull(applicationManager, "application manager");

        this.dcopInfoProvider = dcopInfoProvider;
        this.applicationManager = applicationManager;
        //////
        if (!DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) {
            currentStage = Stage.FREE;
            parent = null;
            children = null;
            messageToSend = null;
        }

    }

    private final DcopInfoProvider dcopInfoProvider;

    /**
     * Where to get application specifications and profiles from
     */
    @SuppressWarnings("unused")
    private final ApplicationManagerApi applicationManager;

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Cannot guarantee that DCOP will compute a non-null plan. Appears to be a bug in FindBugs")
    @Override
    protected void execute() {
        try {
            final RegionPlan prevPlan = dcopInfoProvider.getDcopPlan();
            final RegionPlan plan = computePlan();
            if (null == plan) {
                LOGGER.warn("DCOP produced a null plan, ignoring");
            } else if (!plan.equals(prevPlan)) {
                LOGGER.info("Publishing DCOP plan: {}", plan);
                dcopInfoProvider.publishDcopPlan(plan);
            }
        } catch (final Throwable t) {
            LOGGER.error("Got error computing DCOP plan. Skipping this round and will try again next round", t);
        }
    }

    /**
     * For new algorithm
     */
    private void iniVariables() {
        neighborSet = new HashSet<>();
        childrenMap = new HashMap<>();
        parentServicesMap = new HashMap<>();
        nonChildrenParentSet = new HashSet<>();
        sourceServiceMapToThisServer = new HashMap<>();
        excessLoadMapToParent = new HashMap<>();
        serviceIncomingLoadMap = new HashMap<>();
        serviceOutgoingLoadMap = new HashMap<>();
        serviceLoadToKeepMap = new HashMap<>();
        leafServices = new HashSet<>();
        totalServerDemand  = new HashMap<ServiceIdentifier<?>, Double>();
        totalNetworkDemand = new HashMap<ServiceIdentifier<?>, Double>();
    }

    /**
     * For old algorithm
     */
    private void init() {
        regionCapacity = 0;
        neighborSet = new HashSet<RegionIdentifier>();
        children = new HashSet<RegionIdentifier>();
        storedInfoType2List = new ArrayList<DcopInfoMessage>();
        numberOfSentType2 = 0;
        messageToForward = null;
        countingChildren = 0;
        finishCount = false;
        freeNeighbors = new HashSet<RegionIdentifier>();
        others = new HashSet<RegionIdentifier>();

        overloaded = INIT_OVERLOADED_VALUE;
        leaves = 0;
        inTransition = false;
        latency = new HashMap<RegionIdentifier, Double>();
        hop = 0;
        
        currentStage = Stage.FREE;
        parent = null;
        children.clear();
        messageToSend = null;
        freeNeighbors.clear();
        others.clear();
        
        outgoingLoadMap.clear();
        excessLoadMap.clear();
        totalDemandMap.clear();
        excessLoadMap.clear();
        loadAgreeToReceiveFromOtherMap.clear();
        totalIncomingLoadMap.clear();
        overloadSharedToOtherMap.clear();
        
        storedInfoType2List.clear();
    }

    private RegionPlan computePlan() {
        LOGGER.info("Using algorithm {}", AgentConfiguration.getInstance().getDcopAlgorithm());

        if (DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            iniVariables();
        else if(DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            init();
        else neighborSet = new HashSet<RegionIdentifier>();

        try {
            Thread.sleep(DcopConstants.TIME_SEARCHING_NEIGHBORS);
        } catch (InterruptedException e) {
            LOGGER.warn("InterruptedException when searching for neighbors. Return the default DCOP plan: {} ",
                    e.getMessage(), e);
            return defaultPlan(dcopInfoProvider.getRegionSummary(ResourceReport.EstimationWindow.LONG));
        }

        final ResourceSummary summary = dcopInfoProvider.getRegionSummary(ResourceReport.EstimationWindow.LONG);

        retrieveNeighborSetFromNetworkLink(summary); // same as
                                                     // retrieveNeighborLink(summary);
        if (DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            retrieveAggregateDemand(summary);
        retrieveAggregateCapacity(summary);

        if (DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) {
            LOGGER.info("My neighbors are: {}", neighborSet.toString());
            for (RegionIdentifier neighbor : neighborSet) {
                outgoingLoadMap.put(neighbor, new HashMap<>());
            }

            regionCapacity = regionCapacity * AgentConfiguration.getInstance().getDcopCapacityThreshold();

            /* ASSUME ONLY ONE SERVICE FOR NOW */
            efficiency = 0;
            for (Map.Entry<ServiceIdentifier<?>, Double> entry : summary.getServerAverageProcessingTime().entrySet()) {
                efficiency += entry.getValue();
            }

            if (efficiency == 0)
                efficiency = DEFAULT_EFFICIENCY;
        }

        if (DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) {
            LOGGER.info("Efficiency " + efficiency);
            LOGGER.info("Total Demand " + totalDemandMap);
            retrieveLatency(summary);
        }


        if (DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) {} 
        else if(DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())){
            int newIteration = 0;
            if (inbox != null) {
                // initialize newIteration by taking the max iteration from the
                // inbox and increment it
                // newIteration =
                // inbox.getIterationDcopInfoMap().keySet().stream().mapToInt(Integer::intValue).max().getAsInt()
                // + 1;

                final OptionalInt maxIteration = inbox.getItDcopInfoMap().entrySet().stream().map(Map.Entry::getKey)
                        .mapToInt(Integer::intValue).max();
                if (maxIteration.isPresent()) {
                    newIteration = maxIteration.getAsInt() + 1;
                } else {
                    newIteration = 0;
                }
            }
            clearItSelfInbox();
            
            LOGGER.info("*Iteration {} Server Demand REGION {}: {}", newIteration, selfRegionID, summary.getServerDemand());
            LOGGER.info("Iteration {} Server Load: {}", newIteration, summary.getServerLoad());
            LOGGER.info("Iteration {} Server Capacity: {}", newIteration, summary.getServerCapacity());
            LOGGER.info("*Iteration {} Network Demand REGION {}: {}", newIteration, selfRegionID, summary.getNetworkDemand());
            LOGGER.info("Iteration {} Network Load: {}", newIteration, summary.getNetworkLoad());
            LOGGER.info("Iteration {} Network Capacity: {}", newIteration, summary.getNetworkCapacity());

            LOGGER.info("Iteration {} Total Region Capacity: {}", newIteration, regionCapacity);

            for (int iteration = newIteration; iteration < AgentConfiguration.getInstance().getDcopIterationLimit()
                    + newIteration; iteration++) {
                LOGGER.info("Iteration {} Region {} currentStage {} has summary {}", iteration, selfRegionID, currentStage, summary);
                LOGGER.info(
                        "Iteration {} Region {} has outgoingLoadMap {} totalDemandMap {} overloadSharedToOtherMap {} loadAgreeToReceiveFromOtherMap {}",
                        iteration, selfRegionID, outgoingLoadMap, totalDemandMap, overloadSharedToOtherMap,
                        loadAgreeToReceiveFromOtherMap);

                inbox = dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID);
                inbox.removeMessageAtIteration(iteration - NUMBER_OF_PREVIOUS_ITERATION_TO_REMOVE_OLD_MESSAGES);
                // SEND
                if (currentStage == Stage.FREE) {
                    // In FREE stage and nothing to send
                    if (messageToSend == null) {
                        // if overloaded, ask for help
                        // if not overloaded, send stage messages
                        if (compareDouble(availableCapacity(iteration), 0) < 0) {
                            // if overloaded send message type 1 to all neighbors
                            currentStage = Stage.STAGE1_ASKING_FOR_HELP;
                            
                            // send the excessLoadMap
                            double tempCapacity = regionCapacity;
                            
                            for (Entry<ServiceIdentifier<?>, Double> entry : totalDemandMap.entrySet()) {
                                double entryLoad = entry.getValue();
                                if (compareDouble(entryLoad, tempCapacity) <= 0) {
                                    tempCapacity -= entryLoad;
                                } else {
                                    excessLoadMap.put(entry.getKey(), entryLoad - tempCapacity);
                                    tempCapacity = 0;
                                }
                            }
                                                        
                            for (RegionIdentifier neighbor : neighborSet) {
//                                sendMessageType1(neighbor, 1, totalDemandMap, iteration);
                                sendMessageType1(neighbor, 1, excessLoadMap, iteration);
                            }

                            overloaded = IS_OVERLOADED;
//                            totalExcessLoad = -availableCapacity(iteration);
                        }
                        
                        
                        // it remains FREE so send just stage message to all neighbors
                        else {
                            for (RegionIdentifier neighbor : neighborSet) {
                                sendStageMessage(neighbor, iteration);
                            }
                        }
                    } 
                    // In FREE stage and need to forward help message
                    // Can only help partial load
                    else if (messageToSend.getType() == 1) {
                        // need to forward
                        currentStage = Stage.STAGE1_ASKING_FOR_HELP;
                        messageToForward = messageToSend.getMessages().get(parent).get(0);
                        Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                        remaining.addAll(neighborSet);
                        remaining.removeAll(freeNeighbors);
                        
                        // Forward help messageToForward to all free neighbors
                        for (RegionIdentifier neighbor : freeNeighbors) {
                            sendMessageType1(neighbor, messageToForward.getHop(), messageToForward.getLoadMap(), iteration);
                        }
                        
                        // Send stage messages to the other neighbors
                        for (RegionIdentifier neighbor : remaining) {
                            sendStageMessage(neighbor, iteration);
                        }
                    }
                    // receive help message and can help with all the asked load
                    // send the messageToSend back to the parent
                    else if (messageToSend.getType() == 2) {
                        currentStage = Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN;
                        messageToSend.setIteration(iteration);
                        messageToSend.setStage(currentStage);
                        // Reply with help message to the parent
                        sendMessage(messageToSend, iteration);
                        
                        // Send stage to the other neighbors
                        Set<RegionIdentifier> remainingNeighbors = new HashSet<RegionIdentifier>();
                        remainingNeighbors.addAll(neighborSet);
                        remainingNeighbors.remove(parent);
                        for (RegionIdentifier neighbor : remainingNeighbors) {
                            sendStageMessage(neighbor, iteration);
                        }
                    }
                } // end FREE
                // has sent message type 1 to my children
                else if (currentStage == Stage.STAGE1_ASKING_FOR_HELP) {
                    if (overloaded == IS_OVERLOADED && finishCount && children.size() == 0 && parent == null) {
                        // if I am the root and all my neighbors are busy
                        // continue to ask for help
                        for (RegionIdentifier neighbor : neighborSet) {
//                            sendMessageType1(neighbor, 1, totalDemandMap, iteration);
                          sendMessageType1(neighbor, 1, excessLoadMap, iteration);
                        }
                    } 
                    // not all my children answered yet
                    // send messageType1 to freeNeighbors and stage message to the rest
                    else if (finishCount && (numberOfSentType2 < children.size())) {
                        for (RegionIdentifier neighbor : freeNeighbors) {
                            sendMessageType1(neighbor, messageToForward.getHop(), messageToForward.getLoadMap(), iteration);
                        }
                        
                        Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                        remaining.addAll(neighborSet);
                        remaining.removeAll(freeNeighbors);
                        for (RegionIdentifier neighbor : remaining) {
                            sendStageMessage(neighbor, iteration);
                        }
                    } 
                    // have aggregated messages to send to my parent
                    else if (finishCount && (numberOfSentType2 == children.size())) {
                        currentStage = Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN;
                        messageToSend.setIteration(iteration);
                        messageToSend.setStage(currentStage);
                        
                        // Send aggregated message to the parent
                        sendMessage(messageToSend, iteration);
                        
                        Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                        remaining.addAll(neighborSet);
                        remaining.remove(parent);
                        
                        for (RegionIdentifier neighbor : remaining) {
                            sendStageMessage(neighbor, iteration);
                        }
                    }
                    // do not know all my children yet, keep sending stage message to everybody
                    // keep sending stage message
                    else if (!finishCount) {
                        for (RegionIdentifier neighbor : neighborSet) {
                            sendStageMessage(neighbor, iteration);
                        }
                    }
                } // end STAGE1
                // has receive message type 2 from my children, and sent message type 2 to my parent
                else if (currentStage == Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN) {
                    // I am the root, sending the plan to my children
                    if (parent == null && messageToSend != null && messageToSend.getType() == 3) {
                        currentStage = Stage.FREE;
                        
                        // The root creates the PLAN message and send to the children
                        for (DcopInfoMessage info : storedInfoType2List) {
                            messageToSend = new DcopInfoMessage(selfRegionID, info.getSender(),
                                    info.getMessages().get(selfRegionID), iteration, currentStage, parent);
                            messageToSend.setType(3);

                            sendMessage(messageToSend, iteration);

                            LOGGER.info("ITERATION {} REGION {} PLAN FROM ROOT {}", iteration, selfRegionID, messageToSend);
//                            LOGGER.info("info.getMessages().get(selfRegionID) {} ", info.getMessages().get(selfRegionID));
                            
                            // update the overloadSharedToOtherMap
                            for (DcopMessage fromChild : info.getMessages().get(selfRegionID)) {
//                                overloadSharedToOtherMap = overloadSharedToOtherMap + fromChild.getLoadMap();
                                for (Entry<ServiceIdentifier<?>, Double> entry : fromChild.getLoadMap().entrySet()) {
                                    overloadSharedToOtherMap.put(entry.getKey(),
                                            overloadSharedToOtherMap.getOrDefault(entry.getKey(), 0.0)
                                                    + entry.getValue());
                                }
                                
                            }
                            
//                            LOGGER.info("ITERATION {} overloadSharedToOtherMap {}", iteration, overloadSharedToOtherMap);

                            // Update the outgoingLoadMap
                            for (Map.Entry<RegionIdentifier, List<DcopMessage>> entry : messageToSend.getMessages()
                                    .entrySet()) {
                                RegionIdentifier messageReceiver = entry.getKey();
                                List<DcopMessage> messageList = entry.getValue();

                                for (DcopMessage dcopMessage : messageList) {
                                    Map<ServiceIdentifier<?>, Double> outLoadMap = outgoingLoadMap.get(messageReceiver);
//                                    outgoingLoadMap.put(messageReceiver, outgoingLoadMap.get(messageReceiver) + dcopMessage.getLoadMap());
                                    for (Entry<ServiceIdentifier<?>, Double> outLoadEntry : dcopMessage.getLoadMap().entrySet()) {
                                        outLoadMap.put(outLoadEntry.getKey(),
                                                outLoadMap.getOrDefault(outLoadEntry.getKey(), 0.0)
                                                        + outLoadEntry.getValue());
                                    }
                                }
                            }                            
                        }

                        // Send stage message to the rest
                        Set<RegionIdentifier> remainingNeighbors = new HashSet<RegionIdentifier>();
                        remainingNeighbors.addAll(neighborSet);
                        remainingNeighbors.removeAll(children);
                        for (RegionIdentifier neighbor : remainingNeighbors) {
                            sendStageMessage(neighbor, iteration);
                        }

                        empty();
                    } 
                    // In stage2, has received msg from childrent and sent to parent
                    // But I am not the root
                    else {
                        // did not receive answer from my parent yet
                        // then send stage message
                        if (messageToSend == null) {                            
                            for (RegionIdentifier neighbor : neighborSet) {
                                sendStageMessage(neighbor, iteration);
                            }
                        }
                        // has received answer from the parent, then build plan
                        // Build the plan
                        // The storedInfoType2List plan has been changed after receiving messages from the parent
                        // loadMap from the messageToSend is the most updated
                        else if (messageToSend.getType() == 4) {
                            currentStage = Stage.FREE;
                            List<DcopMessage> dcopMessageToSendList = messageToSend.getMessages().get(selfRegionID);
                            
//                            LOGGER.info("BEFORE storedInfoType2List {}", storedInfoType2List);
                            
                            // Modify the loadMap in the storedInfoType2List
                            for (DcopInfoMessage infoType2Stored : storedInfoType2List) {
                                for (DcopMessage messageStoredType2 : infoType2Stored.getMessages().get(selfRegionID)) {
                                    for (DcopMessage messageToSend : dcopMessageToSendList) {
                                        
                                        if (messageStoredType2.getOriginalSender().equals(messageToSend.getOriginalSender())
                                                && messageStoredType2.getHop() == messageToSend.getHop()) {
                                            messageStoredType2.setLoadMap(messageToSend.getLoadMap());
                                        }
                                    }
                                }
                            }
                            
//                            LOGGER.info("AFTER storedInfoType2List {}", storedInfoType2List);
                            
                            // Based on the storedInfoType2List
                            // Modify the outgoingLoadMap
                            
                            for (DcopInfoMessage info : storedInfoType2List) {
                                DcopInfoMessage send = new DcopInfoMessage(selfRegionID, info.getSender(),
                                        info.getMessages().get(selfRegionID), iteration, currentStage, parent);
                                send.setType(4);
                                sendMessage(send, iteration);

                                RegionIdentifier directChildren = info.getSender();
                                for (DcopMessage msg : info.getMessages().get(selfRegionID)) {
                                    Map<ServiceIdentifier<?>, Double> outLoadMap = outgoingLoadMap.get(directChildren);
//                                    outgoingLoadMap.put(directChildren,
//                                            outgoingLoadMap.get(directChildren) + msg.getLoadMap());
                                    for (Entry<ServiceIdentifier<?>, Double> loadEntry : msg.getLoadMap().entrySet()) {
                                        outLoadMap.put(loadEntry.getKey(),
                                                outLoadMap.getOrDefault(loadEntry.getKey(), 0.0)
                                                        + loadEntry.getValue());
                                    }
                                }
                            }
                            
//                            LOGGER.info("outgoingLoadMap {}", outgoingLoadMap);

                            // Send Stage message to the rest
                            Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                            remaining.addAll(neighborSet);
                            remaining.removeAll(children);
                            for (RegionIdentifier neighbor : remaining) {
                                sendStageMessage(neighbor, iteration);
                            }

                            empty();
                        }
                    }
                } // end STAGE2

                /// *********************
                // READ
                // PROCESSING messages
                Set<DcopInfoMessage> receivedMessageSet = null;
                try {
                    receivedMessageSet = checkFreeNeighborsAndChildren(iteration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    LOGGER.warn(
                            "InterruptedException catched when waiting for messages and return the default DCOP plan: {} ",
                            e.getMessage(), e);
                    return defaultPlan(summary);
                }

                // In FREE stage, check if any neighbor is asking for help
                if (currentStage == Stage.FREE) {
                    decide(receivedMessageSet, iteration);
                }
                // In STAGE1_ASKING_FOR_HELP
                // Check message in STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN
                else if (currentStage == Stage.STAGE1_ASKING_FOR_HELP) {
                    // Processing receivedMessage and add to storedInfoType2List
                    for (DcopInfoMessage info : receivedMessageSet) {
                        if (info.getStage() == Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN && info.getParent() != null
                                && info.getParent().equals(selfRegionID)) {
                            
                            for (DcopMessage fromChild : info.getMessages().get(selfRegionID)) {
                                if (!info.getSender().equals(fromChild.getOriginalSender())) {
//                                    fromChild.setLatency(fromChild.getLatency() + latency.get(info.getSender()));
                                }
                            }

                            if (!info.getMessages().get(selfRegionID).isEmpty()) {
                                // store while all children send their messages
                                storedInfoType2List.add(info);
                                numberOfSentType2++;
                            }
                        }
                    } // End processing receiveMessage and storedInfoType2List
                    
                    // if all information received
                    if (!children.isEmpty() && finishCount && numberOfSentType2 == children.size()) {
                        // isLeaf and prepare the plan to send back to the parent
                        // isLeaf so there is only one children?
                        if (leaf) {
                            for (DcopInfoMessage info : receivedMessageSet) {
                                if (!info.getMessages().get(selfRegionID).isEmpty()) {
                                    DcopMessage requestedDcopMessage = info.getMessages().get(selfRegionID).get(0);
                                    
                                    Map<ServiceIdentifier<?>, Double> requestedLoadMap = requestedDcopMessage.getLoadMap();
                                    
                                    double totalExcessLoad = sumValue(requestedLoadMap);
                                    
                                    excessLoadMap.putAll(requestedLoadMap);
                                    
                                    List<DcopMessage> tempList = new ArrayList<DcopMessage>();
                                    
                                    if (compareDouble(availableCapacity(iteration), 0) > 0
                                            && compareDouble(totalExcessLoad, availableCapacity(iteration)) <= 0) {
                                        // Can help all
                                        tempList.add(requestedDcopMessage);
                                    } else if (compareDouble(availableCapacity(iteration), 0) > 0
                                            && compareDouble(totalExcessLoad - availableCapacity(iteration), 0) > 0) {
                                        // Can help the most with the availableCapacity
                                        DcopMessage dcopMsgReplyBack = new DcopMessage(requestedDcopMessage);
                                        
                                        Map<ServiceIdentifier<?>, Double> serviceLoadMapToServe = createLoadMapGiveMaxCapacity(requestedLoadMap, availableCapacity(iteration));
                                        requestedDcopMessage.setLoadMap(serviceLoadMapToServe);
                                        
//                                        tempList.add(requestedDcopMessage);
                                      tempList.add(dcopMsgReplyBack);
                                    }
                                    
                                    requestedDcopMessage.setOriginalSender(selfRegionID);
                                    requestedDcopMessage.setHop(requestedDcopMessage.getHop() - 1);
                                    requestedDcopMessage.setEfficiency(efficiency);
//                                    requestedDcopMessage.setLatency(latency.get(parent));
                                    messageToSend = new DcopInfoMessage(selfRegionID, parent, tempList, -1,
                                            currentStage, parent);
                                    messageToSend.setType(2);
                                }
                            }
                        } 
                        // Non leaf and now aggregating all the messages from all of my children
                        else if (!children.isEmpty()) {
                            aggregateMessages(iteration);
                        }
                        
                        // I am the root
                        if (parent == null && !children.isEmpty()) {
                            currentStage = Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN;
                            messageToSend.setType(3);
                        }
                    }
                } else if (currentStage == Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN) {
                    LOGGER.info("-----STAGE 2 READING MESSSAGES-------");
                    
                    if (parent == null) {
                        // just to know that I need to send a personalized plan for every children
                        messageToSend.setType(3);
                    } else {
                        // waiting for a plan from my parent
                        LOGGER.info("receivedMessageSet {}", receivedMessageSet);
                        
                        for (DcopInfoMessage receivedMessage : receivedMessageSet) {
                            // If message's stage is FREE, then it's a plan from the parent
                            if (receivedMessage.getStage().equals(Stage.FREE) && receivedMessage.getSender().equals(parent)) {
                                if (!receivedMessage.getMessages().get(selfRegionID).isEmpty()) {
//                                    serviceID = info.getServiceID();

//                                    if (messageToSend != null)
//                                        messageToSend.setServiceID(serviceID);
                                    
                                    LOGGER.info("I am a leaf or not {}", leaf);

                                    if (!leaf) {
                                        messageToSend = receivedMessage;
                                        messageToSend.setType(4);
                                        
                                        LOGGER.info("++++receivedMessage.getMessages().get(selfRegionID) {}", receivedMessage.getMessages().get(selfRegionID));

                                        for (DcopMessage msgSentToMe : receivedMessage.getMessages().get(selfRegionID))
                                            if (msgSentToMe.getOriginalSender().equals(selfRegionID)) {
//                                                loadAgreeToReceiveFromOtherMap += msgSentToMe.getLoadMap();
//                                                totalIncomingLoadMap += msgSentToMe.getLoadMap();
                                                for (Entry<ServiceIdentifier<?>, Double> entry : msgSentToMe.getLoadMap().entrySet()) {
                                                    loadAgreeToReceiveFromOtherMap.put(entry.getKey(),
                                                            loadAgreeToReceiveFromOtherMap.getOrDefault(entry.getKey(), 0.0)
                                                                    + entry.getValue());
                                                }
                                                
                                                for (Entry<ServiceIdentifier<?>, Double> entry : msgSentToMe.getLoadMap().entrySet()) {
                                                    totalIncomingLoadMap.put(entry.getKey(),
                                                            totalIncomingLoadMap.getOrDefault(entry.getKey(), 0.0)
                                                                    + entry.getValue());
                                                }
                                                
//                                                break;
                                            }
                                            // this message plan is for my children
                                            else {
//                                                totalIncomingLoadMap += msgSentToMe.getLoadMap();
                                                for (Entry<ServiceIdentifier<?>, Double> entry : msgSentToMe.getLoadMap().entrySet()) {
                                                    totalIncomingLoadMap.put(entry.getKey(),
                                                            totalIncomingLoadMap.getOrDefault(entry.getKey(), 0.0)
                                                                    + entry.getValue());
                                                }
                                            }
                                       break; // to prevent to the last else that set the messageToSend = null;
                                    }   
                                    // Leaf received message
                                    else {
//                                        loadAgreeToReceiveFromOtherMap += receivedMessage.getMessages().get(selfRegionID).get(0).getLoadMap();
                                        for (Entry<ServiceIdentifier<?>, Double> entry : receivedMessage.getMessages().get(selfRegionID).get(0).getLoadMap().entrySet()) {
                                            loadAgreeToReceiveFromOtherMap.put(entry.getKey(),
                                                    loadAgreeToReceiveFromOtherMap.getOrDefault(entry.getKey(), 0.0)
                                                            + entry.getValue());
                                        }
                                        
//                                        totalIncomingLoad += receivedMessage.getMessages().get(selfRegionID).get(0).getLoadMap();
                                        for (Entry<ServiceIdentifier<?>, Double> entry : receivedMessage.getMessages().get(selfRegionID).get(0).getLoadMap().entrySet()) {
                                            totalIncomingLoadMap.put(entry.getKey(),
                                                    totalIncomingLoadMap.getOrDefault(entry.getKey(), 0.0)
                                                            + entry.getValue());
                                        }
                                        
                                        leaves--;
                                        
                                        if (leaves == 0) {
//                                            overloaded = 0;
                                            overloaded = NOT_OVERLOADED;
                                        }
                                        currentStage = Stage.FREE;
//                                        totalExcessLoad = 0;
                                        excessLoadMap.clear();
                                        empty();
                                    }
                                }
                            } else {
                                messageToSend = null;
                            }
                        } // end for
                    }
                }
            } // end of iterations

            // Print out the plan
//            double dcopIncomingLoad = totalIncomingLoadMap + totalDemandMap;
            Map<ServiceIdentifier<?>, Double> dcopIncomingLoadMap = new HashMap<>();
            Set<ServiceIdentifier<?>> allServices = new HashSet<>(totalIncomingLoadMap.keySet());
            allServices.addAll(totalDemandMap.keySet());
            
            for (ServiceIdentifier<?> service : allServices) {
                dcopIncomingLoadMap.put(service, 
                        totalIncomingLoadMap.getOrDefault(service, 0.0) + totalDemandMap.getOrDefault(service, 0.0));
            }

//            double dcopLoadToKeep = compareDouble(totalDemandMap, regionCapacity) >= 0
//                    ? (loadAgreeToReceiveFromOtherMap + regionCapacity)
//                    : (loadAgreeToReceiveFromOtherMap + totalDemandMap);
            
            Map<ServiceIdentifier<?>, Double> dcopLoadToKeep = new HashMap<>();
            if (compareDouble(totalDemandMap.values().stream().mapToDouble(Double::doubleValue).sum(), regionCapacity) >= 0) {
                double tempCapacity = regionCapacity;
                
                for (ServiceIdentifier<?> service : allServices) {
                    double demand = totalDemandMap.getOrDefault(service, 0.0);
                    
                    if (compareDouble(demand, tempCapacity) <= 0) {
                        dcopLoadToKeep.put(service, loadAgreeToReceiveFromOtherMap.getOrDefault(service, 0.0) + demand);
                        tempCapacity -= demand;
                    } else {
                        dcopLoadToKeep.put(service, loadAgreeToReceiveFromOtherMap.getOrDefault(service, 0.0) + tempCapacity);
                        tempCapacity = 0;
                    }
                }
            } else {
                for (ServiceIdentifier<?> service : allServices) {
                    dcopLoadToKeep.put(service, 
                            loadAgreeToReceiveFromOtherMap.getOrDefault(service, 0.0) + totalDemandMap.getOrDefault(service, 0.0));
                }
            }

            LOGGER.info("Iteration {} Region {} has Total incomingLoad {} ", AgentConfiguration.getInstance().getDcopIterationLimit()
                    + newIteration - 1, selfRegionID, dcopIncomingLoadMap);
            LOGGER.info("Iteration {} Region {} has Load to keep {} ", AgentConfiguration.getInstance().getDcopIterationLimit()
                    + newIteration - 1, selfRegionID, dcopLoadToKeep);
            LOGGER.info("Iteration {} Region {} has Outgoing load map: {}", AgentConfiguration.getInstance().getDcopIterationLimit()
                    + newIteration - 1, selfRegionID, outgoingLoadMap);

            // Return default plan if totalLoads coming is 0
            double dcopIncomingLoad = dcopIncomingLoadMap.values().stream().mapToDouble(Double::doubleValue).sum();
            if (compareDouble(dcopIncomingLoad, 0) == 0) {
                RegionPlan defaultRegionPlan = defaultPlan(summary);
                LOGGER.info("Iteration {} Region {} REGION PLAN {}", AgentConfiguration.getInstance().getDcopIterationLimit()
                        + newIteration - 1, selfRegionID, defaultRegionPlan);
                return defaultRegionPlan;
            }

            Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
            Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();

            for (ServiceIdentifier<?> service : allServices) {
                regionPlanBuilder = new Builder<>();
                
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : outgoingLoadMap.entrySet()) {
                    RegionIdentifier neighbor = entry.getKey();
                    
                    regionPlanBuilder.put(neighbor, 1.0 * entry.getValue().getOrDefault(service, 0.0) / dcopIncomingLoadMap.get(service));
                }

                regionPlanBuilder.put(selfRegionID, 1.0 * dcopLoadToKeep.getOrDefault(service, 0.0) / dcopIncomingLoadMap.get(service));

                ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();

                servicePlanBuilder.put(service, regionPlan);
            }
            
            
//            for (Map.Entry<RegionIdentifier, Double> entry : outgoingLoadMap.entrySet()) {
//                regionPlanBuilder.put(entry.getKey(), 1.0 * entry.getValue() / dcopIncomingLoad);
//            }
//
//            regionPlanBuilder.put(selfRegionID, 1.0 * dcopLoadToKeep / dcopIncomingLoad);
//
//
//            ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
//
//            servicePlanBuilder.put(serviceID, regionPlan);

            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
                    .build();

            LOGGER.info("Iteration {} Region {} REGION PLAN {}", AgentConfiguration.getInstance().getDcopIterationLimit()
                    + newIteration - 1, selfRegionID, dcopPlan.toString());

            final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);

            return rplan;
        }
        else if(DcopAlgorithm.DISTRIBUTED_PRIORITY_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())){
//            int newIteration;
            WeightedAlgorithm algo;
               algo = new WeightedAlgorithm(summary, inbox, dcopInfoProvider, selfRegionID, regionCapacity, applicationManager, neighborSet);
//            newIteration = algo.initializeIteration();
//            RegionPlan plan = algo.run(newIteration);
            RegionPlan plan = algo.run(lastIteration);
            lastIteration = algo.getInbox().getMessageAtIteration(-1).getMessageForThisReceiver(selfRegionID).getLastIteration()+1;
            if(!algo.getTree().isEmpty())
                inbox = algo.getInbox();
            if(plan == null) return defaultPlan(summary);
            else return plan;
        }
        return null;
    }

    /**
     * @postcondition the availableCapacity is greater than sum of the load from the requestdLoadMap
     * @param requestedLoadMap
     * @param availableCapacity
     * @return
     */
    private Map<ServiceIdentifier<?>, Double> createLoadMapGiveMaxCapacity(
            Map<ServiceIdentifier<?>, Double> requestedLoadMap,
            double availableCapacity) {
        Map<ServiceIdentifier<?>, Double> loadMapToServe = new HashMap<>(requestedLoadMap);
        
        double currentCapacity = availableCapacity;
        
        for (Entry<ServiceIdentifier<?>, Double> entry : loadMapToServe.entrySet()) {
            double entryLoad = entry.getValue();
            
            // The askedLoad is smaller than the currentCapacity
            // Serve with the current askedLoad, reduce the currentCapacity
            if (compareDouble(entryLoad, currentCapacity) < 0) {
                currentCapacity -= entryLoad;
            } 
            // Serve with the current capacity
            // Set currentCapcity to 0
            else {
                entry.setValue(currentCapacity);
                currentCapacity = 0;
            }
        }
        
        return loadMapToServe;

    }

    /**
     * Update incoming and outgoing load map.
     * 
     * @param iteration
     *            of the DCOP round
     * @param receivedMessageMapInput
     *            is the messages received from neighbors
     */
    private void updateIncomingOutgoingLoadMaps(int iteration,
            Map<RegionIdentifier, DcopMessage> receivedMessageMapInput) {
        LOGGER.info("Iteration {} Region {} BEFORE UPDATING INCOMING {}", iteration, selfRegionID,
                serviceIncomingLoadMap);
        LOGGER.info("Iteration {} Region {} BEFORE UPDATING OUTGOING {}", iteration, selfRegionID,
                serviceOutgoingLoadMap);
        LOGGER.info("Iteration {} Region {} BEFORE UPDATING LOAD-TO-KEEP {}", iteration, selfRegionID,
                serviceLoadToKeepMap);

        Map<ServiceIdentifier<?>, Double> excessLoadMap = new HashMap<>();
        // Create the excessLoadMap: service -> load
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : excessLoadMapToParent.entrySet()) {
            for (Entry<ServiceIdentifier<?>, Double> entry2 : entry.getValue().entrySet()) {
                double excessLoad = excessLoadMap.getOrDefault(entry2.getKey(), 0.0) + entry2.getValue();
                excessLoadMap.put(entry2.getKey(), excessLoad);
            }
        }

        // Create the loadFromParent: parent -> load
        Map<ServiceIdentifier<?>, Double> loadFromParent = new HashMap<>();
        for (Entry<RegionIdentifier, DcopMessage> messageEntry : receivedMessageMapInput.entrySet()) {
            RegionIdentifier parent = messageEntry.getKey();
            if (parentServicesMap.containsKey(parent)) {
                DcopMessage dcopMsg = messageEntry.getValue();
                if (dcopMsg.getType() == MessageType.PARENT_TO_CHILDREN && null != dcopMsg.getClientDemandMap()) {
                    for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : dcopMsg.getClientDemandMap()
                            .entrySet()) {
                        // skip the source
                        for (Entry<ServiceIdentifier<?>, Double> entry2 : entry.getValue().entrySet()) {
                            double load = entry2.getValue() + loadFromParent.getOrDefault(entry2.getKey(), 0.0);
                            loadFromParent.put(entry2.getKey(), load);
                        }
                    }
                }
            }
        }

        LOGGER.info("Iteration {} Region {} BEFORE UPDATING EXCESS {}", iteration, selfRegionID, excessLoadMap);
        LOGGER.info("Iteration {} Region {} BEFORE UPDATING LEAF {}", iteration, selfRegionID, leafServices);

        for (Entry<ServiceIdentifier<?>, Double> entry : excessLoadMap.entrySet()) {
            ServiceIdentifier<?> service = entry.getKey();
            double excessLoad = entry.getValue();

            // non-leaf
            if (!leafServices.contains(service)) {
                if (compareDouble(excessLoad, 0) > 0) {
                    // update the incoming load map
                    // correct if no load from children
                    double updatedIncomingLoad = serviceIncomingLoadMap.getOrDefault(service, 0.0) - excessLoad;
                    serviceIncomingLoadMap.put(service, updatedIncomingLoad);

                    // if excessLoad is larger than loads from parent => update
                    // the outgoing load map
                    if (compareDouble(excessLoad, loadFromParent.getOrDefault(service, 0.0)) > 0) {
                        double outgoingLoad = excessLoad - loadFromParent.get(service);
                        for (RegionIdentifier parent : parentServicesMap.keySet()) {
                            double updatedOutgoingLoad = serviceOutgoingLoadMap.get(service).get(parent) + outgoingLoad;
                            serviceOutgoingLoadMap.computeIfAbsent(service, s -> new HashMap<>()).put(parent,
                                    updatedOutgoingLoad);
                        }
                    }
                }
            } else { // leaf region
                     // which also means excessLoad > 0
                if (compareDouble(excessLoad, loadFromParent.getOrDefault(service, 0.0)) > 0) {
                    serviceIncomingLoadMap.put(service, sourceServiceMapToThisServer.get(selfRegionID).get(service));

                    double outgoingLoad = excessLoad - loadFromParent.getOrDefault(service, 0.0);
                    for (RegionIdentifier parent : parentServicesMap.keySet()) {
                        double updatedOutgoingLoad = 0;
                        if (serviceOutgoingLoadMap.containsKey(service)) {
                            updatedOutgoingLoad = serviceOutgoingLoadMap.get(service).getOrDefault(parent, 0.0)
                                    + outgoingLoad;
                        }
                        serviceOutgoingLoadMap.computeIfAbsent(service, s -> new HashMap<>()).put(parent,
                                updatedOutgoingLoad);
                    }

                } else {
                    double updatedIncomingLoad = serviceIncomingLoadMap.getOrDefault(service, 0.0) - excessLoad;
                    serviceIncomingLoadMap.put(service, updatedIncomingLoad);
                }
            }

            // if excessLoad is smaller than loadFromParent
            // if (compareDouble(excessLoad,
            // loadFromParent.getOrDefault(service, 0.0)) <= 0) {
            // double updatedIncomingLoad =
            // serviceIncomingLoadMap.getOrDefault(service, 0.0) - excessLoad;
            // serviceIncomingLoadMap.put(service, updatedIncomingLoad);
            // } else { // non-leaf regions where excessLoad is bigger than
            // loadFromParent
            // double outgoingLoad = excessLoad - loadFromParent.get(service);
            //
            // // HACK FOR ONE PARENT
            // for (RegionIdentifier parent : parentServicesMap.keySet()) {
            // serviceOutgoingLoadMap.computeIfAbsent(service, s -> new
            // HashMap<>()).put(parent, outgoingLoad);
            // }
            //
            // // LEAF REGION of this service
            // if (leafServices.contains(service)) {
            // // update incomingLoad from receivedMessageMap
            // serviceIncomingLoadMap.put(service,
            // sourceServiceMapToThisServer.get(selfRegionID).get(service));
            // } else {
            // // update incomingLoad from receivedMessageMap
            // for (Entry<RegionIdentifier, DcopMessage> messageEntry :
            // receivedMessageMapInput.entrySet()) {
            // RegionIdentifier child = messageEntry.getKey();
            // DcopMessage dcopMsg = messageEntry.getValue();
            // if (dcopMsg.getType() == MessageType.LOAD_TO_PARENT && null !=
            // dcopMsg.getExcessLoadMapToParent()) {
            // if (childrenMap.containsKey(child)) {
            // double loadFromChildren =
            // dcopMsg.getExcessLoadMapToParent().getOrDefault(service, 0.0)
            // - loadFromParent.get(service);
            // serviceIncomingLoadMap.put(service, loadFromChildren);
            // }
            // }
            // }
            // }
            // }
        }
    }
    
    /**
     * This function is used to replaced the compareDouble(double, double)
     * If |a - b| is <= DOUBLE_TOLERANCE, then return 0; // a == b
     * @param a
     * @param b
     * @return
     */
    private int compareDouble(double a, double b) {
        double absDiff = Math.abs(a - b);
        
        if (Double.compare(absDiff, DOUBLE_TOLERANCE) <= 0) return 0;
        
        return Double.compare(a, b);
    }


    /**
     * Read tree information from the previous round.
     */
    private void readPreviousTree() {
        Map<RegionIdentifier, DcopMessage> receivedMessageMap;
        try {
            receivedMessageMap = waitForMessagesFromNeighbors(TREE_ITERATION, DcopConstants.READING_TREE);
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.warn("InterruptedException when reading the DCOP from previous run messages {} ", e.getMessage(), e);
            return;
        }

        LOGGER.info("TREE message read");

        // Assume that this is not null
        // Copying using loop can avoid the communication bug from Jon's
        // framework
        childrenMap = new HashMap<>();
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entry : receivedMessageMap
                .get(selfRegionID).getChildrenMap().entrySet()) {
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceMap = new HashMap<>();
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry2 : entry.getValue().entrySet()) {
                Map<ServiceIdentifier<?>, Double> newServiceLoad = new HashMap<>();
                for (Entry<ServiceIdentifier<?>, Double> entry3 : entry2.getValue().entrySet()) {
                    newServiceLoad.put(entry3.getKey(), entry3.getValue());
                }
                sourceServiceMap.put(entry2.getKey(), newServiceLoad);
            }
            childrenMap.put(entry.getKey(), sourceServiceMap);
        }

        // Copying parentServiceMap using loops to avoid potential bug in
        // communication
        parentServicesMap = new HashMap<>();
        for (Entry<RegionIdentifier, Set<ServiceIdentifier<?>>> entry : receivedMessageMap.get(selfRegionID)
                .getParentServicesMap().entrySet()) {
            Set<ServiceIdentifier<?>> serviceSet = new HashSet<>();
            for (ServiceIdentifier<?> service : entry.getValue()) {
                serviceSet.add(service);
            }
            parentServicesMap.put(entry.getKey(), serviceSet);
        }

        // parentServicesMap = new
        // HashMap<>(receivedMessageMap.get(selfRegionID).getParentServicesMap());

        LOGGER.info("Children from TREE: {}", childrenMap);
        LOGGER.info("Parent from TREE: {}", parentServicesMap);
    }

    /**
     * Write parent/children information to be used in next DCOP round.
     */
    private void writeTreeInfomation() {
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenRegion : childrenMap
                .entrySet()) {
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> client : childrenRegion.getValue()
                    .entrySet()) {
                for (Entry<ServiceIdentifier<?>, Double> loadSent : client.getValue().entrySet()) {
                    loadSent.setValue(0.0);
                }
            }
        }

        DcopMessage dcopMsg = new DcopMessage(parentServicesMap, childrenMap, MessageType.TREE);
        sendMessage(selfRegionID, TREE_ITERATION, dcopMsg);
    }

    /**
     * To clear self mailbox from previous DCOP round. This can prevent delete
     * other's mailbox while they're reading their trees.
     */
    private void clearSelfInbox() {
        dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID).clear();
    }

    /**
     * To clear self mailbox from previous DCOP round. This can prevent delete
     * other's mailbox while they're reading their trees.
     */
    private void clearItSelfInbox() {
        dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID).clearItDcopInfomap();
    }

    /*/**
     * Compute {@link #sourceServiceMapToThisServer} from serverDemand. <br>
     * Compute {@link #serviceIncomingLoadMap} from serverDemand <br>
     * Initialize {@link #serviceLoadToKeepMap} to zeros <br>
     * 
     * @param summary
     */
   /* private void initServiceIncomingAndKeepMap(ResourceSummary summary) {
        // traverse the serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry : summary
                .getServerDemand().entrySet()) {
            ServiceIdentifier<?> service = entry.getKey();
            double incomingLoadGivenService = 0;
            // traverse the source -> noteAttribte -> load
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> regionDemandEntry : entry.getValue()
                    .entrySet()) {
                RegionIdentifier sourceRegion = regionDemandEntry.getKey();
                Double loadValue = regionDemandEntry.getValue().get(NodeMetricName.TASK_CONTAINERS);
                sourceServiceMapToThisServer.computeIfAbsent(sourceRegion, key -> new HashMap<>()).put(service,
                        loadValue);
                incomingLoadGivenService += loadValue;
            }
            serviceIncomingLoadMap.put(service, incomingLoadGivenService);
            // By default, regions push all the incoming load to its children
            serviceLoadToKeepMap.put(service, 0.0);
        }

        LOGGER.info("The ROOT has clientServiceMap {}", sourceServiceMapToThisServer);
        LOGGER.info("The ROOT has serviceIncomingLoadMap {}", serviceIncomingLoadMap);
    }*/
    
    /**
     * Compute {@link #sourceServiceMapToThisServer} from serverDemand. <br>
     * Compute {@link #serviceIncomingLoadMap} from serverDemand <br>
     * Initialize {@link #serviceLoadToKeepMap} to zeros <br>
     * Changed to avoid reading source region from Server Demand
     * @precondition Needs totalServerDemand and totalNetworkDemand Computed
     * @param summary
     */
    private void initServiceIncomingAndKeepMap(ResourceSummary summary) {
        // infer the source
        Map<RegionIdentifier,Map<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>> recClientServiceInferredLoad = findSourceClient(summary);
        for(Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entry1:recClientServiceInferredLoad.entrySet()){
            for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceLoad:entry1.getValue().entrySet()){
                RegionIdentifier sourceRegion = sourceServiceLoad.getKey();
                for(Entry<ServiceIdentifier<?>, Double> serviceLoad:sourceServiceLoad.getValue().entrySet()){
                    ServiceIdentifier<?> service = serviceLoad.getKey();
                    //Assume no two neighbors have the same source client request
                    sourceServiceMapToThisServer.computeIfAbsent(sourceRegion, key -> new HashMap<>()).put(service, serviceLoad.getValue());
                    double currentLoad = serviceIncomingLoadMap.getOrDefault(service, 0.0);
                    if(compareDouble(currentLoad, 0.0) > 0) serviceIncomingLoadMap.put(service, serviceLoad.getValue() + currentLoad);
                    else serviceIncomingLoadMap.put(service, serviceLoad.getValue());
                    // By default, regions push all the incoming load to its children
                    serviceLoadToKeepMap.putIfAbsent(service, 0.0);
                }
            }
        }
        
        LOGGER.info("The ROOT has clientServiceMap {}", sourceServiceMapToThisServer);  
        LOGGER.info("The ROOT has serviceIncomingLoadMap {}", serviceIncomingLoadMap);
    }

    /**
     * From serverDemand INFER the clientsPer service and an amount of load for each one
     * using networkDemand
     * @param summary
     * @return neighbor->inferredClientSource->service->load
     * Equivalent to ServerDemand
     */
    private Map<RegionIdentifier,Map<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>> findSourceClient(ResourceSummary summary){
        Map<RegionIdentifier,Map<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>> recClientServiceInferredLoad =
                new HashMap<RegionIdentifier,Map<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>>();
        //neighbor->sourceClient->service->load
        for(Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> entryNet:
            summary.getNetworkDemand().entrySet()){
            RegionIdentifier receiver = entryNet.getKey();
          //sourceClient -> service -> load
            Map<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>sourceServiceLoad = new HashMap<RegionIdentifier, Map<ServiceIdentifier<?>,Double>>();
            for(Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>
            clientService:entryNet.getValue().entrySet()){
                RegionIdentifier source = clientService.getKey();
                Map<ServiceIdentifier<?>,Double>serviceInferedLoad = new HashMap<ServiceIdentifier<?>,Double>();
                for(Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceLoad : clientService.getValue().entrySet()){
                     ServiceIdentifier<?> service = serviceLoad.getKey();   
                     double tx = -1;
                    for(Entry<LinkAttribute<?>, Double> link:serviceLoad.getValue().entrySet()){
                        if(link.getKey().toString().contains("TX")) {
                            tx = link.getValue(); 
                        }else if(link.getKey().toString().contains("RX") && compareDouble(tx,link.getValue()) <= 0){//if it is receiving
                            double loadPerClient = tx*totalServerDemand.getOrDefault(service, 0.0)/totalNetworkDemand.getOrDefault(service, 1.0);
                            serviceInferedLoad.put(service, loadPerClient);
                           
                        }
                    } 
                   
                 }
                
                sourceServiceLoad.put(source, serviceInferedLoad);
            }
            recClientServiceInferredLoad.put(receiver, sourceServiceLoad);
            LOGGER.info("*** inferred demand "+ recClientServiceInferredLoad);
        }
        return recClientServiceInferredLoad;
    }
    
    /**
     * Compute the total load received from serverDemand per service
     * will be equivalent to value received in GLOBAL
     * @param summary
     * @return
     */
    private  void computeTotalServerDemand(ResourceSummary summary){
        double totalLoad = 0.0;
        //from serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry : summary.getServerDemand().entrySet()) {
            if(entry != null){
                ServiceIdentifier<?> service = entry.getKey();
                for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> innerEntry : entry.getValue().entrySet()) {
                    if(innerEntry != null){
                        totalLoad += innerEntry.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0.0);
                        totalServerDemand.put(service, totalLoad);               
                    }//end if innerEntry != null
                }//end for
            }
        }
        
    }
    
    /**
     * Compute total load from networkDemand
     * used to infer the client source
     * @param summary
     */
    private void computeTotalNetworkDemand(ResourceSummary summary){
        //from networkDemand
        for(Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> entryNet:
            summary.getNetworkDemand().entrySet()){
            if(entryNet != null)
            for(Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> clientService:entryNet.getValue().entrySet()){
                if(clientService != null)   
                for(Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceLoad : clientService.getValue().entrySet()){
                    if(serviceLoad != null){
                    ServiceIdentifier<?> service = serviceLoad.getKey();
                    double tx = -1;
                    
                    for(Entry<LinkAttribute<?>, Double> link:serviceLoad.getValue().entrySet()){
                        if(link.getKey().toString().contains("TX")){
                            
                            tx = link.getValue();
                        }else if(link.getKey().toString().contains("RX") && compareDouble(tx,link.getValue()) <= 0){//if it is receiving or RX==TX 
                            double temp = totalNetworkDemand.getOrDefault(service,0.0);
                            temp += tx;
                            totalNetworkDemand.put(service, temp);
                            
                        }
                    }
                    }
                        
                }
            }
        }//end networkDemand
    }

    /**
     * @param iteration
     *            the current iteration
     * @param receivedMessageMapInput
     *            all the received messages at this iteration
     * @param summary
     */
    private void updateLoadMapsFromChildren(int iteration, Map<RegionIdentifier, DcopMessage> receivedMessageMapInput) {
        double tempCapacity = regionCapacity;
        // resetting the serviceLoadToKeepMap
        for (Entry<ServiceIdentifier<?>, Double> entry : serviceLoadToKeepMap.entrySet()) {
            entry.setValue(0.0);
        }

        // resetting the excessLoadMapToParent
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : excessLoadMapToParent.entrySet()) {
            for (Entry<ServiceIdentifier<?>, Double> entry2 : entry.getValue().entrySet()) {
                entry2.setValue(0.0);
            }
        }

        for (Entry<RegionIdentifier, DcopMessage> msgEntry : receivedMessageMapInput.entrySet()) {
            RegionIdentifier child = msgEntry.getKey();
            DcopMessage dcopMsg = msgEntry.getValue();
            // Process messages from children only
            if (dcopMsg.getType() == MessageType.LOAD_TO_PARENT && null != dcopMsg.getExcessLoadMapToParent()) {
                // If this message is from the children
                if (childrenMap.containsKey(child)) {
                    for (Entry<ServiceIdentifier<?>, Double> entry : dcopMsg.getExcessLoadMapToParent().entrySet()) {
                        ServiceIdentifier<?> service = entry.getKey();
                        double loadPushedBackFromChildren = entry.getValue();

                        // Update the outgoingLoadMap to Children
                        // By default, sending all loads to children.
                        // Now after receiving load back from children
                        // this agent has to update the load sent to the
                        // children
                        if (compareDouble(serviceOutgoingLoadMap.get(service).get(child), 0) > 0) {
                            double updatedLoad = serviceOutgoingLoadMap.get(service).get(child)
                                    - loadPushedBackFromChildren;
                            if (compareDouble(updatedLoad, 0) >= 0) {
                                serviceOutgoingLoadMap.get(service).put(child, updatedLoad);
                            } else {
                                // update outgoing to 0
                                serviceOutgoingLoadMap.get(service).put(child, 0.0);
                                // add up the incoming load map
                                double updatedIncomingLoad = serviceIncomingLoadMap.getOrDefault(service, 0.0)
                                        + Math.abs(updatedLoad);
                                serviceIncomingLoadMap.put(service, updatedIncomingLoad);
                            }
                        } else if (compareDouble(serviceOutgoingLoadMap.get(service).get(child), 0) == 0) {
                            // add up the incoming load map
                            double updatedIncomingLoad = serviceIncomingLoadMap.getOrDefault(service, 0.0)
                                    + loadPushedBackFromChildren;
                            serviceIncomingLoadMap.put(service, updatedIncomingLoad);
                        }

                        // Now update the serviceLoadToKeepMap
                        if (compareDouble(loadPushedBackFromChildren, tempCapacity) < 0) {
                            double excessLoad = 0;
                            serviceLoadToKeepMap.put(service,
                                    serviceLoadToKeepMap.getOrDefault(service, 0.0) + loadPushedBackFromChildren);
                            computeExcessLoadMap(service, excessLoad);
                            tempCapacity -= loadPushedBackFromChildren;
                        } else { // Can't keep all the load
                            double excessLoad = loadPushedBackFromChildren - tempCapacity;
                            serviceLoadToKeepMap.put(service,
                                    serviceLoadToKeepMap.getOrDefault(service, 0.0) + tempCapacity);
                            computeExcessLoadMap(service, excessLoad);
                        }
                    }
                }
            }
        }
    }

    /**
     * After receiving messages from children, update the loadToKeep and
     * ExcessLoadMap in order to send it to parent in the next iteration
     * 
     * @param iteration
     * @param receivedMessageMapInput
     *            messages receives from neighbors
     */
    private void computeLoadToKeepAndExcessLoadMapForLeaf(int iteration,
            Map<RegionIdentifier, DcopMessage> receivedMessageMapInput) {
        double tempCapacity = regionCapacity;

        // source -> service -> load
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : sourceServiceMapToThisServer
                .entrySet()) {
            RegionIdentifier sourceFromRequest = entry.getKey();
            Map<ServiceIdentifier<?>, Double> serviceLoad = entry.getValue();

            // THIS IS THE LEAF REGION OF THIS SERVICE
            // updating incomingLoad and excessLoad
            if (sourceFromRequest.equals(selfRegionID) || leafServices.containsAll(serviceLoad.keySet())) {
                for (ServiceIdentifier<?> service : serviceLoad.keySet()) {
                    leafServices.add(service);
                }

                for (Entry<ServiceIdentifier<?>, Double> entry2 : serviceLoad.entrySet()) {
                    ServiceIdentifier<?> serviceFromRequest = entry2.getKey();
                    double loadRequested = entry2.getValue();
                    // Able to keep the load
                    if (compareDouble(loadRequested, tempCapacity) < 0) {
                        serviceLoadToKeepMap.put(serviceFromRequest,
                                serviceLoadToKeepMap.getOrDefault(serviceFromRequest, 0.0) + loadRequested);
                        computeExcessLoadMap(serviceFromRequest, 0);
                        tempCapacity -= loadRequested;
                    } else {
                        serviceLoadToKeepMap.put(serviceFromRequest,
                                serviceLoadToKeepMap.getOrDefault(serviceFromRequest, 0.0) + tempCapacity);
                        computeExcessLoadMap(serviceFromRequest, loadRequested - tempCapacity);
                        tempCapacity = 0;
                    }
                }
            }
        }

        for (Entry<RegionIdentifier, DcopMessage> msgEntry : receivedMessageMapInput.entrySet()) {
            RegionIdentifier sender = msgEntry.getKey();
            DcopMessage dcopMsg = msgEntry.getValue();
            // Process loadToKeep and excessLoadMap for the leaf region
            if (parentServicesMap.containsKey(sender) && dcopMsg.getType() == MessageType.PARENT_TO_CHILDREN) {
                // This agent is one of the agents sending some requests to the
                // server through its parent
                if (dcopMsg.getClientDemandMap().containsKey(selfRegionID)) {
                    for (Entry<ServiceIdentifier<?>, Double> entry : dcopMsg.getClientDemandMap().get(selfRegionID)
                            .entrySet()) {
                        ServiceIdentifier<?> service = entry.getKey();
                        leafServices.add(service);

                        double loadAskingFromParent = entry.getValue();
                        // Able to keep the load
                        if (compareDouble(loadAskingFromParent, tempCapacity) < 0) {
                            serviceLoadToKeepMap.put(service,
                                    serviceLoadToKeepMap.getOrDefault(service, 0.0) + loadAskingFromParent);
                            computeExcessLoadMap(service, 0);
                            tempCapacity -= loadAskingFromParent;
                        } else {
                            serviceLoadToKeepMap.put(service,
                                    serviceLoadToKeepMap.getOrDefault(service, 0.0) + tempCapacity);
                            computeExcessLoadMap(service, loadAskingFromParent - tempCapacity);
                            tempCapacity = 0;
                        }
                    }
                }
            }
        }
    }

    /**
     * This excessLoadMap is used to push excessLoads to parent
     * 
     * @param service
     * @param excessLoad
     *            is the excessLoad needed to push back to its parent
     */
    private void computeExcessLoadMap(ServiceIdentifier<?> service, double excessLoad) {
        for (Entry<RegionIdentifier, Set<ServiceIdentifier<?>>> parentServiceEntry : parentServicesMap.entrySet()) {
            RegionIdentifier parent = parentServiceEntry.getKey();
            Set<ServiceIdentifier<?>> servicesGivenParent = parentServiceEntry.getValue();

            if (servicesGivenParent.contains(service)) {
                // COMPUTE excessLoadMapToParent
                // Add up or create new entry
                if (excessLoadMapToParent.containsKey(parent)) {
                    Map<ServiceIdentifier<?>, Double> serviceLoad = excessLoadMapToParent.get(parent);
                    double tempLoad = serviceLoad.getOrDefault(service, 0.0) + excessLoad;
                    serviceLoad.put(service, tempLoad);
                } else {
                    excessLoadMapToParent.computeIfAbsent(parent, p -> new HashMap<>()).put(service, excessLoad);
                }
            }
        }
    }

    /**
     * Create the childrenMap: neighbor -> sourceRegion -> service -> load
     * 
     * @param requestMapInput
     * @param summaryInput
     */
    private void computeChildrenMap(Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> requestMapInput,
            ResourceSummary summaryInput) {
        // traverse through networkDemand, find if the service is contained in
        // requestMap
        // neighbor -> source -> service -> link -> networkLoad
        for (Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> networkEntry : summaryInput
                .getNetworkDemand().entrySet()) {
            RegionIdentifier neighborFromNetwork = networkEntry.getKey();

            // create neighbor -> source region -> service -> load
            for (Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> demandEntryGivenNeighbor : networkEntry
                    .getValue().entrySet()) {
                RegionIdentifier sourceFromNetwork = demandEntryGivenNeighbor.getKey();
                if (sourceFromNetwork.equals(selfRegionID))
                    continue;

                ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceMapGivenSoureRegion = demandEntryGivenNeighbor
                        .getValue();

                // Now support multiple services
                for (Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceEntry : serviceMapGivenSoureRegion
                        .entrySet()) {
                    ServiceIdentifier<?> serviceFromNetwork = serviceEntry.getKey();

                    // This service is forwarded to parent
                    if (parentServicesMap.containsKey(neighborFromNetwork)) {
                        if (parentServicesMap.get(neighborFromNetwork).contains(serviceFromNetwork)) {
                            continue;
                        }
                    }

                    // Now we have neighbor, source and service from network
                    // traverse the requestMapInput:
                    // source -> service -> serverLoad
                    for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> requestMapEntry : requestMapInput
                            .entrySet()) {
                        RegionIdentifier sourceFromRequest = requestMapEntry.getKey();

                        Map<ServiceIdentifier<?>, Double> serviceLoadMapFromRequestMap = requestMapEntry.getValue();

                        // The neighbor with this source and service is the
                        // child
                        if (sourceFromRequest.equals(sourceFromNetwork)
                                && serviceLoadMapFromRequestMap.containsKey(serviceFromNetwork)) {
                            // modify the childrenMap here: children -> source
                            // -> service -> load
                            // The childrenMap has contained this child
                            if (childrenMap.containsKey(neighborFromNetwork)) {
                                // source -> service -> load
                                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceLoadFromChildren = childrenMap
                                        .get(neighborFromNetwork);
                                // sourceServiceLoadFromChildren has contained
                                // sourceFromRequest
                                if (sourceServiceLoadFromChildren.containsKey(sourceFromRequest)) {
                                    Map<ServiceIdentifier<?>, Double> serviceLoadFromChildrenGivenSource = sourceServiceLoadFromChildren
                                            .get(sourceFromRequest);
                                    double load = serviceLoadFromChildrenGivenSource.getOrDefault(serviceFromNetwork,
                                            0.0);
                                    load += serviceLoadMapFromRequestMap.get(serviceFromNetwork);
                                    serviceLoadFromChildrenGivenSource.put(serviceFromNetwork, load);
                                } else {
                                    // Map<ServiceIdentifier<?>, Double>
                                    // newServiceLoadMap = new HashMap<>();
                                    // newServiceLoadMap.put(serviceFromNetwork,
                                    // serviceLoadMapFromRequestMap.get(serviceFromNetwork));
                                    sourceServiceLoadFromChildren.put(sourceFromRequest, serviceLoadMapFromRequestMap);
                                }
                            } else {
                                Map<ServiceIdentifier<?>, Double> newServiceLoadMap = new HashMap<>();
                                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> newSourceServiceLoad = new HashMap<>();
                                newServiceLoadMap.put(serviceFromNetwork,
                                        serviceLoadMapFromRequestMap.get(serviceFromNetwork));
                                newSourceServiceLoad.put(sourceFromRequest, newServiceLoadMap);
                                childrenMap.put(neighborFromNetwork, newSourceServiceLoad);
                            }
                        } // end of if matching source and service
                    } // end of for loop of requestMapInput
                } // end of for loop of serviceGivenSoureRegion
            } // end of for loop of demandEntryGivenNeighbor
        } // end of for loop of networkDemand

        // CHECKING if there is any source -> service -> 0.0 in children
        // If there exists such an entry, set value as found from the
        // requestMapInput
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entryChildren : childrenMap
                .entrySet()) {
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceLoadChildren : entryChildren
                    .getValue().entrySet()) {
                RegionIdentifier sourceFromChildrenMap = sourceServiceLoadChildren.getKey();
                for (Entry<ServiceIdentifier<?>, Double> serviceLoadChildren : sourceServiceLoadChildren.getValue()
                        .entrySet()) {
                    ServiceIdentifier<?> serviceFromChildrenMap = serviceLoadChildren.getKey();
                    double loadFromChildrenMap = serviceLoadChildren.getValue();
                    if (compareDouble(loadFromChildrenMap, 0) == 0) {
                        if (requestMapInput.containsKey(sourceFromChildrenMap)) {
                            Map<ServiceIdentifier<?>, Double> sourceLoadFromRequest = requestMapInput
                                    .get(sourceFromChildrenMap);
                            if (sourceLoadFromRequest.containsKey(serviceFromChildrenMap)) {
                                serviceLoadChildren
                                        .setValue(new Double(sourceLoadFromRequest.get(serviceFromChildrenMap)));
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * By default, push all loads to children
     * 
     * @param childrenMapInput
     *            used to compute the outgoingLoadMap for the root region
     */
    private void computeDefaultOutgoingLoadMap(
            Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenMapInput) {
        // reset the serviceOutgoingLoadMap
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : serviceOutgoingLoadMap.entrySet()) {
            ServiceIdentifier<?> service = entry.getKey();
            for (RegionIdentifier neighbor : neighborSet) {
                serviceOutgoingLoadMap.get(service).put(neighbor, 0.0);
            }
        }

        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenEntry : childrenMapInput
                .entrySet()) {
            RegionIdentifier children = childrenEntry.getKey();
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceEntry : childrenEntry.getValue()
                    .entrySet()) {
                // skip the source which is sourceEntry.getKey()
                for (Entry<ServiceIdentifier<?>, Double> serviceEntry : sourceEntry.getValue().entrySet()) {
                    ServiceIdentifier<?> service = serviceEntry.getKey();
                    double load = serviceEntry.getValue();
                    // if the map doesn't contain the service yet, create new
                    // entry
                    if (!serviceOutgoingLoadMap.containsKey(service)) {
                        Map<RegionIdentifier, Double> childenTempMap = new HashMap<>();
                        childenTempMap.put(children, load);
                        serviceOutgoingLoadMap.put(service, childenTempMap);
                    } else {
                        Map<RegionIdentifier, Double> childenTempMap = serviceOutgoingLoadMap.get(service);
                        double loadToAdd = childenTempMap.getOrDefault(children, 0.0) + load;
                        childenTempMap.put(children, loadToAdd);
                        serviceOutgoingLoadMap.put(service, childenTempMap);
                    }
                }
            }
        }
    }

    /**
     * Set root based on serverDemand from ResourceSummary
     * 
     * @param summary
     *            is the ResourceSummary
     */
    private void setRoot(ResourceSummary summary) {
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry : summary
                .getServerDemand().entrySet()) {
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> innerEntry : entry.getValue()
                    .entrySet()) {
                double totalLoad = innerEntry.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
                if (compareDouble(totalLoad, 0) > 0) {
                    isRoot = true;
                    return;
                }
            }
        }
    }

    /**
     * Compute children map <br>
     * Compute incomingloadMap as loads from parent Compute keeploadMap as 0s
     * (by default, push all loads to children)
     * 
     * @param iteration
     * @param receivedMessageMapInput
     * @param summary
     */
    private void computeParentAndChildren(int iteration,
            Map<RegionIdentifier, DcopMessage> receivedMessageMapInput,
            ResourceSummary summary) {
        // resetting the incomingLoad to 0
        resetIncomingLoadMap();
        if (!isRoot) {
            resetChildrenMap();
        }

        for (Map.Entry<RegionIdentifier, DcopMessage> entry : receivedMessageMapInput.entrySet()) {
            if (entry.getValue().getType() == MessageType.PARENT_TO_CHILDREN) {
                // extend the parent service map
                Set<ServiceIdentifier<?>> servicesToAddToParent = entry.getValue().getClientDemandMap().values()
                        .stream().map(Map::keySet).flatMap(Set::stream).collect(Collectors.toSet());
                Set<ServiceIdentifier<?>> previousServices = parentServicesMap.get(entry.getKey());
                if (null != previousServices) {
                    previousServices.addAll(servicesToAddToParent);
                } else {
                    previousServices = new HashSet<>(servicesToAddToParent);
                }
                parentServicesMap.put(entry.getKey(), previousServices);

                computeChildrenMap(entry.getValue().getClientDemandMap(), summary);
                setIncomingLoadAndKeepLoadMapFromParent(entry.getValue().getClientDemandMap());
            }
        }
        LOGGER.info("Iteration {} PARENT map: {}", iteration, parentServicesMap);
        LOGGER.info("Iteration {} CHILDREN map: {}", iteration, childrenMap);
    }

    /**
     * Reset the incomingLoadMap to 0 or service requesting to this server.
     */
    private void resetIncomingLoadMap() {
        for (Entry<ServiceIdentifier<?>, Double> entry : serviceIncomingLoadMap.entrySet()) {
            entry.setValue(0.0);
        }

        // update the incomingLoad with serviceRequest to this node
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : sourceServiceMapToThisServer
                .entrySet()) {
            for (Entry<ServiceIdentifier<?>, Double> entry1 : entry.getValue().entrySet()) {
                double accumulatedLoad = entry1.getValue() + serviceIncomingLoadMap.getOrDefault(entry1.getKey(), 0.0);
                serviceIncomingLoadMap.put(entry1.getKey(), accumulatedLoad);
            }
        }
    }

    /**
     * Reset loads from childrenMap to avoid accumulated values.
     * 
     */
    private void resetChildrenMap() {
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entry : childrenMap
                .entrySet()) {
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry2 : entry.getValue().entrySet()) {
                for (Entry<ServiceIdentifier<?>, Double> entry3 : entry2.getValue().entrySet()) {
                    entry3.setValue(0.0);
                }
            }
        }
    }

    /**
     * Reset incomingLoadMap to 0 or serviceComingToThisServer Set
     * serviceLoadToKeep to 0
     * 
     * @param clientDemandMap
     *            is the load map from PARENT_TO_CHILDREN message
     */
    private void setIncomingLoadAndKeepLoadMapFromParent(
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> clientDemandMap) {
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> clientEntry : clientDemandMap.entrySet()) {
            // Skip the source which is the clientEntry.getKey()
            for (Entry<ServiceIdentifier<?>, Double> serviceEntry : clientEntry.getValue().entrySet()) {
                ServiceIdentifier<?> service = serviceEntry.getKey();
                double load = serviceIncomingLoadMap.getOrDefault(service, 0.0) + serviceEntry.getValue();
                serviceIncomingLoadMap.put(service, load);
                // default keep 0 load after receiving from parent
                serviceLoadToKeepMap.put(service, 0.0);
            }
        }
    }

    /**
     * @param iteration
     *            the iteration where the agents are waiting for messages
     * @param isReadingTree
     *            indicate if it's reading previous tree iteration
     * @return a mapping from neighbor -> dcopMessage
     * @throws InterruptedException
     *             when the sleeping thread is interrupted
     */
    private Map<RegionIdentifier, DcopMessage> waitForMessagesFromNeighbors(int iteration, boolean isReadingTree)
            throws InterruptedException {
        Map<RegionIdentifier, DcopMessage> messageMap = new HashMap<>();
        int noMessageToRead = isReadingTree ? 1 : neighborSet.size();

        do {
            messageMap.clear();
            keepInboxUpdated();
            for (Map.Entry<RegionIdentifier, DcopSharedInformation> entry : dcopInfoProvider
                    .getAllDcopSharedInformation().entrySet()) {

                MessagesPerIteration message = entry.getValue().getMessageAtIteration(iteration);

                if (message != null && message.isSentTo(selfRegionID, iteration)) {
                    messageMap.put(message.getSender(), message.getMessageForThisReceiver(selfRegionID));
                }
            }
            // only wait if the region hasn't received all messages
            if (messageMap.size() < noMessageToRead) {
                Thread.sleep(DcopConstants.SLEEPTIME_WAITING_FOR_MESSAGE);
            }
        } while (messageMap.size() < noMessageToRead);

        for (Entry<RegionIdentifier, DcopMessage> entry : messageMap.entrySet()) {
            LOGGER.info("Iteration {} Region {} receives message {} from {}", iteration, selfRegionID, entry.getValue(),
                    entry.getKey());
        }

        return messageMap;
    }

    private void sendChildAndLoadMessages(int iteration) {
        nonChildrenParentSet = new HashSet<>(neighborSet);
        nonChildrenParentSet.removeAll(childrenMap.keySet());
        nonChildrenParentSet.removeAll(parentServicesMap.keySet());

        // forward the service map to children
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entry : childrenMap
                .entrySet()) {
            RegionIdentifier children = entry.getKey();

            // copying before sending
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceLoadToSend = null;
            if (null != childrenMap.get(children)) {
                sourceServiceLoadToSend = new HashMap<>();
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry1 : childrenMap.get(children)
                        .entrySet()) {
                    Map<ServiceIdentifier<?>, Double> serviceLoad = new HashMap<>();
                    for (Entry<ServiceIdentifier<?>, Double> entry2 : entry1.getValue().entrySet()) {
                        double load = entry2.getValue().doubleValue();
                        serviceLoad.put(entry2.getKey(), load);
                    }
                    sourceServiceLoadToSend.put(entry1.getKey(), serviceLoad);
                }
            }

            // DcopMessage dcopMsg = new
            // DcopMessage(MessageType.PARENT_TO_CHILDREN,
            // childrenMap.get(children), null);
            DcopMessage dcopMsg = new DcopMessage(MessageType.PARENT_TO_CHILDREN, sourceServiceLoadToSend, null);
            sendMessage(children, iteration, dcopMsg);
        }

        // send the excess load map to parent
        for (Entry<RegionIdentifier, Set<ServiceIdentifier<?>>> entry : parentServicesMap.entrySet()) {
            RegionIdentifier parent = entry.getKey();

            // copying before sending
            Map<ServiceIdentifier<?>, Double> serviceLoadToSend = null;
            if (null != excessLoadMapToParent.get(parent)) {
                serviceLoadToSend = new HashMap<>();
                for (Entry<ServiceIdentifier<?>, Double> entry2 : excessLoadMapToParent.get(parent).entrySet()) {
                    double load = entry2.getValue().doubleValue();
                    serviceLoadToSend.put(entry2.getKey(), load);
                }
            }

            // DcopMessage dcopMsg = new DcopMessage(MessageType.LOAD_TO_PARENT,
            // null, excessLoadMapToParent.get(parent));
            DcopMessage dcopMsg = new DcopMessage(MessageType.LOAD_TO_PARENT, null, serviceLoadToSend);
            sendMessage(parent, iteration, dcopMsg);
        }

        // send null to the other
        for (RegionIdentifier other : nonChildrenParentSet) {
            DcopMessage dcopMsg = new DcopMessage(MessageType.LOAD_TO_PARENT, null, null);
            sendMessage(other, iteration, dcopMsg);
        }
    }

    private void sendMessage(RegionIdentifier receiver, int iteration, DcopMessage dcopMsg) {
        keepInboxUpdated();
        // The first time adding a message to this iteration
        if (null == inbox.getMessageAtIteration(iteration)) {
            MessagesPerIteration msgPerIter = new MessagesPerIteration(selfRegionID, iteration);
            msgPerIter.addMessageToReceiver(receiver, dcopMsg);
            inbox.addIterationDcopInfo(iteration, msgPerIter);
        } else {
            MessagesPerIteration currentMsgAtThisIteration = inbox.getMessageAtIteration(iteration);
            currentMsgAtThisIteration.addMessageToReceiver(receiver, dcopMsg);
            inbox.addIterationDcopInfo(iteration, currentMsgAtThisIteration);
        }

        DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        dcopInfoProvider.setLocalDcopSharedInformation(messageToSend);
        LOGGER.info("Iteration {} Region {} sends message {} to {}", iteration, selfRegionID, dcopMsg, receiver);
    }

    private void keepInboxUpdated() {
        inbox = dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID);
    }

    /**
     * @param summary
     *            Temporary
     */
    private void retrieveLatency(ResourceSummary summary) {

        ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity = summary
                .getNetworkCapacity();
        for (Map.Entry<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> entry : networkCapacity.entrySet()) {
            latency.put(entry.getKey(), 0.0);
        }
    }

    /**
     * @param summary
     * @postcondition regionCapacity contains the TASK_CONTAINERS of the server
     *                of this region
     */
    private void retrieveAggregateCapacity(ResourceSummary summary) {
        final NodeAttribute<?> containersAttribute = NodeMetricName.TASK_CONTAINERS;
        if (summary.getServerCapacity().containsKey(containersAttribute)) {
            regionCapacity = summary.getServerCapacity().get(containersAttribute).doubleValue();
        }
        if (DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            regionCapacity = regionCapacity * AgentConfiguration.getInstance().getDcopCapacityThreshold();
    }

    /**
     * @param summary
     * @postcondition serviceID gets the id of the service demanded and demand
     *                from all clients to the region is added in totalDemand For
     *                previous algorithm
     */
    private void retrieveAggregateDemand(ResourceSummary summary) {                
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverDemand = summary
                .getServerDemand();
        for (Map.Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entryService : serverDemand
                .entrySet()) {
             
            ServiceIdentifier<?> serviceIDKey = entryService.getKey();

             ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceDemand = entryService
                    .getValue();

            for (Map.Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> entryRegion : serviceDemand
                    .entrySet()) {

                double localDemand = entryRegion.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0.0);

                totalDemandMap.put(serviceIDKey, totalDemandMap.getOrDefault(serviceIDKey, 0.0) + localDemand);
            }
        }
        LOGGER.info("@@@@ serverDemand {} totalDemand {}", serverDemand, totalDemandMap);
    }

    /**
     * @param aggregateDemand
     *            demand. Previous algorithm
     */
    public void setAggregateDemandMap(Map<ServiceIdentifier<?>, Double> aggregateDemand) {
        this.totalDemandMap = aggregateDemand;
    }

    /**
     * @param summary
     * @postcondition neighborSet contains the set of neighbors
     */
    private void retrieveNeighborSetFromNetworkLink(ResourceSummary summary) {
        ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity = summary
                .getNetworkCapacity();
        for (Map.Entry<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> entry : networkCapacity.entrySet()) {
            // LinkAttribute: LinkMetricName {DATARATE, false}
            // where true/false is application specific
            RegionIdentifier neighborID = entry.getKey();
            if (!neighborID.equals(selfRegionID)) {
                neighborSet.add(neighborID);
            }
        }

        LOGGER.info("My neighbors are: {}", neighborSet.toString());
    }

    /**
     * @param summary
     * @return the plan by default when this region processes all the load
     */
    private RegionPlan defaultPlan(ResourceSummary summary) {
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();

        regionPlanBuilder.put(selfRegionID, 1.0);
        for (RegionIdentifier neighbor : neighborSet) {
            regionPlanBuilder.put(neighbor, 0.0);
        }

        ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();

        for (ServiceIdentifier<?> serviceID : summary.getServerDemand().keySet()) {
            servicePlanBuilder.put(serviceID, regionPlan);
        }

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> defaultPlan = servicePlanBuilder
                .build();
        LOGGER.info("Plan by default " + defaultPlan);
        return new RegionPlan(summary.getRegion(), defaultPlan);
    }

    /**
     * @return aggregateCapacity
     */
    public double getAggregateCapacity() {
        return regionCapacity;
    }

    /**
     * @param aggregateCapacity
     *            capacity
     */
    public void setAggregateCapacity(double aggregateCapacity) {
        this.regionCapacity = aggregateCapacity;
    }

    /// Methods for old algorithm
    /**
     * @return the available load Capacity - demand + sharedLoad for previous
     *         algorithm
     */
    private double availableCapacity(int iter) {
        double totalDemand = sumValue(totalDemandMap); //.values().stream().mapToDouble(Double::doubleValue).sum();
        
        double totalLoadShared = sumValue(overloadSharedToOtherMap); // .values().stream().mapToDouble(Double::doubleValue).sum();
        
        double loadAgreeToReceiveFromOther = sumValue(loadAgreeToReceiveFromOtherMap); //.values().stream().mapToDouble(Double::doubleValue).sum();
        
//      return regionCapacity - totalDemand + overloadSharedToOtherMap - loadAgreeToReceiveFromOtherMap;
        
//        LOGGER.info("ITERATION {} regionCapacity {} totalDemand {} totalLoadShared {} loadAgreeToReceiveFromOther {} return {} ",
//                iter, regionCapacity, totalDemand, totalLoadShared, loadAgreeToReceiveFromOther, regionCapacity - totalDemand + totalLoadShared - loadAgreeToReceiveFromOther);        
        
        return regionCapacity - totalDemand + totalLoadShared - loadAgreeToReceiveFromOther;
    }

    /**
     * @param receiver
     *            a free neighbor (verify before calling the method)
     * @param hop
     *            1 if I am the root, more otherwise
     * @param loadMap
     *            requested load >0
     * @param iteration
     *            iteration
     * @param parent
     *            null if the root Sends only hop,load for previous algorithm
     */
    private void sendMessageType1(RegionIdentifier receiver,
            int hop,
            Map<ServiceIdentifier<?>, Double> loadMap,
            int iteration) {
        DcopMessage message = new DcopMessage(hop, loadMap);

        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration,
                    new DcopInfoMessage(selfRegionID, receiver, iteration, currentStage, parent));
        } else if (inbox.getMsgAtIteration(iteration) == null) {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(selfRegionID);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            l.add(message);
            inside.getMessages().put(receiver, l);
            inside.setStage(currentStage);
            inside.setParent(parent);
            inside.setType(1);
            inbox.addIterationDcopInfo(iteration, inside);
        } else {
            DcopInfoMessage inside = inbox.getMsgAtIteration(iteration);
            inside.setSender(selfRegionID);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            l.add(message);
            inside.getMessages().put(receiver, l);
            inside.setStage(currentStage);
            inside.setParent(parent);
            inside.setType(1);
            inbox.addIterationDcopInfo(iteration, inside);
        }
        // Change reference of localStoredMessage in order for the system to
        // send this *new* message
        DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        dcopInfoProvider.setLocalDcopSharedInformation(messageToSend);
        LOGGER.info("Iteration " + iteration + " Region " + selfRegionID + " Stage " + currentStage + " sends message "
                + message + " to " + receiver);
    }

    /**
     * @param receiver
     *            receiver
     * @param iteration
     *            current iteration
     */
    public void sendStageMessage(RegionIdentifier receiver, int iteration) {
        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration,
                    new DcopInfoMessage(selfRegionID, receiver, iteration, currentStage, parent));
        } else if (inbox.getMsgAtIteration(iteration) == null) {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(selfRegionID);
            inside.setStage(currentStage);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            inside.getMessages().put(receiver, l);
            inside.setType(0);
            inside.setParent(parent);
            inbox.addIterationDcopInfo(iteration, inside);
        } else {
            DcopInfoMessage inside = inbox.getMsgAtIteration(iteration);
            inside.setSender(selfRegionID);
            inside.setStage(currentStage);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            inside.getMessages().put(receiver, l);
            inside.setType(0);
            inside.setParent(parent);
            inbox.addIterationDcopInfo(iteration, inside);
        }
        // Change reference of localStoredMessage in order for the system to
        // send this *new* message
        DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        dcopInfoProvider.setLocalDcopSharedInformation(messageToSend);
        LOGGER.info("Iteration " + iteration + " Region " + selfRegionID + " Stage " + currentStage
                + " sends STAGE message to " + receiver);
    }

    /**
     * @param o
     *            DcopSharedInformationInside sends info provided in o for
     *            previous algorithm
     */
    private void sendMessage(DcopInfoMessage o, int iteration) {
        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration, new DcopInfoMessage(o));
        } else if (inbox.getMsgAtIteration(iteration) == null) {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(o.getSender());
            inside.setIteration(iteration);

            for (RegionIdentifier receiver : o.getMessages().keySet()) {
                List<DcopMessage> l = new ArrayList<DcopMessage>();
                if (!o.getMessages().isEmpty())
                    for (DcopMessage m : o.getMessages().get(receiver)) {
                        l.add(new DcopMessage(m.getOriginalSender(), m.getHop(), m.getLoadMap(), m.getEfficiency(),
                                m.getLatency()));
                        inside.getMessages().put(receiver, l);
                    }
                else {
                    inside.getMessages().put(receiver, l);
                }
            }
//            inside.setServiceID(o.getServiceID());
            inside.setStage(o.getStage());
            inside.setParent(o.getParent());
            inside.setType(o.getType());
            inbox.addIterationDcopInfo(iteration, inside);
        } else {
            DcopInfoMessage inside = inbox.getMsgAtIteration(iteration);
            inside.setSender(o.getSender());
            inside.setIteration(iteration);

            for (RegionIdentifier receiver : o.getMessages().keySet()) {
                List<DcopMessage> l = new ArrayList<DcopMessage>();
                if (!o.getMessages().isEmpty())
                    for (DcopMessage m : o.getMessages().get(receiver)) {
                        l.add(new DcopMessage(m.getOriginalSender(), m.getHop(), m.getLoadMap(), m.getEfficiency(),
                                m.getLatency()));
                        inside.getMessages().put(receiver, l);
                    }
                else {
                    inside.getMessages().put(receiver, l);
                }
            }
//            inside.setServiceID(o.getServiceID());
            inside.setStage(o.getStage());
            inside.setParent(o.getParent());
            inside.setType(o.getType());
            inbox.addIterationDcopInfo(iteration, inside);
        }
        // Change reference of localStoredMessage in order for the system to
        // send this *new* message
        DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        dcopInfoProvider.setLocalDcopSharedInformation(messageToSend);

        LOGGER.info("Iteration " + iteration + " Region " + selfRegionID + " Stage " + currentStage + " sends message "
                + messageToSend.getMsgAtIteration(iteration).getMessages() + " to "
                + messageToSend.getMsgAtIteration(iteration).getMessages().keySet());
    }

    /**
     * when an agent gets FREE again
     *
     * @postcondition clear children, clear parent, clear toSend, clear
     *                storedInfoType2, leaf = false, messageToForward,
     *                numberOfChildren=0,finishCount = false clear others, fill
     *                freeNeighbors with all neighbors again, numberOfSentType2
     *                = 0, hop = 0 for previous algorithm
     */
    private void empty() {
        // the set of children can change once finished with current tree;
        children = new HashSet<RegionIdentifier>();
        parent = null;
        messageToSend = null;
        storedInfoType2List = new ArrayList<DcopInfoMessage>();
        leaf = false;
        messageToForward = null;
        countingChildren = 0;
        finishCount = false;
        others = new HashSet<RegionIdentifier>();
        freeNeighbors.addAll(neighborSet);
        numberOfSentType2 = 0;
        hop = 0;
    }

    /**
     * @param iteration
     *            current iteration
     * @postcondition freeNeighbors is a set with all free neighbors and
     *                children is filled
     * @return set of messages sent only to me
     * @throws InterruptedException
     *             for previous algorithm
     */
    private Set<DcopInfoMessage> checkFreeNeighborsAndChildren(int iteration) throws InterruptedException {
        Map<RegionIdentifier, DcopInfoMessage> messageMap = new HashMap<>();
        // initially assume all neighbors are free
        freeNeighbors.addAll(neighborSet);
        do {
            messageMap.clear();
            for (Map.Entry<RegionIdentifier, DcopSharedInformation> entry : dcopInfoProvider
                    .getAllDcopSharedInformation().entrySet()) {
                RegionIdentifier sender = entry.getKey();
                DcopInfoMessage message = entry.getValue().getMsgAtIteration(iteration);

                // if (currentStage != Stage.STAGE2)
                {
                    freeNeighbors.removeAll(children);
                    freeNeighbors.remove(parent);
                    freeNeighbors.removeAll(others);
                }

                if (message != null && message.isSentTo(selfRegionID, iteration)) {
                    messageMap.put(sender, message);
                    if (currentStage != Stage.STAGE2_HAS_RECEIVED_ALL_MSG_FROM_CHILDREN) {
                        if (message.getStage() != Stage.FREE) {
                            freeNeighbors.remove(entry.getKey());
                        }

                        if (compareDouble(availableCapacity(iteration), 0) < 0 && message.getParent() == null
                                && message.getStage() != Stage.FREE) {
                            if (others.add(message.getSender())) {
                                countingChildren++;
                                if (countingChildren == neighborSet.size())
                                    finishCount = true;
                            }
                        } else if (message.getParent() != null && message.getParent().equals(selfRegionID)) {
                            // LOGGER.info("%%Children "+children);
                            if (currentStage != Stage.FREE && children.add(message.getSender())) {
                                countingChildren++;
                                if (countingChildren == neighborSet.size())
                                    finishCount = true;
                            } else if (currentStage == Stage.FREE) {
                                inTransition = true;
                            }
                        } else if (message.getParent() != null && !message.getParent().equals(selfRegionID)) {
                            // message has a parent that is not me:
                            // If I have no parent: 1)I am FREE 2)I am root->add
                            // to others
                            // else: 1)message is from my parent 2)else ->add to
                            // others
                            // Chris:add the following line
                            if ((parent == null && currentStage != Stage.FREE)
                                    || (parent != null && !message.getSender().equals(parent)))
                                if (others.add(message.getSender())) {
                                    countingChildren++;
                                    if (countingChildren == neighborSet.size())
                                        finishCount = true;
                                }
                        } else if ((message.getParent() == null && message.getStage() != Stage.FREE)) {
                            if (countingChildren == neighborSet.size())
                                finishCount = true;
                        }
                        if (parent != null && countingChildren + 1 == neighborSet.size()) {
                            // parent could be in others as well
                            finishCount = true;
                        }
//                        if (finishCount && children.size() == 0 && overloaded != 1) {
                        if (finishCount && children.size() == 0 && overloaded != INIT_OVERLOADED_VALUE) {
                            if (!leaf) {
                                leaf = true;
                                leaves++;
                            }
                        }
                    } // end if stage2
                } // end of if message is sent to me
            }

            // only wait if the region hasn't received all messages
            if (messageMap.size() < neighborSet.size()) {
                Thread.sleep(DcopInfo.SLEEPTIME_WAITING_FOR_MESSAGE);
            }
        } while (messageMap.size() < neighborSet.size());
        
        for (DcopInfoMessage entry : messageMap.values()) {
            LOGGER.info("Iteration {} Region {} receives message from {}: {}", iteration, selfRegionID, entry.getSender(), entry.getMessages().get(selfRegionID));
        }
        
        for (Map.Entry<RegionIdentifier, DcopSharedInformation> entry : dcopInfoProvider
                .getAllDcopSharedInformation().entrySet()) {
            // entry.getKey() is the sender
            DcopInfoMessage message = entry.getValue().getMsgAtIteration(iteration);
            LOGGER.info("Iteration {} Region {} has inbox {}", iteration, selfRegionID, message);
        }
        
        LOGGER.info("Iteration {} Region {} FINISH READING messages. finishCount {} freeNeighbors {} children {} others {} parent {}", iteration, selfRegionID, finishCount, freeNeighbors, children, others, parent);

        return new HashSet<>(messageMap.values());
    }

    /**
     * In FREE stage, check if any neighbor is asking for help
     * @param readInfo
     *            set of dcopSharedInfo sent only to me, obtained when checking
     *            for free neighbors
     * @precondition this agent is FREE and receives petition of help
     * @postcondition parent is defined, DcopMessage to send is filled
     *                accordingly but stages do not change until message is sent
     *                Heuristics to decide what to do If I am free it means My
     *                available load >=0 otherwise I will be at least at Stage1
     *                for previous algorithm
     */
    private void decide(Set<DcopInfoMessage> readInfo, int iteration) {
        double availableCapacity = availableCapacity(iteration);
        double min = Double.MAX_VALUE;
        double minPartial = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        DcopInfoMessage chosenAllLoad = null;
        DcopInfoMessage chosenNoNeigh = null;
        DcopInfoMessage toForward = null;
        for (DcopInfoMessage message : readInfo) {
            // Not a STAGE message
            // This should be the message asking for help from the parent
            // Then DECIDE HOW TO MUCH TO HELP and PASSING THE REST
            if (!message.getMessages().get(selfRegionID).isEmpty()) {
                Map<ServiceIdentifier<?>, Double> askedLoadMap = message.getMessages().get(selfRegionID).get(0)
                        .getLoadMap();                
//              double load = message.getMessages().get(selfRegionID).get(0).getLoadMap();
                // if this region can help with all the load heuristics:
                // minimize availableCapacity - load
                
                double loadAskFromParent = sumValue(askedLoadMap);
                
                // Can help with all load
                if (compareDouble(availableCapacity, loadAskFromParent) >= 0) {
                    
                    double temp = availableCapacity - loadAskFromParent;
                    // What is min, why compare temp with min?
                    // Update min to the temp
                    if (compareDouble(temp, min) < 0) {
                        chosenAllLoad = message;
                        min = temp;
                    }   
                } // can't help with all loads and still have free neighbors 
                else if (freeNeighbors.size() > 0) {
                    // Why compare with max
                    if (compareDouble(loadAskFromParent, max) > 0) {
                        max = loadAskFromParent;
                        toForward = message;
                    }
                } // can't help all and there is no free neighbors 
                else if (!inTransition) {
                    double temp = loadAskFromParent - availableCapacity;
                    if (compareDouble(temp, minPartial) < 0) {
                        minPartial = temp;
                        chosenNoNeigh = message;
                    }
                }
            }
        } // end for
        
        // prepare information toSend
        // Can help with all the load, then send this back to the parent
        if (chosenAllLoad != null) {
            parent = chosenAllLoad.getSender();
            DcopMessage sent = chosenAllLoad.getMessages().get(selfRegionID).get(0);
            
//            totalRequestedLoad = sent.getLoadMap();
//            totalExcessLoad = sent.getLoadMap().values().stream().mapToDouble(Double::doubleValue).sum();
//            excessLoadMap.putAll(sent.getLoadMap());
            
            // asks only for load excess
//            double loadVar = sent.getLoadMap().values().stream().mapToDouble(Double::doubleValue).sum();
            
            hop = sent.getHop();
            
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
            
//            temp.add(new DcopMessage(selfRegionID, hop, loadVar, efficiency, latency.get(parent)));
            temp.add(new DcopMessage(selfRegionID, hop, sent.getLoadMap(), efficiency, latency.get(parent)));
            
            // do not copy stage and iteration!! when sending message
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, currentStage, parent);
            if (!leaf) {
                leaf = true;
                leaves++;
            }
            finishCount = true;
            children = new HashSet<RegionIdentifier>();
        } 
        // Forward all the excess load to other neighbors 
        // Agents are not keeping load here, just forward all asked load the next children
        else if (toForward != null) {
            parent = toForward.getSender();
            DcopMessage sent = toForward.getMessages().get(selfRegionID).get(0);
//            totalRequestedLoad = sent.getLoadMap();
//            totalRequestedLoad = sent.getLoadMap().values().stream().mapToDouble(Double::doubleValue).sum();
            excessLoadMap.putAll(sent.getLoadMap());
            
            // NEW CODE
//            double loadVar = IS_OPTIMIZATION ? sent.getLoadMap() : sent.getLoadMap() - availableCapacity;
            
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
//            temp.add(new DcopMessage(hop + 1, loadVar));
            temp.add(new DcopMessage(sent.getHop() + 1, sent.getLoadMap()));
            
            // do not copy stage and receiver when the message is sent
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, currentStage, parent);
            messageToSend.setType(1);
            
            LOGGER.info("Iteration {} Region {} forward message {}", iteration, selfRegionID, messageToSend);
        }
        // Can't help all and is the leaf agent
        // Send the message back with the most capacity
        else if (chosenNoNeigh != null) {
            parent = chosenNoNeigh.getSender();
            DcopMessage sent = chosenNoNeigh.getMessages().get(selfRegionID).get(0);
//            totalRequestedLoad = sent.getLoadMap();
//            totalRequestedLoad = sent.getLoadMap().values().stream().mapToDouble(Double::doubleValue).sum();
            excessLoadMap.putAll(sent.getLoadMap());
            hop = sent.getHop();
            
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
            
            Map<ServiceIdentifier<?>, Double> askedLoad = sent.getLoadMap();
            Map<ServiceIdentifier<?>, Double> repliedLoadMap = createLoadMapGiveMaxCapacity(askedLoad, availableCapacity);
            
            
            DcopMessage tempDcopMsg = new DcopMessage(selfRegionID, hop, repliedLoadMap, efficiency, latency.get(parent));
            temp.add(tempDcopMsg);

            // do not copy stage and iteration when sending message
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, currentStage, parent);
            if (!leaf) {
                leaf = true;
                leaves++;
            }
            finishCount = true;
            children = new HashSet<RegionIdentifier>();
        }
    }

    /**
     * @postcondition toSend has aggregated message for parent for previous
     *                algorithm
     */
    private void aggregateMessages(int iteration) {
        // List<DcopMessage> forParent = IS_OPTIMIZATION ?
        // preprocessListDCOP(iteration) : preprocessListDCSP(iteration);
//        List<DcopMessage> forParent = IS_OPTIMIZATION ? preprocessListDCOPwithJacop(iteration)
//                : preprocessListDCSP(iteration);
        List<DcopMessage> forParent = preprocessListDCSP(iteration);
        
        messageToSend = new DcopInfoMessage(selfRegionID, parent, forParent, -1, currentStage, parent);
    }

//    private List<DcopMessage> preprocessListDCOPwithJacop(int iteration) {
//        double sum = 0;
//        double limit = 0;
//
//        double availableCapacity = availableCapacity(iteration);
//        List<Double> maxPerAgent = new ArrayList<>();
//        List<Double> processingAndLatency = new ArrayList<>();
//
//        for (DcopInfoMessage info : storedInfoType2List) {
//            for (DcopMessage message : info.getMessages().get(selfRegionID)) {
//                double load = message.getLoadMap();
//                maxPerAgent.add(load);
//                processingAndLatency.add((message.getEfficiency() + message.getLatency()));
//            }
//        }
//        sum = maxPerAgent.stream().mapToDouble(Double::doubleValue).sum();
//
//        // THIS LINE of Jacop is fixed here by Khoi.
//        if (compareDouble(availableCapacity, 0) > 0) {
//            maxPerAgent.add(availableCapacity);
//            processingAndLatency.add(efficiency);
//            sum += availableCapacity;
//        }
//
//        limit = Math.min(sum, totalRequestedLoad); // if not enough to help send the
//                                              // max possible
//
//        LOGGER.info("MAX PER AGENT: " + maxPerAgent);
//        LOGGER.info("SUM, LIMIT: " + sum + " " + limit);
//
//        // List<Integer> selected = minimaxByJacobInteger(maxPerAgent,
//        // processingAndLatency, limit);
//        List<Double> selected = minimaxByJacobDouble(maxPerAgent, processingAndLatency, limit);
//
//        LOGGER.info("SELECTED LOADS: " + selected);
//
//        return createNewPlan(selected, iteration);
//    }

    /**
     * @param domainMaxPerAgent
     * @param processingAndLatency
     * @param sum
     * @return list for previous algorithm
     */
    private static List<Double> minimaxByJacobDouble(List<Double> domainMaxPerAgent,
            List<Double> processingAndLatency,
            double sum) {
        List<Double> results = new ArrayList<>();
        Store store = new Store(); // define FD store
        int varCount = domainMaxPerAgent.size();
        double maxDomain = domainMaxPerAgent.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
        double maxProcAndLatency = processingAndLatency.stream().mapToDouble(Double::doubleValue).max().getAsDouble();

        FloatVar[] x = new FloatVar[varCount];
        FloatVar[] weightedX = new FloatVar[varCount];
        Constraint[] weightedXConstraints = new Constraint[varCount];

        for (int index = 0; index < varCount; index++) {
            x[index] = new FloatVar(store, 0.0, domainMaxPerAgent.get(index));
            weightedX[index] = new FloatVar(store, 0.0, domainMaxPerAgent.get(index) * processingAndLatency.get(index));
            weightedXConstraints[index] = new PmulCeqR(x[index], processingAndLatency.get(index), weightedX[index]);
            store.impose(weightedXConstraints[index]);
        }

        double[] sumCoeff = new double[varCount];
        Arrays.fill(sumCoeff, 1);

        PrimitiveConstraint sumConstraint = new LinearFloat(store, x, sumCoeff, "==", sum);
        store.impose(sumConstraint);

        FloatVar maximum = new FloatVar(store, "toMinimize", 0.0, maxDomain * maxProcAndLatency);
        Constraint minimizeOjbective = new Max(weightedX, maximum);
        store.impose(minimizeOjbective);

        // store.impose(new );
        // search for a solution and print results
        DepthFirstSearch<FloatVar> search = new DepthFirstSearch<FloatVar>();
        SplitSelectFloat<FloatVar> select = new SplitSelectFloat<FloatVar>(store, x,
                new LargestDomainFloat<FloatVar>());

        Optimize<FloatVar> toMinimize = new Optimize<>(store, search, select, maximum);
        toMinimize.minimize();
        // toMinimize.getFinalVarValues();

        for (FloatInterval var : toMinimize.getFinalVarValues()) {
            results.add(Double.valueOf(var.toString()));
        }

        return results;
    }

    /**
     * @param loads
     * @return create a message for parent with the distribution load in loads
     *         and storedInfoType2 for previous algorithm
     */
//    private List<DcopMessage> createNewPlan(List<Double> loads, int iteration) {
//        List<DcopMessage> forParent = new ArrayList<DcopMessage>();
//        int index = 0;
//        for (DcopInfoMessage info : storedInfoType2List) {
//            for (DcopMessage offered : info.getMessages().get(selfRegionID)) {
//                changeLoad(info, offered, loads.get(index));
//                offered.setLoadMap(loads.get(index));
//                forParent.add(offered);
//                index++;
//            }
//        }
//        if (compareDouble(availableCapacity(iteration), 0) > 0)
//            forParent.add(new DcopMessage(selfRegionID, hop, loads.get(index), efficiency,
//                    (parent == null) ? 0 : latency.get(parent)));
//
//        return forParent;
//    }

    private double sumValue(Map<?, Double> map) {
        return map.values().stream().mapToDouble(Double::doubleValue).sum();
    }
    
    /**
     * Input storedInfoType2List
     * It is a collection of map <s1, v1>, <s2, v2>,...,<sn, vn> from all the children
     * @return a proposed plan to the parent
     *  where sum of the load is exact as the asked from excessLoadMap
     *  
     * @postcondition storedInfoType2 has modified loads to build the plan for
     *                previous algorithm
     */
    private List<DcopMessage> preprocessListDCSP(int iteration) {
        double myRegionCapacity = availableCapacity(iteration);
                
        Map<ServiceIdentifier<?>, Double> loadToAllocateMap = new HashMap<>(excessLoadMap);
        
        List<DcopMessage> msgSentToParentLater = new ArrayList<DcopMessage>();
       
        // Add my contribution first
        if (compareDouble(myRegionCapacity, 0) > 0) {
            Map<ServiceIdentifier<?>, Double> myContributionLoadMap = new HashMap<>();
            for (Entry<ServiceIdentifier<?>, Double> entry : loadToAllocateMap.entrySet()) {
                // If I can help all
                // Then add to my contributionLoadMap
                // Update the loadToAllocateMap
                // Update the capacity
                if (compareDouble(entry.getValue(), myRegionCapacity) <= 0) {
                    myContributionLoadMap.put(entry.getKey(), entry.getValue());
                    entry.setValue(0.0);
                    myRegionCapacity -= entry.getValue();
                } 
                // The asked load is more than I can help
                else {
                    myContributionLoadMap.put(entry.getKey(), myRegionCapacity);
                    entry.setValue(entry.getValue() - myRegionCapacity);
                    myRegionCapacity = 0;
                }
            }
            
            msgSentToParentLater.add(new DcopMessage(selfRegionID, hop, myContributionLoadMap, efficiency, 0));
        }
        
        // Comparator<DcopMessage> comparator = new DcopMessage,;
        PriorityQueue<DcopMessage> bucket = new PriorityQueue<DcopMessage>(10, DcopMessage.getSortByHop());
        for (DcopInfoMessage info : storedInfoType2List) {
            bucket.addAll(info.getMessages().get(selfRegionID));
        }
        
        DcopMessage originalOfferFromChildren = bucket.poll();
        while (originalOfferFromChildren != null) {
            Map<ServiceIdentifier<?>, Double> loadMapAllocatedToChildren = new HashMap<>(originalOfferFromChildren.getLoadMap());

            for (Entry<ServiceIdentifier<?>, Double> entry : loadMapAllocatedToChildren.entrySet()) {
                double childrenCapacity = entry.getValue();
                double loadToAllocate = loadToAllocateMap.get(entry.getKey());

                // If children can't serve all, update the loadToAllocate
                if (compareDouble(childrenCapacity, loadToAllocate) <= 0) {
                    loadToAllocateMap.put(entry.getKey(), loadToAllocate - childrenCapacity);
                } 
                // Children can serve with all loadToAllocate
                else {
                    entry.setValue(loadToAllocate);
                    loadToAllocateMap.put(entry.getKey(), 0.0);
                }
            }

            // Update the storedInforType2List
            for (DcopInfoMessage info : storedInfoType2List) {
                if (info.getMessages().get(selfRegionID).contains(originalOfferFromChildren)) {
                    changeLoadMap(info, originalOfferFromChildren, loadMapAllocatedToChildren);
                    
                    DcopMessage msgSendToParent = new DcopMessage(originalOfferFromChildren);
                    msgSendToParent.setLoadMap(loadMapAllocatedToChildren);
                    
                    msgSentToParentLater.add(msgSendToParent);
                    break;
                }
            }
            originalOfferFromChildren = bucket.poll();
        }
        
        return msgSentToParentLater;
    }

    /**
     * @param old
     * @param newMessage
     * @param toAllocate
     * @return for previous algorithm
     */
    private boolean changeLoadMap(DcopInfoMessage old, DcopMessage newMessage, Map<ServiceIdentifier<?>, Double> newLoadMap) {
        for (DcopMessage m : old.getMessages().get(selfRegionID)) {
            if (m.equals(newMessage)) {
                m.setLoadMap(new HashMap<>(newLoadMap));
                return true;
            }
        }
        return false;
    }
}
