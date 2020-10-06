package com.bbn.map.dcop.modular_acdiff;

import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.map.dcop.AugmentedRoot;
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.modular_acdiff.ModularACdiffDcopMessage.ModularACdiffMessageType;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#MODULAR_ACDIFF}
 *
 */
public class ModularACdiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModularACdiffAlgorithm.class);
    
    /**
     * Type of flow for incoming or going load.
     * Used in computing DCOP plans 
     * 
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
     * Used to compute the excess load when region serves some amount of load.
     * Indicate if the region actually serve or just pretend to serve the load (thus for the purpose of computing 
     * 
     * @author khoihd
     *
     */
    private enum StoreType {
        /**
         * Used to indicate that the region is storing this load from server demand
         */
        STORE_DEMAND,
        /**
         * Used to indicate that the region will replace old load with the new load from PLAN message
         */
        STORE_PLAN,
        /**
         * Used to compute the excess load without actually storing the load
         */
        NOT_STORING,
    }
    
    private enum SetFree {
        /**
         * Allowed to set to free
         */
        SET_TO_FREE,
        /**
         * Not allowed to set to free
         */
        NOT_SET_TO_FREE
    }
    
    private static final boolean READING_MESSAGES = true;
                    
    private RegionIdentifier rootCurrentTree = null;
    
    /**
     * Receiver -> Message
     * Store all the message-to-send before actually sending them to the corresponding receiver
     */
    private final Map<RegionIdentifier, ModularACdiffDcopMessage> messageMapToSend = new HashMap<>();
    
    /**
     * Store all received messages. Used to identify new incoming messages
     */
    private final Map<RegionIdentifier, ModularACdiffDcopMessage> storedMessages = new HashMap<>();
    
    /**
     * RootID -> Number of hops 
     */
    private final Map<RegionIdentifier, Integer> hopMap = new HashMap<>();
    
    /**
     * Store current partially plans. Root regions will update this map if they compute new plans
     */
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> storedPlan = new HashMap<>();
    
    /**
     * Root -> Neighbor -> Service -> Load
     * Store the flow load map for each rootID. The rootID is necessary since regions might need to delete all flow information of a given rootID.
     */
    private final Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> rootFlowLoadMap = new HashMap<>();
    
    /**
     * RootID -> Service -> Client -> Load 
     * Store the load that the region has committed for each rootID
     */
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> rootKeepLoadMap = new HashMap<>();
    
    private int currentDcopRun;
    
    private ResourceSummary summary;
    
    private DcopSharedInformation inbox;
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> selfExcessLoadMap = new HashMap<>();

    /**
     * RootID -> Set of children
     */
    private final Map<RegionIdentifier, Set<RegionIdentifier>> childrenMap = new HashMap<>();
            
    /**
     * RootID -> Parent
     */
    private final Map<RegionIdentifier, RegionIdentifier> parentMap = new HashMap<>();
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> parentExcessLoadMap = new HashMap<>();
    
    /**
     * RootID -> Child -> Number of hop -> Some region -> Load
     */
    private final Map<RegionIdentifier, Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>>> proposedChildrenMessageMap = new HashMap<>();

    /**
     * Used to store roots that have been done. Ignore messages of this root except for PLAN messages
     */
    private final Set<RegionIdentifier> storedDoneRootSet = new HashSet<>();

    private final Map<RegionIdentifier, Map<RegionIdentifier, ModularACdiffLoadMap>> storedAskMessages = new HashMap<>();
        
    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public ModularACdiffAlgorithm(RegionIdentifier regionID,
            DcopInfoProvider dcopInfoProvider,
            ApplicationManagerApi applicationManager) {
        super(regionID, dcopInfoProvider, applicationManager);
    }

    private static final Random RANDOM = new Random();
    
    /**
     * @return DCOP plan
     */
    public RegionPlan run() {
        initialize();
        
        writeIterationInformation();
        
//        if (currentDcopRun != 1) {
        if (currentDcopRun == 0) {
            return defaultPlan(summary);
        }
        
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());
        
        // Service - Client -> Double
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = aggregateDemandMap(inferredServerDemand);
        
        LOGGER.info("DCOP Run {} Region {} has network demand {}", currentDcopRun, getRegionID(), summary.getNetworkDemand());
        
        LOGGER.info("DCOP Run {} Region {} has server demand {}", currentDcopRun, getRegionID(), summary.getServerDemand());
        
        LOGGER.info("DCOP Run {} Region {} has inferred server demand {}", currentDcopRun, getRegionID(), inferredServerDemand);
        
        LOGGER.info("DCOP Run {} Region {} has demand {}", currentDcopRun, getRegionID(), demandMap);
                
