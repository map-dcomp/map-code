package com.bbn.map.dcop.modular_rcdiff;

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
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.ServerClientService;
import com.bbn.map.dcop.modular_rcdiff.ModularRCdiffDcopMessage.ModularRCdiffMessageType;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#MODULAR_RCDIFF}
 *
 */
public class ModularRCdiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModularRCdiffAlgorithm.class);
    
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
         * Store the demand into another map
         */
        STORE_DEMAND,
        /**
         * Delete the old load of this root and store the updated plan
         */
        STORE_PLAN,
        /**
         * Not store the load
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
                    
    private AugmentedRoot currentAugmentedRoot = AugmentedRoot.getEmptyTree();
    
    private final Map<RegionIdentifier, ModularRCdiffDcopMessage> messageMapToSend = new HashMap<>();
    
    private final Map<RegionIdentifier, ModularRCdiffDcopMessage> storedMessages = new HashMap<>();
    
    private final Map<AugmentedRoot, Integer> hopMap = new HashMap<>();
    
    private final Map<AugmentedRoot, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>>> storedPlan = new HashMap<>();
    
    private final Map<AugmentedRoot, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> rootFlowLoadMap = new HashMap<>();
    
    /**
     * Root -> Service -> Client -> Load 
     */
    private final Map<AugmentedRoot, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> rootKeepLoadMap = new HashMap<>();
    
    private int currentDcopRun;
    
    private ResourceSummary summary;
    
    private DcopSharedInformation inbox;
    
    private int treeOrdering = 0;
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadFromParent = new HashMap<>();
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadToShed = new HashMap<>();

    /**
     * Root -> Set of children
     */
    private final Map<AugmentedRoot, Set<RegionIdentifier>> childrenMap = new HashMap<>();
    
    private Map<RegionIdentifier, Map<ServerClientService, Double>> childrenLoadMap = new HashMap<>();
            
    /**
     * Root -> Parent
     */
    private final Map<AugmentedRoot, RegionIdentifier> parentMap = new HashMap<>();
    
//    private final Map<AugmentedRoot, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> excessLoadMapForGivenRoot = new HashMap<>();
    
    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> selfDemandMap = new HashMap<>();
    
    /**
     * Root -> Child -> Number of hop -> Some region -> Load
     */
    private final Map<AugmentedRoot, Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>>> proposedChildrenMessageMap = new HashMap<>();

    private final Set<AugmentedRoot> storedDoneRootSet = new HashSet<>();
    
    /**
     *  Used for storing self demand
     */
    private final AugmentedRoot selfRoot = new AugmentedRoot(getRegionID());
    
    /**
     * If regions receive some ASK messages but cannot help, regions store them in this map so that they can help later (i.e done with current tree)
     * Root -> Sender -> Content
     */
    private final Map<AugmentedRoot, Map<RegionIdentifier, ModularRCdiffLoadMap>> storedAskMessages = new HashMap<>();
        
    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public ModularRCdiffAlgorithm(RegionIdentifier regionID,
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
        
        //TODO: check this line before committing the code
//        if (currentDcopRun != 1) {
        if (currentDcopRun == 0) {
            return defaultPlan(summary);
        }
        
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());
        
        Map<ServerClientService, Double> demandFromOtherRegion = new HashMap<>();
        
        // From the inferred server demand, create two maps:
        // selfDemandMap:           demands that are initiated by the self region
        // demandFromOtherRegion:   demands that are initiated by other regions
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> clientEntry : serviceEntry.getValue().entrySet()) {
                RegionIdentifier client = clientEntry.getKey();
                double load = clientEntry.getValue().get(MapUtils.COMPUTE_ATTRIBUTE);
                
                // This demand comes from self region
                if (client.equals(getRegionID())) {
                    updateKeyKeyLoadMap(selfDemandMap, serviceEntry.getKey(), client, sumValues(clientEntry.getValue()), true);
                } else {
                    demandFromOtherRegion.put(new ServerClientService(getRegionID(), client, service), load);
                }
                
                // update incoming load map
                updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), service, load, true);
            }
        }
        
        LOGGER.info("DCOP Run {} Region {} has network demand {}", currentDcopRun, getRegionID(), summary.getNetworkDemand());
        
        LOGGER.info("DCOP Run {} Region {} has server demand {}", currentDcopRun, getRegionID(), summary.getServerDemand());
        
        LOGGER.info("DCOP Run {} Region {} has inferred server demand {}", currentDcopRun, getRegionID(), inferredServerDemand);
        
        LOGGER.info("DCOP Run {} Region {} has self server demand {}", currentDcopRun, getRegionID(), selfDemandMap);
        
        LOGGER.info("DCOP Run {} Region {} has server demand from other regions {}", currentDcopRun, getRegionID(), demandFromOtherRegion);
                
