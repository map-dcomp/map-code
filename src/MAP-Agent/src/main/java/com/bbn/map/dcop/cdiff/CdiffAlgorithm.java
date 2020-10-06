package com.bbn.map.dcop.cdiff;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.cdiff.CdiffDcopMessage.CdiffMessageType;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#DISTRIBUTED_CONSTRAINT_DIFFUSION}
 *
 */
public class CdiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(CdiffAlgorithm.class);
    
    /**
     * @author khoihd
     *
     */
    private enum Stage {
        /**
         * When agent is not belonging to any tree.
         */
        STAGE_FREE,
        /**
         * When agent is about to send message to ask for help.
         */
        STAGE_ASK_FOR_HELP,
        /**
         * When agent is waiting for help reply.
         */
        STAGE_WAIT_FOR_ASK_REPLY,
        /**
         * When agent is about to send the aggregated plan to the parent.
         */
        STAGE_SEND_PLAN_TO_PARENT,
        /**
         * when agent is waiting for the final plan from parent.
         */
        STAGE_WAIT_FOR_PLAN_FROM_PARENT,
        /**
         * When agent is about to send the final plan to children.
         */
        STAGE_SEND_PLAN_TO_CHILDREN,
    }
    
    /**
     * @author khoihd
     *
     */
    private enum FlowType {
        /**
         * Incoming flow, positive value in flowLoadMap
         */
        INCOMING,
        /**
         * Outgoing flow, negative value in flowLoadMap
         */
        OUTGOING
    }
    
    /**
     * @author khoihd
     *
     */
    private enum StoreType {
        /**
         * Used when parent is finalizing plan for children
         */
        STORE_THE_LOAD,
        /**
         * Used when parent is pretending to store the load and send the excess load to children
         */
        NOT_STORE_THE_LOAD,
    }
    
    private final Map<RegionIdentifier, CdiffDcopMessage> messageMap = new HashMap<>();
    
    private int firstIteration;
    
    private int lastIteration;

    private ResourceSummary summary;
    
    private DcopSharedInformation inbox;
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMap = new HashMap<>();
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> cdiffKeepLoadMap = new HashMap<>();
    
    private Stage currentStage = Stage.STAGE_FREE;
    
    private final Set<RegionIdentifier> childrenSet = new HashSet<>();
    
    private final Map<RegionIdentifier, CdiffDcopMessage> replyFromChildrenMap = new HashMap<>();
    
    private final SortedMap<Integer, Map<RegionIdentifier, Double>> aggregatedChildrenPlan = new TreeMap<>(); 
    
    private RegionIdentifier parent;
    
    private CdiffDcopMessage messageFromParent;
    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public CdiffAlgorithm(RegionIdentifier regionID,
            DcopInfoProvider dcopInfoProvider,
            ApplicationManagerApi applicationManager) {
        super(regionID, dcopInfoProvider, applicationManager);
    }

    /**
     * @return DCOP plan
     */
    public RegionPlan run() {
        initialize();
        
        writeTheLastIteration();

        // Not running the first DCOP run due to incomplete neighbor information
        if (firstIteration == 0) {
            return defaultPlan(summary);
        }
                
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());
        
        LOGGER.info("Iteration {} Region {} has inferred server demand {}", firstIteration, getRegionID(), inferredServerDemand);
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = aggregateDemandMap(inferredServerDemand);
        
        updateFlowLoadMap(getRegionID(), convert(demandMap), FlowType.INCOMING);
        
        processInitialDemandLoadMap(demandMap);
        
        for (int iteration = firstIteration; iteration < DCOP_ITERATION_LIMIT + firstIteration; iteration++) {                                    
            LOGGER.info("Iteration {} Region {} is in {} has parent={} and has childrenSet={}", iteration, getRegionID(), currentStage, parent, childrenSet);
            
            sendAllMessages(iteration);
            
            changeCurrentStage();
            
            LOGGER.info("Iteration {} Region {} is in {}", iteration, getRegionID(), currentStage);
            
            // waiting for messages from neighbors
            final Map<RegionIdentifier, CdiffDcopMessage> receivedCdiffMessageMap = new HashMap<>();
            
            try {
                Map<RegionIdentifier, GeneralDcopMessage> receivedGeneralMessageMap = waitForMessagesFromNeighbors(iteration);
                for (Entry<RegionIdentifier, GeneralDcopMessage> entry : receivedGeneralMessageMap.entrySet()) {
                    GeneralDcopMessage msg = entry.getValue();
                                        
                    if (msg instanceof CdiffDcopMessage) {
                        receivedCdiffMessageMap.put(entry.getKey(), new CdiffDcopMessage((CdiffDcopMessage) msg));
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOGGER.warn("InterruptedException when waiting for messages. Return the default DCOP plan: {} ",
                        e.getMessage(), e);
                return defaultPlan(summary);
            }
            
            LOGGER.info("Iteration {} Region {} has receivedCdiffMessageMap {}", iteration, getRegionID(), receivedCdiffMessageMap);
            
            for (Entry<RegionIdentifier, CdiffDcopMessage> entry : receivedCdiffMessageMap.entrySet()) {
                LOGGER.info("Iteration {} Region {} receives message from Region {}: {}", iteration, getRegionID(), entry.getKey(), entry.getValue());
            }

            // Processing message and prepare message to send for the next DCOP iteration
            if (currentStage == Stage.STAGE_FREE) {
                processMessagesInFreeStage(receivedCdiffMessageMap);
            }
            else if (currentStage == Stage.STAGE_WAIT_FOR_ASK_REPLY) {
                processMessagesWhenWaitingForHelpReply(receivedCdiffMessageMap);
            } 
            else if (currentStage == Stage.STAGE_WAIT_FOR_PLAN_FROM_PARENT) {
                processMessagesWhenWaitingForFinalPlan(receivedCdiffMessageMap);
            }

            LOGGER.info("Iteration {} Region {} has flowLoadMap {}", iteration, getRegionID(), getFlowLoadMap());
            LOGGER.info("Iteration {} Region {} has getClientLoadMap {}", iteration, getRegionID(), getClientKeepLoadMap());
        } // end of DCOP iterations
        
//        createKeepLoadMap();
        createClientKeepLoadMap();
        
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has flowLoadMap {}", lastIteration, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has getClientLoadMap {}", lastIteration, getRegionID(), getClientKeepLoadMap());
        
        return computeRegionDcopPlan(summary, lastIteration, false);
        
    }
    
    private void createClientKeepLoadMap() {
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceEntry : cdiffKeepLoadMap.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            for (Entry<RegionIdentifier, Double> clientEntry : serviceEntry.getValue().entrySet()) {
                updateKeyKeyLoadMap(getClientKeepLoadMap(), clientEntry.getKey(), service, clientEntry.getValue(), true);
            }
        }
    }

    /**
     * Create keepLoadMap from getCdiffKeepLoadMap
     */
