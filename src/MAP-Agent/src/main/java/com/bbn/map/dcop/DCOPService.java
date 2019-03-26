package com.bbn.map.dcop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkState;
import com.bbn.protelis.networkresourcemanagement.NetworkStateProvider;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import java8.util.Objects;

/**
 * The main entry point for DCOP. The {@link Controller} will use this class to
 * interact with DCOP. The {@link Controller} will start this service as
 * appropriate for the node.
 */

/**
 * @author cwayllace
 *
 */
public class DCOPService extends AbstractService {
    
    /**
     * @author cwayllace Stage at which is each DCOP agent FREE does not belong
     *         to any tree STAGE1 Sent messages asking for help STAGE2 Answered
     *         with load that can help
     */
    public enum Stage {
        /**
         * When agent is not belonging to any tree.
         */
        FREE, 
        /**
         * When agent is asking for help.
         */
        STAGE1, 
        /**
         * When agent has received all response from the children, and start sending type2 message to its parent.
         */
        STAGE2
    }
    
    private static final double DEFAULT_EFFICIENCY = 1.0;
    
    /**
     * Flag switch between the two algorithms.
     * It is true if the optimization algorithm is being used
     * It is false if the satisfaction algorithm is being used
     */
    private static final boolean IS_OPTIMIZATION = false;
    
    private static final int NUMBER_OF_PREVIOUS_ITERATION_TO_REMOVE_OLD_MESSAGES = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(DCOPService.class);
    private RegionIdentifier selfRegionID;
    private int totalDemand;
    private int regionCapacity;
    private DcopSharedInformation inbox;
    private Set<RegionIdentifier> neighborSet;
    private ServiceIdentifier<?> serviceID;

    private Stage currentStage;
    private RegionIdentifier parent;
    private Set<RegionIdentifier> children;
    private Set<RegionIdentifier> freeNeighbors;
    private Set<RegionIdentifier> others;
    private boolean leaf = false;
    private DcopInfoMessage messageToSend;
    private int requestedLoad;
    private List<DcopInfoMessage> storedInfoType2List;
    private int overloadSharedToOther;
    private int numberOfSentType2, countingChildren;
    private DcopMessage messageToForward;
    private boolean finishCount;
    private int overloaded;
    private int leaves;
    private boolean inTransition;
    private double efficiency;
    private Map<RegionIdentifier, Double> latency;
    private int hop;
    private int loadAgreeToReceiveFromOther;
    private Map<RegionIdentifier, Double> outgoingLoadMap;
    private int totalIncomingLoad;
    private int myHop; //NEW CODE


    /**
     * Construct a DCOP service.
     *
     * @param networkStateProvider
     *            where resource summary information is to be retrieved from.
     *
     * @param applicationManager
     *            source of information about applications, including
     *            specifications and profiles
     *
     * @param dcopInfoProvider
     *            how to access {@link DcopInfoMessage}
     * @param nodeName
     *            the name of the node that this service is running on (for
     *            logging)
     *
     */
    public DCOPService(@Nonnull final String nodeName,
            @Nonnull final NetworkStateProvider networkStateProvider,
            @Nonnull final DcopInfoProvider dcopInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager) {
        super("DCOP-" + nodeName);

        Objects.requireNonNull(applicationManager, "application manager");

        this.networkStateProvider = networkStateProvider;
        this.dcopInfoProvider = dcopInfoProvider;
        this.applicationManager = applicationManager;
        currentStage = Stage.FREE;
        parent = null;
        children = null;
        messageToSend = null;
    }

    private final DcopInfoProvider dcopInfoProvider;

    /**
     * Where to get resource summary information from.
     */
    private final NetworkStateProvider networkStateProvider;

    /**
     * Where to get application specifications and profiles from
     */
    @SuppressWarnings("unused")
    private final ApplicationManagerApi applicationManager;

