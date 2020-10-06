package com.bbn.map.dcop.acdiff;

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
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.acdiff.ACdiffDcopMessage.ACdiffMessageType;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#ASYNCHRONOUS_CDIFF}
 *
 */
public class ACdiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(ACdiffAlgorithm.class);

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

    private static final boolean READING_MESSAGES = true;

    private static final boolean IS_UPDATING = true;

    private boolean isBelongToATree = false;

    private final Map<RegionIdentifier, ACdiffDcopMessage> messageMapToSend = new HashMap<>();

    private final Map<RegionIdentifier, ACdiffDcopMessage> storedMessages = new HashMap<>();

    private final Map<RegionIdentifier, Integer> hopMap = new HashMap<>();

    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> storedPlan = new HashMap<>();

    private final Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> rootFlowLoadMap = new HashMap<>();

    /**
     * Root -> Service -> Client -> Load
     */
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> rootKeepLoadMap = new HashMap<>();

    private int currentDcopRun;

    private ResourceSummary summary;

    private DcopSharedInformation inbox;

    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoadMap = new HashMap<>();

    /**
     * Root -> Integer -> Some region -> Load
     */
    private final Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> storedProposedPlan = new HashMap<>();

    /**
     * Root -> Set of children
     */
    private final Map<RegionIdentifier, Set<RegionIdentifier>> childrenMap = new HashMap<>();

    /**
     * Root -> Parent
     */
    private final Map<RegionIdentifier, RegionIdentifier> parentMap = new HashMap<>();

    private final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessParentLoadMap = new HashMap<>();

    /**
     * Root -> Child -> Number of hop -> Some region -> Load
     */
    private final Map<RegionIdentifier, Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>>> proposedChildrenMessageMap = new HashMap<>();

    private final Set<RegionIdentifier> storedDoneRootSet = new HashSet<>();

    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public ACdiffAlgorithm(RegionIdentifier regionID,
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

        if (currentDcopRun == 0) {
            return defaultPlan(summary);
        }

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());

        // Service - Client -> Double
//        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = aggregateDemandMap(inferredServerDemand);
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = aggregateDemandMap(inferredServerDemand);

        LOGGER.info("DCOP Run {} Region {} has network demand {}", currentDcopRun, getRegionID(), summary.getNetworkDemand());

        LOGGER.info("DCOP Run {} Region {} has server demand {}", currentDcopRun, getRegionID(), summary.getServerDemand());

        LOGGER.info("DCOP Run {} Region {} has inferred server demand {}", currentDcopRun, getRegionID(), inferredServerDemand);

        LOGGER.info("DCOP Run {} Region {} has demand {}", currentDcopRun, getRegionID(), demandMap);

        processInitialDemandLoadMap(demandMap);

        final LocalTime stopTime = LocalTime.now().plus(AgentConfiguration.getInstance().getDcopAcdiffTimeOut());

        while (READING_MESSAGES) {

            boolean toPrint = false;

            if (AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDrops()) {
                if (compareDouble(RANDOM.nextDouble(),
                        1 - AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDropRate()) < 0) {
                    sendAllMessages();
                }
            } else {
                sendAllMessages();
            }

            // waiting for messages from neighbors
            final Map<RegionIdentifier, ACdiffDcopMessage> receivedMessageMap = readAcdiffMessges(currentDcopRun);

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
                for (Entry<RegionIdentifier, ACdiffDcopMessage> receivedMessageEntry : receivedMessageMap.entrySet()) {
                    RegionIdentifier sender = receivedMessageEntry.getKey();
                    ACdiffDcopMessage newReceivedMessageGivenSender = receivedMessageEntry.getValue();
                    ACdiffDcopMessage oldStoredMessageGivenSender = storedMessages.get(sender);

                    // Check if this sender sends a new message
                    if (!newReceivedMessageGivenSender.equals(oldStoredMessageGivenSender)) {
                        Map<ACdiffMessageType, Set<ACdiffLoadMap>> storedMsgMap = oldStoredMessageGivenSender.getMessageTypeMap();

                        for (Entry<ACdiffMessageType, Set<ACdiffLoadMap>> receivedEntry : newReceivedMessageGivenSender.getMessageTypeMap().entrySet()) {
                            for (ACdiffLoadMap receivedMessage : receivedEntry.getValue()) {
                                // First time received this message type or received new message of this type
                                if (receiveNewMessage(storedMsgMap, receivedEntry.getKey(), receivedMessage)) {

                                    LOGGER.info("DCOP Run {} Region {} receives and processes message from Region {} with Type {}: {}", currentDcopRun, getRegionID(), sender, receivedEntry.getKey(), receivedMessage);

                                    toPrint = true;

                                    if (IS_UPDATING) {
                                        processNewMessageWithUpdating(receivedEntry.getKey(), receivedMessage, sender);
                                    } else {
                                        processNewMessageWithoutUpdating(receivedEntry.getKey(), receivedMessage, sender);
                                    }

                                    // Store this message
                                    Set<ACdiffLoadMap> msgSet = storedMsgMap.getOrDefault(receivedEntry.getKey(),new HashSet<>());
                                    msgSet.add(receivedMessage);
                                    storedMsgMap.put(receivedEntry.getKey(), msgSet);
                                }
                            } // End for loop processing new message
                        } // End for looking for new messages
                    } // Endif comparing new and stored messages
                } // Endfor traversing messages from every sender
            }

            if (toPrint) {
                LOGGER.info("DCOP Run {} Region {} end the current cycle", currentDcopRun, getRegionID());
            }

            if (LocalTime.now().isAfter(stopTime)) {
                break;
            }
        }

        if (IS_UPDATING) {
            createFlowLoadMap();
        }

        createClientKeepLoadMap();