//    private void createKeepLoadMap() {
//        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : cdiffKeepLoadMap.entrySet()) {
//            getKeepLoadMap().put(entry.getKey(), sumValues(entry.getValue()));
//        }
//    }
//    
    private void updateFlowLoadMap(RegionIdentifier regionID, Map<ServiceIdentifier<?>, Double> demandMap, FlowType flowType) {
        for (Entry<ServiceIdentifier<?>, Double> entry : demandMap.entrySet()) {
            if (flowType.equals(FlowType.INCOMING)) {
                updateKeyKeyLoadMap(getFlowLoadMap(), regionID, entry.getKey(), entry.getValue(), true);
            } else if (flowType.equals(FlowType.OUTGOING)) {
                updateKeyKeyLoadMap(getFlowLoadMap(), regionID, entry.getKey(), -entry.getValue(), true);
            }
        }
    }

    /**
     * 
     * If Region has excess load 
     *  => send AGREE_TO_HELP with excess load map and hop = 0 to all neighbors
     *  
     * If Region has no excess load
     *  Check if receive some ASK_FOR_HELP messages <hop -> load>, ignore messages with other types:
     *      + Pick the ASK_FOR_HELP message with highest priority service among the messages
     *      + Make sender as the parent
     *      + Given the <service -> load>, agents pretend to serve as much load as possible from highest to lowest service
     *      + If there is no excess load
     *          => send PROPOSED_PLAN <#hop + 1, requestedLoad> to parent
     *          => set currentStage to STAGE_TO_SEND_PLAN_TO_PARENT
     *          => Send REFUSE_TO_HELP to the remaining senders of ASK_FOR_HELP messages that are not chosen
     *      + If there is excess load
     *          => send ASK_FOR_HELP <service -> load> with hop++ to all other neighbors except for the parent
     *          => send AGREE_TO_HELP message to the parent
     * @precondition region is in FREE_STAGE
     * @param receivedCdiffMessageMap message map received from neighbors
     */
    private void processMessagesInFreeStage(Map<RegionIdentifier, CdiffDcopMessage> receivedCdiffMessageMap) {
        // If region still has excess loads
        // Send ASK_FOR_HELP message to all neighbors
        if (hasExcess(excessLoadMap)) {
            for (RegionIdentifier neighbor : getNeighborSet()) {
                addMessage(neighbor, CdiffMessageType.ASK_FOR_HELP, excessLoadMap, 0);
            }
            
            childrenSet.addAll(getNeighborSet());
            setCurrentStage(Stage.STAGE_ASK_FOR_HELP);
            
            return ;
        }
        
        // Has no excess load
        // Checking for ASK_FOR_HELP message
        // If there is no ASK_FOR_HELP message, send FREE
        // If choose a ASK_FOR_HELP message
        //  => Send AGREE to HELP to the parent
        //  => Send ASK_FOR_HELP to others
    
        int maxServicePriority = Integer.MIN_VALUE;
        
        final Set<RegionIdentifier> regionAskingForHelpSet = new HashSet<>();
        
        for (Entry<RegionIdentifier, CdiffDcopMessage> msgEntry : receivedCdiffMessageMap.entrySet()) {                
            // Consider only ASK_FOR_HELP message
            if (msgEntry.getValue().getMessageType() != CdiffMessageType.ASK_FOR_HELP) {
                continue;
            }
            
            RegionIdentifier sender = msgEntry.getKey();
            
            regionAskingForHelpSet.add(sender);
            
            CdiffDcopMessage helpMessage = msgEntry.getValue();
            
            // Determine the ask message with highest priority
            SortedSet<ServiceIdentifier<?>> sortedServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
            for (Object object : helpMessage.getCdiffLoadMap().keySet()) {
                sortedServiceSet.add((ServiceIdentifier<?>) object);
            }
            
            if (getPriority(sortedServiceSet.first()) > maxServicePriority) {
                parent = sender;
                messageFromParent = helpMessage;
                maxServicePriority = getPriority(sortedServiceSet.first());
            }
        } // end for reading message
        
        // No region asking for help
        // Stage not changed
        if (null == parent) {
            for (RegionIdentifier neighbor : getNeighborSet()) {
                addMessage(neighbor, CdiffMessageType.EMPTY_MESSAGE, Collections.emptyMap(), 0);
            }
        }
        // Either Send REFUSE_TO_HELP to regionAskingForHelpSet
        // Or Send ASK_FOR_HELP to other neighbors
        // Change stage to STAGE_TO_ASK_FOR_HELP
        else {
            regionAskingForHelpSet.remove(parent);
            
            childrenSet.addAll(getNeighborSet());
            childrenSet.removeAll(regionAskingForHelpSet);
            childrenSet.remove(parent);
            
            CdiffDcopMessage msgFromParent = receivedCdiffMessageMap.get(parent);
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> parentLoadMap = convertObjectToServiceMap(msgFromParent.getCdiffLoadMap());
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = storePriorityServiceLoad(parentLoadMap, StoreType.NOT_STORE_THE_LOAD);
                                    
            if (hasExcess(excessLoad)) {
                // If there is no children
                // Propose with the difference
                if (childrenSet.isEmpty()) {
                    // If the excessLoad is the same, then refuse to help
                    if (parentLoadMap.equals(excessLoad)) {
                        addMessage(parent, CdiffMessageType.REFUSE_TO_HELP, Collections.emptyMap(), 0);
                    }
                    // If can help, propose with the difference
                    else {               
                        final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                                msgFromParent.getHop() + 1, getRegionID(), getAvailableCapacity());
                        addMessage(parent, CdiffMessageType.REPLY_WITH_PROPOSED_PLAN, proposePlan, 0);
                        setCurrentStage(Stage.STAGE_SEND_PLAN_TO_PARENT);
                    }
                }
                // Children set is not empty
                else {
                    addMessage(parent, CdiffMessageType.AGREE_TO_HELP, Collections.emptyMap(), 0);
                    
                    // Ask for help
                    for (RegionIdentifier child : childrenSet) {
                        addMessage(child, CdiffMessageType.ASK_FOR_HELP, excessLoad, msgFromParent.getHop() + 1);
                    }
                    setCurrentStage(Stage.STAGE_ASK_FOR_HELP);
                }
            } 
            // No excess load map
            // Propose <hop + 1, sum load>
            else {
                final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                        msgFromParent.getHop() + 1, getRegionID(), sumKeyKeyValues(parentLoadMap));
//                proposePlan.put(msgFromParent.getHop() + 1, sumValues(parentLoadMap));
                addMessage(parent, CdiffMessageType.REPLY_WITH_PROPOSED_PLAN, proposePlan, 0);
                setCurrentStage(Stage.STAGE_SEND_PLAN_TO_PARENT);
            }
            
            // Reply with refuse to help
            for (RegionIdentifier neighbor : regionAskingForHelpSet) {
                addMessage(neighbor, CdiffMessageType.REFUSE_TO_HELP, Collections.emptyMap(), 0);
            }
            
            // Send EMPTY message to the rest
            Set<RegionIdentifier> neighborToSendFree = new HashSet<>(getNeighborSet());
            neighborToSendFree.removeAll(messageMap.keySet());
            for (RegionIdentifier region : neighborToSendFree) {
                addMessage(region, CdiffMessageType.EMPTY_MESSAGE, Collections.emptyMap(), 0);
            }
        }
    }

    /*
     *  - If a region is STAGE_WAIT_FOR_ASK_REPLY:
     *    + If received REFUSE_TO_HELP, delete the region from childrenSet
          + If received ASK_FOR_HELP, reply with REFUSE_TO_HELP
          + If received PROPOSED_PLAN, store it and add the sender to childrenSet
          + If received AGREE_TO_HELP, add the sender to childrenSet
          
          + If already received PROPOSED_PLAN from all children
            ++ If parent is not null: (not root)
              +++ Aggregate the PROPOSED_PLAN from all children, store hop -> children -> load
              +++ aggregateProposePlan()
              +++ Send the the PROPOSED_PLAN in <hop -> load> to parent
              +++ Change stage to STAGE_TO_SEND_PLAN_TO_PARENT
            ++ If parent is null: (root of the tree)
              +++ From the <hop -> children -> proposedLoad> and <service -> load> from server demand
              +++ finalizePlan()
              +++ Send <service -> load> to each children
              +++ Change stage to STAGE_TO_SEND_PLAN_TO_CHILDREN
     */
    private void processMessagesWhenWaitingForHelpReply(Map<RegionIdentifier, CdiffDcopMessage> receivedCdiffMessageMap) {
        for (Entry<RegionIdentifier, CdiffDcopMessage> msgEntry : receivedCdiffMessageMap.entrySet()) {
            RegionIdentifier sender = msgEntry.getKey();
            CdiffDcopMessage cdiffMsg = msgEntry.getValue();
            
            if (cdiffMsg.getMessageType() == CdiffMessageType.REFUSE_TO_HELP) {
                childrenSet.remove(sender);
            }
            else if (cdiffMsg.getMessageType() == CdiffMessageType.ASK_FOR_HELP) {
                childrenSet.remove(sender);
                addMessage(sender, CdiffMessageType.REFUSE_TO_HELP, Collections.emptyMap(), 0);
            }
            else if (cdiffMsg.getMessageType() == CdiffMessageType.REPLY_WITH_PROPOSED_PLAN) {
                replyFromChildrenMap.put(sender, cdiffMsg);
            }
        }
        
        if (isRoot()) {
            // Receive all refuse from children
            // Then keep asking for help to all neighbors
            if (childrenSet.size() == 0) {
                resetTreeInformation();
                
                for (RegionIdentifier neighbor : getNeighborSet()) {
                    addMessage(neighbor, CdiffMessageType.ASK_FOR_HELP, excessLoadMap, 0);
                }
                
                childrenSet.addAll(getNeighborSet());
                setCurrentStage(Stage.STAGE_ASK_FOR_HELP);
            } 
            // Has received all proposed plan from children
            else if (replyFromChildrenMap.size() == childrenSet.size()) {
                aggregateChildrenPlan();
                
                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(excessLoadMap);
                
                for (RegionIdentifier child : childrenSet) {
                    addMessage(child, CdiffMessageType.FINAL_PLAN_TO_CHILDREN, finalPlan.get(child), 0);
                    updateFlowLoadMap(child, convert(finalPlan.get(child)), FlowType.OUTGOING);
                }
                setCurrentStage(Stage.STAGE_SEND_PLAN_TO_CHILDREN);
            }
        } // end if isRoot() 
        else {
            // Receive all REFUSE_TO_HELP messages
            if (childrenSet.size() == 0) {
                if (hasAvailableCapacity()) {                    
                    double proposedLoad = Math.min(getAvailableCapacity(), sumKeyKeyValues(messageFromParent.getCdiffLoadMap()));
                    
                    Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(messageFromParent.getHop() + 1, getRegionID(), proposedLoad);
                    
                    addMessage(parent, CdiffMessageType.REPLY_WITH_PROPOSED_PLAN, proposePlan, 0);
                    setCurrentStage(Stage.STAGE_SEND_PLAN_TO_PARENT);
                } 
                // Receives all REFUSE_TO_HELP from other neighbors and has no capacity to help
                else {
                    addMessage(parent, CdiffMessageType.REFUSE_TO_HELP, Collections.emptyMap(), 0);
                    setCurrentStage(Stage.STAGE_FREE);
                }
            }
            // Has received all proposed plan from children
            else if (replyFromChildrenMap.size() == childrenSet.size()) {
                Map<Integer, Double> proposedPlan = aggregatePlanFromChildrenToSenToParent();
                
                Map<Integer, Map<RegionIdentifier, Double>> proposedPlanToParent = new HashMap<>();
                for (Entry<Integer, Double> entry : proposedPlan.entrySet()) {
                    Map<RegionIdentifier, Double> plan = new HashMap<>();
                    
                    plan.put(getRegionID(), entry.getValue());
                    
                    proposedPlanToParent.put(entry.getKey(), plan);
                }
                
                addMessage(parent, CdiffMessageType.REPLY_WITH_PROPOSED_PLAN, proposedPlanToParent, 0);
                
                setCurrentStage(Stage.STAGE_SEND_PLAN_TO_PARENT);
            }
        } // Processing plan if any
        
        // Send EMPTY message to the neighbors that aren't in the message map
        Set<RegionIdentifier> neighborToSendFreeMsg = new HashSet<>(getNeighborSet());
        neighborToSendFreeMsg.removeAll(messageMap.keySet());
        
        for (RegionIdentifier region : neighborToSendFreeMsg) {
            addMessage(region, CdiffMessageType.EMPTY_MESSAGE, Collections.emptyMap(), 0);
        }                        
    }

    private void processMessagesWhenWaitingForFinalPlan(Map<RegionIdentifier, CdiffDcopMessage> receivedCdiffMessageMap) {
        for (Entry<RegionIdentifier, CdiffDcopMessage> msgEntry : receivedCdiffMessageMap.entrySet()) {
            RegionIdentifier sender = msgEntry.getKey();
            CdiffDcopMessage cdiffMsg = msgEntry.getValue();
            
            if (cdiffMsg.getMessageType() == CdiffMessageType.ASK_FOR_HELP) {
                addMessage(sender, CdiffMessageType.REFUSE_TO_HELP, Collections.emptyMap(), 0);
            }
            
            if (cdiffMsg.getMessageType() == CdiffMessageType.FINAL_PLAN_TO_CHILDREN && sender.equals(parent)) {                
                final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> finalPlan = convertObjectToServiceMap(cdiffMsg.getCdiffLoadMap());
                
                updateFlowLoadMap(parent, convert(finalPlan), FlowType.INCOMING);
                
                final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planToChildren = storePriorityServiceLoad(finalPlan, StoreType.STORE_THE_LOAD);
                                
                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childrenPlan = createFinalPlan(planToChildren);
                
                for (RegionIdentifier child : childrenSet) {
                    addMessage(child, CdiffMessageType.FINAL_PLAN_TO_CHILDREN, childrenPlan.get(child), 0);
                    updateFlowLoadMap(child, convert(childrenPlan.get(child)), FlowType.OUTGOING);
                }
                
                setCurrentStage(Stage.STAGE_SEND_PLAN_TO_CHILDREN);
            }
        } // END READING MESSAGE
        
        Set<RegionIdentifier> neighborToSendFreeMsg = new HashSet<>(getNeighborSet());
        neighborToSendFreeMsg.removeAll(messageMap.keySet());
        
        for (RegionIdentifier region : neighborToSendFreeMsg) {
            addMessage(region, CdiffMessageType.EMPTY_MESSAGE, Collections.emptyMap(), 0);
        }
    }
    
    /**
     * @param hop
     * @param client
     * @param load
     * @return a map with single entry hop -> client -> load
     */
    private Map<Integer, Map<RegionIdentifier, Double>> createProposeSingleEntryPlan(int hop, RegionIdentifier client, double load) {
        final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = new HashMap<>();
        final Map<RegionIdentifier, Double> clientPlan = new HashMap<>();

        clientPlan.put(client, load);
        proposePlan.put(hop, clientPlan);
        
        return proposePlan;
    }

    /**
     * From aggregatedChildrenPlan: hop -> children -> load
     *              and excessLoad: service -> load
     * Create children -> service -> load
     * @return
     */
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> createFinalPlan(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMap) {
        final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = new HashMap<>();
        for (RegionIdentifier child : childrenSet) {
            finalPlan.put(child, new HashMap<>());
        }
        
        final SortedSet<ServiceIdentifier<?>> priorityServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        priorityServiceSet.addAll(excessLoadMap.keySet());
        
        for (ServiceIdentifier<?> service : priorityServiceSet) {            
            
            for (Entry<Integer, Map<RegionIdentifier, Double>> hopEntry : aggregatedChildrenPlan.entrySet()) {                
                for (Entry<RegionIdentifier, Double> childrenProposeEntry : hopEntry.getValue().entrySet()) {
                    
                    for (Entry<RegionIdentifier, Double> excessEntry : excessLoadMap.get(service).entrySet()) {
                        RegionIdentifier child = childrenProposeEntry.getKey();
                        double proposedLoad = childrenProposeEntry.getValue();

                        double excessLoad = excessEntry.getValue();
                        RegionIdentifier client = excessEntry.getKey();
                        
                        // If excessLoad is larger than proposedLoad
                        // Then only assign proposedLoad
                        // Reduce the excessLoad
                        if (compareDouble(excessLoad, proposedLoad) >= 0) {
                            updateKeyKeyKeyLoadMap(finalPlan, child, service, client, proposedLoad, true);
                            excessLoadMap.get(service).put(client, excessLoad  - proposedLoad);
                            childrenProposeEntry.setValue(0.0);
                        }
                        else {
                            // If excessLoad is smaller than proposedLoad
                            // Then only assign excessLoad
                            // Remove the excessLoadMap for service
                            updateKeyKeyKeyLoadMap(finalPlan, child, service, client, excessLoad, true);
                            excessLoadMap.get(service).put(client, 0.0);
                            childrenProposeEntry.setValue(proposedLoad - excessLoad);
                        }
                    }
                }
            }
            
            excessLoadMap.get(service).values().removeIf(v -> compareDouble(v, 0) == 0);
        }
        
        excessLoadMap.values().removeIf(v -> compareDouble(sumValues(v), 0) == 0);
        
        return finalPlan;
    }

    /**
     * @return true if the region is a root of some tree
     */
    private boolean isRoot() {
        return (null == parent);
    }

    /**
     * Create the aggregatedChildrenPlan
     * @return
     */
    private Map<Integer, Double> aggregatePlanFromChildrenToSenToParent() {
        Map<Integer, Double> proposedPlan = new HashMap<>();
        
        aggregateChildrenPlan();
        
        for (Entry<Integer, Map<RegionIdentifier, Double>> childrenPlanEntry : aggregatedChildrenPlan.entrySet()) {
            proposedPlan.put(childrenPlanEntry.getKey(), sumValues(childrenPlanEntry.getValue()));
        }
        
        // Add self-load to the plan and send it to the parent
        double keepLoad = hasAvailableCapacity() ? getAvailableCapacity() : 0;
        proposedPlan.put(messageFromParent.getHop() + 1, keepLoad);
        
        return proposedPlan;
    }

    /**
     * From replyFromChildrenMap, modify the aggregatedChildrenPlan
     */
    private void aggregateChildrenPlan() {
        for (Entry<RegionIdentifier, CdiffDcopMessage> msgEntry : replyFromChildrenMap.entrySet()) {
            RegionIdentifier child = msgEntry.getKey();
            
            for (Entry<Object, Map<RegionIdentifier, Double>> proposedEntry : msgEntry.getValue().getCdiffLoadMap().entrySet()) {
                updateKeyKeyLoadMap(aggregatedChildrenPlan, (Integer) proposedEntry.getKey(), child, sumValues(proposedEntry.getValue()), true);
            }
        }        
    }
    
    private void resetTreeInformation() {
        parent = null;
        messageFromParent = null;
        childrenSet.clear();
        replyFromChildrenMap.clear();
        aggregatedChildrenPlan.clear();
    }

    private void changeCurrentStage() {
        switch (currentStage) {
        case STAGE_ASK_FOR_HELP:
            setCurrentStage(Stage.STAGE_WAIT_FOR_ASK_REPLY);
            break;
        
        case STAGE_SEND_PLAN_TO_CHILDREN:
            setCurrentStage(Stage.STAGE_FREE);
            resetTreeInformation();
            break;
        
        case STAGE_SEND_PLAN_TO_PARENT:
            setCurrentStage(Stage.STAGE_WAIT_FOR_PLAN_FROM_PARENT);
            break;
        default:
            break;
        }
    }

    private void processInitialDemandLoadMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = storePriorityServiceLoad(demandMap, StoreType.STORE_THE_LOAD);
        
        excessLoadMap.putAll(excessLoad);
        
        if (hasExcess(excessLoad)) {                                    
            for (RegionIdentifier neighbor : getNeighborSet()) {
                addMessage(neighbor, CdiffMessageType.ASK_FOR_HELP, excessLoad, 0);
            }
            
            childrenSet.addAll(getNeighborSet());
            currentStage = Stage.STAGE_ASK_FOR_HELP;
        }
        else {
            for (RegionIdentifier neighbor : getNeighborSet()) {
                addMessage(neighbor, CdiffMessageType.EMPTY_MESSAGE, Collections.emptyMap(), 0);
            }
        }
    }
    
    /**
     * Add message that will be sent in the beginning of the next DCOP iteration
     * Message will be overridden
     * @param receiver
     * @param messageType
     * @param loadMap
     * @param hop
     */
    private <A> void addMessage(RegionIdentifier receiver, CdiffMessageType messageType, Map<A, Map<RegionIdentifier, Double>> loadMap, int hop) {
        CdiffDcopMessage cdiffMsg = new CdiffDcopMessage(loadMap, messageType, hop);
        messageMap.put(receiver, cdiffMsg);
    }
    

    /**
     * Update keepLoadMap and return the excessLoadMap
     * @param loadMap is the load map that region needs to store
     * @return the excessLoadMap
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> storePriorityServiceLoad(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadMap, StoreType storeType) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = deepCopyMap(loadMap);
        
        SortedSet<ServiceIdentifier<?>> sortedServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedServiceSet.addAll(excessLoad.keySet());
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> keepLoadMap = null;
        
        if (storeType == StoreType.STORE_THE_LOAD) {
            keepLoadMap = cdiffKeepLoadMap;
        } 
        else { // if (storeType == StoreType.NOT_STORE_THE_LOAD)
            // Use deep copy to avoid affecting cdiffKeepLoadMap
            // The goal is to compute the excess load and to avoid storing any load
            keepLoadMap = deepCopyMap(cdiffKeepLoadMap);
        }
        
        for (ServiceIdentifier<?> service : sortedServiceSet) {
            for (Entry<RegionIdentifier, Double> entry : excessLoad.get(service).entrySet()) {
                RegionIdentifier client = entry.getKey();
                
                // Can't use the function getAvailableCapacity() because it depends on the keepLoadMap
                double availableCapacity = getRegionCapacity() - sumKeyKeyValues(keepLoadMap);
                
                // Break if region has no available capacity
                if (compareDouble(availableCapacity, 0) <= 0) {
                    break;
                }
                
                double serviceLoad = entry.getValue();
                
                // If availableCapacity >= serviceLoad
                // Store all the load 
                if (compareDouble(availableCapacity, serviceLoad) >= 0) {
                    updateKeyKeyLoadMap(keepLoadMap, service, client, serviceLoad, true);
                    excessLoad.get(service).put(client, 0.0);
                } 
                // If availableCapacity <= serviceLoad
                // Store availableCapacity, reduce the loadMap by availableCapacity
                // Then break since there is no available capacity
                else {
                    updateKeyKeyLoadMap(keepLoadMap, service, client, availableCapacity, true);
                    excessLoad.get(service).put(client, serviceLoad - availableCapacity);
                }       
            }
            excessLoad.get(service).values().removeIf(v -> compareDouble(v, 0) == 0);
        }
        
        excessLoad.values().removeIf(v -> compareDouble(sumValues(v), 0) == 0);

        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMapToChildren = swapHigherAndLowerPriorityService(excessLoad, keepLoadMap);
        
        return deepCopyMap(excessLoadMapToChildren);
    }
    
    /**
     * Given the inputLoadMap, agents get the keepLoadMap and optimize over them
     * Return the excess load map
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> swapHigherAndLowerPriorityService(
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad,
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> keepLoadMap) {
        SortedSet<ServiceIdentifier<?>> sortedInputSet = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedInputSet.addAll(excessLoad.keySet());
        
        SortedSet<ServiceIdentifier<?>> reverseSortedKeepSet = new TreeSet<>(Collections.reverseOrder(new SortServiceByPriorityComparator()));
        reverseSortedKeepSet.addAll(keepLoadMap.keySet());
//        Map<ServiceIdentifier<?>, Double> keepLoadMap = getCdiffKeepLoadMap();
        
        for (ServiceIdentifier<?> higherService : sortedInputSet) {
            for (Entry<RegionIdentifier, Double> higherEntry : excessLoad.get(higherService).entrySet()) {
                RegionIdentifier higherClient = higherEntry.getKey();
                double higherLoad = higherEntry.getValue();
            
                for (ServiceIdentifier<?> lowerService : reverseSortedKeepSet) {
                    for (Entry<RegionIdentifier, Double> lowerEntry : keepLoadMap.get(lowerService).entrySet()) {
                        RegionIdentifier lowerClient = lowerEntry.getKey();
                        double lowerLoad = lowerEntry.getValue();
                    
                        if (getPriority(higherService) > getPriority(lowerService)) {
                            if (compareDouble(higherLoad, lowerLoad) <= 0) {
        
                                // add higherLoad of higherService to keepLoadMap
                                updateKeyKeyLoadMap(keepLoadMap, higherService, higherClient, higherLoad, true);
                                
                                // put or add <lowerService, higherLoad> to the input service -> load
                                updateKeyKeyLoadMap(excessLoad, lowerService, lowerClient, higherLoad, true);
        
                                // lowerLoad -= higherLoad
                                keepLoadMap.get(lowerService).put(lowerClient, lowerLoad - higherLoad);
                                
                                // higherLoad = 0;
                                excessLoad.get(higherService).put(higherClient, 0.0);
                            } 
                            else {
                                // put or add (higherService, lowerLoad) to the keepMap
                                updateKeyKeyLoadMap(keepLoadMap, higherService, higherClient, lowerLoad, true);
        
                                // put or add (lowerService, lowerLoad) to the service -> load
                                updateKeyKeyLoadMap(excessLoad, lowerService, lowerClient, lowerLoad, true);
                                
        //                      higherLoad -= lowerLoad
                                excessLoad.get(higherService).put(higherClient, higherLoad - lowerLoad);
                                
        //                      lowerLoad = 0;
                                keepLoadMap.get(lowerService).put(lowerClient,  0.0);
                            } // endIf comparing higherLoad and lowerLoad
                        } // endIf comparing higherService and lowerService
                    }
                    keepLoadMap.get(lowerService).values().removeIf(v -> compareDouble(v, 0) == 0);
                } // end for-loop lowerService in keepSet
            }
            excessLoad.get(higherService).values().removeIf(v -> compareDouble(v, 0) == 0);
        } // end for-loop higherService in inputSet
        
        // Remove all entries with 0.0 load
        excessLoad.values().removeIf(v -> compareDouble(sumValues(v), 0) == 0);
        
        keepLoadMap.values().removeIf(v -> compareDouble(sumValues(v), 0) == 0);

        // Return the excess load map
        return deepCopyMap(excessLoad);
    }

//    /**
//     * @param excessLoadMap
//     * @return true if the sum of loads is greater than 0
//     */
//    private boolean hasExcessLoad(Map<ServiceIdentifier<?>, Double> loadMap) {
//        return compareDouble(sumValues(loadMap), 0) > 0;
//    }
    
    private <A, B, C> Map<A, Map<B, C>> deepCopyMap(Map<A, Map<B, C>> originalMap) {
        Map<A, Map<B, C>> copiedMap = new HashMap<>();
        originalMap.forEach((key, map) -> copiedMap.put(key, new HashMap<>(map)));
        return copiedMap;
    }
    
    /**
     * Send message to parent, children and other neighbors
     * Clear the messageMap after sending
     * @param iteration
     */
    private void sendAllMessages(int iteration) {
        inbox.removeMessageAtIteration(iteration - 2);
        
        // Send all messages in the messageMap
        for (Entry<RegionIdentifier, CdiffDcopMessage> entry : messageMap.entrySet()) {
            RegionIdentifier receiver = entry.getKey();
            CdiffDcopMessage cdiffMessage = entry.getValue();
            
            DcopReceiverMessage cdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            
            cdiffMsgPerIteration.setMessageToTheReceiver(receiver, cdiffMessage);
            
            inbox.setMessageAtIteration(iteration, cdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} {} message: {}", iteration, getRegionID(), receiver, cdiffMessage.getMessageType(), cdiffMessage);
        }
        
        messageMap.clear();
        
//        if (iteration == lastIteration) {
//            writeTheLastIteration();
//        }
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }
    
    /**
     * Write the last iteration of the current DCOP round .
     */
    private void writeTheLastIteration() {
        lastIteration = firstIteration + DCOP_ITERATION_LIMIT - 1;
        
        DcopReceiverMessage abstractTreeMsg = inbox.getMessageAtIterationOrDefault(TREE_ITERATION, new DcopReceiverMessage());
        abstractTreeMsg.setIteration(lastIteration);        
        abstractTreeMsg.addMessageToReceiver(getRegionID(), new CdiffDcopMessage());    
        inbox.putMessageAtIteration(TREE_ITERATION, abstractTreeMsg);
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }

    /**
     * From inferredServerDemand, create the demand map service -> load
     * @param inferredServerDemand
     * @return
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> aggregateDemandMap(
            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand) {
        
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = new HashMap<>();
        
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionEntry : serviceEntry.getValue().entrySet()) {
                updateKeyKeyLoadMap(demandMap, service, regionEntry.getKey(), sumValues(regionEntry.getValue()), true);
            }
        }
        
        return demandMap;
    }

    /** Initialize firstIteration by reading the inbox with {@link #TREE_ITERATION}
     *  @return 0 (or more if more than second DCOP run)
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
                
        if (inbox != null) {
            if (inbox.getIterationMessageMap().containsKey(TREE_ITERATION)) {
                firstIteration = inbox.getIterationMessageMap().get(TREE_ITERATION).getIteration() + 1;
            } else {
                firstIteration = 0;
            }
        }
                
        summary = getDcopInfoProvider().getRegionSummary(ResourceReport.EstimationWindow.LONG);
                
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        
        LOGGER.info("Iteration {} Region {} has Region Capacity {}", firstIteration, getRegionID(), getAvailableCapacity());
    }
    
    /**
     * Convert the map by ignoring the client and use the total load
     * @param clientLoad
     * @return
     */
    private Map<ServiceIdentifier<?>, Double> convert(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> clientLoad) {
        final Map<ServiceIdentifier<?>, Double> loadMap = new HashMap<>();
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : clientLoad.entrySet()) {
            updateKeyLoadMap(loadMap, entry.getKey(), sumValues(entry.getValue()), true);
        }
        
        return loadMap;
    }

    /**
     * @param currentStage the currentStage to set
     */
    private void setCurrentStage(Stage currentStage) {
        this.currentStage = currentStage;
    }
    
    /**
     * @return true if the region has available capacity
     */
    @Override
    protected boolean hasAvailableCapacity() {
        return compareDouble(getAvailableCapacity(), 0) > 0;
    }
    
    /**
     * regionCapacity - sumValues(keepLoadMap).
     * @return available capacity if positive
     */
    @Override
    protected double getAvailableCapacity() {
        return getRegionCapacity() - sumKeyKeyValues(cdiffKeepLoadMap);
    }
}