    @Override
    protected void executeService() {
        LOGGER.info("DCOP STARTED");

        while (Status.RUNNING == getStatus()) {
            final RegionPlan plan = computePlan();

            if (null != plan) {
                LOGGER.info("Publishing DCOP plan: {}", plan);

                networkStateProvider.getNetworkState().setRegionPlan(plan);
            }

            // check before sleep in case computePlan is slow
            if (Status.RUNNING == getStatus()) {
                try {
                    Thread.sleep(AgentConfiguration.getInstance().getDcopRoundDuration().toMillis());
                } catch (final InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Got interrupted, likely time to shutdown, top of while loop will confirm.");
                    }
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Exiting DCOP service");
        }
    }

    private void init() {
        selfRegionID = null;
        totalDemand = 0;
        regionCapacity = 0;
        neighborSet = new HashSet<RegionIdentifier>();
        serviceID = null;
        children = new HashSet<RegionIdentifier>();
        storedInfoType2List = new ArrayList<DcopInfoMessage>();
        overloadSharedToOther = 0;
        numberOfSentType2 = 0;
        messageToForward = null;
        countingChildren = 0;
        finishCount = false;
        freeNeighbors = new HashSet<RegionIdentifier>();
        others = new HashSet<RegionIdentifier>();
        overloaded = -1;
        leaves = 0;
        inTransition = false;
        latency = new HashMap<RegionIdentifier, Double>();
        hop = 0;
        loadAgreeToReceiveFromOther = 0;
        outgoingLoadMap = new HashMap<>();
        totalIncomingLoad = 0;
    }

    private RegionPlan computePlan() {
        try {
            Thread.sleep(DcopInfo.TIME_SEARCHING_NEIGHBORS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final NetworkState networkState = networkStateProvider.getNetworkState();
        final ResourceSummary summary = networkState.getRegionSummary(ResourceReport.EstimationWindow.LONG);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received summary.serverDemand: {}", summary.getServerDemand());
            LOGGER.debug("Received summary.serverCapacity: {}", summary.getServerCapacity());
        }

        init();
        selfRegionID = summary.getRegion();
        retrieveNeighborLink(summary);
        retrieveAggregateDemand(summary);
        retrieveAggregateCapacity(summary);

        LOGGER.info("My neighbors are: " + neighborSet.toString());
        
        for (RegionIdentifier neighbor:neighborSet) {
            outgoingLoadMap.put(neighbor, 0.0);
        }

        regionCapacity = (int) (regionCapacity * AgentConfiguration.getInstance().getDcopCapacityThreshold());

        /* ASSUME ONLY ONE SERVICE FOR NOW */
        efficiency = 0;
        for (Map.Entry<ServiceIdentifier<?>, Double> entry:summary.getServerAverageProcessingTime().entrySet()) {
            efficiency += entry.getValue();
        }
        
        if (efficiency == 0) efficiency = DEFAULT_EFFICIENCY;

        LOGGER.info("Efficiency " + efficiency);
        LOGGER.info("Estimated demand " + summary.getServerDemand());
        LOGGER.info("Server load " + summary.getServerLoad()); 
        LOGGER.info("Network load " + summary.getNetworkLoad());
        LOGGER.info("Estimated demand " + totalDemand);
        LOGGER.info("Capacity " + regionCapacity);

        retrieveLatency(summary);

        for (int iteration = 0; iteration <= AgentConfiguration.getInstance().getDcopIterationLimit(); iteration++) { 
            LOGGER.info("Iteration " + iteration + " outgoingLoadMap " + outgoingLoadMap + 
                    " totalIncoming " + totalIncomingLoad + " loadShared " + overloadSharedToOther
                    + " loadAgreeToShare " + loadAgreeToReceiveFromOther);

            inbox = dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID);
            inbox.removeMessageAtIteration(iteration - NUMBER_OF_PREVIOUS_ITERATION_TO_REMOVE_OLD_MESSAGES);
            // SEND
            if (currentStage == Stage.FREE) {
                if (messageToSend == null) {
                // if overloaded, ask for help
                // if not overloaded, send stage messages
                    if (availableLoad(iteration) < 0) {
                    // if overloaded send message type 1 to all neighbors
                        currentStage = Stage.STAGE1;
                        for (RegionIdentifier neighbor : neighborSet) {
                            sendMessageType1(neighbor, 1, -availableLoad(iteration), iteration, serviceID);
                        }
                        
                        overloaded = 1;
                        requestedLoad = -availableLoad(iteration);
                    } 
                    // it remains FREE so send just stage message to all neighbors
                    else {                      
                        for (RegionIdentifier neighbor : neighborSet) {
                            sendStageMessage(neighbor, iteration, serviceID);
                        }
                    }
                } else if (messageToSend.getType() == 2) {
                // can help
                    currentStage = Stage.STAGE2;
                    messageToSend.setIteration(iteration);
                    messageToSend.setStage(currentStage);
                    sendMessage(messageToSend, iteration);
                    Set<RegionIdentifier> remainingNeighbors = new HashSet<RegionIdentifier>();
                    remainingNeighbors.addAll(neighborSet);
                    remainingNeighbors.remove(parent);
                    for (RegionIdentifier neighbor : remainingNeighbors) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                } else if (messageToSend.getType() == 1) {
                // need to forward
                    currentStage = Stage.STAGE1;
                    messageToForward = messageToSend.getMessages().get(parent).get(0);
                    Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                    remaining.addAll(neighborSet);
                    remaining.removeAll(freeNeighbors);
                    for (RegionIdentifier neighbor : freeNeighbors) {
                        sendMessageType1(neighbor, messageToForward.getHop(), messageToForward.getLoad(), iteration,
                                serviceID);
                    }
                    for (RegionIdentifier neighbor : remaining) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                }
            } // end FREE
            else if (currentStage == Stage.STAGE1) {
            // has sent message type 1 to my children
                if (overloaded == 1 && finishCount && children.size() == 0 && parent == null) {
                // if I am the root and all my neighbors are busy
                // continue to ask for help
                    for (RegionIdentifier neighbor : neighborSet) {
                        sendMessageType1(neighbor, 1, -availableLoad(iteration), iteration, serviceID);
                    }
                } else if (finishCount && (numberOfSentType2 < children.size())) {
                // not all my children answered yet
                    for (RegionIdentifier neighbor : freeNeighbors) {
                        sendMessageType1(neighbor, messageToForward.getHop(), messageToForward.getLoad(), iteration,
                                serviceID);
                    }
                    Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                    remaining.addAll(neighborSet);
                    remaining.removeAll(freeNeighbors);
                    for (RegionIdentifier neighbor : remaining) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                } else if (finishCount && (numberOfSentType2 == children.size())) { 
                // have aggregated messages to send to my parent
                    currentStage = Stage.STAGE2;
                    messageToSend.setIteration(iteration);
                    messageToSend.setStage(currentStage);
                    sendMessage(messageToSend, iteration);
                    Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                    remaining.addAll(neighborSet);
                    remaining.remove(parent);
                    for (RegionIdentifier neighbor : remaining) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                } else if (!finishCount) {
                // do not know all my children yet, keep sending stage message to everybody
                    for (RegionIdentifier neighbor : neighborSet) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                }
            } // end STAGE1
            else if (currentStage == Stage.STAGE2) {
            // has receive message type 2 from my children, and sent message type 2 to my parent
                // I am the root
                if (parent == null && messageToSend != null && messageToSend.getType() == 3) {                    
                    currentStage = Stage.FREE;
                    for (DcopInfoMessage info : storedInfoType2List) {                        
                        messageToSend = new DcopInfoMessage(selfRegionID, info.getSender(),
                                info.getMessages().get(selfRegionID), iteration, serviceID, currentStage, parent);
                        messageToSend.setType(3);
                        messageToSend.setServiceID(serviceID);
//                        LOGGER.info("==SET THE SERVICE ID HERE ");
                        
                        sendMessage(messageToSend, iteration);

                        LOGGER.info("ITERATION " + iteration + " PLAN FROM ROOT: " + messageToSend);
                        for (DcopMessage fromChild : info.getMessages().get(selfRegionID)) {
                            overloadSharedToOther = overloadSharedToOther + fromChild.getLoad();                                                     
                        }
                        requestedLoad = -availableLoad(iteration);
                        
                        // modify the outgoingLoadMap here
                        for (Map.Entry<RegionIdentifier, List<DcopMessage>> entry : messageToSend.getMessages()
                                .entrySet()) {
                            RegionIdentifier messageReceiver = entry.getKey();
                            List<DcopMessage> messageList = entry.getValue();

                            for (DcopMessage dcopMessage : messageList) {
                                outgoingLoadMap.put(messageReceiver,
                                        outgoingLoadMap.get(messageReceiver) + dcopMessage.getLoad());
                            }
                        }
                    }

                    Set<RegionIdentifier> remainingNeighbors = new HashSet<RegionIdentifier>();
                    remainingNeighbors.addAll(neighborSet);
                    remainingNeighbors.removeAll(children);
                    for (RegionIdentifier neighbor : remainingNeighbors) {
                        sendStageMessage(neighbor, iteration, serviceID);
                    }
                    
                    empty();                    
                } else { // I am not the root
                    if (messageToSend == null) { // did not receive answer from my parent yet
                        for (RegionIdentifier neighbor : neighborSet) {
                            sendStageMessage(neighbor, iteration, serviceID);
                        }
                    }
                    // build plan
                    else if (messageToSend.getType() == 4) {
                        currentStage = Stage.FREE;
                        List<DcopMessage> dcopMessageList = messageToSend.getMessages().get(selfRegionID);
                        for (DcopInfoMessage infoType2Stored : storedInfoType2List) {
                            List<DcopMessage> dcopMessageListType2Stored = infoType2Stored.getMessages().get(selfRegionID);
                            for (DcopMessage messageType2 : dcopMessageListType2Stored) {
                                for (DcopMessage message : dcopMessageList) {
                                    if (messageType2.getOriginalSender().equals(message.getOriginalSender())
                                            && messageType2.getHop() == message.getHop()) {
                                        messageType2.setLoad(message.getLoad());
                                    }
                                }
                            }
                        }
                        for (DcopInfoMessage info : storedInfoType2List) {
                            DcopInfoMessage send = new DcopInfoMessage(selfRegionID, info.getSender(),
                                    info.getMessages().get(selfRegionID), iteration, serviceID, currentStage, parent);
                            send.setType(4);
                            sendMessage(send, iteration);
                            
                            RegionIdentifier directChildren = info.getSender();
                            for (DcopMessage msg : info.getMessages().get(selfRegionID)) {
                                outgoingLoadMap.put(directChildren,
                                        outgoingLoadMap.get(directChildren) + msg.getLoad());
                            }
                        }

                        Set<RegionIdentifier> remaining = new HashSet<RegionIdentifier>();
                        remaining.addAll(neighborSet);
                        remaining.removeAll(children);
                        for (RegionIdentifier neighbor : remaining) {
                            sendStageMessage(neighbor, iteration, serviceID);
                        }

                        requestedLoad = 0;
                        empty();
                    }
                }
            } // end STAGE2
            
            /// *********************
            // READ
            Set<DcopInfoMessage> received = checkFreeNeighborsAndChildren(iteration);
            if (currentStage == Stage.FREE) {
                decide(received, iteration);
            } else if (currentStage == Stage.STAGE1) {
                for (DcopInfoMessage info : received) {
                    if (info.getStage() == Stage.STAGE2 && info.getParent() != null
                            && info.getParent().equals(selfRegionID)) {
                        for (DcopMessage fromChild : info.getMessages().get(selfRegionID)) {
                            if (!info.getSender().equals(fromChild.getOriginalSender())) {
                                fromChild.setLatency(fromChild.getLatency() + latency.get(info.getSender()));
                            }
                        }

                        if (!info.getMessages().get(selfRegionID).isEmpty()) {
                            // store while all children send their messages
                            storedInfoType2List.add(info);
                            numberOfSentType2++;
                        }
                    }
                }
                // if all information received
                if (finishCount && numberOfSentType2 == children.size()) {
                    if (leaf) {
                        for (DcopInfoMessage info : received) {
                            if (!info.getMessages().get(selfRegionID).isEmpty()) {
                                DcopMessage temp = info.getMessages().get(selfRegionID).get(0);
                                requestedLoad = temp.getLoad();
                                List<DcopMessage> tempList = new ArrayList<DcopMessage>();
                                if (availableLoad(iteration) > 0 && requestedLoad - availableLoad(iteration) <= 0) {
                                    tempList.add(temp);
                                } else if (availableLoad(iteration) > 0
                                        && requestedLoad - availableLoad(iteration) > 0) {
                                    temp.setLoad(availableLoad(iteration));
                                    tempList.add(temp);
                                }
                                temp.setOriginalSender(selfRegionID);
                                temp.setHop(temp.getHop() - 1);
                                temp.setEfficiency(efficiency);
                                temp.setLatency(latency.get(parent));
                                messageToSend = new DcopInfoMessage(selfRegionID, parent, tempList, -1, serviceID,
                                        currentStage, parent);
                                messageToSend.setType(2);
                            }
                        }
                    } else if (!children.isEmpty()) {// aggregate messages from my children
                        aggregateMessages(iteration);
                    }
                    if (parent == null && !children.isEmpty()) {// I am the root
                        currentStage = Stage.STAGE2;
                        messageToSend.setType(3);
                    }
                }
            } else if (currentStage == Stage.STAGE2) {
                if (parent == null) {
                    // just to know that I need to send a personalized plan for every children
                    messageToSend.setType(3);
                } else {// waiting for a plan from my parent
                    for (DcopInfoMessage info : received) {
                        // If message's stage is FREE, then it's a plan from the parent
                        if (info.getStage() == Stage.FREE && info.getSender() == parent) {
                            if (!info.getMessages().get(selfRegionID).isEmpty()) {
                                // KHOI's code to assign serviceID
                                serviceID = info.getServiceID();
//                                LOGGER.info("==SET THE SERVICE ID HERE " + serviceID);
                                
                                // KHOI's code to assign serviceID
                                if (messageToSend != null)
                                    messageToSend.setServiceID(serviceID);
//                                LOGGER.info("==SET THE SERVICE ID HERE ");
                                
                                if (!leaf) {
                                    messageToSend = info;
                                    messageToSend.setType(4);
                                    
                                    for (DcopMessage forMe:info.getMessages().get(selfRegionID))
                                        if (forMe.getOriginalSender().equals(selfRegionID)) {
                                            loadAgreeToReceiveFromOther += forMe.getLoad();
                                            totalIncomingLoad += forMe.getLoad();
                                            // totalDemand = totalDemand + forMe.getLoad();
                                            break;
                                        }
                                        // this message plan is for my children
                                        else {
                                            totalIncomingLoad += forMe.getLoad();
                                        }
//                                    toSend.setServiceID(serviceID);
                                    break;
                                } 
                                else {
                                    loadAgreeToReceiveFromOther += info.getMessages().get(selfRegionID).get(0)
                                            .getLoad();
                                    totalIncomingLoad += info.getMessages().get(selfRegionID).get(0).getLoad();
                                    // totalDemand = totalDemand +
                                    // info.getMessages().get(selfRegion).get(0).getLoad();
                                    leaves--;
                                    if (leaves == 0)
                                        overloaded = 0;
                                    currentStage = Stage.FREE;
                                    requestedLoad = 0;
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
        int dcopIncomingLoad = totalIncomingLoad + totalDemand;
        
        int dcopLoadToKeep = (totalDemand >= regionCapacity) ? (loadAgreeToReceiveFromOther + regionCapacity)
                : (loadAgreeToReceiveFromOther + totalDemand);
        
//        if (totalDemand >= regionCapacity)
//            dcopLoadToKeep = loadAgreeToReceiveFromOther + regionCapacity;
//        else {
//            dcopLoadToKeep = loadAgreeToReceiveFromOther + totalDemand;
//        }
       
        LOGGER.info("Total incomingLoad: " + (totalIncomingLoad + totalDemand));
        LOGGER.info("Load to keep " + dcopLoadToKeep);
        LOGGER.info("Outgoing load map: " + outgoingLoadMap);

        //  Return default plan if totalLoads coming is 0
        if (dcopIncomingLoad == 0) {
            RegionPlan defaultRegionPlan = defaultPlan(summary);
            LOGGER.info("REGION PLAN " + defaultRegionPlan);
            return defaultRegionPlan;
        }
        
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
        
        for (Map.Entry<RegionIdentifier, Double> entry:outgoingLoadMap.entrySet()) {
            regionPlanBuilder.put(entry.getKey(), 1.0 * entry.getValue() / dcopIncomingLoad);
        }
        
//        for (Map.Entry<RegionIdentifier, Double> entry:outgoingLoadMap.entrySet()) {
//            regionPlanBuilder.put(entry.getKey(), 0.2);
//        }
        
        regionPlanBuilder.put(selfRegionID, 1.0 * dcopLoadToKeep / dcopIncomingLoad);
        
//        regionPlanBuilder.put(selfRegion, 1.0 - 0.2 * neighborSet.size());

        ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();

        servicePlanBuilder.put(serviceID, regionPlan);

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
                .build();

        LOGGER.info("REGION PLAN " + dcopPlan.toString());

        final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);

        return rplan;
    }

    /**
     * @return the available load Capacity - demand + sharedLoad
     */
    private int availableLoad(int iter) {
        // Either overloadSharedToOther = 0 or loadAgreeToReceiveFromOther = 0
        return (regionCapacity - totalDemand + overloadSharedToOther - loadAgreeToReceiveFromOther);
    }

    /**
     * when an agent gets FREE again
     * 
     * @postcondition clear children, clear parent, clear toSend, clear
     *                storedInfoType2, leaf = false, messageToForward,
     *                numberOfChildren=0,finishCount = false clear others, fill
     *                freeNeighbors with all neighbors again, numberOfSentType2
     *                = 0, hop = 0
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
     * @param receiver
     *            receiver
     * @param iteration
     *            current iteration
     * @param serviceID
     *            service send only my stage to receiver, type 0, empty messages
     */
    public void sendStageMessage(RegionIdentifier receiver, int iteration, ServiceIdentifier<?> serviceID) {
        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration, new DcopInfoMessage(selfRegionID, receiver, iteration,
                    serviceID, currentStage, parent));
        } else if (inbox.getMessageAtIteration(iteration) == null) {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(selfRegionID);
            inside.setServiceID(serviceID);
            inside.setStage(currentStage);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            inside.getMessages().put(receiver, l);
            inside.setType(0);
            inside.setParent(parent);
            inbox.addIterationDcopInfo(iteration, inside);
        } else {
            DcopInfoMessage inside = inbox.getMessageAtIteration(iteration);
            inside.setSender(selfRegionID);
            inside.setServiceID(serviceID);
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
     * @param receiver
     *            a free neighbor (verify before calling the method)
     * @param hop
     *            1 if I am the root, more otherwise
     * @param load
     *            requested load >0
     * @param iteration
     *            iteration
     * @param serviceID
     *            serviceID
     * @param parent
     *            null if the root Sends only hop,load
     */
    private void sendMessageType1(RegionIdentifier receiver,
            int hop,
            int load,
            int iteration,
            ServiceIdentifier<?> serviceID) {
        DcopMessage message = new DcopMessage(hop, load);

        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration, new DcopInfoMessage(selfRegionID, receiver, iteration,
                    serviceID, currentStage, parent));
        } else if (inbox.getMessageAtIteration(iteration) == null) {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(selfRegionID);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            l.add(message);
            inside.getMessages().put(receiver, l);
            inside.setServiceID(serviceID);
            inside.setStage(currentStage);
            inside.setParent(parent);
            inside.setType(1);
            inbox.addIterationDcopInfo(iteration, inside);
        }
        else {
            DcopInfoMessage inside = inbox.getMessageAtIteration(iteration);
            inside.setSender(selfRegionID);
            inside.setIteration(iteration);
            List<DcopMessage> l = new ArrayList<DcopMessage>();
            l.add(message);
            inside.getMessages().put(receiver, l);
            inside.setServiceID(serviceID);
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
     * @param o
     *            DcopSharedInformationInside sends info provided in o
     */
    private void sendMessage(DcopInfoMessage o, int iteration) {
        if (inbox == null) {// it never happens
            inbox = new DcopSharedInformation();
            inbox.addIterationDcopInfo(iteration, new DcopInfoMessage(o));
        } else if (inbox.getMessageAtIteration(iteration) == null)  {
            DcopInfoMessage inside = new DcopInfoMessage();
            inside.setSender(o.getSender());
            inside.setIteration(iteration);

            for (RegionIdentifier receiver : o.getMessages().keySet()) {
                List<DcopMessage> l = new ArrayList<DcopMessage>();
                if (!o.getMessages().isEmpty())
                    for (DcopMessage m : o.getMessages().get(receiver)) {
                        l.add(new DcopMessage(m.getOriginalSender(), m.getHop(), m.getLoad(), m.getEfficiency(),
                                m.getLatency()));
                        inside.getMessages().put(receiver, l);
                    }
                else {
                    inside.getMessages().put(receiver, l);
                }
            }
            inside.setServiceID(o.getServiceID());
            inside.setStage(o.getStage());
            inside.setParent(o.getParent());
            inside.setType(o.getType());
            inbox.addIterationDcopInfo(iteration, inside);
        } else {
            DcopInfoMessage inside = inbox.getMessageAtIteration(iteration);
            inside.setSender(o.getSender());
            inside.setIteration(iteration);

            for (RegionIdentifier receiver : o.getMessages().keySet()) {
                List<DcopMessage> l = new ArrayList<DcopMessage>();
                if (!o.getMessages().isEmpty())
                    for (DcopMessage m : o.getMessages().get(receiver)) {
                        l.add(new DcopMessage(m.getOriginalSender(), m.getHop(), m.getLoad(), m.getEfficiency(),
                                m.getLatency()));
                        inside.getMessages().put(receiver, l);
                    }
                else {
                    inside.getMessages().put(receiver, l);
                }
            }
            inside.setServiceID(o.getServiceID());
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
                + messageToSend.getMessageAtIteration(iteration).getMessages() + " to "
                + messageToSend.getMessageAtIteration(iteration).getMessages().keySet());
    }

    /**
     * @param iteration
     *            current iteration
     * @postcondition freeNeighbors is a set with all free neighbors and
     *                children is filled
     * @return set of messages sent only to me
     */
    private Set<DcopInfoMessage> checkFreeNeighborsAndChildren(int iteration) {
        Set<DcopInfoMessage> messageSet = new HashSet<DcopInfoMessage>();
        // initially assume all neighbors are free
        freeNeighbors.addAll(neighborSet);
        do {            
            for (Map.Entry<RegionIdentifier, DcopSharedInformation> entry : dcopInfoProvider
                    .getAllDcopSharedInformation().entrySet()) {
                
                DcopInfoMessage message = entry.getValue().getMessageAtIteration(iteration); //entry.getKey() is the sender
                
                if (currentStage != Stage.STAGE2) {
                    freeNeighbors.removeAll(children);
                    freeNeighbors.remove(parent);
                    freeNeighbors.removeAll(others);
                }

                if (message != null && message.isSentTo(selfRegionID, iteration)) {
                    messageSet.add(message);
                    if (currentStage != Stage.STAGE2) {
                        if (message.getStage() != Stage.FREE) {
                            freeNeighbors.remove(entry.getKey());
                        }

                        if (availableLoad(iteration) < 0 && message.getParent() == null
                                && message.getStage() != Stage.FREE) {
                            if (others.add(message.getSender())) {
                                countingChildren++;
                                if (countingChildren == neighborSet.size())
                                    finishCount = true;
                            }
                        } else if (message.getParent() != null && message.getParent().equals(selfRegionID)) {
                            if (currentStage != Stage.FREE && children.add(message.getSender())) {
                                countingChildren++;
                                if (countingChildren == neighborSet.size())
                                    finishCount = true;
                            } else if (currentStage == Stage.FREE) {
                                inTransition = true;
                            }
                        } else if ((message.getParent() != null && !message.getParent().equals(selfRegionID))) {
                            if (others.add(message.getSender())) {
                                countingChildren++;
                                if (countingChildren == neighborSet.size())
                                    finishCount = true;
                            }
                        } else if ((message.getParent() == null && message.getStage() != Stage.FREE)) {
                            if (countingChildren == neighborSet.size())
                                finishCount = true;
                        }
                        if (parent != null && children.size() + 1 == neighborSet.size()) {
                            finishCount = true;
                        }
                        if (finishCount && children.size() == 0 && overloaded != 1) {
                            if (!leaf) {
                                leaf = true;
                                leaves++;
                            }
                        }
                    } // end if stage2
                } // end of if message is sent to me
            }

            // wait before continue a new loop of reading messages
            try {
                Thread.sleep(DcopInfo.SLEEPTIME_WAITING_FOR_MESSAGE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (messageSet.size() < neighborSet.size());
        
        return messageSet;

    }

    /**
     * @param readInfo
     *            set of dcopSharedInfo sent only to me, obtained when checking
     *            for free neighbors
     * @precondition this agent is FREE and receives petition of help
     * @postcondition parent is defined, DcopMessage to send is filled
     *                accordingly but stages do not change until message is sent
     *                Heuristics to decide what to do If I am free it means My
     *                available load >=0 otherwise I will be at least at Stage1
     */
    private void decide(Set<DcopInfoMessage> readInfo, int iteration) {
        int availableLoad = availableLoad(iteration);
        int min = Integer.MAX_VALUE;
        int minPartial = Integer.MAX_VALUE;
        int max = -1;
        DcopInfoMessage chosenAllLoad = null;
        DcopInfoMessage chosenNoNeigh = null;
        DcopInfoMessage toForward = null;
        for (DcopInfoMessage message : readInfo) {
            if (!message.getMessages().get(selfRegionID).isEmpty()) {
                int load = message.getMessages().get(selfRegionID).get(0).getLoad();
                // if this region can help with all the load heuristics: minimize availableLoad-load
                if (availableLoad >= load) {
                    int temp = availableLoad - load;
                    if (temp < min) {
                        chosenAllLoad = message;
                        min = temp;
                    }
                } else if (freeNeighbors.size() > 0) {// forward max load
                    if (load > max) {
                        max = load;
                        toForward = message;
                    }
                } else if (!inTransition) {
                    int temp = load - availableLoad;
                    if (temp < minPartial) {
                        minPartial = temp;
                        chosenNoNeigh = message;
                    }
                }
            }
        } // end for
        // prepare information toSend
        if (chosenAllLoad != null) {
            parent = chosenAllLoad.getSender();
            DcopMessage sent = chosenAllLoad.getMessages().get(selfRegionID).get(0);
            requestedLoad = sent.getLoad();
            //asks only for load excess 
            int load = sent.getLoad();
            hop = sent.getHop();
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
            temp.add(new DcopMessage(selfRegionID, hop, load, efficiency, latency.get(parent)));
            
            // do not copy stage and iteration!! when sending message
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, serviceID, currentStage, parent); 
            if (!leaf) {
                leaf = true;
                leaves++;
            }
            finishCount = true;
            children = new HashSet<RegionIdentifier>();
        } else if (toForward != null) {
            parent = toForward.getSender();
            DcopMessage sent = toForward.getMessages().get(selfRegionID).get(0);
            requestedLoad = sent.getLoad();
            int load = IS_OPTIMIZATION ? sent.getLoad() : sent.getLoad() - availableLoad; //NEW CODE
            hop = sent.getHop();
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
            temp.add(new DcopMessage(hop + 1, load));
            // do not copy stage and receiver when the message is sent
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, serviceID, currentStage, parent);
            messageToSend.setType(1);
        } else if (chosenNoNeigh != null) {// leaf
            parent = chosenNoNeigh.getSender();
            DcopMessage sent = chosenNoNeigh.getMessages().get(selfRegionID).get(0);
            requestedLoad = sent.getLoad();
            hop = sent.getHop();
            List<DcopMessage> temp = new ArrayList<DcopMessage>();
            temp.add(new DcopMessage(selfRegionID, hop, availableLoad, efficiency, latency.get(parent)));
            // do not copy stage and iteration when sending message
            messageToSend = new DcopInfoMessage(selfRegionID, parent, temp, -1, serviceID, currentStage, parent); 
            if (!leaf) {
                leaf = true;
                leaves++;
            }
            finishCount = true;
            children = new HashSet<RegionIdentifier>();
        }
    }

    /**
     * @postcondition toSend has aggregated message for parent
     */
    private void aggregateMessages(int iteration) {
        List<DcopMessage> forParent = IS_OPTIMIZATION ? preprocessListDCOP(iteration) : preprocessListDCSP(iteration);
        messageToSend = new DcopInfoMessage(selfRegionID, parent, forParent, -1, serviceID, currentStage, parent);
    }

    /**
     * @precondition storedInfoType2 has the load offered by the children and
     *               the correct latency
     * @return aggregated information for parent
     * @postcondition storedInfoType2 is updated with aggregated load to use
     */
    private List<DcopMessage> preprocessListDCOP(int iteration) {
        int sum = 0;
        int limit = 0;
        int maxLoad = -1;
        int children;
        int availableLoad = availableLoad(iteration);
        List<Integer> maxPerAgent = new ArrayList<Integer>();
        List<List<Integer>> result = new ArrayList<List<Integer>>();

        for (DcopInfoMessage info : storedInfoType2List) {
            for (DcopMessage message : info.getMessages().get(selfRegionID)) {
                int load = message.getLoad();
                maxPerAgent.add(load);
                sum += load;
                if (maxLoad < load) {
                    maxLoad = load;
                }
            }
        }

        if (availableLoad > 0)
            maxPerAgent.add(availableLoad);
        children = maxPerAgent.size();

        limit = Math.min(sum, requestedLoad); // if not enough to help send the max possible
        // Obtain permutations of load
        List<Integer> str = new ArrayList<Integer>();
        List<Integer> data = new ArrayList<Integer>();
        List<List<Integer>> valid = new ArrayList<List<Integer>>();
        for (int i = 0; i <= maxLoad; i++) {
            str.add(i);
        }
        for (int i = 0; i < children; i++) {
            data.add(0);
        }
        permutationWithRepetition(str, data, children - 1, 0, valid, limit);
        for (List<Integer> oneCombination : valid) {
            for (int i = 0; i < children; i++) {
                if (oneCombination.get(i) > maxPerAgent.get(i)) {
                    break;
                }
                if (i == children - 1)
                    result.add(oneCombination);
            }
        }
        // min max
        double temp;
        double min = Double.POSITIVE_INFINITY;
        List<Integer> selected = null;

        for (List<Integer> load : result) {
            int index = 0;
            double max = -1.0;
            for (DcopInfoMessage info : storedInfoType2List) {
                for (DcopMessage message : info.getMessages().get(selfRegionID)) {
                    temp = load.get(index) * (message.getEfficiency() + message.getLatency());
                    if (temp > max) {
                        max = temp;
                    }
                    index++;
                }
            }
            if (min > max) {
                min = max;
                selected = load;
            }
        }
        return createNewPlan(selected, iteration);
    }

    /**
     * @param loads
     * @return create a message for parent with the distribution load in loads
     *         and storedInfoType2
     */
    private List<DcopMessage> createNewPlan(List<Integer> loads, int iteration) {
        List<DcopMessage> forParent = new ArrayList<DcopMessage>();
        int index = 0;
        for (DcopInfoMessage info : storedInfoType2List) {
            for (DcopMessage offered : info.getMessages().get(selfRegionID)) {
                changeLoad(info, offered, loads.get(index));
                offered.setLoad(loads.get(index));
                forParent.add(offered);
                index++;
            }
        }
        if (availableLoad(iteration) > 0)
            forParent.add(new DcopMessage(selfRegionID, hop, loads.get(index), efficiency,
                    (parent == null) ? 0 : latency.get(parent)));

        return forParent;
    }

    /**
     * @param str
     *            list from 0 to maxLoad
     * @param data
     *            list of size of the children initialized with all 0
     * @param children
     * @param index
     *            start with 0
     * @param valid
     *            empty list to put the result
     * @param total
     *            load asked by the parent
     * @postcondition valid contains all permutations that add to total
     *                (constraint)
     */
    private void permutationWithRepetition(List<Integer> str,
            List<Integer> data,
            int children,
            int index,
            List<List<Integer>> valid,
            int total) {
        int i, length = str.size();

        for (i = 0; i < length; i++) {
            data.set(index, str.get(i));
            if (index == children) {
                int sum = data.stream().mapToInt(Integer::intValue).sum();
                if (sum == total) {
                    valid.add(new ArrayList<Integer>(data));
                }
            } else {
                permutationWithRepetition(str, data, children, index + 1, valid, total);
            }
        }
    }

    /**
     * @param summary
     *            Temporary
     */
    private void retrieveLatency(ResourceSummary summary) {

        ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity = summary
                                                                                             .getNetworkCapacity();
        for (Map.Entry<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> entry : networkCapacity.entrySet()) {
            // latency.put(entry.getKey(),
            // entry.getValue().get(LinkMetricAttribute.of(LinkMetricName.DATARATE)));
            latency.put(entry.getKey(), 0.0);
        }
    }

    // **************************//

    /**
     * @param summary
     * @postcondition serviceID gets the id of the service demanded and demand
     *                from all clients to the region is added in totalDemand
     */
    private void retrieveAggregateDemand(ResourceSummary summary) {
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverDemand = summary
                .getServerDemand();
        double localAggregateDemand = 0;
        for (Map.Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entryService : serverDemand
                .entrySet()) {
//            ServiceIdentifier<?> serviceIDKey = entryService.getKey();
            serviceID = entryService.getKey();
            ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceDemand = entryService
                    .getValue();

            for (Map.Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> entryRegion : serviceDemand
                    .entrySet()) {
                // NodeAttribute: NodeMetricName {TASK_CONTAINERS, false}
                // where true/false is application specific
                ImmutableMap<NodeAttribute<?>, Double> regionDemand = entryRegion.getValue();
                for (Double demandValue : regionDemand.values()) {
//                    setAggregateDemand((int) (getAggregateDemand() + demandValue));
                    localAggregateDemand += demandValue;
                }
            }
        }
        setAggregateDemand((int) localAggregateDemand);
    }

    /**
     * @param summary
     * @postcondition regionCapacity contains the TASK_CONTAINERS of the server
     *                of this region
     */
    private void retrieveAggregateCapacity(ResourceSummary summary) {
        final NodeAttribute<?> containersAttribute = NodeMetricName.TASK_CONTAINERS;
        if (summary.getServerCapacity().containsKey(containersAttribute)) {
            regionCapacity = summary.getServerCapacity().get(containersAttribute).intValue();
        }
    }

    /**
     * @param summary
     * @postcondition neighborSet contains the set of neighbors
     */
    private void retrieveNeighborLink(ResourceSummary summary) {
        ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity = summary
                .getNetworkCapacity();
        for (Map.Entry<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> entry : networkCapacity.entrySet()) {
            // LinkAttribute: LinkMetricName {DATARATE, false}
            // where true/false is application specific
            RegionIdentifier neighborID = entry.getKey();
            if (!neighborID.toString().equals(selfRegionID.toString())) {
                neighborSet.add(neighborID);
            }
        }
    }

    /**
     * @param summary
     * @return the plan by default when this region processes all the load
     */
    private RegionPlan defaultPlan(ResourceSummary summary) {
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();

        regionPlanBuilder.put(selfRegionID, 1.0);
        for (RegionIdentifier neighbor:neighborSet) {
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

    private boolean changeLoad(DcopInfoMessage old, DcopMessage newMessage, int load) {
        for (DcopMessage m : old.getMessages().get(selfRegionID)) {
            if (m.equals(newMessage)) {
                m.setLoad(load);
                return true;
            }
        }
        return false;
    }

    /**
     * @return aggregateDemand
     */
    public double getAggregateDemand() {
        return totalDemand;
    }

    /**
     * @param aggregateDemand
     *            demand
     */
    public void setAggregateDemand(int aggregateDemand) {
        this.totalDemand = aggregateDemand;
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
    public void setAggregateCapacity(int aggregateCapacity) {
        this.regionCapacity = aggregateCapacity;
    }

    /**
     * @return load that agent agree to receive from other
     */
    public int getAgreeLoad() {
        return loadAgreeToReceiveFromOther;
    }

    /**
     * @param agreeLoad set loads that agent agree to receive from other
     */
    public void setAgreeLoad(int agreeLoad) {
        this.loadAgreeToReceiveFromOther = agreeLoad;
    }

    /**
     * @return totalIncomingLoad
     */
    public int getTotalIncomingLoad() {
        return totalIncomingLoad;
    }

    /**
     * @param totalIncomingLoad totalIncomingLoad
     */
    public void setTotalIncomingLoad(int totalIncomingLoad) {
        this.totalIncomingLoad = totalIncomingLoad;
    }

    
    /**
     * @return aggregated messages for parent
     * @postcondition storedInfoType2 has modified loads to build the plan
     */
    private List<DcopMessage> preprocessListDCSP(int iteration) {
//        Comparator<DcopMessage> comparator = new DcopMessage,;
        PriorityQueue<DcopMessage> bucket = new PriorityQueue<DcopMessage>(10, DcopMessage.getSortByHop());
        for (DcopInfoMessage info : storedInfoType2List) {
            bucket.addAll(info.getMessages().get(selfRegionID));
        }
        DcopMessage offered = bucket.poll();
        int toAllocate = requestedLoad;
        List<DcopMessage> forParent = new ArrayList<DcopMessage>();

        while (offered != null && offered.getLoad() <= toAllocate) {
            for (DcopInfoMessage info : storedInfoType2List) {
                if (info.getMessages().get(selfRegionID).contains(offered)) {
                    forParent.add(offered);
                    break;
                }
            }
            toAllocate -= offered.getLoad();
            offered = bucket.poll();
        }
        if (offered != null && offered.getLoad() > toAllocate) {
            for (DcopInfoMessage info : storedInfoType2List) {
                if (info.getMessages().get(selfRegionID).contains(offered)) {
                    changeLoad(info, offered, toAllocate);
                    offered.setLoad(toAllocate);
                    forParent.add(offered);
                    break;
                }
            }
        }
        offered = bucket.poll();
        while (offered != null) {
            for (DcopInfoMessage info : storedInfoType2List) {
                if (info.getMessages().get(selfRegionID).contains(offered)) {
                    changeLoad(info, offered, 0);
                    offered.setLoad(0);
                    forParent.add(offered);
                    break;
                }
            } // end for
            offered = bucket.poll();
        }

        // storedInfoType2 contains the DcopSharedInformationInside needed to build a plan
        // add my contribution
        if (availableLoad(iteration) > 0) {// NEW CODE
            // NEW CODE
            // Khoi: change myHop to hop
            forParent.add(new DcopMessage(selfRegionID, hop, availableLoad(iteration), efficiency, 0));
        }
        return forParent;
    }

    /**
     * @return number of hops away from the current tree root
     */
    public int getMyHop() {
        return myHop;
    }

    /**
     * @param myHop
     *            number of hops away from the current tree root
     */
    public void setMyHop(int myHop) {
        this.myHop = myHop;
    }
}