//        processSelfDemand();
        executeGblock(selfDemandMap, getAvailableCapacity());
        
        // Find children and forward the demand
        for (Entry<ServerClientService, Double> entry : demandFromOtherRegion.entrySet()) {
            findChildrenAndSendMessage(entry.getKey(), entry.getValue(), summary, currentAugmentedRoot);
        }
        for (Entry<RegionIdentifier, Map<ServerClientService, Double>> entry : childrenLoadMap.entrySet()) {
            addMessage(entry.getKey(), ModularRCdiffMessageType.SERVER_TO_CLIENT, currentAugmentedRoot, 0, new HashMap<>(), entry.getValue());    
        }
        
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
            final Map<RegionIdentifier, ModularRCdiffDcopMessage> receivedMessageMap = readModularRCdiffMessges(currentDcopRun);
            
            // There is no new message, then sleep
            if (receivedMessageMap.equals(storedMessages)) {
                // Sleep for AP round duration to avoid continuously checking for messages
                try {
                    Thread.sleep(AgentConfiguration.getInstance().getApRoundDuration().toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Look for new messages and process them
            else {
                for (Entry<RegionIdentifier, ModularRCdiffDcopMessage> receivedMessageEntry : receivedMessageMap.entrySet()) {
                    RegionIdentifier sender = receivedMessageEntry.getKey();
                    ModularRCdiffDcopMessage newReceivedMessageGivenSender = receivedMessageEntry.getValue();
                    ModularRCdiffDcopMessage oldStoredMessageGivenSender = storedMessages.get(sender);

                    // Check if this sender sends a new message
                    if (!newReceivedMessageGivenSender.equals(oldStoredMessageGivenSender)) {
                        Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> storedMsgMap = oldStoredMessageGivenSender.getMessageTypeMap();

                        for (Entry<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> receivedEntry : newReceivedMessageGivenSender.getMessageTypeMap().entrySet()) {
                            
                            ModularRCdiffMessageType receivedMessageType = receivedEntry.getKey();
                            
                            for (ModularRCdiffLoadMap receivedMessage : receivedEntry.getValue()) {
                                // First time received this message type or received new message of this type
                                if (receiveNewMessage(storedMsgMap, receivedEntry.getKey(), receivedMessage)) {
                                                                        
                                    LOGGER.info("DCOP Run {} Region {} receives message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, receivedEntry.getKey(), receivedMessage);
                                                                        
                                    LOGGER.info("DCOP Run {} Region {} is in tree {} has tree ordering {}", currentDcopRun, getRegionID(), currentAugmentedRoot, treeOrdering);
                                    
//                                    processNewMessageWithUpdating(receivedEntry.getKey(), receivedMessage, sender);
                                    processServerToClientMessage(receivedMessageType, receivedMessage, sender);
                                    
                                    executeGblock(excessLoadFromParent, getAvailableCapacity(), receivedMessageType, receivedMessage, sender);
                                    
                                    executeCblock(receivedMessageType, receivedMessage, sender);
                                    
                                    executeClearBlock(receivedMessageType, receivedMessage, sender);
                                    
                                    // Store this message
                                    Set<ModularRCdiffLoadMap> msgSet = storedMsgMap.getOrDefault(receivedEntry.getKey(),new HashSet<>());
                                    msgSet.add(receivedMessage);
                                    storedMsgMap.put(receivedEntry.getKey(), msgSet);
                                }
                            } // End for loop processing new message
                        } // End for looking for new messages
                    } // Endif comparing new and stored messages
                } // Endfor traversing messages from every sender
            }
            
            createClientKeepLoadMap();
            LOGGER.info("Dcop Run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
            LOGGER.info("DCOP Run {} Region {} end the current cycle", currentDcopRun, getRegionID());

            if (LocalTime.now().isAfter(stopTime)) {
                break;
            }
        }              
        
        updateFlowLoadMap();
        createClientKeepLoadMap();
//        writeIterationInformation();
        
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has flowLoadMap {}", currentDcopRun, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
//        return compareDouble(MSG_DROP_RATE, 0) > 0 ? computeRegionDcopPlan(summary, currentDcopRun, true)
//                : computeRegionDcopPlan(summary, currentDcopRun, false);
        
        return computeRegionDcopPlan(summary, currentDcopRun, true);
    }
    
    private void processServerToClientMessage(ModularRCdiffMessageType msgType, ModularRCdiffLoadMap receivedMsg, RegionIdentifier sender) {        
        AugmentedRoot receivedMsgRoot = receivedMsg.getAugmentedRootID();

        if (ModularRCdiffMessageType.SERVER_TO_CLIENT.equals(msgType)) {
            LOGGER.info("DCOP Run {} Region {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, msgType, receivedMsg);

            Map<ServerClientService, Double> serverClientServiceMap = receivedMsg.getServerClientServiceMap();
            
            for (Entry<ServerClientService, Double> entry : serverClientServiceMap.entrySet()) {
                // This request comes from self region
                if (entry.getKey().getClient().equals(getRegionID())) {
                    updateKeyKeyLoadMap(selfDemandMap, entry.getKey().getService(), entry.getKey().getClient(), entry.getValue(), true);
                }
                else {
                    findChildrenAndSendMessage(entry.getKey(), entry.getValue(), summary, receivedMsgRoot);
                    for (Entry<RegionIdentifier, Map<ServerClientService, Double>> childEntry : childrenLoadMap.entrySet()) {
                        addMessage(childEntry.getKey(), ModularRCdiffMessageType.SERVER_TO_CLIENT, currentAugmentedRoot, 0, new HashMap<>(), childEntry.getValue());    
                    }
                }
                
                updateKeyKeyLoadMap(getFlowLoadMap(), sender, entry.getKey().getService(), entry.getValue(), true);
            }
            
            if (currentAugmentedRoot.isEmptyTree()) {
                processSelfDemand();
            }
        } 
    }
    
    /**
     * G-block of the design, output ASK messages
     * @param loadToShedMap
     * @param currentCapacity
     */
    private void executeGblock(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadToShedMap, double currentCapacity) {
        executeGblock(loadToShedMap, currentCapacity, null, null, null);
    }

    private void executeGblock(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadToShedMap, double currentCapacity,
            ModularRCdiffMessageType receivedMessageType, ModularRCdiffLoadMap receivedMsg, RegionIdentifier sender) {
        // Process self demand
        // Done reviewing
        if (null == receivedMsg) {
            processSelfDemand();
        }
        // Process ASK or PROPOSE messages and output ASK message only
        else {
            AugmentedRoot receivedMsgRoot = receivedMsg.getAugmentedRootID();
            
            Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMsg.getLoadMap();
            
            boolean isMessageFromParent = receivedMsgRoot.equals(currentAugmentedRoot) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);
            
            boolean isThisRootDone = storedDoneRootSet.contains(receivedMsgRoot);

            // Process ASK messages
            if (ModularRCdiffMessageType.ASK.equals(receivedMessageType) && !isThisRootDone) {
                // First time receiving ASK or receiving an updated ASK message from parent
                if (currentAugmentedRoot.isEmptyTree() || isMessageFromParent) {                
                    LOGGER.info("DCOP Run {} Region {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, receivedMessageType, receivedMsg);

                    currentAugmentedRoot = receivedMsgRoot;              
                                        
                    parentMap.put(receivedMsgRoot, sender);
                    
                    hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);
                    
                    Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);
                    
                    processAskMessage(receivedLoadMap, receivedMsgRoot, ModularRCdiffMessageType.ASK);
                }
                else {
                    // Do not store and process ASK message from this regionID
                    // This mean the region has received its own ASK message forwarded by other regions
                    if (!receivedMsgRoot.hasRegionID(getRegionID())) {
                        storedAskMessages.computeIfAbsent(receivedMsgRoot, k -> new HashMap<>()).put(sender, receivedMsg);
                        
                        LOGGER.info("DCOP Run {} Region {} stores Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), ModularRCdiffMessageType.ASK, sender, receivedMsg);
                    }
                }
            }
            // Process PROPOSE message
            else if (ModularRCdiffMessageType.PROPOSE.equals(receivedMessageType) && !isThisRootDone) {
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMsg);
                
                if (compareDouble(sumKeyKeyValues(receivedMsgLoadMap), 0D) == 0) {return ;}
                
                Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = convertProposeLoadMap(receivedMsgLoadMap, sender);
                
                processProposeMessage(proposedPlanFromChild, receivedMsgRoot, sender, ModularRCdiffMessageType.ASK);
            }
        }
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
    
    private void processProposeMessage(final Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild, AugmentedRoot receivedMsgRoot, RegionIdentifier sender, ModularRCdiffMessageType outputMsgType) {                       
        // If the output message is PROPOSE and is root, then output PLAN and ASK
        if (ModularRCdiffMessageType.PROPOSE == outputMsgType && isRoot(receivedMsgRoot)) {
            LOGGER.info("DCOP Run {} Region {} before computing plan has proposedChildrenMessageMap {}", currentDcopRun, getRegionID(), proposedChildrenMessageMap);
            
            Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> storedProposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
            
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
            
            // Store the new received proposed load map by overwriting the old one
            storedProposedMap.put(sender, proposedPlanFromChild);
            proposedChildrenMessageMap.put(receivedMsgRoot, storedProposedMap);
            
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(excessLoadToShed, differentProposeLoadMap, receivedMsgRoot);
                            
//            if (!storedPlan.containsKey(receivedMsgRoot)) {
//                storedPlan.put(receivedMsgRoot, new HashMap<>());
//            }
            
            storedPlan.computeIfAbsent(receivedMsgRoot, k -> new HashMap<>());
            
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> storedPlanGivenRoot = storedPlan.get(receivedMsgRoot);
            
            addUpPlans(storedPlanGivenRoot, finalPlan);
            
            LOGGER.info("DCOP Run {} Region {} after computing plan has proposedChildrenMessageMap {}", currentDcopRun, getRegionID(), proposedChildrenMessageMap);
            
            LOGGER.info("DCOP Run {} Region {} has temporary plan {}", currentDcopRun, getRegionID(), storedPlan);
                            
            LOGGER.info("DCOP Run {} Region {} has aggregated excessLoadMap {}", currentDcopRun, getRegionID(), excessLoadToShed);
                            
            if (hasExcess(excessLoadToShed)) {
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                    RegionIdentifier child = entry.getKey();
                    
                    addMessage(child, ModularRCdiffMessageType.PLAN, currentAugmentedRoot, 0, storedPlanGivenRoot.get(child), new HashMap<>());
                    updateFlowLoadMapWithRoot(child, convert(storedPlanGivenRoot.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                for (RegionIdentifier child : getNeighborSet()) {
                    addMessage(child, ModularRCdiffMessageType.ASK, currentAugmentedRoot, 0, excessLoadToShed, new HashMap<>());
                }
            } 
            // Done with current excess load
            else {
                // Add itself to the storeDoneRootSet
                storedDoneRootSet.add(currentAugmentedRoot);
                
                // Send DONE messages to all regions
                // Add the plan to the DONE messages
                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                    RegionIdentifier child = entry.getKey();
                    
                    addMessage(child, ModularRCdiffMessageType.DONE, currentAugmentedRoot, 0, storedPlanGivenRoot.get(child), new HashMap<>());
                    updateFlowLoadMapWithRoot(child, convert(storedPlanGivenRoot.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                Set<RegionIdentifier> remainingRegions = new HashSet<>(getNeighborSet());
                remainingRegions.removeAll(storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).keySet());
                for (RegionIdentifier regions : remainingRegions) {
                    addMessage(regions, ModularRCdiffMessageType.DONE, currentAugmentedRoot, 0, new HashMap<>(), new HashMap<>());
                }
                
                clearTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);
                // If has excess load map, start a new CDIFF with <regionID, treeNumber + 1>
                processSelfDemand();
                if (currentAugmentedRoot.isEmptyTree()) {
                    processStoreAskedMessages();
                }
            }
        }
        // Output PROPOSE message at non-root
        else if (ModularRCdiffMessageType.PROPOSE == outputMsgType) {
            // hop -> child -> load
            Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.get(receivedMsgRoot));
                                            
            // Propose the current plan
            addMessage(parentMap.get(receivedMsgRoot), ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap, new HashMap<>());
        }
        // Output updated ASK message at non-root
        else if (ModularRCdiffMessageType.ASK == outputMsgType && !isRoot(receivedMsgRoot)) {
            // Update proposedChildrenMessageMap with the proposed map of this child
            Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
            // Override the previous PROPOSE messages received from this sender
            proposedMap.put(sender, proposedPlanFromChild);
            proposedChildrenMessageMap.put(receivedMsgRoot, proposedMap);
            
            // Checking if the excess load from parent is larger or smaller than the load proposed by children
            double excessParentLoad = sumKeyKeyValues(excessLoadFromParent);
            
            // Compute the ASK message for each children accordingly
            // ASK_i = Excess - sum_{j!=i} PROPOSE_j
            for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                    
                double loadProposedFromOtherChildren = 0;
                
                for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> childrenLoadOtherEntry : proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                    if (childrenLoadOtherEntry.getKey().equals(child)) {continue;}
                    
                    loadProposedFromOtherChildren += sumKeyKeyValues(childrenLoadOtherEntry.getValue());
                }
                
                double loadToAsk = Math.max(excessParentLoad - loadProposedFromOtherChildren, 0D);
                                    
                if (compareDouble(loadToAsk, 0) > 0) {
                    // Just pick an arbitrary service
                    ServiceIdentifier<?> serviceToChild = excessLoadFromParent.keySet().iterator().next();
                    
                    Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createAskMsgSingleEntry(serviceToChild, getRegionID(), loadToAsk);
                    addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap, Collections.emptyMap());
                } else {
                    addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap(), Collections.emptyMap());
                }
            }
        }                       
    }
    
    /**
     * Process a received ASK messages, and output messages based on the msgType flag.
     * @param receivedLoadMap load map from the ASK message which is sent from the parent
     * @param receivedMsgRoot root of the current tree
     * @param outputMsgType process and output this message type only
     * DONE
     */
    private void processAskMessage(final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap, AugmentedRoot receivedMsgRoot, ModularRCdiffMessageType outputMsgType) {        
        RegionIdentifier parent = parentMap.get(receivedMsgRoot);
        
        boolean hasReceivedProposedMessage = !proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).isEmpty();
        
        // Not received any message from children
        if (!hasReceivedProposedMessage) {
            if (ModularRCdiffMessageType.ASK == outputMsgType) {                
                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);
                
                // Update the excess load map from parent
                excessLoadFromParent.clear();
                excessLoad.forEach((key, map) -> excessLoadFromParent.put((ServiceIdentifier<?>) key, new HashMap<>(map)));

                // Send the excess load to children if any to ask for help
                if (hasExcess(excessLoad)) {
                    childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
                    childrenMap.get(receivedMsgRoot).remove(parent);
                    
                    // Ask for help
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                        addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad, Collections.emptyMap());
                    }
                } 
                // Empty children set if there is no excess load 
                else {
                    childrenMap.put(receivedMsgRoot, new HashSet<>());
                }
            }
            else if (ModularRCdiffMessageType.PROPOSE == outputMsgType) {
                final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                        hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());
                addMessage(parent, ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan, Collections.emptyMap());
            }
        }
        // Has received some PROPOSE messages from children
        else {
            if (ModularRCdiffMessageType.ASK == outputMsgType) {
                // Update the excess load map from parent with the updated ASK message from parent
                // As self region has children, it means the self region doesn't have available capacity
                excessLoadFromParent.clear();
                receivedLoadMap.forEach((key, map) -> excessLoadFromParent.put((ServiceIdentifier<?>) key, new HashMap<>(map)));
                
                // Checking if the excess load from parent is larger or smaller than the load proposed by children
                double excessParentLoad = sumKeyKeyValues(excessLoadFromParent);
                
                double loadFromAllChildren = 0;
                
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
                                              
                        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createSingleEntryAskLoadMap(excessLoadFromParent.keySet().iterator().next(), getRegionID(), loadToAsk);
                        
                        addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap, Collections.emptyMap());
                    }
                } 
                // Send ASK message with 0 load since all children can help all (and has proposed all) 
                else {
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                            
                        addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap(), Collections.emptyMap());
                    }
                }
            }
            else if (ModularRCdiffMessageType.PROPOSE == outputMsgType) {
                // Propose aggregated plan to the parent
                Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
                
                addMessage(parent, ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap, new HashMap<>());
            }
        }
    }
    
    /**
     * Execute the C block from module design, output PROPOSE messages
     * @param sender 
     * @param receivedMessage 
     * @param receivedMessageType 
     */
    private void executeCblock(ModularRCdiffMessageType receivedMessageType, ModularRCdiffLoadMap receivedMsg, RegionIdentifier sender) {
        AugmentedRoot receivedMsgRoot = receivedMsg.getAugmentedRootID();
        
        Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMsg.getLoadMap();
        
        boolean isMessageFromParent = receivedMsgRoot.equals(currentAugmentedRoot) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);
        
        boolean isThisRootDone = storedDoneRootSet.contains(receivedMsgRoot);
        
        // Process ASK messages
        if (ModularRCdiffMessageType.ASK.equals(receivedMessageType) && !isThisRootDone) {
            // Receiving this ASK message when not in any tree or receiving an updated ASK message from parent
            if (currentAugmentedRoot.isEmptyTree() || isMessageFromParent) {                
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMsg);
                                
                currentAugmentedRoot = receivedMsgRoot;    
                
                parentMap.put(receivedMsgRoot, sender);
                
                hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);
                
                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);
                                               
                processAskMessage(receivedLoadMap, receivedMsgRoot, ModularRCdiffMessageType.PROPOSE);
            }
        }
        // Process PROPOSE messages
        else if (ModularRCdiffMessageType.PROPOSE.equals(receivedMessageType) && !isThisRootDone) {
            LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), receivedMessageType, sender, receivedMsg);

            if (compareDouble(sumKeyKeyValues(receivedMsgLoadMap), 0D) == 0) {return ;}
            
            // Convert the object message to Integers
            Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = convertProposeLoadMap(receivedMsgLoadMap, sender);
            
            processProposeMessage(proposedPlanFromChild, receivedMsgRoot, sender, ModularRCdiffMessageType.PROPOSE);
        }
    }
    
    /**
     * Execute the Clear block from module design
     * @param sender 
     * @param receivedMessageContent 
     * @param receivedMessageType 
     */
    private void executeClearBlock(ModularRCdiffMessageType msgType, ModularRCdiffLoadMap receivedMsg, RegionIdentifier sender) {
        AugmentedRoot receivedMsgRoot = receivedMsg.getAugmentedRootID();
                
        boolean isMessageFromParent = receivedMsgRoot.equals(currentAugmentedRoot) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);
        
        boolean isThisRootDone = storedDoneRootSet.contains(receivedMsgRoot);
        
        // Process PLAN and DONE message from parent of the current tree
        if ((ModularRCdiffMessageType.PLAN.equals(msgType) && !isThisRootDone && isMessageFromParent)
                || ModularRCdiffMessageType.DONE.equals(msgType) && isMessageFromParent) {
            LOGGER.info("DCOP Run {} Region {} in Root {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), currentAugmentedRoot, sender, msgType, receivedMsg.getLoadMap());

            RegionIdentifier parent = sender;
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planFromParent = convertObjectToServiceMap(receivedMsg.getLoadMap());
            
            Map<Integer, Map<RegionIdentifier, Double>> proposeChildrenLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
            
            // Update the incoming load from parent
            // Noe: this function probably affects the incoming/outgoing load of the Server-2-Client messages
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
                    addMessage(childEntry.getKey(), msgType, receivedMsgRoot, hopMap.get(receivedMsgRoot), childEntry.getValue(), new HashMap<>());
                    
                    updateFlowLoadMapWithRoot(childEntry.getKey(), convert(childEntry.getValue()), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                // Send DONE to the other neighbors
                if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> remainingAgents = new HashSet<>(getNeighborSet());
                    remainingAgents.removeAll(childrenPlan.keySet());
                    remainingAgents.remove(parent);
                    remainingAgents.remove(receivedMsgRoot.getRoot());
                    
                    for (RegionIdentifier remaining : remainingAgents) {
                        addMessage(remaining, ModularRCdiffMessageType.DONE, receivedMsgRoot, hopMap.get(receivedMsgRoot), new HashMap<>(), new HashMap<>());
                    }
                }
            } 
            // Has no children
            else {
                // Forward the DONE messages to neighboring agents except for the parent (sender) and the root
                if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
                    forwardingDoneAgents.remove(parent);
                    forwardingDoneAgents.remove(receivedMsgRoot.getRoot());
                    
                    for (RegionIdentifier agents : forwardingDoneAgents) {
                        addMessage(agents, ModularRCdiffMessageType.DONE, receivedMsgRoot, 0, new HashMap<>(), new HashMap<>());
                    }
                }
            } // end if checking for children
            
            if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                storedDoneRootSet.add(receivedMsgRoot);
                clearTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);
                // Only start a new CDIFF run if just getting out of this tree
                // Do nothing if receiving DONE messages from some other tree
                processSelfDemand();

                // Only process ASK messages if not in any 
                if (currentAugmentedRoot.isEmptyTree()) {
                    processStoreAskedMessages();
                }
            }
        }
        // First time receiving DONE message for this root
        else if (ModularRCdiffMessageType.DONE.equals(msgType) && !isThisRootDone) {
            storedDoneRootSet.add(receivedMsgRoot);
            clearTreeInformation(receivedMsgRoot, SetFree.NOT_SET_TO_FREE);
            
            Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
            forwardingDoneAgents.remove(sender);
            forwardingDoneAgents.remove(receivedMsgRoot.getRoot());
            // In this case, do not forward DONE messages
            // Reason: this parent might just forward DONE message to its children,
            // and the children will set to free as the above case
            forwardingDoneAgents.removeAll(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).keySet());
            
            for (RegionIdentifier agents : forwardingDoneAgents) {
                addMessage(agents, ModularRCdiffMessageType.DONE, receivedMsgRoot, 0, new HashMap<>(), new HashMap<>());
            }
        
        }        
    }

    /**
     * @precondition This region is not the client
     * @param serverClientServiceDemand
     * @param demandLoad
     * @param summaryInput
     */
    private void findChildrenAndSendMessage(ServerClientService serverClientServiceDemand, double demandLoad, ResourceSummary summaryInput, AugmentedRoot augmentedRoot) {
        RegionIdentifier demandServer = serverClientServiceDemand.getServer();
        RegionIdentifier demandClient = serverClientServiceDemand.getClient();
        ServiceIdentifier<?> demandService = serverClientServiceDemand.getService();
        
        for (Entry<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkNeighborEntry : summaryInput.getNetworkDemand().entrySet()) {
            RegionIdentifier networkNeighbor = networkNeighborEntry.getKey();
            
            // Skip self networkDemand
            if (networkNeighbor.equals(getRegionID())) {
                continue;
            }
            
            for (Entry<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> networkClientEntry : networkNeighborEntry.getValue().entrySet()) {
                RegionNetworkFlow flow = networkClientEntry.getKey();
                RegionIdentifier networkClient = getClient(networkClientEntry.getKey());
                ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> networkServiceMap = networkClientEntry.getValue();
                RegionIdentifier networkServer = getServer(flow);
                
                for (Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> networkServiceEntry : networkServiceMap.entrySet()) {
                    ServiceIdentifier<?> networkService = networkServiceEntry.getKey();
                    
                    if (!isIncomingFlowUsingFlow(flow)) {
                        continue;
                    }

                    // FOUND THE MATCH <service, client> in both serverDemand and networkDemand
                    // Send all load to this neighbor which is a children
                    // Update the incomingLoadMap, and outgoingLoadMap
                    if (demandClient.equals(networkClient) && demandService.equals(networkService) && demandServer.equals(networkServer)) {  
                        // If the flow is 0, then keep all the load
                        if (!isNonZeroFlow(networkServiceEntry.getValue())) {
                            updateKeyKeyLoadMap(getClientKeepLoadMap(), demandClient, demandService, demandLoad, true);
                            continue;
                        }
                        
                        ServerClientService serverClientService = new ServerClientService(serverClientServiceDemand);
                        updateKeyKeyLoadMap(childrenLoadMap, networkNeighbor, serverClientService, demandLoad, true);
                        updateKeyKeyLoadMap(getFlowLoadMap(), networkNeighbor, demandService, -demandLoad, true);
                    } // end of if matching source and service
                } // end for loop serviceNetworkMap
            } // end for loop neighborNetworkEntry 
        } // end for loop summaryInput
    }

    private void updateFlowLoadMap() {
        rootFlowLoadMap.forEach((root, neighborFlowMap) -> 
            neighborFlowMap.forEach((neighbor, regionFlow) -> 
                regionFlow.forEach((service, load) -> updateKeyKeyLoadMap(getFlowLoadMap(), neighbor, service, load, true))));
    }

    private boolean receiveNewMessage(Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> storedMsgMap, ModularRCdiffMessageType msgType, ModularRCdiffLoadMap receivedMessage) {
        return !storedMsgMap.containsKey(msgType) || !storedMsgMap.get(msgType).contains(receivedMessage);
    }
    
    @SuppressWarnings("unused")
    private void processNewMessageWithUpdating(ModularRCdiffMessageType msgType, ModularRCdiffLoadMap receivedMsg, RegionIdentifier sender) {
        AugmentedRoot receivedMsgRoot = receivedMsg.getAugmentedRootID();
                
        Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMsg.getLoadMap();
                        
        boolean isMessageFromParent = receivedMsgRoot.equals(currentAugmentedRoot) && parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);
        
        boolean isThisRootDone = storedDoneRootSet.contains(receivedMsgRoot);
        
        LOGGER.info("DCOP Run {} Region {} before processing message is in the tree {} has parentMap {} and has storedDoneRootSet {}", currentDcopRun, getRegionID(), currentAugmentedRoot, parentMap, storedDoneRootSet);
                
        if (ModularRCdiffMessageType.SERVER_TO_CLIENT.equals(msgType)) {
            LOGGER.info("DCOP Run {} Region {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, msgType, receivedMsg);

            Map<ServerClientService, Double> serverClientServiceMap = receivedMsg.getServerClientServiceMap();
            
            for (Entry<ServerClientService, Double> entry : serverClientServiceMap.entrySet()) {
                // This request comes from self region
                if (entry.getKey().getClient().equals(getRegionID())) {
                    updateKeyKeyLoadMap(selfDemandMap, entry.getKey().getService(), entry.getKey().getClient(), entry.getValue(), true);
                }
                else {
                    findChildrenAndSendMessage(entry.getKey(), entry.getValue(), summary, receivedMsgRoot);
                    for (Entry<RegionIdentifier, Map<ServerClientService, Double>> childEntry : childrenLoadMap.entrySet()) {
                        addMessage(childEntry.getKey(), ModularRCdiffMessageType.SERVER_TO_CLIENT, currentAugmentedRoot, 0, new HashMap<>(), childEntry.getValue());    
                    }
                }
                
                updateKeyKeyLoadMap(getFlowLoadMap(), sender, entry.getKey().getService(), entry.getValue(), true);
            }
            
            if (currentAugmentedRoot.isEmptyTree()) {
                processSelfDemand();
            }
        }
        else if (ModularRCdiffMessageType.ASK.equals(msgType) && !isThisRootDone) {
            // First time receiving ASK or receiving an updated ASK message from parent
            if (currentAugmentedRoot.isEmptyTree() || isMessageFromParent) {                
                LOGGER.info("DCOP Run {} Region {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, msgType, receivedMsg);

                currentAugmentedRoot = receivedMsgRoot;              
                
                RegionIdentifier parent = sender;
                
                parentMap.put(receivedMsgRoot, parent);
                
                hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);
                
                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);
                                                                                   
                excessLoadFromParent.clear();
                excessLoadFromParent.putAll(receivedLoadMap);
                
                // If there is no proposed load from children: keep (and propose) as much as possible and send the excess load to children 
                if (!proposedChildrenMessageMap.containsKey(receivedMsgRoot)) {
                    Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);
                                                            
                    final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                            hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());
                                                                            
                    // Propose the plan with current available capacity
                    addMessage(parent, ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan, new HashMap<>());
                    
                    // Send the excess load to children if any to ask for help
                    if (hasExcess(excessLoad)) {
                        childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
                        childrenMap.get(receivedMsgRoot).remove(parent);
                        
                        // Ask for help
                        for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                            addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad, new HashMap<>());
                        }
                    } 
                    // Empty children set if there is no excess load 
                    else {
                        childrenMap.put(receivedMsgRoot, new HashSet<>());
                    }
                }
                // If there is some propose plan from children
                else {
                    // hops -> child -> totalLoad
                    Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
                                        
                    // Propose the current plan
                    addMessage(parent, ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap, new HashMap<>());
                    
                    // Checking if the excess load from parent is larger or smaller than the load proposed by children
                    double excessParentLoad = sumKeyKeyValues(excessLoadFromParent);
                    
                    double loadFromAllChildren = 0;
                    
                    for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> childrenLoadOtherEntry : proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                        loadFromAllChildren += sumKeyKeyValues(childrenLoadOtherEntry.getValue());
                    }
                    
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
                                                  
                            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createSingleEntryAskLoadMap(excessLoadFromParent.keySet().iterator().next(), getRegionID(), loadToAsk);
                            
                            addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap, new HashMap<>());
                        }
                    } else {
                        for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                            
                            addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), new HashMap<>(), new HashMap<>());
                        }
                    }
                }
            }
            else {
                // Do not store and process ASK message with self root
                if (!receivedMsgRoot.hasRegionID(getRegionID())) {
                    storedAskMessages.computeIfAbsent(receivedMsgRoot, k -> new HashMap<>()).put(sender, receivedMsg);
                    LOGGER.info("DCOP Run {} Region {} stores Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), msgType, sender, receivedMsg);
                }
            }
        }
        else if (ModularRCdiffMessageType.PROPOSE.equals(msgType) && !isThisRootDone) {
            if (compareDouble(sumKeyKeyValues(receivedMsgLoadMap), 0) == 0) {return ;}
            
            LOGGER.info("DCOP Run {} Region {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, msgType, receivedMsg);
            
            // Convert the object message to Integers
            Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = new HashMap<>();
//            receivedMsgLoadMap.forEach((key, map) -> proposedPlanFromChild.put((Integer) key, new HashMap<>(map)));
            for (Entry<Object, Map<RegionIdentifier, Double>> entry : receivedMsgLoadMap.entrySet()) {
                int hop = (Integer) entry.getKey();
                double totalLoad = entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
                
                updateKeyKeyLoadMap(proposedPlanFromChild, hop, sender, totalLoad, true);
            }
                        
            if (isRoot(receivedMsgRoot)) {
                LOGGER.info("DCOP Run {} Region {} before computing plan has proposedChildrenMessageMap {}", currentDcopRun, getRegionID(), proposedChildrenMessageMap);
                
                Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> storedProposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
                
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
                
                // Store the new received proposed load map by overwriting the old one
                storedProposedMap.put(sender, proposedPlanFromChild);
                proposedChildrenMessageMap.put(receivedMsgRoot, storedProposedMap);
                
                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(excessLoadToShed, differentProposeLoadMap, receivedMsgRoot);
                                
                if (!storedPlan.containsKey(receivedMsgRoot)) {
                    storedPlan.put(receivedMsgRoot, new HashMap<>());
                }
                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> storedPlanGivenRoot = storedPlan.get(receivedMsgRoot);
                
                addUpPlans(storedPlanGivenRoot, finalPlan);
                
                LOGGER.info("DCOP Run {} Region {} after computing plan has proposedChildrenMessageMap {}", currentDcopRun, getRegionID(), proposedChildrenMessageMap);
                
                LOGGER.info("DCOP Run {} Region {} has temporary plan {}", currentDcopRun, getRegionID(), storedPlan);
                                
                LOGGER.info("DCOP Run {} Region {} has aggregated excessLoadMap {}", currentDcopRun, getRegionID(), excessLoadToShed);
                                
                if (hasExcess(excessLoadToShed)) {
                    for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                        RegionIdentifier child = entry.getKey();
                        
                        addMessage(child, ModularRCdiffMessageType.PLAN, currentAugmentedRoot, 0, storedPlanGivenRoot.get(child), new HashMap<>());
                        updateFlowLoadMapWithRoot(child, convert(storedPlanGivenRoot.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                    }
                    
                    for (RegionIdentifier child : getNeighborSet()) {
                        addMessage(child, ModularRCdiffMessageType.ASK, currentAugmentedRoot, 0, excessLoadToShed, new HashMap<>());
                    }
                } 
                // Done with current excess load
                else {
                    // Add itself to the storeDoneRootSet
                    storedDoneRootSet.add(currentAugmentedRoot);
                    
                    // Send DONE messages to all regions
                    // Add the plan to the DONE messages
                    for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                        RegionIdentifier child = entry.getKey();
                        
                        addMessage(child, ModularRCdiffMessageType.DONE, currentAugmentedRoot, 0, storedPlanGivenRoot.get(child), new HashMap<>());
                        updateFlowLoadMapWithRoot(child, convert(storedPlanGivenRoot.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                    }
                    
                    Set<RegionIdentifier> remainingRegions = new HashSet<>(getNeighborSet());
                    remainingRegions.removeAll(storedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()).keySet());
                    for (RegionIdentifier regions : remainingRegions) {
                        addMessage(regions, ModularRCdiffMessageType.DONE, currentAugmentedRoot, 0, new HashMap<>(), new HashMap<>());
                    }
                    
                    clearTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);
                    // If has excess load map, start a new CDIFF with <regionID, treeNumber + 1>
                    processSelfDemand();
                    if (currentAugmentedRoot.isEmptyTree()) {
                        processStoreAskedMessages();
                    }
                }
            }
            // If not root, combine with other PROPOSE messages and with the updated ASK message 
            // to create a PROPOSE message to send to the parent
            else { 
                // Update proposedChildrenMessageMap with the proposed map of this child
                Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
                // Override the previous PROPOSE messages received from this sender
                proposedMap.put(sender, proposedPlanFromChild);
                proposedChildrenMessageMap.put(receivedMsgRoot, proposedMap);
                
                // hop -> child -> load
                Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.get(receivedMsgRoot));
                                                
                // Propose the current plan
                addMessage(parentMap.get(receivedMsgRoot), ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap, new HashMap<>());
                
                double excessParentLoad = sumKeyKeyValues(excessLoadFromParent);
                
                // Compute the ASK message for each children accordingly
                // ASK_i = Excess - sum_{j!=i} PROPOSE_j
                for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {                    
                    double loadProposedFromOtherChildren = 0;
                    
                    for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> childrenLoadOtherEntry : proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).entrySet()) {
                        if (childrenLoadOtherEntry.getKey().equals(child)) {continue;}
                        
                        loadProposedFromOtherChildren += sumKeyKeyValues(childrenLoadOtherEntry.getValue());
                    }
                    
                    double loadToAsk = Math.max(excessParentLoad - loadProposedFromOtherChildren, 0D);
                                        
                    if (compareDouble(loadToAsk, 0) > 0) {
                        // Just pick an arbitrary service
                        ServiceIdentifier<?> serviceToChild = excessLoadFromParent.keySet().iterator().next();
                        
                        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createAskMsgSingleEntry(serviceToChild, getRegionID(), loadToAsk);
                        addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap, new HashMap<>());
                    } else {
                        addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), new HashMap<>(), new HashMap<>());
                    }
                }
            } // End if checking for root
        } // End if processing PROPOSE message
        // Process PLAN and DONE message from parent of the current tree
        else if ((ModularRCdiffMessageType.PLAN.equals(msgType) && !isThisRootDone && isMessageFromParent)
                || ModularRCdiffMessageType.DONE.equals(msgType) && isMessageFromParent) {
            LOGGER.info("DCOP Run {} Region {} in Root {} processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), currentAugmentedRoot, sender, msgType, receivedMsg.getLoadMap());

            RegionIdentifier parent = sender;
            
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planFromParent = convertObjectToServiceMap(receivedMsg.getLoadMap());
            
            Map<Integer, Map<RegionIdentifier, Double>> proposeChildrenLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));
            
            // Update the incoming load from parent
            // Note: this function probably affects the incoming/outgoing load of the Server-2-Client messages
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
                    addMessage(childEntry.getKey(), msgType, receivedMsgRoot, hopMap.get(receivedMsgRoot), childEntry.getValue(), new HashMap<>());
                    
                    updateFlowLoadMapWithRoot(childEntry.getKey(), convert(childEntry.getValue()), FlowType.OUTGOING, receivedMsgRoot);
                }
                
                // Send DONE to the other neighbors
                if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> remainingAgents = new HashSet<>(getNeighborSet());
                    remainingAgents.removeAll(childrenPlan.keySet());
                    remainingAgents.remove(parent);
                    remainingAgents.remove(receivedMsgRoot.getRoot());
                    
                    for (RegionIdentifier remaining : remainingAgents) {
                        addMessage(remaining, ModularRCdiffMessageType.DONE, receivedMsgRoot, hopMap.get(receivedMsgRoot), new HashMap<>(), new HashMap<>());
                    }
                }
            } 
            // Has no children
            else {
                // Forward the DONE messages to neighboring agents except for the parent (sender) and the root
                if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                    Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
                    forwardingDoneAgents.remove(parent);
                    forwardingDoneAgents.remove(receivedMsgRoot.getRoot());
                    
                    for (RegionIdentifier agents : forwardingDoneAgents) {
                        addMessage(agents, ModularRCdiffMessageType.DONE, receivedMsgRoot, 0, new HashMap<>(), new HashMap<>());
                    }
                }
            } // end if checking for children
            
            if (ModularRCdiffMessageType.DONE.equals(msgType)) {
                storedDoneRootSet.add(receivedMsgRoot);
                clearTreeInformation(receivedMsgRoot, SetFree.SET_TO_FREE);
                // Only start a new CDIFF run if just getting out of this tree
                // Do nothing if receiving DONE messages from some other tree
                processSelfDemand();

                // Only process ASK messages if not in any 
                if (currentAugmentedRoot.isEmptyTree()) {
                    processStoreAskedMessages();
                }
            }
        }
        // First time receiving DONE message for this root
        else if (ModularRCdiffMessageType.DONE.equals(msgType) && !isThisRootDone) {
            storedDoneRootSet.add(receivedMsgRoot);
            clearTreeInformation(receivedMsgRoot, SetFree.NOT_SET_TO_FREE);
            
            Set<RegionIdentifier> forwardingDoneAgents = new HashSet<>(getNeighborSet());
            forwardingDoneAgents.remove(sender);
            forwardingDoneAgents.remove(receivedMsgRoot.getRoot());
            
            for (RegionIdentifier agents : forwardingDoneAgents) {
                addMessage(agents, ModularRCdiffMessageType.DONE, receivedMsgRoot, 0, new HashMap<>(), new HashMap<>());
            }
        }
    }

    /**
     * Process only one ASK message at a time
     */
    private void processStoreAskedMessages() {
        if (storedAskMessages.isEmpty()) {return ;}
        
        AugmentedRoot receivedMsgRoot = storedAskMessages.entrySet().iterator().next().getKey();
        
        Map<RegionIdentifier, ModularRCdiffLoadMap> senderContentMap = storedAskMessages.get(receivedMsgRoot);

        RegionIdentifier sender = senderContentMap.entrySet().iterator().next().getKey();
        
        ModularRCdiffLoadMap receivedMsg = senderContentMap.get(sender);
        
        currentAugmentedRoot = receivedMsgRoot;              
        
        parentMap.put(receivedMsgRoot, sender);
        
        hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsg.getLoadMap());
        
        LOGGER.info("DCOP Run {} Region {} takes and processes Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), ModularRCdiffMessageType.ASK, sender, receivedMsg);
                                                           
        excessLoadFromParent.clear();
        excessLoadFromParent.putAll(receivedLoadMap);

        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);
        
        LOGGER.info("DCOP Run {} Region {} processes Message Type {} from Region {} with excess load: {}", currentDcopRun, getRegionID(), ModularRCdiffMessageType.ASK, sender, excessLoad);
        
        final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());
                                                                
        // Propose the plan with current available capacity
        addMessage(sender, ModularRCdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan, new HashMap<>());
        
        // Send the excess load to children if any to ask for help
        if (hasExcess(excessLoad)) {
            childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
            childrenMap.get(receivedMsgRoot).remove(sender);
            
            // Ask for help
            for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                addMessage(child, ModularRCdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad, new HashMap<>());
            }
        } 
        // Empty children set if there is no excess load 
        else {
            childrenMap.put(receivedMsgRoot, new HashSet<>());
        }
    }

    /**
     * Remove PROPOSE, ASK and PLAN messages for this augmentedRoot
     * @param receivedMsgRoot
     */
    private void removeMessage(AugmentedRoot receivedMsgRoot) {
        for (Entry<RegionIdentifier, GeneralDcopMessage> msgEntry : inbox.getAsynchronousMessage().getReceiverMessageMap().entrySet()) {
            if (msgEntry.getValue() instanceof ModularRCdiffDcopMessage) {
                ModularRCdiffDcopMessage rcdiffMsg = (ModularRCdiffDcopMessage) msgEntry.getValue();
                
                // Remove all PROPOSE message of this root
                Set<ModularRCdiffLoadMap> msgToRemove = new HashSet<>();
                
                Set<ModularRCdiffLoadMap> proposeMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularRCdiffMessageType.PROPOSE, Collections.emptySet());
                for (ModularRCdiffLoadMap msg : proposeMsgSet) {
                    if (msg.getAugmentedRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                proposeMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
                
                
                Set<ModularRCdiffLoadMap> askMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularRCdiffMessageType.ASK, Collections.emptySet());
                // Remove all the ASK message of this root
                for (ModularRCdiffLoadMap msg : askMsgSet) {
                    if (msg.getAugmentedRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                askMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
                
                Set<ModularRCdiffLoadMap> planMsgSet = rcdiffMsg.getMessageTypeMap().getOrDefault(ModularRCdiffMessageType.PLAN, Collections.emptySet());
                // Remove all the REFUSE message of this root
                for (ModularRCdiffLoadMap msg : askMsgSet) {
                    if (msg.getAugmentedRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                planMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
            }
        }        
    }

    /**
     * Clear current tree information and start processing self demand with ACDIFF.<br>
     * Also clear stored ASK message of this region
     * @param receivedMsgRoot
     * @param isSetToFree only set the current tree to non-tree when received DONE message from the parent
     */
    private void clearTreeInformation(AugmentedRoot receivedMsgRoot, SetFree isSetToFree) {
        removeMessage(receivedMsgRoot);
        storedAskMessages.remove(receivedMsgRoot);
        
        // If in this tree, then set to free 
        if (currentAugmentedRoot.equals(receivedMsgRoot) && isSetToFree == SetFree.SET_TO_FREE) {
            currentAugmentedRoot = AugmentedRoot.getEmptyTree();
            LOGGER.info("DCOP Run {} Region {} changes from tree {} to empty tree {}", currentDcopRun, getRegionID(), receivedMsgRoot, AugmentedRoot.getEmptyTree());
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

    /**
     * @param proposedMap: child -> hops -> dummy_agent -> load
     * @return
     */
    private Map<Integer, Map<RegionIdentifier, Double>> aggregateChildrenProposeMessage(Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap) {
        Map<Integer, Map<RegionIdentifier, Double>> aggregatePlan = new HashMap<>();
        
        for (Entry<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedPlanEntry : proposedMap.entrySet()) {
            RegionIdentifier child = proposedPlanEntry.getKey();
            
            for (Entry<Integer, Map<RegionIdentifier, Double>> hopLoadMapEntry : proposedPlanEntry.getValue().entrySet()) {
                int hop = hopLoadMapEntry.getKey();
                double totalLoad = hopLoadMapEntry.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
                
                updateKeyKeyLoadMap(aggregatePlan, hop, child, totalLoad, true);                
//                for (Entry<RegionIdentifier, Double> loadMapEntry : hopLoadMapEntry.getValue().entrySet()) {
//                    updateKeyKeyLoadMap(aggregatePlan, hopLoadMapEntry.getKey(), loadMapEntry.getKey(), loadMapEntry.getValue(), true);
//                }
            }
        }
        
        return aggregatePlan;
    }

    private Map<RegionIdentifier, ModularRCdiffDcopMessage> readModularRCdiffMessges(int dcopRun) {
        Map<RegionIdentifier, ModularRCdiffDcopMessage> messageMap = new HashMap<>();
                
        // Fill in the map with all neighbors
        getNeighborSet().forEach(neighbor -> messageMap.put(neighbor, new ModularRCdiffDcopMessage()));

        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();
                                        
        for (RegionIdentifier sender : getNeighborSet()) {                
            if (null != allSharedInformation.get(sender)) {
                DcopReceiverMessage abstractMsgMap = allSharedInformation.get(sender).getAsynchronousMessage();
                
                if (abstractMsgMap != null) {                    
                    if (abstractMsgMap.isSentTo(getRegionID()) && abstractMsgMap.getIteration() == dcopRun) {
                        
                        GeneralDcopMessage abstractMessage = abstractMsgMap.getMessageForThisReceiver(getRegionID());
                        
                        if (abstractMessage != null) {
                            ModularRCdiffDcopMessage modularRcdiffMessage = (ModularRCdiffDcopMessage) abstractMessage;
                            messageMap.put(sender, modularRcdiffMessage);
                        }
                    }
                }
            }
        }
        
        return messageMap;
    }

    private void createClientKeepLoadMap() {   
        getClientKeepLoadMap().clear();
        
        for (Entry<AugmentedRoot, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : rootKeepLoadMap.entrySet()) {
            for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceEntry : entry.getValue().entrySet()) {
                ServiceIdentifier<?> service = serviceEntry.getKey();
                
                for (Entry<RegionIdentifier, Double> clientEntry : serviceEntry.getValue().entrySet()) {
                    RegionIdentifier client = clientEntry.getKey();
                    double load = clientEntry.getValue();
                    
                    if (compareDouble(load, 0D) > 0) {
                        updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
                    }
                }
            }
        }
    }
    
    private void updateFlowLoadMapWithRoot(RegionIdentifier neighbor, Map<ServiceIdentifier<?>, Double> demandMap, FlowType flowType, AugmentedRoot augmentedRoot) {
        LOGGER.info("Demand map {}", demandMap);
        
        LOGGER.info("rootFlowLoadMap {}", rootFlowLoadMap);
        
        if (flowType.equals(FlowType.INCOMING)) {
            if (rootFlowLoadMap.containsKey(augmentedRoot) && rootFlowLoadMap.get(augmentedRoot).containsKey(neighbor)) {
                rootFlowLoadMap.get(augmentedRoot).remove(neighbor);
            }
        }
        
        for (Entry<ServiceIdentifier<?>, Double> entry : demandMap.entrySet()) {
            if (flowType.equals(FlowType.INCOMING)) {                
                updateKeyKeyKeyLoadMap(rootFlowLoadMap, augmentedRoot, neighbor, entry.getKey(), entry.getValue(), true);
            } else if (flowType.equals(FlowType.OUTGOING)) {
                updateKeyKeyKeyLoadMap(rootFlowLoadMap, augmentedRoot, neighbor, entry.getKey(), -entry.getValue(), false);
            }
        }        
        
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
     *              and excessLoad: service -> load
     * Create children -> service -> load
     * Update the excess load map
     * @param storedPlan 
     * @return
     */
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> createFinalPlan(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMap, Map<Integer, Map<RegionIdentifier, Double>> storedPlan, AugmentedRoot augmentedRootID) {
        final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = new HashMap<>();
        
        for (RegionIdentifier child : childrenMap.get(augmentedRootID)) {
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
        //Remove entry where key is self region or the plan is empty
        finalPlan.entrySet().removeIf(v -> v.getKey().equals(getRegionID()));
        finalPlan.entrySet().removeIf(v -> v.getValue().isEmpty());
        
        return finalPlan;
    }

    /**
     * @return true if the region is a root of some tree
     */
    private boolean isRoot(AugmentedRoot augmentedRootID) {
        return getRegionID().equals(augmentedRootID.getRoot()) && treeOrdering == augmentedRootID.getTreeNumber();
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

    private void processSelfDemand() {
        if (selfDemandMap.isEmpty() || !currentAugmentedRoot.isEmptyTree()) {return;}
        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(selfDemandMap, StoreType.STORE_DEMAND, selfRoot);
        
        excessLoadToShed.putAll(excessLoad);
        
        LOGGER.info("DCOP Run {} Region {} has demand keep load map: {}", currentDcopRun, getRegionID(), rootKeepLoadMap.get(selfRoot));
        
        LOGGER.info("DCOP Run {} Region {} has excess load map: {}", currentDcopRun, getRegionID(), excessLoad);
        
        if (hasExcess(excessLoad)) {    
            // Set to a new tree every time processing self-demand and self-demand is not empty
            currentAugmentedRoot = new AugmentedRoot(getRegionID(), ++treeOrdering);
            
            childrenMap.put(currentAugmentedRoot, new HashSet<>(getNeighborSet()));
            
            for (RegionIdentifier neighbor : childrenMap.get(currentAugmentedRoot)) {
                addMessage(neighbor, ModularRCdiffMessageType.ASK, currentAugmentedRoot, 0, excessLoad, new HashMap<>());
            }
            
        } else {
            childrenMap.put(currentAugmentedRoot, Collections.emptySet());
        }
        
        selfDemandMap.clear();
    }
    
    /**
     * Add messages in order to send later
     * @param <A>
     * @param receiver
     * @param messageType
     * @param augmentedRootID
     * @param hop
     * @param loadMap
     * @param serverClientServiceMap
     */
    private <A> void addMessage(RegionIdentifier receiver, ModularRCdiffMessageType messageType, AugmentedRoot augmentedRootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap, Map<ServerClientService, Double> serverClientServiceMap) {        
        ModularRCdiffLoadMap modularRcdiffLoadMap = new ModularRCdiffLoadMap(augmentedRootID, hop, loadMap, serverClientServiceMap);
        
        LOGGER.info("DCOP Run {} Region {} sends message to Region {} type {}: {}", currentDcopRun, getRegionID(), receiver, messageType, modularRcdiffLoadMap);
        
        // Update the message if it's already been in the messageMap to send
        if (messageMapToSend.get(receiver).getMessageTypeMap().containsKey(messageType)) {
            
            // Extending the set if matching those message types
            if (ModularRCdiffMessageType.DONE == messageType || ModularRCdiffMessageType.SERVER_TO_CLIENT == messageType) {
                messageMapToSend.get(receiver).getMessageTypeMap().get(messageType).add(modularRcdiffLoadMap);
            }
            // Update the plan if the same root
            // Add new if not same root
            else if (ModularRCdiffMessageType.PLAN == messageType) {
                Set<ModularRCdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);

                for (ModularRCdiffLoadMap loadMapMsg : msgSet) {
                    // Update and exit
                    if (loadMapMsg.getAugmentedRootID().equals(augmentedRootID)) {
                        loadMapMsg.setLoadMap(loadMap);
                        return ;
                    }
                }
                
                // There is no message in the set, so append the set
                msgSet.add(new ModularRCdiffLoadMap(augmentedRootID, hop, loadMap, new HashMap<>()));
            }
            // Replacing current one
            else {
                Set<ModularRCdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);
                msgSet.clear();
                msgSet.add(modularRcdiffLoadMap);
            }
        } 
        // Otherwise, create a new message for this type
        else {
//            RCdiffLoadMap aCdiffLoadmap = new RCdiffLoadMap(augmentedRootID, hop, loadMap, new HashMap<>());
            Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> modularRcdiffMsg = messageMapToSend.get(receiver).getMessageTypeMap(); 
//            Set<RCdiffLoadMap> msgSet = cdiffMsg.getOrDefault(messageType, new HashSet<>());
//            msgSet.add(modularRcdiffLoadMap);            
//            cdiffMsg.put(messageType, msgSet);
            modularRcdiffMsg.computeIfAbsent(messageType, k -> new HashSet<>()).add(modularRcdiffLoadMap);
        }        
    }
    

    /**
     * Update keepLoadMap and return the excessLoadMap
     * @param loadMap is the load map that region needs to store
     * @return the excessLoadMap
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> computeExcessLoad(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadMap, StoreType storeType, AugmentedRoot augmentedRoot) {        
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = deepCopyMap(loadMap);

        SortedSet<ServiceIdentifier<?>> sortedServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedServiceSet.addAll(excessLoad.keySet());
                
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> keepLoadMap = new HashMap<>();
                
        if (storeType == StoreType.STORE_DEMAND) {
            keepLoadMap = rootKeepLoadMap.computeIfAbsent(augmentedRoot, k -> new HashMap<>());                    
        }
        else if (storeType == StoreType.STORE_PLAN) {
            // Clear old load of this root
            rootKeepLoadMap.put(augmentedRoot, new HashMap<>());
            // Store the new plan directly to this
            keepLoadMap = rootKeepLoadMap.get(augmentedRoot);
            
        }
        else if (storeType == StoreType.NOT_STORING) {
            for (Entry<AugmentedRoot, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> outerEntry : rootKeepLoadMap.entrySet()) {
                for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> innerEntry : outerEntry.getValue().entrySet()) {
                    ServiceIdentifier<?> service = innerEntry.getKey();
                    
                    for (Entry<RegionIdentifier, Double> entry : innerEntry.getValue().entrySet()) {
                        RegionIdentifier client = entry.getKey();
                        double load = entry.getValue();
                        
                        updateKeyKeyLoadMap(keepLoadMap, service, client, load, true);
                    }
                }
            }            
        }
                
        for (ServiceIdentifier<?> service : sortedServiceSet) {
            for (Entry<RegionIdentifier, Double> entry : excessLoad.get(service).entrySet()) {
                RegionIdentifier client = entry.getKey();

//                double availableCapacity = getRegionCapacity() - sumKeyKeyKeyValues(rootKeepLoadMap);
                double availableCapacity = storeType == StoreType.NOT_STORING ? getRegionCapacity() - sumKeyKeyValues(keepLoadMap) : getRegionCapacity() - sumKeyKeyKeyValues(rootKeepLoadMap);
                
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
//                    excessLoad.get(service).put(client, 0.0);
                    entry.setValue(0.0);
                } 
                // If availableCapacity <= serviceLoad
                // Store availableCapacity, reduce the loadMap by availableCapacity
                // Then break since there is no available capacity
                else {
                    updateKeyKeyLoadMap(keepLoadMap, service, client, availableCapacity, true);
//                    excessLoad.get(service).put(client, serviceLoad - availableCapacity);
                    entry.setValue(serviceLoad - availableCapacity);
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
//    @SuppressWarnings("unused")
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
        DcopReceiverMessage modularRcdiffMsgPerIteration = inbox.getAsynchronousMessage();
                
        for (Entry<RegionIdentifier, ModularRCdiffDcopMessage> entry : messageMapToSend.entrySet()) {
            RegionIdentifier receiver = entry.getKey();
            ModularRCdiffDcopMessage modularRcdiffMessage = entry.getValue();
            modularRcdiffMsgPerIteration.setMessageToTheReceiver(receiver, modularRcdiffMessage);
        }
        inbox.setAsynchronousMessage(modularRcdiffMsgPerIteration);
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
        
    }
    
//    /**
//     * From inferredServerDemand, create the demand map service -> load
//     * @param inferredServerDemand
//     * @return
//     */
//    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> aggregateDemandMap(
//            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand) {
//        
//        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = new HashMap<>();
//        
//        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand.entrySet()) {
//            ServiceIdentifier<?> service = serviceEntry.getKey();
//            
//            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionEntry : serviceEntry.getValue().entrySet()) {
//                updateKeyKeyLoadMap(demandMap, service, regionEntry.getKey(), sumValues(regionEntry.getValue()), true);
//            }
//        }
//        
//        return demandMap;
//    }

    private void writeIterationInformation() {
        DcopReceiverMessage abstractMessage = inbox.getAsynchronousMessage();
        
        if (abstractMessage != null) {
            DcopReceiverMessage treeMsg = abstractMessage;
            
            LOGGER.info("DCOP Run {} Region {} before clearing {}", currentDcopRun, getRegionID(), treeMsg);
            
            treeMsg.getReceiverMessageMap().clear();
            
            LOGGER.info("DCOP Run {} Region {} after clearing {}", currentDcopRun, getRegionID(), treeMsg);
            
            treeMsg.setIteration(currentDcopRun);
            
            treeMsg.addMessageToReceiver(getRegionID(), new ModularRCdiffDcopMessage());
            
            LOGGER.info("DCOP Run {} Region {} sets asynchronous msg {}", currentDcopRun, getRegionID(), treeMsg);
                        
            inbox.setAsynchronousMessage(treeMsg);
            
            LOGGER.info("DCOP Run {} Region {} write Dcop Run {}", currentDcopRun, getRegionID(), currentDcopRun);
        }
                
        LOGGER.info("DCOP Run {} Region {} write new Inbox {}", currentDcopRun, getRegionID(), inbox);
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }

    /** Initialize newIteration by reading the inbox
     *  @return the run count or the first iteration count of the current DCOP run 
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
                
        currentDcopRun = inbox.getAsynchronousMessage().getIteration() + 1;
        
        LOGGER.info("DCOP Run {} Region {} read inbox {}", currentDcopRun, getRegionID(), inbox);
                
        summary = getDcopInfoProvider().getRegionSummary(ResourceReport.EstimationWindow.LONG);
                
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        
        getNeighborSet().forEach(neighbor -> messageMapToSend.put(neighbor, new ModularRCdiffDcopMessage()));
        getNeighborSet().forEach(neighbor -> storedMessages.put(neighbor, new ModularRCdiffDcopMessage()));
        getNeighborSet().forEach(neighbor -> getFlowLoadMap().put(neighbor, new HashMap<>()));
        
        LOGGER.info("DCOP Run {} Region {} has Region Capacity {}", currentDcopRun, getRegionID(), getRegionCapacity());
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