//        processInitialDemandLoadMap(demandMap);
        
        executeGblock(demandMap, getAvailableCapacity());
                
        final LocalTime stopTime = LocalTime.now().plus(AgentConfiguration.getInstance().getDcopAcdiffTimeOut());
                
        while (READING_MESSAGES) {            
            if (AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDrops()) {
                if (compareDouble(RANDOM.nextDouble(),
                        1 - AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDropRate()) < 0) {
                    sendAllMessages();
                }
            } else {
                sendAllMessages();
            }

            // waiting for messages from neighbors
            final Map<RegionIdentifier, ModularACdiffDcopMessage> receivedMessageMap = readAcdiffMessges(currentDcopRun);

            // There is no new message, then sleep
            if (receivedMessageMap.equals(storedMessages)) {
                // Sleep for AP round duration to avoid continuously checking for messages
                try {
                    Thread.sleep(AgentConfiguration.getInstance().getApRoundDuration().toMillis() * DCOPService.AP_ROUNDS_TO_SLEEP_BETWEEN_MESSAGE_CHECKS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Look for new messages and process them
            else {
                for (Entry<RegionIdentifier, ModularACdiffDcopMessage> receivedMessageEntry : receivedMessageMap.entrySet()) {
                    RegionIdentifier sender = receivedMessageEntry.getKey();
                    ModularACdiffDcopMessage newReceivedMessageGivenSender = receivedMessageEntry.getValue();
                    ModularACdiffDcopMessage oldStoredMessageGivenSender = storedMessages.get(sender);
                    
                    // Check if this sender sends a new message
                    if (!newReceivedMessageGivenSender.equals(oldStoredMessageGivenSender)) {
                         Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> storedMsgMap = oldStoredMessageGivenSender.getMessageTypeMap();

                        for (Entry<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> receivedEntry : newReceivedMessageGivenSender.getMessageTypeMap().entrySet()) {
                            ModularACdiffMessageType receivedMessageType = receivedEntry.getKey();
                            
                            for (ModularACdiffLoadMap receivedMessageContent : receivedEntry.getValue()) {
                                // First time received this message type or received new message of this type
                                if (receiveNewMessage(storedMsgMap, receivedMessageType, receivedMessageContent)) {
                                                                        
                                    LOGGER.info("DCOP Run {} Region {} receives and processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, receivedMessageType, receivedMessageContent);                                    
                                    
                                    executeGblock(parentExcessLoadMap, getAvailableCapacity(), receivedMessageType, receivedMessageContent, sender);
                                    
                                    executeCblock(receivedMessageType, receivedMessageContent, sender);
                                    
                                    executeClearBlock(receivedMessageType, receivedMessageContent, sender);

//                                    processNewMessageWithUpdating(receivedEntry.getKey(), receivedMessage, sender);
                                    
                                    // Store this message                                 
                                    storedMsgMap.computeIfAbsent(receivedMessageType, (unused) -> new HashSet<>()).add(receivedMessageContent);
                                    
                                }
                            } // End for loop processing new message
                        } // End for looking for new messages
                    } // Endif comparing new and stored messages
                } // Endfor traversing messages from every sender
            }
            
            createClientKeepLoadMap();
            LOGGER.info("Dcop Run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
//            LOGGER.info("DCOP Run {} Region {} end the current cycle", currentDcopRun, getRegionID());

            if (LocalTime.now().isAfter(stopTime)) {
                break;
            }
        }              
        
        createFlowLoadMap();
        createClientKeepLoadMap();
//        writeIterationInformation();
        
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has flowLoadMap {}", currentDcopRun, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
        
        return computeRegionDcopPlan(summary, currentDcopRun, true);
    }
    
    /**
     * Process PROPOSE messages and output the message type msgType only
     * @param receivedLoadMap load map from the received PROPOSE message
     * @param receivedMsgRoot root of the tree
     * @param sender one of the child
     * @param outputMsgType 
     */
    private void processProposeMessage(final Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild, RegionIdentifier receivedMsgRoot, RegionIdentifier sender, ModularACdiffMessageType outputMsgType) {                       
        // Only process PROPOSE message at root in G block
        if (ModularACdiffMessageType.PROPOSE == outputMsgType && isRoot(receivedMsgRoot)) {
            Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> storedProposedMap = proposedChildrenMessageMap.getOrDefault(getRegionID(), new HashMap<>());
            
            // Compute the difference between the current proposed and the stored proposed load map
            Map<Integer, Map<RegionIdentifier, Double>> differentProposeLoadMap = new HashMap<>();
            
            // If this is the first time receiving propose message from this sender,
            // The difference is the proposedPlanFromChild
            if (!storedProposedMap.containsKey(sender)) {
                differentProposeLoadMap = proposedPlanFromChild;
            }
            else {
                Map<Integer, Map<RegionIdentifier, Double>> storedProposedMapFromThisChild = storedProposedMap.get(sender);
                
                for (Entry<Integer, Map<RegionIdentifier, Double>> currentProposeEntry : proposedPlanFromChild.entrySet()) {
                    // If the stored doesn't contain, the difference is the load map from proposeEntry
                    if (!storedProposedMapFromThisChild.containsKey(currentProposeEntry.getKey())) {
                        differentProposeLoadMap.put(currentProposeEntry.getKey(), new HashMap<>(currentProposeEntry.getValue()));
                    }
                    else {
                        Map<RegionIdentifier, Double> storedLoadMapGivenHop = storedProposedMapFromThisChild.get(currentProposeEntry.getKey());
                        
                        Map<RegionIdentifier, Double> differentLoadMapGivenHop = new HashMap<>();
                        for (Entry<RegionIdentifier, Double> currentProposeLoadMap : currentProposeEntry.getValue().entrySet()) {
                            differentLoadMapGivenHop.put(currentProposeLoadMap.getKey(), currentProposeLoadMap.getValue() - storedLoadMapGivenHop.getOrDefault(currentProposeLoadMap.getKey(), 0D));
                        }
                        
                        differentProposeLoadMap.put(currentProposeEntry.getKey(), new HashMap<>(differentLoadMapGivenHop));   
                    }
                }
            }
            
            // Override or store the received proposed plan of this child
            storedProposedMap.put(sender, proposedPlanFromChild);
            proposedChildrenMessageMap.put(receivedMsgRoot, storedProposedMap);
            
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(selfExcessLoadMap, differentProposeLoadMap, receivedMsgRoot);
                            
            LOGGER.info("DCOP Run {} Region {} has new temporary plan {}", currentDcopRun, getRegionID(), storedPlan);
            
//            storedPlan.computeIfAbsent(receivedMsgRoot, k -> new HashMap<>());
            
            addUpPlans(storedPlan, finalPlan);
            
            LOGGER.info("DCOP Run {} Region {} has temporary plan {}", currentDcopRun, getRegionID(), storedPlan);
                            
            LOGGER.info("DCOP Run {} Region {} has current excessLoadMap {}", currentDcopRun, getRegionID(), selfExcessLoadMap);
                            
            if (hasExcess(selfExcessLoadMap)) {
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.entrySet()) {
                    RegionIdentifier child = entry.getKey();
                    
                    addMessage(child, ModularACdiffMessageType.PLAN, rootCurrentTree, 0, storedPlan.get(child));
                    updateFlowLoadMapWithRoot(child, convert(storedPlan.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                for (RegionIdentifier child : getNeighborSet()) {
                    addMessage(child, ModularACdiffMessageType.ASK, rootCurrentTree, 0, selfExcessLoadMap);
                }
            } 
            // Done with current excess load
            else {
                // Add itself to the storeDoneRootSet
                storedDoneRootSet.add(getRegionID());
                
                // Send DONE messages to all regions
                // Add the plan to the DONE messages
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.entrySet()) {
                    RegionIdentifier child = entry.getKey();
                    
                    addMessage(child, ModularACdiffMessageType.DONE, rootCurrentTree, 0, storedPlan.get(child));
                    updateFlowLoadMapWithRoot(child, convert(storedPlan.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                Set<RegionIdentifier> remainingRegions = new HashSet<>(getNeighborSet());
                remainingRegions.removeAll(storedPlan.keySet());
                for (RegionIdentifier regions : remainingRegions) {
                    addMessage(regions, ModularACdiffMessageType.DONE, rootCurrentTree, 0, Collections.emptyMap());
                }
                
                clearCurrentTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);
                if (isEmptyTree()) {
                    processStoreAskedMessages();
                }
            }
        }
        // Process PROPOSE message at non-root
        // Output aggregated PROPOSE message
        else if (ModularACdiffMessageType.PROPOSE == outputMsgType) {
            Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
            
            addMessage(parentMap.get(receivedMsgRoot), ModularACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap);
        }
        // Output updated ASK message at non-root
        else if (ModularACdiffMessageType.ASK == outputMsgType && !isRoot(receivedMsgRoot)) {
            Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
            // Override the previous PROPOSE messages received from this sender
            proposedMap.put(sender, proposedPlanFromChild);
            proposedChildrenMessageMap.put(receivedMsgRoot, proposedMap);
            
            double excessParentLoad = sumKeyKeyValues(parentExcessLoadMap);
            
            // Compute the ASK message for each children accordingly
            // ASK_i = Excess - sum_{j!=i} PROPOSE_j
            for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                    
                double loadProposedFromOtherChildren = 0;
                
                for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> childrenLoadOtherEntry : proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                    if (childrenLoadOtherEntry.getKey().equals(child)) {continue;}
                    
                    loadProposedFromOtherChildren += sumKeyKeyValues(childrenLoadOtherEntry.getValue());
                }
                
                double loadToAsk = compareDouble(excessParentLoad, loadProposedFromOtherChildren) >= 0 ? excessParentLoad - loadProposedFromOtherChildren : 0;
                                    
                if (compareDouble(loadToAsk, 0) > 0) {
                    // Pick an arbitrary service
                    ServiceIdentifier<?> serviceToChild = parentExcessLoadMap.keySet().iterator().next();
                    
                    Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createAskMsgSingleEntry(serviceToChild, getRegionID(), loadToAsk);
                    addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap);
                } else {
                    addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap());
                }
            }
        }
    }
    
    /**
     * Process only one ASK message at a time
     */
    private void processStoreAskedMessages() {
        if (storedAskMessages.isEmpty()) {return ;}
        
        RegionIdentifier receivedMsgRoot = storedAskMessages.entrySet().iterator().next().getKey();
        
        Map<RegionIdentifier, ModularACdiffLoadMap> senderContentMap = storedAskMessages.get(receivedMsgRoot);

        RegionIdentifier sender = senderContentMap.entrySet().iterator().next().getKey();
        
        ModularACdiffLoadMap receivedMsg = senderContentMap.get(sender);
        
        rootCurrentTree = receivedMsgRoot;              
        
        parentMap.put(receivedMsgRoot, sender);
        
        hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsg.getLoadMap());
        
        LOGGER.info("DCOP Run {} Region {} takes and processes Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), ModularACdiffMessageType.ASK, sender, receivedMsg);
                                                           
        parentExcessLoadMap.clear();
        parentExcessLoadMap.putAll(receivedLoadMap);

        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);
        
        LOGGER.info("DCOP Run {} Region {} processes Message Type {} from Region {} with excess load: {}", currentDcopRun, getRegionID(), ModularACdiffMessageType.ASK, sender, excessLoad);
        
        final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());
                                                                
        // Propose the plan with current available capacity
        addMessage(sender, ModularACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan);
        
        // Send the excess load to children if any to ask for help
        if (hasExcess(excessLoad)) {
            childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
            childrenMap.get(receivedMsgRoot).remove(sender);
            
            // Ask for help
            for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad);
            }
        } 
        // Empty children set if there is no excess load 
        else {
            childrenMap.put(receivedMsgRoot, new HashSet<>());
        }
    }
    
    /**
     * Process a received ASK messages, and output messages based on the msgType flag.
     * @param receivedLoadMap load map from the ASK message which is sent from the parent
     * @param receivedMsgRoot root of the current tree
     * @param outputMsgType process and output this message type only
     */
    private void processAskMessage(final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap, RegionIdentifier receivedMsgRoot, ModularACdiffMessageType outputMsgType) {        
        RegionIdentifier parent = parentMap.get(receivedMsgRoot);
        
        boolean hasReceivedProposedMessage = !proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).isEmpty();
        
        // Not received any message from children
        if (!hasReceivedProposedMessage) {
            if (ModularACdiffMessageType.ASK == outputMsgType) {
                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);
                
                // Update the excess load map from parent
                parentExcessLoadMap.clear();
                excessLoad.forEach((key, map) -> parentExcessLoadMap.put((ServiceIdentifier<?>) key, new HashMap<>(map)));
                
                // If there is excess load, send ASK message to children
                if (hasExcess(excessLoad)) {
                    childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
                    childrenMap.get(receivedMsgRoot).remove(parent);
                    
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                        addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad);
                    }
                } 
                // Otherwise, empty the children set for this root
                else {
                    childrenMap.put(receivedMsgRoot, Collections.emptySet());
                }
            }
            else if (ModularACdiffMessageType.PROPOSE == outputMsgType) {
                final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                        hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());
                                                                        
                addMessage(parent, ModularACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan);
            }
        }
        // Has received some PROPOSE messages from children
        else {
            if (ModularACdiffMessageType.ASK == outputMsgType) {
                // Update the excess load map from parent with the updated ASK message from parent
                // As self region has children, it means the self region doesn't have available capacity
                parentExcessLoadMap.clear();
                receivedLoadMap.forEach((key, map) -> parentExcessLoadMap.put((ServiceIdentifier<?>) key, new HashMap<>(map)));
                
                // Checking if the excess load from parent is larger or smaller than the load proposed by children
                final double excessParentLoad = sumKeyKeyValues(parentExcessLoadMap);
                
                final double loadFromAllChildren = sumKeyKeyKeyValues(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
                
                // Update ASK messages for other children if the children is not being able to help all
                if (compareDouble(excessParentLoad, loadFromAllChildren) > 0) {
                    // Compute the ASK message for each children accordingly
                    // ASK_i = Excess - sum_{j!=i} PROPOSE_j
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                        double loadProposedFromOtherChildren = 0;
                                                
                        for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> childrenLoadOtherEntry : proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                            if (childrenLoadOtherEntry.getKey().equals(child)) {continue;}
                            
                            loadProposedFromOtherChildren += sumKeyKeyValues(childrenLoadOtherEntry.getValue());
                        }
                        
                        double loadToAsk = Math.max(excessParentLoad - loadProposedFromOtherChildren, 0D); 
                        
                        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createSingleEntryAskLoadMap(parentExcessLoadMap.keySet().iterator().next(), getRegionID(), loadToAsk);
                                              
                        addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap);
                    }
                } 
                // Send ASK message with 0 load since all children can help all (and has proposed all) 
                else {
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                            
                        addMessage(child, ModularACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap());
                    }
                }
            }
            else if (ModularACdiffMessageType.PROPOSE == outputMsgType) {
                // Propose aggregated plan to the parent
                Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
                
                addMessage(parent, ModularACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap);
            }
        }
    }
    
    private void processSelfDemand(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> selfDemandMap) {        
        if (selfDemandMap.isEmpty() || !isEmptyTree()) {return;}
        
        updateFlowLoadMap(getRegionID(), convert(selfDemandMap), FlowType.INCOMING);
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(selfDemandMap, StoreType.STORE_DEMAND, getRegionID());
        
        selfExcessLoadMap.putAll(excessLoad);
        
        LOGGER.info("DCOP Run {} Region {} has demand keep load map: {}", currentDcopRun, getRegionID(), rootKeepLoadMap.get(getRegionID()));
        
        LOGGER.info("DCOP Run {} Region {} has excess load map: {}", currentDcopRun, getRegionID(), excessLoad);
        
        if (hasExcess(excessLoad)) {    
            // Set to a new tree every time processing self-demand and self-demand is not empty
            rootCurrentTree = getRegionID();
            
            childrenMap.put(rootCurrentTree, new HashSet<>(getNeighborSet()));
            
            for (RegionIdentifier neighbor : childrenMap.get(rootCurrentTree)) {
                addMessage(neighbor, ModularACdiffMessageType.ASK, rootCurrentTree, 0, excessLoad);
            }
            
        } else {
            childrenMap.put(rootCurrentTree, Collections.emptySet());
        }
        
        selfDemandMap.clear();
    }
    
    /**
     * G-block of the design, output ASK messages
     * @param loadToShedMap
     * @param currentCapacity
     */
    private void executeGblock(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadToShedMap, double currentCapacity) {
        executeGblock(loadToShedMap, currentCapacity, null, null, null);
    }
    
    /**
     * Execute the G block from module design
     * Output:
     *  - ASK messages with hops-to-source, excess load to shed, and parent to each neighbor
     *  - Number of hops to the source
     *  - Parent of the self agent // null if root
     *  - Children of the self agent // null if no neighbors hold self node as a parent
     *  Behavior:
     *  - From the server demand, capacity, excess load to shed and available capacity, each root computes the excess load to shed somehow into its tree of children
     *  - Build a spanning tree
     * 
     * @param selfDemandMap is either self demand map or current excess load map of the parent
     * @param currentCapacity is the current capacity of the self region
     * @param receivedMessageType is the type of received message
     * @param receivedMessageContent is the content of received message
     * @param sender
     */
    private void executeGblock(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> selfDemandMap, double currentCapacity,
            ModularACdiffMessageType receivedMessageType, ModularACdiffLoadMap receivedMessageContent, RegionIdentifier sender) {
        // Process self demand
        if (null == receivedMessageContent) {
            processSelfDemand(selfDemandMap);
        } 
        // Process ASK or PROPOSE messages and output ASK message only
        else {            
            final RegionIdentifier receivedMsgRoot = receivedMessageContent.getRootID();
            
            final Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMessageContent.getLoadMap();
            
            final boolean isReceivingMsgFromParent = parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);            
            
            final boolean rootDone = storedDoneRootSet.contains(receivedMsgRoot);
            
            // Process ASK messages
            if (ModularACdiffMessageType.ASK.equals(receivedMessageType) && !rootDone) {
                // Receiving this ASK message when not in any tree or receiving an updated ASK message from parent
                if (isEmptyTree() || isReceivingMsgFromParent) {
                    LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMessageContent);
                                    
                    rootCurrentTree = receivedMsgRoot;
                    
                    parentMap.put(receivedMsgRoot, sender);
                    
                    hopMap.put(receivedMsgRoot, receivedMessageContent.getHop() + 1);
                    
                    final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);
                                                   
                    processAskMessage(receivedLoadMap, receivedMsgRoot, ModularACdiffMessageType.ASK);
                }
                // Otherwise, store the ASK messages
                else {
//                    addMessage(sender, ModularACdiffMessageType.REFUSE, receivedMsgRoot, 0, Collections.emptyMap());
                    // Do not store and process ASK message from this regionID
                    // This mean the region has received its own ASK message forwarded by other regions
                    if (!receivedMsgRoot.equals(getRegionID())) {
                        storedAskMessages.computeIfAbsent(receivedMsgRoot, k -> new HashMap<>()).put(sender, receivedMessageContent);
                        
                        LOGGER.info("DCOP Run {} Region {} stores Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), ModularACdiffMessageType.ASK, sender, receivedMessageContent);
                    }
                }
            }
            // Process PROPOSE messages
            else if (ModularACdiffMessageType.PROPOSE.equals(receivedMessageType) && !rootDone) {
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMessageContent);
                // Convert the object message to Integers
                if (compareDouble(sumKeyKeyValues(receivedMsgLoadMap), 0D) == 0) {return ;}
                
                Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = convertObjectToIntegerMap(receivedMsgLoadMap);
                
                processProposeMessage(proposedPlanFromChild, receivedMsgRoot, sender, ModularACdiffMessageType.ASK);
            }
        }
    }
    
    /**
     * Execute the C block from module design
     * @param sender 
     * @param receivedMessage 
     * @param receivedMessageType 
     */
    private void executeCblock(ModularACdiffMessageType receivedMessageType, ModularACdiffLoadMap receivedMessageContent, RegionIdentifier sender) {
        RegionIdentifier receivedMsgRoot = receivedMessageContent.getRootID(); 
        
        Map<Object, Map<RegionIdentifier, Double>> receivedLoadMapContent = receivedMessageContent.getLoadMap();
        
        final boolean isReceivingMsgFromParent = receivedMsgRoot.equals(rootCurrentTree) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);            
        
        final boolean rootDone = storedDoneRootSet.contains(receivedMsgRoot);
                                        
        // Process ASK messages
        if (ModularACdiffMessageType.ASK.equals(receivedMessageType) && !rootDone) {
            // Receiving this ASK message when not in any tree or receiving an updated ASK message from parent
            if (isEmptyTree() || isReceivingMsgFromParent) {
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMessageContent);
                                
                rootCurrentTree = receivedMsgRoot;     
                
                parentMap.put(receivedMsgRoot, sender);
                
                hopMap.put(receivedMsgRoot, receivedMessageContent.getHop() + 1);
                
                final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedLoadMapContent);
                                               
                processAskMessage(receivedLoadMap, receivedMsgRoot, ModularACdiffMessageType.PROPOSE);
            }
        }
        // Process PROPOSE messages
        else if (ModularACdiffMessageType.PROPOSE.equals(receivedMessageType) && !rootDone) {
            LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMessageContent);

            // Convert the object message to Integers
            Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = convertObjectToIntegerMap(receivedLoadMapContent);
            
            processProposeMessage(proposedPlanFromChild, receivedMsgRoot, sender, ModularACdiffMessageType.PROPOSE);
        }
    }
    
    /**
     * Execute the Clear block from module design
     * @param sender 
     * @param receivedMsg 
     * @param msgType 
     */
    private void executeClearBlock(ModularACdiffMessageType msgType, ModularACdiffLoadMap receivedMsg, RegionIdentifier sender) {
        RegionIdentifier receivedMsgRoot = receivedMsg.getRootID();
        
        boolean isMessageFromParent = receivedMsgRoot.equals(rootCurrentTree) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);
        
        boolean rootDone = storedDoneRootSet.contains(receivedMsgRoot);
        
        // Process PLAN and DONE message from parent of the current tree
        if ((ModularACdiffMessageType.PLAN.equals(msgType) && !rootDone && isMessageFromParent)
                || ModularACdiffMessageType.DONE.equals(msgType) && isMessageFromParent) {
            LOGGER.info("DCOP Run {} Region {} in Root {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), rootCurrentTree, sender, msgType, receivedMsg.getLoadMap());

            RegionIdentifier parent = sender;
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planFromParent = convertObjectToServiceMap(receivedMsg.getLoadMap());
            
            Map<Integer, Map<RegionIdentifier, Double>> proposeChildrenLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
            
            // Update the incoming load from parent
            // TODO: this function probably affects the incoming/outgoing load of the Server-2-Client messages
            updateFlowLoadMapWithRoot(parent, convert(planFromParent), FlowType.INCOMING, receivedMsgRoot);
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(planFromParent, StoreType.STORE_PLAN, receivedMsgRoot);
                            
            LOGGER.info("DCOP Run {} Region {} has plan from parent {}", currentDcopRun, getRegionID(), planFromParent);
            
            LOGGER.info("DCOP Run {} Region {} has proposed load from children {}", currentDcopRun, getRegionID(), proposeChildrenLoadMap);
            
            LOGGER.info("DCOP Run {} Region {} has excess load {}", currentDcopRun, getRegionID(), excessLoad);
            
            // Compute plan for children
            // Send PLAN or DONE messages
            if (!childrenMap.get(receivedMsgRoot).isEmpty()) {
                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childrenPlan = createFinalPlan(excessLoad, proposeChildrenLoadMap, receivedMsgRoot);
                                    
                LOGGER.info("DCOP Run {} Region {} has plan to children: {}", currentDcopRun, getRegionID(), childrenPlan);

                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childEntry : childrenPlan.entrySet()) {                        
                    // Send PLAN or DONE depends on the received message type
                    addMessage(childEntry.getKey(), msgType, receivedMsgRoot, hopMap.get(receivedMsgRoot), childEntry.getValue());
                    
                    updateFlowLoadMapWithRoot(childEntry.getKey(), convert(childEntry.getValue()), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                // Send DONE to the other neighbors
                if (ModularACdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> remainingAgents = new HashSet<>(getNeighborSet());
                    remainingAgents.removeAll(childrenPlan.keySet());
                    remainingAgents.remove(parent);
                    remainingAgents.remove(receivedMsgRoot);
                    
                    for (RegionIdentifier remaining : remainingAgents) {
                        addMessage(remaining, ModularACdiffMessageType.DONE, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap());
                    }
                }
            } 
            // Has no children
            else {
                // Forward the DONE messages to neighboring agents except for the parent (sender) and the root
                if (ModularACdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
                    forwardingDoneAgents.remove(parent);
                    forwardingDoneAgents.remove(receivedMsgRoot);
                    
                    for (RegionIdentifier agents : forwardingDoneAgents) {
                        addMessage(agents, ModularACdiffMessageType.DONE, receivedMsgRoot, 0, Collections.emptyMap());
                    }
                }
            } // end if checking for children
            
            if (ModularACdiffMessageType.DONE.equals(msgType)) {
                storedDoneRootSet.add(receivedMsgRoot);
                clearCurrentTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);

                // Only process ASK messages if not in any 
                if (isEmptyTree()) {
                    processStoreAskedMessages();
                }
            }
        
        }
        // First time receiving DONE message for this root
        else if (ModularACdiffMessageType.DONE.equals(msgType) && !rootDone) {
            storedDoneRootSet.add(receivedMsgRoot);
            
            clearCurrentTreeInformation(receivedMsgRoot, SetFree.NOT_SET_TO_FREE);
            
            Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
            forwardingDoneAgents.remove(sender);
            forwardingDoneAgents.remove(receivedMsgRoot);
            // In this case, do not forward DONE messages
            // Reason: this parent might just forward DONE message to its children,
            // and the children will set to free as the above case
            forwardingDoneAgents.removeAll(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).keySet());
            
            for (RegionIdentifier agents : forwardingDoneAgents) {
                addMessage(agents, ModularACdiffMessageType.DONE, receivedMsgRoot, 0, Collections.emptyMap());
            }
        
        } 
        
    }

    private void createFlowLoadMap() {
        rootFlowLoadMap.forEach((root, neighborFlowMap) -> 
            neighborFlowMap.forEach((neighbor, regionFlow) -> 
                regionFlow.forEach((service, load) -> updateKeyKeyLoadMap(getFlowLoadMap(), neighbor, service, load, true))));
    }

    private boolean receiveNewMessage(Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> storedMsgMap, ModularACdiffMessageType msgType, ModularACdiffLoadMap receivedMessage) {
        return !storedMsgMap.containsKey(msgType) || !storedMsgMap.get(msgType).contains(receivedMessage);
    }

    private void clearCurrentTreeInformation(RegionIdentifier receivedMsgRoot, SetFree isSetToFree) {
        removeMessage(receivedMsgRoot);
        
        storedAskMessages.remove(receivedMsgRoot);
        
        // If in this tree, then set to free 
        if (rootCurrentTree.equals(receivedMsgRoot) && isSetToFree == SetFree.SET_TO_FREE) {
            
            rootCurrentTree = null;
            
            LOGGER.info("DCOP Run {} Region {} changes from tree {} to empty tree {}", currentDcopRun, getRegionID(), receivedMsgRoot, AugmentedRoot.getEmptyTree());
        }      
    }
    
    /**
     * Remove PROPOSE, ASK and PLAN messages for this augmentedRoot
     * @param receivedMsgRoot
     */
    private void removeMessage(RegionIdentifier receivedMsgRoot) {
        for (Entry<RegionIdentifier, GeneralDcopMessage> msgEntry : inbox.getAsynchronousMessage().getReceiverMessageMap().entrySet()) {
            if (msgEntry.getValue() instanceof ModularACdiffDcopMessage) {
                ModularACdiffDcopMessage rcdiffMsg = (ModularACdiffDcopMessage) msgEntry.getValue();
                
                // Remove all PROPOSE message of this root
                Set<ModularACdiffLoadMap> msgToRemove = new HashSet<>();
                
                Set<ModularACdiffLoadMap> proposeMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularACdiffMessageType.PROPOSE, Collections.emptySet());
                for (ModularACdiffLoadMap msg : proposeMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                proposeMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
                
                
                Set<ModularACdiffLoadMap> askMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularACdiffMessageType.ASK, Collections.emptySet());
                // Remove all the ASK message of this root
                for (ModularACdiffLoadMap msg : askMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                askMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
                
                Set<ModularACdiffLoadMap> planMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularACdiffMessageType.PLAN, Collections.emptySet());
                // Remove all the REFUSE message of this root
                for (ModularACdiffLoadMap msg : askMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                planMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
            }
        }        
    }

    private void addUpPlans(Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> plan,
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> planToAdd) {
        
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry1 : planToAdd.entrySet()) {
            for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry2 : entry1.getValue().entrySet()) {
                for (Entry<RegionIdentifier, Double> entry3 : entry2.getValue().entrySet()) {
                    updateKeyKeyKeyLoadMap(plan, entry1.getKey(), entry2.getKey(), entry3.getKey(), entry3.getValue(), true);
                }
            }
        }
        
    }

    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> createSingleEntryAskLoadMap(
            ServiceIdentifier<?> service,
            RegionIdentifier regionID,
            double load) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = new HashMap<>();
        Map<RegionIdentifier, Double> loadMap = new HashMap<>();
        
        loadMap.put(regionID, load);
        askLoadMap.put(service, loadMap);
        
        return askLoadMap;
    }

    private Map<Integer, Map<RegionIdentifier, Double>> aggregateChildrenProposeMessage(Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap) {
        Map<Integer, Map<RegionIdentifier, Double>> aggregatePlan = new HashMap<>();
        
        for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedPlanEntry : proposedMap.entrySet()) {
            for (Entry<Integer, Map<RegionIdentifier, Double>> hopLoadMapEntry : proposedPlanEntry.getValue().entrySet()) {
                for (Entry<RegionIdentifier, Double> loadMapEntry : hopLoadMapEntry.getValue().entrySet()) {
                    updateKeyKeyLoadMap(aggregatePlan, hopLoadMapEntry.getKey(), loadMapEntry.getKey(), loadMapEntry.getValue(), true);
                }
            }
        }
        
        return aggregatePlan;
    }

    private Map<RegionIdentifier, ModularACdiffDcopMessage> readAcdiffMessges(int dcopRun) {
        Map<RegionIdentifier, ModularACdiffDcopMessage> messageMap = new HashMap<>();
                
        // Fill in the map with all neighbors
        getNeighborSet().forEach(neighbor -> messageMap.put(neighbor, new ModularACdiffDcopMessage()));

        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();
                                
        for (RegionIdentifier sender : getNeighborSet()) {                
            if (null != allSharedInformation.get(sender)) {
                DcopReceiverMessage abstractMsgMap = allSharedInformation.get(sender).getAsynchronousMessage();
                
                if (abstractMsgMap != null) {                    
                    if (abstractMsgMap.isSentTo(getRegionID()) && abstractMsgMap.getIteration() == dcopRun) {
                        
                        GeneralDcopMessage abstractMessage = abstractMsgMap.getMessageForThisReceiver(getRegionID());
                        
                        if (abstractMessage != null) {
                            ModularACdiffDcopMessage cdiffMessage = (ModularACdiffDcopMessage) abstractMessage;
                            messageMap.put(sender, cdiffMessage);
                        }
                    }
                }
            }
        }
        
        return messageMap;
    }

    private void createClientKeepLoadMap() {   
        getClientKeepLoadMap().clear();
        
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : rootKeepLoadMap.entrySet()) {
            for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceEntry : entry.getValue().entrySet()) {
                ServiceIdentifier<?> service = serviceEntry.getKey();
                
                for (Entry<RegionIdentifier, Double> clientEntry : serviceEntry.getValue().entrySet()) {
                    updateKeyKeyLoadMap(getClientKeepLoadMap(), clientEntry.getKey(), service, clientEntry.getValue(), true);
                }
            }
        }
    }
    
    private void updateFlowLoadMap(RegionIdentifier regionID, Map<ServiceIdentifier<?>, Double> demandMap, FlowType flowType) {
        for (Entry<ServiceIdentifier<?>, Double> entry : demandMap.entrySet()) {
            if (flowType.equals(FlowType.INCOMING)) {
                updateKeyKeyLoadMap(getFlowLoadMap(), regionID, entry.getKey(), entry.getValue(), true);
            } else if (flowType.equals(FlowType.OUTGOING)) {
                updateKeyKeyLoadMap(getFlowLoadMap(), regionID, entry.getKey(), -entry.getValue(), true);
            }
        }
    }
    
    private void updateFlowLoadMapWithRoot(RegionIdentifier regionID, Map<ServiceIdentifier<?>, Double> demandMap, FlowType flowType, RegionIdentifier root) {
        
        LOGGER.info("Demand map {}", demandMap);
        
        LOGGER.info("rootFlowLoadMap {}", rootFlowLoadMap);
        
        if (flowType.equals(FlowType.INCOMING)) {
            if (rootFlowLoadMap.containsKey(root) && rootFlowLoadMap.get(root).containsKey(regionID)) {
                rootFlowLoadMap.get(root).remove(regionID);
            }
        }
        
        for (Entry<ServiceIdentifier<?>, Double> entry : demandMap.entrySet()) {
            if (flowType.equals(FlowType.INCOMING)) {                
                updateKeyKeyKeyLoadMap(rootFlowLoadMap, root, regionID, entry.getKey(), entry.getValue(), true);
            } else if (flowType.equals(FlowType.OUTGOING)) {
                updateKeyKeyKeyLoadMap(rootFlowLoadMap, root, regionID, entry.getKey(), -entry.getValue(), false);
            }
        }        
        
//        for (Entry<ServiceIdentifier<?>, Double> entry : demandMap.entrySet()) {
//            if (flowType.equals(FlowType.INCOMING)) {
//                if (rootFlowLoadMap.containsKey(root) && rootFlowLoadMap.get(root).containsKey(regionID)) {
//                    rootFlowLoadMap.get(root).remove(regionID);
//                }
//                
//                updateKeyKeyKeyLoadMap(rootFlowLoadMap, root, regionID, entry.getKey(), entry.getValue(), false);
//            } else if (flowType.equals(FlowType.OUTGOING)) {
//                updateKeyKeyKeyLoadMap(rootFlowLoadMap, root, regionID, entry.getKey(), -entry.getValue(), false);
//            }
//        }
        
        LOGGER.info("DCOP Run {} Region {} has current root flow load map {}", currentDcopRun, getRegionID(), rootFlowLoadMap);
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
     * @param service
     * @param client
     * @param load
     * @return a map with single entry hop -> client -> load
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> createAskMsgSingleEntry(ServiceIdentifier<?> service, RegionIdentifier client, double load) {
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> proposePlan = new HashMap<>();
        final Map<RegionIdentifier, Double> clientPlan = new HashMap<>();

        clientPlan.put(client, load);
        proposePlan.put(service, clientPlan);
        
        return proposePlan;
    }

    /**
     * From aggregatedChildrenPlan: hop -> children -> load
     *  and excessLoad: service -> load
     * Create children -> service -> load
     * @param storedPlan 
     * @return
     */
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> createFinalPlan(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMap, Map<Integer, Map<RegionIdentifier, Double>> storedPlan, RegionIdentifier rootID) {
        final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = new HashMap<>();
        
        for (RegionIdentifier child : childrenMap.get(rootID)) {
            finalPlan.put(child, new HashMap<>());
        }
        
        final SortedSet<ServiceIdentifier<?>> priorityServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        priorityServiceSet.addAll(excessLoadMap.keySet());
        
        for (ServiceIdentifier<?> service : priorityServiceSet) {            
            
            for (Entry<Integer, Map<RegionIdentifier, Double>> hopEntry : storedPlan.entrySet()) {                
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
        finalPlan.entrySet().removeIf(v -> v.getKey().equals(getRegionID()));
        finalPlan.entrySet().removeIf(v -> v.getValue().isEmpty());
        
        return finalPlan;
    }

    /**
     * @return true if the region is a root of some tree
     */
    private boolean isRoot(RegionIdentifier rootID) {
        return getRegionID().equals(rootID);
    }

    /**
     * Create the aggregatedChildrenPlan
     * @return
     */
//    private Map<Integer, Double> aggregatePlanFromChildrenToSenToParent() {
//        Map<Integer, Double> proposedPlan = new HashMap<>();
//        
//        aggregateChildrenPlan();
//        
//        for (Entry<Integer, Map<RegionIdentifier, Double>> childrenPlanEntry : aggregatedChildrenPlan.entrySet()) {
//            proposedPlan.put(childrenPlanEntry.getKey(), sumValues(childrenPlanEntry.getValue()));
//        }
//        
//        // Add self-load to the plan and send it to the parent
//        double keepLoad = hasAvailableCapacity() ? getAvailableCapacity() : 0;
//        proposedPlan.put(messageFromParent.getHop() + 1, keepLoad);
//        
//        return proposedPlan;
//    }

    @SuppressWarnings("unused")
    private void processInitialDemandLoadMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap) {        
        updateFlowLoadMap(getRegionID(), convert(demandMap), FlowType.INCOMING);
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(demandMap, StoreType.STORE_DEMAND, getRegionID());
        
        selfExcessLoadMap.putAll(excessLoad);
        
        LOGGER.info("DCOP Run {} Region {} has demand keep load map: {}", currentDcopRun, getRegionID(), rootKeepLoadMap.get(getRegionID()));
        
        LOGGER.info("DCOP Run {} Region {} has excess load map: {}", currentDcopRun, getRegionID(), excessLoad);
        
        if (hasExcess(excessLoad)) {                                    
            rootCurrentTree = getRegionID(); // set self as the root
            
            childrenMap.put(getRegionID(), new HashSet<>(getNeighborSet()));
            
            for (RegionIdentifier neighbor : childrenMap.get(getRegionID())) {
                addMessage(neighbor, ModularACdiffMessageType.ASK, getRegionID(), 0, excessLoad);
            }
        } else {
            childrenMap.put(getRegionID(), Collections.emptySet());
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
    private <A> void addMessage(RegionIdentifier receiver, ModularACdiffMessageType messageType, RegionIdentifier rootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap) {        
        ModularACdiffLoadMap macdiffLoadMap = new ModularACdiffLoadMap(rootID, hop, loadMap);
        
        LOGGER.info("DCOP Run {} Region {} adds message to Region {} type {}: {}", currentDcopRun, getRegionID(), receiver, messageType, macdiffLoadMap);
        
        // Update the message if it's already been in the messageMap to send
        if (messageMapToSend.get(receiver).getMessageTypeMap().containsKey(messageType)) {
            // Extending the set if matching those message types
            if (ModularACdiffMessageType.DONE == messageType) {
                messageMapToSend.get(receiver).getMessageTypeMap().get(messageType).add(macdiffLoadMap);
            }
            // Update the plan if the same root
            // Add new if not same root
            else if (ModularACdiffMessageType.PLAN == messageType) {
                Set<ModularACdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);

                for (ModularACdiffLoadMap loadMapMsg : msgSet) {
                    // Update and exit
                    if (loadMapMsg.getRootID().equals(rootID)) {
                        loadMapMsg.setLoadMap(loadMap);
                        return ;
                    }
                }
                
                // There is no message in the set, so append the set
                msgSet.add(new ModularACdiffLoadMap(rootID, hop, loadMap));
            }
            // Replacing current one
            else {
                Set<ModularACdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);
                msgSet.clear();
                msgSet.add(macdiffLoadMap);
            }
        } 
        // Otherwise, create a new message for this type
        else {
            ModularACdiffLoadMap aCdiffLoadmap = new ModularACdiffLoadMap(rootID, hop, loadMap);
            Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> cdiffMsg = messageMapToSend.get(receiver).getMessageTypeMap(); 
            Set<ModularACdiffLoadMap> msgSet = cdiffMsg.getOrDefault(messageType, new HashSet<>());
            msgSet.add(aCdiffLoadmap);
            cdiffMsg.put(messageType, msgSet);
        }        
    }
    

    /**
     * Update keepLoadMap and return the excessLoadMap
     * @param loadMap is the load map that region needs to store
     * @return the excessLoadMap
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> computeExcessLoad(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadMap, StoreType storeType, RegionIdentifier root) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = deepCopyMap(loadMap);
        
        SortedSet<ServiceIdentifier<?>> sortedServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedServiceSet.addAll(excessLoad.keySet());
                
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> keepLoadMap = new HashMap<>();
        
        // Store the load to keepLoadMap AND rootKeepLoadMap.get(getRegionID())
        if (storeType == StoreType.STORE_DEMAND || storeType == StoreType.STORE_PLAN) {
            rootKeepLoadMap.put(getRegionID(), new HashMap<>());
            keepLoadMap = rootKeepLoadMap.get(getRegionID());
        } 
        else if (storeType == StoreType.NOT_STORING) {            
            if (rootKeepLoadMap.containsKey(root)) {
                // Creating a copy
                for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : rootKeepLoadMap.get(root).entrySet()) {
                    keepLoadMap.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }                
            }
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
                
//                double serviceLoad = excessLoad.get(service);
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
//        Map<ServiceIdentifier<?>, Double> keepLoadMap = getAsynchronousCdiffKeepLoadMap();
        
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
    private void sendAllMessages() {        
        DcopReceiverMessage cdiffMsgPerIteration = inbox.getAsynchronousMessage();
        
        for (Entry<RegionIdentifier, ModularACdiffDcopMessage> entry : messageMapToSend.entrySet()) {
            RegionIdentifier receiver = entry.getKey();
            ModularACdiffDcopMessage cdiffMessage = entry.getValue();
            cdiffMsgPerIteration.setMessageToTheReceiver(receiver, cdiffMessage);
        }
        inbox.setAsynchronousMessage(cdiffMsgPerIteration);
        
//        LOGGER.info("DCOP Run {} Region {} sends the current batch of messages", currentDcopRun, getRegionID());
        
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

    private void writeIterationInformation() {
        DcopReceiverMessage abstractMessage = inbox.getAsynchronousMessage();
        
        if (abstractMessage != null) {
            DcopReceiverMessage treeMsg = abstractMessage;
            
            LOGGER.info("DCOP Run {} Region {} before clearing {}", currentDcopRun, getRegionID(), treeMsg);
            
            treeMsg.getReceiverMessageMap().clear();
            
            LOGGER.info("DCOP Run {} Region {} after clearing {}", currentDcopRun, getRegionID(), treeMsg);
            
            treeMsg.setIteration(currentDcopRun);
            
            treeMsg.addMessageToReceiver(getRegionID(), new ModularACdiffDcopMessage());
            
            LOGGER.info("DCOP Run {} Region {} sets asynchronous msg {}", currentDcopRun, getRegionID(), treeMsg);
                        
            inbox.setAsynchronousMessage(treeMsg);
            
            LOGGER.info("DCOP Run {} Region {} write Dcop Run {}", currentDcopRun, getRegionID(), currentDcopRun);
        }
                
        LOGGER.info("DCOP Run {} Region {} write new Inbox {}", currentDcopRun, getRegionID(), inbox);
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }

    /** Initialize newIteration by taking the max iteration from the inbox and increment it.
     *  @return 0 (or more if more than second DCOP run)
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
                
        currentDcopRun = inbox.getAsynchronousMessage().getIteration() + 1;
        
        LOGGER.info("DCOP Run {} Region {} read inbox {}", currentDcopRun, getRegionID(), inbox);
                
        summary = getDcopInfoProvider().getRegionSummary(ResourceReport.EstimationWindow.LONG);
                
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        
        getNeighborSet().forEach(neighbor -> messageMapToSend.put(neighbor, new ModularACdiffDcopMessage()));
        getNeighborSet().forEach(neighbor -> storedMessages.put(neighbor, new ModularACdiffDcopMessage()));
        getNeighborSet().forEach(neighbor -> getFlowLoadMap().put(neighbor, new HashMap<>()));
        
        LOGGER.info("DCOP Run {} Region {} has Region Capacity {}", currentDcopRun, getRegionID(), getRegionCapacity());
    }
    
    /**
     * @return true if rootCurrentTree is not null
     */
    private boolean isEmptyTree() {
        return rootCurrentTree == null;
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
    
    private Map<Integer, Map<RegionIdentifier, Double>> convertProposeLoadMap(Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap, RegionIdentifier sender) {
        // Convert the object message to Integers
        // Ignore the regionID, assume it is sender (child)
        Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = new HashMap<>();
        for (Entry<Object, Map<RegionIdentifier, Double>> entry : receivedMsgLoadMap.entrySet()) {
            int hop = (Integer) entry.getKey();
            double totalLoad = entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
            
            updateKeyKeyLoadMap(proposedPlanFromChild, hop, sender, totalLoad, true);
        }
        
        return proposedPlanFromChild;
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
        return getRegionCapacity() - sumKeyKeyKeyValues(rootKeepLoadMap);
    }
}