//        writeIterationInformation();

        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has flowLoadMap {}", currentDcopRun, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());

        return computeRegionDcopPlan(summary, currentDcopRun, true);
    }

    private void createFlowLoadMap() {
        rootFlowLoadMap.forEach((root, neighborFlowMap) ->
            neighborFlowMap.forEach((neighbor, regionFlow) ->
                regionFlow.forEach((service, load) -> updateKeyKeyLoadMap(getFlowLoadMap(), neighbor, service, load, true))));
    }

    private boolean receiveNewMessage(Map<ACdiffMessageType, Set<ACdiffLoadMap>> storedMsgMap, ACdiffMessageType msgType, ACdiffLoadMap receivedMessage) {
        return !storedMsgMap.containsKey(msgType) || !storedMsgMap.get(msgType).contains(receivedMessage);
    }

    private void processNewMessageWithoutUpdating(ACdiffMessageType msgType, ACdiffLoadMap receivedMsg, RegionIdentifier sender) {
        RegionIdentifier receivedMsgRoot = receivedMsg.getRootID();

        int receivedMsgHop = receivedMsg.getHop();

        Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMsg.getLoadMap();

        boolean receivedMsgFromParent = parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);

        if (ACdiffMessageType.ASK.equals(msgType)) {
            if (!isBelongToATree || receivedMsgFromParent) {
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), msgType, sender, receivedMsg);

                isBelongToATree = true;

                RegionIdentifier parent = sender;

                parentMap.put(receivedMsgRoot, parent);

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> servedLoadMap = planDiff(receivedLoadMap, excessLoad);

                final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                        receivedMsgHop + 1, getRegionID(), sumKeyKeyValues(servedLoadMap));

                Map<Integer, Map<RegionIdentifier, Double>> storeProposedPlanGivenRoot = storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>());
                proposePlan.forEach((outerKey, map) -> map.forEach((innerKey, value) -> updateKeyKeyLoadMap(storeProposedPlanGivenRoot, outerKey, innerKey, value, true)));
                storedProposedPlan.put(receivedMsgRoot, storeProposedPlanGivenRoot);

                // Propose the current plan
                addMessage(parent, ACdiffMessageType.PROPOSE, receivedMsgRoot, receivedMsgHop + 1, storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()));

                // Send the excess load to children if any to ask for help
                if (hasExcess(excessLoad)) {
                    childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
                    childrenMap.get(receivedMsgRoot).remove(parent);

                    // Ask for help
                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                        getFlowLoadMap().get(child).clear();
                        addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, receivedMsgHop + 1, excessLoad);
                    }
                } else {
                    childrenMap.put(receivedMsgRoot, Collections.emptySet());
                }
            }
        }
        else if (ACdiffMessageType.PROPOSE.equals(msgType)) {
            if (isRoot(receivedMsgRoot)) {

                Map<Integer, Map<RegionIdentifier, Double>> storeProposedPlanGivenRoot = storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>());
                receivedMsg.getLoadMap().forEach((outerKey, map) -> map.forEach((innerKey, value) -> updateKeyKeyLoadMap(storeProposedPlanGivenRoot, (Integer) outerKey, innerKey, value, true)));
                storedProposedPlan.put(receivedMsgRoot, storeProposedPlanGivenRoot);


                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(excessLoadMap, storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()), receivedMsgRoot);

                for (RegionIdentifier child : childrenMap.get(getRegionID())) {
                    addMessage(child, ACdiffMessageType.PLAN, getRegionID(), 0, finalPlan.get(child));
                    getFlowLoadMap().get(child).clear();
                    updateFlowLoadMap(child, convert(finalPlan.get(child)), FlowType.OUTGOING);
                }
            }
            // If not root, combine with other PROPOSE messages and with the updated ASK message
            // to create a PROPOSE message to send to the parent
            else {

                Map<Integer, Map<RegionIdentifier, Double>> storeProposedPlanGivenRoot = storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>());
                receivedMsg.getLoadMap().forEach((outerKey, map) -> map.forEach((innerKey, value) -> updateKeyKeyLoadMap(storeProposedPlanGivenRoot, (Integer) outerKey, innerKey, value, true)));
                storedProposedPlan.put(receivedMsgRoot, storeProposedPlanGivenRoot);

                addMessage(parentMap.get(receivedMsgRoot), ACdiffMessageType.PROPOSE, receivedMsgRoot, receivedMsgHop + 1, storedProposedPlan.getOrDefault(receivedMsgRoot, new HashMap<>()));
            }
        }
        // Receive the most up-to-date plan
        // Clear the committed load and commit the load from the PLAN
        // Then send the PLAN to the corresponding children
        else if (ACdiffMessageType.PLAN.equals(msgType)) {
            // If if this sender is the parent given the root
            if (receivedMsgFromParent) {
                RegionIdentifier parent = sender;

                final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> finalPlan = convertObjectToServiceMap(receivedMsg.getLoadMap());

                // Clear the current flow load map
                getFlowLoadMap().get(parent).clear();
                updateFlowLoadMap(parent, convert(finalPlan), FlowType.INCOMING);

                final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planToChildren = computeExcessLoad(finalPlan, StoreType.STORE_PLAN, receivedMsgRoot);

                if (!childrenMap.get(receivedMsgRoot).isEmpty()) {
                    Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childrenPlan = createFinalPlan(planToChildren, storedProposedPlan.get(receivedMsgRoot), receivedMsgRoot);

                    for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                        addMessage(child, ACdiffMessageType.PLAN, receivedMsgRoot, receivedMsgHop + 1, childrenPlan.get(child));

                        getFlowLoadMap().get(child).clear();
//                        updateFlowLoadMap(child, convert(childrenPlan.get(child)), FlowType.OUTGOING);
                        updateFlowLoadMapWithRoot(child, convert(childrenPlan.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                    }
                }
            }
        }
        // Remove all the PROPOSE messages for this rootID
        // Forward the DONE messages to the corresponding children
        else if (ACdiffMessageType.DONE.equals(msgType)) {
            for (Entry<RegionIdentifier, GeneralDcopMessage> msgEntry : inbox.getAsynchronousMessage().getReceiverMessageMap().entrySet()) {
                if (msgEntry.getValue() instanceof ACdiffDcopMessage) {
                    ACdiffDcopMessage acdiffMsg = (ACdiffDcopMessage) msgEntry.getValue();

                    // Remove all PROPOSE message of this root
                    Set<ACdiffLoadMap> msgToRemove = new HashSet<>();
                    for (ACdiffLoadMap msg : acdiffMsg.getMessageTypeMap().get(ACdiffMessageType.PROPOSE)) {
                        if (msg.getRootID().equals(receivedMsgRoot)) {
                            msgToRemove.add(msg);
                        }
                    }
                    acdiffMsg.getMessageTypeMap().get(ACdiffMessageType.PROPOSE).removeAll(msgToRemove);

                    // Remove all the ASK message of this root
                    for (ACdiffLoadMap msg : acdiffMsg.getMessageTypeMap().get(ACdiffMessageType.ASK)) {
                        if (msg.getRootID().equals(receivedMsgRoot)) {
                            msgToRemove.add(msg);
                        }
                    }
                    acdiffMsg.getMessageTypeMap().get(ACdiffMessageType.ASK).removeAll(msgToRemove);

                    // Modify the parent and childrenMap
                    parentMap.remove(receivedMsgRoot);
                    childrenMap.remove(receivedMsgRoot);
                    storedProposedPlan.clear();
                }
            }
        }
    }

    private void processNewMessageWithUpdating(ACdiffMessageType msgType, ACdiffLoadMap receivedMsg, RegionIdentifier sender) {
        RegionIdentifier receivedMsgRoot = receivedMsg.getRootID();

        Map<Object, Map<RegionIdentifier, Double>> receivedMsgLoadMap = receivedMsg.getLoadMap();

        boolean processMessageFromParent = parentMap.containsKey(receivedMsgRoot) && parentMap.get(receivedMsgRoot).equals(sender);

        boolean rootDone = storedDoneRootSet.contains(receivedMsgRoot);

        if (ACdiffMessageType.ASK.equals(msgType) && !rootDone) {
            // First time receiving ASK or receiving an updated ASK message from parent
            if (!isBelongToATree || processMessageFromParent) {
                LOGGER.info("DCOP Run {} Region {} process Message Type {} from Region {}: {}", currentDcopRun, getRegionID(), msgType, sender, receivedMsg);

                isBelongToATree = true;

                RegionIdentifier parent = sender;

                parentMap.put(receivedMsgRoot, parent);

                hopMap.put(receivedMsgRoot, receivedMsg.getHop() + 1);

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> receivedLoadMap = convertObjectToServiceMap(receivedMsgLoadMap);

                // The first time receiving the ASK message
                if (proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()).isEmpty()) {
                    Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(receivedLoadMap, StoreType.NOT_STORING, receivedMsgRoot);

                    // Update the excess load map from parent
                    excessParentLoadMap.clear();
                    excessLoad.forEach((key, map) -> excessParentLoadMap.put((ServiceIdentifier<?>) key, new HashMap<>(map)));

                    final Map<Integer, Map<RegionIdentifier, Double>> proposePlan = createProposeSingleEntryPlan(
                            hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());

                    // Propose the current plan
                    addMessage(parent, ACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposePlan);

                    // Send the excess load to children if any to ask for help
                    if (hasExcess(excessLoad)) {
                        childrenMap.put(receivedMsgRoot, new HashSet<>(getNeighborSet()));
                        childrenMap.get(receivedMsgRoot).remove(parent);

                        // Ask for help
                        for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                            addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), excessLoad);
                        }
                    }
                    // Empty children set if there is no excess load map
                    else {
                        childrenMap.put(receivedMsgRoot, Collections.emptySet());
                    }
                }
                // If there is some propose plan from children
                else {
                    Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));

                    Map<Integer, Map<RegionIdentifier, Double>> selfPlan = createProposeSingleEntryPlan(hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());

                    proposeLoadMap = addSelfLoadMap(proposeLoadMap, selfPlan);

                    // Propose the current plan
                    addMessage(parent, ACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap);

                    // Checking if the excess load from parent is larger or smaller than the load proposed by children
                    double excessParentLoad = 0;

                    for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> parentEntry : excessParentLoadMap.entrySet()) {
                        excessParentLoad += sumValues(parentEntry.getValue());
                    }

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

                            double loadToAsk = compareDouble(excessParentLoad, loadProposedFromOtherChildren) >= 0 ? excessParentLoad - loadProposedFromOtherChildren : 0;

                            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createSingleEntryAskLoadMap(excessParentLoadMap.keySet().iterator().next(), getRegionID(), loadToAsk);

                            addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap);
                        }
                    } else {
                        for (RegionIdentifier child : childrenMap.get(receivedMsgRoot)) {
                            addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap());
                        }
                    }
                }
            }
            else {
                addMessage(sender, ACdiffMessageType.REFUSE, receivedMsgRoot, 0, Collections.emptyMap());
            }
        }
        else if (ACdiffMessageType.PROPOSE.equals(msgType) && !rootDone) {
            // Convert the object message to Integers
            Map<Integer, Map<RegionIdentifier, Double>> proposedPlanFromChild = new HashMap<>();
            receivedMsgLoadMap.forEach((key, map) -> proposedPlanFromChild.put((Integer) key, new HashMap<>(map)));

            if (isRoot(receivedMsgRoot)) {
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

                // Override or store the received
                storedProposedMap.put(sender, proposedPlanFromChild);
                proposedChildrenMessageMap.put(receivedMsgRoot, storedProposedMap);

                Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> finalPlan = createFinalPlan(excessLoadMap, differentProposeLoadMap, receivedMsgRoot);

                addUpPlans(storedPlan, finalPlan);

                LOGGER.info("DCOP Run {} Region {} has temporary plan {}", currentDcopRun, getRegionID(), storedPlan);

                LOGGER.info("DCOP Run {} Region {} has aggregated excessLoadMap {}", currentDcopRun, getRegionID(), excessLoadMap);

                for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry : storedPlan.entrySet()) {
                    RegionIdentifier child = entry.getKey();
                    addMessage(child, ACdiffMessageType.PLAN, getRegionID(), 0, storedPlan.get(child));
                    updateFlowLoadMapWithRoot(child, convert(storedPlan.get(child)), FlowType.OUTGOING, receivedMsgRoot);
                }

                if (hasExcess(excessLoadMap)) {
                    for (RegionIdentifier child : getNeighborSet()) {
                        addMessage(child, ACdiffMessageType.ASK, getRegionID(), 0, excessLoadMap);
                    }
                }
                // Send DONE messages if there is no excess load map
                else {
                    // Notify DONE
                    // Add itself to the storeDoneRootSet
                    storedDoneRootSet.add(getRegionID());

                    for (RegionIdentifier child : getNeighborSet()) {
                        addMessage(child, ACdiffMessageType.DONE, getRegionID(), 0, Collections.emptyMap());
                    }

                    clearCurrentTreeInformation(receivedMsgRoot);
                }
            }
            // If not root, combine with other PROPOSE messages and with the updated ASK message
            // to create a PROPOSE message to send to the parent
            else {
                Map<RegionIdentifier, Map<Integer, Map<RegionIdentifier, Double>>> proposedMap = proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>());
                // Override the previous PROPOSE messages received from this sender
                proposedMap.put(sender, proposedPlanFromChild);
                proposedChildrenMessageMap.put(receivedMsgRoot, proposedMap);

                Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));

                Map<Integer, Map<RegionIdentifier, Double>> selfPlan = createProposeSingleEntryPlan(hopMap.get(receivedMsgRoot), getRegionID(), getAvailableCapacity());

                proposeLoadMap = addSelfLoadMap(proposeLoadMap, selfPlan);

                // Propose the current plan
                addMessage(parentMap.get(receivedMsgRoot), ACdiffMessageType.PROPOSE, receivedMsgRoot, hopMap.get(receivedMsgRoot), proposeLoadMap);

                double excessParentLoad = 0;

                for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> parentEntry : excessParentLoadMap.entrySet()) {
                    excessParentLoad += sumValues(parentEntry.getValue());
                }

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
                        // Just pick an arbitrary service
                        ServiceIdentifier<?> serviceToChild = excessParentLoadMap.keySet().iterator().next();

                        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> askLoadMap = createAskMsgSingleEntry(serviceToChild, getRegionID(), loadToAsk);
                        addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), askLoadMap);
                    } else {
                        addMessage(child, ACdiffMessageType.ASK, receivedMsgRoot, hopMap.get(receivedMsgRoot), Collections.emptyMap());
                    }
                }
            }
        }
        // Receive the most up-to-date plan
        // Clear the committed load
        // Modify the incoming load from this parent?
        // Commit the load from the PLAN
        // Then send the PLAN to the corresponding children
        else if (ACdiffMessageType.PLAN.equals(msgType)) {
            // If this sender is the parent given the root
            if (processMessageFromParent) {
                RegionIdentifier parent = sender;

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> finalPlanFromParent = convertObjectToServiceMap(receivedMsg.getLoadMap());

                Map<Integer, Map<RegionIdentifier, Double>> proposeChildrenLoadMap = aggregateChildrenProposeMessage(proposedChildrenMessageMap.getOrDefault(receivedMsgRoot, new HashMap<>()));

                // Update the incoming load from parent
                updateFlowLoadMapWithRoot(parent, convert(finalPlanFromParent), FlowType.INCOMING, receivedMsgRoot);

                Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(finalPlanFromParent, StoreType.STORE_PLAN, receivedMsgRoot);

                LOGGER.info("DCOP Run {} Region {} has plan from parent {}", currentDcopRun, getRegionID(), finalPlanFromParent);

                LOGGER.info("DCOP Run {} Region {} has proposed load from children {}", currentDcopRun, getRegionID(), proposeChildrenLoadMap);

                LOGGER.info("DCOP Run {} Region {} has excess load {}", currentDcopRun, getRegionID(), excessLoad);

                if (!childrenMap.get(receivedMsgRoot).isEmpty()) {
                    Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childrenPlan = createFinalPlan(excessLoad, proposeChildrenLoadMap, receivedMsgRoot);

                    LOGGER.info("DCOP Run {} Region {} has final plan to children load {}", currentDcopRun, getRegionID(), childrenPlan);

                    for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> childEntry : childrenPlan.entrySet()) {
                        // This line of code could be redundant
                        if (getRegionID().equals(childEntry.getKey())) {continue;}

                        // Not forward the plan if the plan to this child is empty
                        if (childEntry.getValue().isEmpty()) {continue;}

                        addMessage(childEntry.getKey(), ACdiffMessageType.PLAN, receivedMsgRoot, hopMap.get(receivedMsgRoot), childEntry.getValue());
                        updateFlowLoadMapWithRoot(childEntry.getKey(), convert(childEntry.getValue()), FlowType.OUTGOING, receivedMsgRoot);
                    }
                }
            }
        }
        // Remove all the PROPOSE messages for this rootID
        // Forward the DONE messages to the corresponding children
        else if (ACdiffMessageType.DONE.equals(msgType)) {
            storedDoneRootSet.add(receivedMsgRoot);
            clearCurrentTreeInformation(receivedMsgRoot);

            for (RegionIdentifier child : childrenMap.getOrDefault(receivedMsgRoot, Collections.emptySet())) {
                addMessage(child, ACdiffMessageType.DONE, receivedMsgRoot, 0, Collections.emptyMap());
            }
        }
        else if (ACdiffMessageType.REFUSE.equals(msgType) && !rootDone) {
            childrenMap.get(receivedMsgRoot).remove(sender);
        }
    }

    private void removeMessage(RegionIdentifier receivedMsgRoot) {
        for (Entry<RegionIdentifier, GeneralDcopMessage> msgEntry : inbox.getAsynchronousMessage().getReceiverMessageMap().entrySet()) {
            if (msgEntry.getValue() instanceof ACdiffDcopMessage) {
                ACdiffDcopMessage acdiffMsg = (ACdiffDcopMessage) msgEntry.getValue();

                // Remove all PROPOSE message of this root
                Set<ACdiffLoadMap> msgToRemove = new HashSet<>();

                Set<ACdiffLoadMap> proposeMsgSet = acdiffMsg.getMessageTypeMap().getOrDefault(ACdiffMessageType.PROPOSE, Collections.emptySet());
                for (ACdiffLoadMap msg : proposeMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }
                proposeMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();


                Set<ACdiffLoadMap> askMsgSet = acdiffMsg.getMessageTypeMap().getOrDefault(ACdiffMessageType.ASK, Collections.emptySet());
                // Remove all the ASK message of this root
                for (ACdiffLoadMap msg : askMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }

                askMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();

                Set<ACdiffLoadMap> refuseMsgSet = acdiffMsg.getMessageTypeMap().getOrDefault(ACdiffMessageType.REFUSE, Collections.emptySet());
                // Remove all the REFUSE message of this root
                for (ACdiffLoadMap msg : askMsgSet) {
                    if (msg.getRootID().equals(receivedMsgRoot)) {
                        msgToRemove.add(msg);
                    }
                }

                refuseMsgSet.removeAll(msgToRemove);
                msgToRemove.clear();
            }
        }
    }

    private void clearCurrentTreeInformation(RegionIdentifier receivedMsgRoot) {
        // Modify the parent and childrenMap
        removeMessage(receivedMsgRoot);

        isBelongToATree = false;
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

    private Map<Integer, Map<RegionIdentifier, Double>> addSelfLoadMap(
            Map<Integer, Map<RegionIdentifier, Double>> proposeLoadMap,
            Map<Integer, Map<RegionIdentifier, Double>> selfPlan) {
        Map<Integer, Map<RegionIdentifier, Double>> aggregatePlan = deepCopyMap(selfPlan);

        proposeLoadMap.forEach((key, map) -> updateKeyKeyLoadMap(aggregatePlan, key, getRegionID(), sumValues(map), true));

        return aggregatePlan;
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

    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> planDiff(
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> beforePlan,
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> afterPlan) {

        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> diff = new HashMap<>();
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceBeforeEntry : beforePlan.entrySet()) {
            ServiceIdentifier<?> serviceBefore = serviceBeforeEntry.getKey();

            if (!afterPlan.containsKey(serviceBefore)) {
                diff.put(serviceBefore, new HashMap<>(serviceBeforeEntry.getValue()));
            }
            else {
                for (Entry<RegionIdentifier, Double> beforeLoadEntry : serviceBeforeEntry.getValue().entrySet()) {
                    RegionIdentifier beforeRegion = beforeLoadEntry.getKey();

                    if (!afterPlan.get(serviceBefore).containsKey(beforeRegion)) {
                        Map<RegionIdentifier, Double> loadMap = diff.getOrDefault(serviceBefore, new HashMap<>());
                        loadMap.put(beforeRegion, beforeLoadEntry.getValue());
                        diff.put(serviceBefore, loadMap);
                    }
                    else {
                        double diffLoad = beforeLoadEntry.getValue() - afterPlan.get(serviceBefore).get(beforeRegion);

                        Map<RegionIdentifier, Double> loadMap = diff.getOrDefault(serviceBefore, new HashMap<>());
                        loadMap.put(beforeRegion, diffLoad);
                        diff.put(serviceBefore, loadMap);
                    }
                }
            }
        }

        return diff;
    }

    private Map<RegionIdentifier, ACdiffDcopMessage> readAcdiffMessges(int dcopRun) {
        Map<RegionIdentifier, ACdiffDcopMessage> messageMap = new HashMap<>();

        // Fill in the map with all neighbors
        getNeighborSet().forEach(neighbor -> messageMap.put(neighbor, new ACdiffDcopMessage()));

        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();

        for (RegionIdentifier sender : getNeighborSet()) {
            if (null != allSharedInformation.get(sender)) {
                DcopReceiverMessage abstractMsgMap = allSharedInformation.get(sender).getAsynchronousMessage();

                if (abstractMsgMap != null) {
                    if (abstractMsgMap.isSentTo(getRegionID()) && abstractMsgMap.getIteration() == dcopRun) {

                        GeneralDcopMessage abstractMessage = abstractMsgMap.getMessageForThisReceiver(getRegionID());

                        if (abstractMessage != null) {
                            ACdiffDcopMessage cdiffMessage = (ACdiffDcopMessage) abstractMessage;
                            messageMap.put(sender, cdiffMessage);
                        }
                    }
                }
            }
        }

        return messageMap;
    }

    private void createClientKeepLoadMap() {
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

    private void processInitialDemandLoadMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap) {
        updateFlowLoadMap(getRegionID(), convert(demandMap), FlowType.INCOMING);

        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = computeExcessLoad(demandMap, StoreType.STORE_DEMAND, getRegionID());

        excessLoadMap.putAll(excessLoad);

        LOGGER.info("DCOP Run {} Region {} has demand keep load map: {}", currentDcopRun, getRegionID(), rootKeepLoadMap.get(getRegionID()));

        LOGGER.info("DCOP Run {} Region {} has excess load map: {}", currentDcopRun, getRegionID(), excessLoad);

        if (hasExcess(excessLoad)) {
            isBelongToATree = true;

            childrenMap.put(getRegionID(), new HashSet<>(getNeighborSet()));

            for (RegionIdentifier neighbor : childrenMap.get(getRegionID())) {
                addMessage(neighbor, ACdiffMessageType.ASK, getRegionID(), 0, excessLoad);
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
    private <A> void addMessage(RegionIdentifier receiver, ACdiffMessageType messageType, RegionIdentifier rootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap) {
        ACdiffLoadMap cdiffLoadMap = new ACdiffLoadMap(rootID, hop, loadMap);

        LOGGER.info("DCOP Run {} Region {} sends message to Region {} type {}: {}", currentDcopRun, getRegionID(), receiver, messageType, cdiffLoadMap);

        // Update the message if it's already been in the messageMap to send
        if (messageMapToSend.get(receiver).getMessageTypeMap().containsKey(messageType)) {

            // Extending the set if matching those message types
            if (ACdiffMessageType.DONE == messageType) {
                messageMapToSend.get(receiver).getMessageTypeMap().get(messageType).add(cdiffLoadMap);
            }
            // Update the plan if the same root
            // Add new if not same root
            else if (ACdiffMessageType.PLAN == messageType) {
                Set<ACdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);

                for (ACdiffLoadMap loadMapMsg : msgSet) {
                    // Update and exit
                    if (loadMapMsg.getRootID().equals(rootID)) {
                        loadMapMsg.setLoadMap(loadMap);
                        return ;
                    }
                }

                // There is no message in the set, so append the set
                msgSet.add(new ACdiffLoadMap(rootID, hop, loadMap));
            }
            // Replacing current one
            else {
                Set<ACdiffLoadMap> msgSet = messageMapToSend.get(receiver).getMessageTypeMap().get(messageType);
                msgSet.clear();
                msgSet.add(cdiffLoadMap);
            }
        }
        // Otherwise, create a new message for this type
        else {
            ACdiffLoadMap aCdiffLoadmap = new ACdiffLoadMap(rootID, hop, loadMap);
            Map<ACdiffMessageType, Set<ACdiffLoadMap>> cdiffMsg = messageMapToSend.get(receiver).getMessageTypeMap();
            Set<ACdiffLoadMap> msgSet = cdiffMsg.getOrDefault(messageType, new HashSet<>());
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
        if (storeType == StoreType.STORE_DEMAND) {
            rootKeepLoadMap.put(getRegionID(), new HashMap<>());
            keepLoadMap = rootKeepLoadMap.get(getRegionID());
        }
        else if (storeType == StoreType.STORE_PLAN) {
            // Clear old load of this root
            rootKeepLoadMap.put(root, new HashMap<>());
            // Store the new plan directly to this
            keepLoadMap = rootKeepLoadMap.get(root);
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

        for (Entry<RegionIdentifier, ACdiffDcopMessage> entry : messageMapToSend.entrySet()) {
            RegionIdentifier receiver = entry.getKey();
            ACdiffDcopMessage cdiffMessage = entry.getValue();
            cdiffMsgPerIteration.setMessageToTheReceiver(receiver, cdiffMessage);
        }
        inbox.setAsynchronousMessage(cdiffMsgPerIteration);

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

            treeMsg.addMessageToReceiver(getRegionID(), new ACdiffDcopMessage());

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

        getNeighborSet().forEach(neighbor -> messageMapToSend.put(neighbor, new ACdiffDcopMessage()));
        getNeighborSet().forEach(neighbor -> storedMessages.put(neighbor, new ACdiffDcopMessage()));
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
