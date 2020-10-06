package com.bbn.map.dcop.rdiff;

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
import com.bbn.map.dcop.ServerClientService;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.rdiff.RdiffDcopMessage.RdiffMessageType;
import com.bbn.map.utils.MAPServices;
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
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#DISTRIBUTED_ROUTING_DIFFUSION}
 *
 */
public class RdiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(RdiffAlgorithm.class);
                    
    private Map<ServerClientService, Double> serverClientServiceMapToThisServer = new HashMap<>();
    
    private Map<ServiceIdentifier<?>, Double> totalGlobalServerDemand = new HashMap<>();
    
    // Send <ServerClientService -> Double> between regions
    private Map<RegionIdentifier, Map<ServerClientService, Double>> parentExcessLoadMap = new HashMap<>();
    
    private Map<RegionIdentifier, Map<ServerClientService, Double>> childrenLoadMap = new HashMap<>();
        
    
    private Map<ServerClientService, RdiffTree> rdiffTreeMap = new HashMap<>();
    
    private final Map<ServiceIdentifier<?>, RdiffTree> dataCenterTreeMap = new HashMap<>();
    
    /** Store the number of messages to wait for */
    private final SortedMap<ServiceIdentifier<?>, Set<ServerClientService>> sortedServiceTupleMapToProcess = new TreeMap<>(new SortServiceByPriorityComparator());
    private final SortedMap<ServiceIdentifier<?>, Set<ServerClientService>> sortedServiceTupleMapHasReceived = new TreeMap<>(new SortServiceByPriorityComparator());
    private final Map<ServerClientService, RegionIdentifier> serverClientServiceSenderMap = new HashMap<>();
    private final Map<ServerClientService, Double> serverClientServiceLoadMap = new HashMap<>();
    
    private int newIteration;
    
    private ResourceSummary summary;
    
    private DcopSharedInformation inbox;
        
    private int lastIteration;
    
    /**
     * This is the region that has a clientPool asking for a service to some remote server
     */
    private boolean isRoot;


    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public RdiffAlgorithm(RegionIdentifier regionID, DcopInfoProvider dcopInfoProvider, ApplicationManagerApi applicationManager) {
        super(regionID, dcopInfoProvider, applicationManager);
    }


    /** Initialize newIteration by taking the max iteration from the inbox and increment it.
     *  @return 0 (or more if more than second DCOP run)
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
        LOGGER.info("Region {} has inbox {}", getRegionID(), inbox);
        
        if (inbox != null) {
            if (inbox.getIterationMessageMap().containsKey(TREE_ITERATION)) {
                newIteration = inbox.getIterationMessageMap().get(TREE_ITERATION).getIteration() + 1;
            } else {
                newIteration = 0;
            }
        }
                
        summary = getDcopInfoProvider().getRegionSummary(ResourceReport.EstimationWindow.LONG);
        
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        
        LOGGER.info("Iteration {} Region {} has Region Capacity {}", newIteration, getRegionID(), getAvailableCapacity());
    }

    /**
     * @return
     *      RegionPlan
     */
    @Override
    public RegionPlan run() {
        initialize();

        readPreviousDataCenterTree();
        writeDataCenterTreeInformation(); // To write the last iteration
        
        // Not running the first DCOP run due to incomplete neighbor information
        if (newIteration == 0) {
            return defaultPlan(summary);
        }
        
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());
        
//        
//        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> entry : summary.getServerDemand().entrySet()) {
//            ImmutableMap.Builder<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionBuilder = ImmutableMap.builder();
//
//            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> innerEntry : entry.getValue().entrySet()) {
//                regionBuilder.put(new StringRegionIdentifier("D"), innerEntry.getValue());
//            }
//            
//            finalCompute.put(entry.getKey(), regionBuilder.build());
//        }
//        
//        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = finalCompute.build();  
        
        LOGGER.info("*Iteration {} Server Demand REGION {}: {}", newIteration, getRegionID(), summary.getServerDemand());
        LOGGER.info("*Iteration {} Server Demand Inferred REGION {}: {}", newIteration, getRegionID(), inferredServerDemand);
//        LOGGER.info("Iteration {} Server Load: {}", newIteration, summary.getServerLoad());
        LOGGER.info("Iteration {} Server Capacity: {}", newIteration, summary.getServerCapacity());
        LOGGER.info("*Iteration {} Network Demand REGION {}: {}", newIteration, getRegionID(), summary.getNetworkDemand());
//        LOGGER.info("Iteration {} Network Load: {}", newIteration, summary.getNetworkLoad());
        LOGGER.info("Iteration {} Network Capacity: {}", newIteration, summary.getNetworkCapacity());
                
//        readPreviousDataCenterTree();
              
        computeTotalGLOBALServerDemand(summary, inferredServerDemand);
        setRoot();
        
        initSortedServiceTupleMapToProcessFromServerDemand(summary);
        
        // Create children map from the server demand
        if (isRoot) {
            // Will be used later for inferred demand
            initServerClientServiceMapToThisServer(summary, inferredServerDemand);
            processServerDemand(serverClientServiceMapToThisServer, summary);
        }

        // Network Demand REGION B: {A={C <-> X={AppCoordinates {com.bbn.map, app1, 1.0}={LinkAttribute {DATARATE_TX, false}=0.027845454545454524, LinkAttribute {DATARATE_RX, false}=0.027845454545454524}}}, 
        //                           C={C <-> X={AppCoordinates {com.bbn.map, app1, 1.0}={LinkAttribute {DATARATE_TX, false}=0.027845454545454524, LinkAttribute {DATARATE_RX, false}=0.027845454545454524}}}}                        
        LOGGER.info("Iteration {} Region {} has serverClientServiceMapToThisServer {}", newIteration, getRegionID(), serverClientServiceMapToThisServer);
        
        for (int iteration = newIteration; iteration < DCOP_ITERATION_LIMIT + newIteration; iteration++) {            
            LOGGER.info("Iteration {} Region {} has sortedServiceTupleMapToProcess {}", iteration, getRegionID(), sortedServiceTupleMapToProcess);
            LOGGER.info("Iteration {} Region {} has sortedServiceTupleMapHasReceived {}", iteration, getRegionID(), sortedServiceTupleMapHasReceived);
            LOGGER.info("Iteration {} Region {} has ParentMap {}", iteration, getRegionID(), parentExcessLoadMap);
            LOGGER.info("Iteration {} Region {} has ChildrenMap {}", iteration, getRegionID(), childrenLoadMap);
            LOGGER.info("Iteration {} Region {} has rdiffTreeMap {}", iteration, getRegionID(), rdiffTreeMap);
            LOGGER.info("Iteration {} Region {} has dataCenterTreeMap {}", iteration, getRegionID(), dataCenterTreeMap);
                        
            sendAllMessages(iteration); // REVIEWED
            
            // waiting for messages from neighbors
            final Map<RegionIdentifier, RdiffDcopMessage> receivedRdiffMessageMap = new HashMap<>();
            try {
                Map<RegionIdentifier, GeneralDcopMessage> receivedGeneralMessageMap = waitForMessagesFromNeighbors(iteration);
                for (Entry<RegionIdentifier, GeneralDcopMessage> entry : receivedGeneralMessageMap.entrySet()) {
                    GeneralDcopMessage rdiffMessage = entry.getValue();
                    
                    if (rdiffMessage instanceof RdiffDcopMessage) {
                        receivedRdiffMessageMap.put(entry.getKey(), new RdiffDcopMessage((RdiffDcopMessage) rdiffMessage));
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOGGER.warn("InterruptedException when waiting for messages. Return the default DCOP plan: {} ",
                        e.getMessage(), e);
                return defaultPlan(summary);
            }
            
            for (Entry<RegionIdentifier, RdiffDcopMessage> entry : receivedRdiffMessageMap.entrySet()) {
                LOGGER.info("Iteration {} Region {} receives message from Region {}: {}", iteration, getRegionID(), entry.getKey(), entry.getValue());
            }

            processParentToChildrenMessages(iteration, receivedRdiffMessageMap, summary);
            processLoad();
            processChildrenToParentMessages(iteration, receivedRdiffMessageMap, summary);
            processLoad();

            LOGGER.info("Iteration {} Region {} has flowLoadMap {}", iteration, getRegionID(), getFlowLoadMap());
            LOGGER.info("Iteration {} Region {} has getClientLoadMap {}", iteration, getRegionID(), getClientKeepLoadMap());
        } // end of DCOP iterations
        
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has flowLoadMap {}", lastIteration, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has getClientLoadMap {}", lastIteration, getRegionID(), getClientKeepLoadMap());

        RegionPlan rplan = computeRegionDcopPlan(summary, lastIteration, false);

        return rplan;
    }
    
    /** 
     * Process all the stored message from the top service
     * Delete the key from sortedServiceTupleMapToProcess if the entry is empty (has processed all) 
     * If the topProcess is empty, then continue processing the next priority
     */
    private void processLoad() {
        SortedSet<ServiceIdentifier<?>> sortedService = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedService.addAll(sortedServiceTupleMapToProcess.keySet());
        
        for (ServiceIdentifier<?> currentTopService : sortedService) {            
            // Process each received ServerClientService 
            for (ServerClientService storedServerClientService : new HashSet<>(sortedServiceTupleMapHasReceived.getOrDefault(currentTopService, new HashSet<>()))) {
                if (rdiffTreeMap.getOrDefault(storedServerClientService, new RdiffTree()).hasParent()) {
                    regionProcessWithRdiffParent(storedServerClientService, serverClientServiceLoadMap.get(storedServerClientService), serverClientServiceSenderMap.get(storedServerClientService), storedServerClientService.getClient()); 
                } else {
                    regionProcessWithoutRdiffParent(storedServerClientService.getService(), serverClientServiceLoadMap.get(storedServerClientService), serverClientServiceSenderMap.get(storedServerClientService), storedServerClientService.getClient());
                }
                
                serverClientServiceLoadMap.remove(storedServerClientService);
                serverClientServiceSenderMap.remove(storedServerClientService);
                sortedServiceTupleMapToProcess.get(currentTopService).remove(storedServerClientService);
                sortedServiceTupleMapHasReceived.get(currentTopService).remove(storedServerClientService);
            }
            
            // If empty, remove topPriorityService in sortedServiceTupleMapHasReceived
            if (sortedServiceTupleMapHasReceived.getOrDefault(currentTopService, new HashSet<>()).isEmpty()) {
                sortedServiceTupleMapHasReceived.remove(currentTopService);
            }
            
            // If empty, remove topPriorityService in sortedServiceTupleMapToProcess
            // And continue to process the next topService
            if (sortedServiceTupleMapToProcess.get(currentTopService).isEmpty()) {
                sortedServiceTupleMapToProcess.remove(currentTopService);
            } 
            // Otherwise, break to exit
            else {
                break;
            }
        }
    }
    
    // Build rdiffTree
//  updateRdiffTree(serverClientService, parent, MofityTreeType.ADD_PARENT);
//  updateDataCenterTree(demandService, parent);

    private void processParentToChildrenMessages(int iteration, Map<RegionIdentifier, RdiffDcopMessage> receivedMessageMap, ResourceSummary summaryInput) {
        // Traverse the receivedMessages with client = getRegionID()
        //  + If the process is highest priority then process.
        //    ++ Delete it from the ToProcess
        //    ++ Remove the key topServicePriority if toProcess is empty
        //  + Otherwise store them
        
        for (Map.Entry<RegionIdentifier, RdiffDcopMessage> msgEntry : receivedMessageMap.entrySet()) {
            RegionIdentifier parent = msgEntry.getKey();
            Map<ServerClientService, Double> demandFromOtherRegion = msgEntry.getValue().getMsgTypeClientServiceMap()
                                                                    .getOrDefault(RdiffMessageType.PARENT_TO_CHILDREN, new HashMap<>());
            for (Entry<ServerClientService, Double> entry : demandFromOtherRegion.entrySet()) {
               ServerClientService serverClientService = entry.getKey();
               RegionIdentifier demandClient = serverClientService.getClient();
               ServiceIdentifier<?> demandService = serverClientService.getService();
               double load = entry.getValue();
               
               // Build rdiffTree and DataCenter tree
               updateRdiffTree(serverClientService, parent);      
               updateDataCenterTree(demandService, serverClientService.getServer(), parent);
               
               // If this region is the client
               // Then store first, then process below
               if (getRegionID().equals(demandClient)) {
                   Set<ServerClientService> serverClientServiceToStore = sortedServiceTupleMapHasReceived.getOrDefault(demandService, new HashSet<>());
                   serverClientServiceToStore.add(serverClientService);
                   sortedServiceTupleMapHasReceived.put(demandService, serverClientServiceToStore);
                   serverClientServiceLoadMap.put(serverClientService, load);
                   serverClientServiceSenderMap.put(serverClientService, parent);                   
               } 
               // If this region is not the client
               // Then forward the message to the children found from network demand 
               else {
                   findChildrenAndSendMessage(serverClientService, load, summaryInput, parent);
               }
            }
        } // end traversing <sender -> message>        
    }


    /**
     * Create sortedServiceTupleMapToProcess based on incoming networkDemand or demandToThisServer
     * @param summaryInput
     */
    private void initSortedServiceTupleMapToProcessFromServerDemand(ResourceSummary summaryInput) {
        for (Entry<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkEntry : summaryInput.getNetworkDemand().entrySet()) {
            for (Entry<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> flowEntry : networkEntry.getValue().entrySet()) {
                RegionIdentifier clientFlow = getClient(flowEntry.getKey());
                RegionIdentifier serverFlow = getServer(flowEntry.getKey());
                
                for (Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> serviceEntry : flowEntry.getValue().entrySet()) {
                    ServiceIdentifier<?> service = serviceEntry.getKey();
                    
                    // Skip if this is the AP or UNMANGED service
                    if (MAPServices.UNPLANNED_SERVICES.contains(service)) {
                        continue;
                    }
                    
                    // Ignore if the flow has RX = TX = 0
                    if (!isNonZeroFlow(serviceEntry.getValue())) {
                        continue;
                    }
                    
                    // If this is the server, then consider incoming flow
                    if (getRegionID().equals(serverFlow)) {
                        // Only store incoming flow
//                        if (isIncomingFlowUsingTrafficType(service, serviceEntry.getValue(), applicationManager)) {
                        if (isIncomingFlowUsingFlow(flowEntry.getKey())) {
                            ServerClientService serverClientService = new ServerClientService(serverFlow, clientFlow, service);
                            
                            Set<ServerClientService> serverClientServiceSet = sortedServiceTupleMapToProcess.getOrDefault(service, new HashSet<>());
                            serverClientServiceSet.add(serverClientService);
                            sortedServiceTupleMapToProcess.put(service, serverClientServiceSet);
                        }
                    }
                    // Otherwise, consider outgoing flow
                    else {
//                        if (!isIncomingFlowUsingTrafficType(service, serviceEntry.getValue(), applicationManager)) {
                        if (!isIncomingFlowUsingFlow(flowEntry.getKey())) {
                            ServerClientService serverClientService = new ServerClientService(serverFlow, clientFlow, service);
                            
                            Set<ServerClientService> serverClientServiceSet = sortedServiceTupleMapToProcess.getOrDefault(service, new HashSet<>());
                            serverClientServiceSet.add(serverClientService);
                            sortedServiceTupleMapToProcess.put(service, serverClientServiceSet);
                        }
                    }
                }
            }
        }        
    }

    /**
     * Send message to parent, children and other neighbors
     * @param iteration
     */
    private void sendAllMessages(int iteration) {
        inbox.removeMessageAtIteration(iteration - 2);
        
        // forward load message to children
        for (Entry<RegionIdentifier, Map<ServerClientService, Double>> entry : childrenLoadMap.entrySet()) {
            RegionIdentifier children = entry.getKey();
            Map<ServerClientService, Double> msgSendToChildren = entry.getValue();
            
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(children, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.PARENT_TO_CHILDREN, msgSendToChildren);
            rdiffMsgPerIteration.setMessageToTheReceiver(children, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} PARENT_TO_CHILDREN message {}", iteration, getRegionID(), children, msgSendToChildren);
        }
        
        // send excess load message back to parent
        for (Entry<RegionIdentifier, Map<ServerClientService, Double>> entry : parentExcessLoadMap.entrySet()) {
            RegionIdentifier parent = entry.getKey();
            Map<ServerClientService, Double> msgSendToChildren = entry.getValue();
            
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(parent, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.CHILDREN_TO_PARENT, msgSendToChildren);
            rdiffMsgPerIteration.setMessageToTheReceiver(parent, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} CHILDREN_TO_PARENT message {}", iteration, getRegionID(), parent, msgSendToChildren);
        }
        
        // send empty message to other neighbor
        Set<RegionIdentifier> nonParentChildren = new HashSet<>(getNeighborSet());
        nonParentChildren.removeAll(childrenLoadMap.keySet());
        nonParentChildren.removeAll(parentExcessLoadMap.keySet());
        
        for (RegionIdentifier receiver : nonParentChildren) {
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(receiver, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.EMPTY, new HashMap<>());
            rdiffMsgPerIteration.setMessageToTheReceiver(receiver, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} EMPTY message {}", iteration, getRegionID(), receiver, rdiffDcopMsg);
        }
        
        parentExcessLoadMap.clear();
        childrenLoadMap.clear();
        
//        if (iteration == lastIteration) {
            writeDataCenterTreeInformation();
//        }
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }

    /**
     * Read Data Center tree information from the previous DCOP round
     */
    private void readPreviousDataCenterTree() {
        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInfo = getDcopInfoProvider().getAllDcopSharedInformation(); 
                
        if (null != allSharedInfo.get(getRegionID())) {
            if (allSharedInfo.get(getRegionID()).containMessageAtIteration(TREE_ITERATION)) {
                GeneralDcopMessage abstractDcopMsg = allSharedInfo.get(getRegionID()).getMessageAtIteration(TREE_ITERATION).getMessageForThisReceiver(getRegionID());
                
                if (abstractDcopMsg instanceof RdiffDcopMessage) {
                    RdiffDcopMessage msg = (RdiffDcopMessage) abstractDcopMsg;
                    dataCenterTreeMap.putAll(msg.getDataCenterTreeMap());
                }
            }
        }
    }
    
    /**
     * Compute the total load received from serverDemand per service will be equivalent to value received in GLOBAL
     * THIS FUNCTION HAS BEEN REVIEWED
     * @param summary
     * @return
     */
    private void computeTotalGLOBALServerDemand(final ResourceSummary summary,
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand) {
        // from serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand
                .entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();

            // Ignore the special demand
            if (MAPServices.UNPLANNED_SERVICES.contains(service)) {
                continue;
            }
            
            double totalLoad = totalGlobalServerDemand.getOrDefault(service, 0.0);

            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> clientEntry : serviceEntry.getValue()
                    .entrySet()) {
                totalLoad += clientEntry.getValue().getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0.0);
                totalGlobalServerDemand.put(service, totalLoad);
            } // end for
        } // end for
    }
    
    /**
     * Set the agent to root if there is some server demand
     * THIS FUNCTION HAS BEEN REVIEWED
     */
    private void setRoot() {
        isRoot = compareDouble(sumValues(totalGlobalServerDemand), 0) > 0; 
    }

    /**
     * THIS FUNCTION HAS BEEN REVIEWED. <br>
     * Compute {@link #serverClientServiceMapToThisServer} from serverDemand. <br>
     * Compute {@link #flowLoadMap} from serverDemand <br>
     *  
     * @param summary
     */
    private void initServerClientServiceMapToThisServer(final ResourceSummary summary,
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand) {
        // traverse the serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            // traverse the source -> noteAttribte -> load
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> clientEntry : serviceEntry.getValue().entrySet()) {
                RegionIdentifier client = clientEntry.getKey();
                
                double loadValue = 0;
                
                // Make sure that server still send 0 out
                // In case clients have non-zero actual demand where inferred demand has 0, it might wait for service with higher priority
                if (null != clientEntry.getValue().get(MapUtils.COMPUTE_ATTRIBUTE)) {
                    loadValue = clientEntry.getValue().get(MapUtils.COMPUTE_ATTRIBUTE);
                } 
                
                ServerClientService serverClientService = new ServerClientService(getRegionID(), client, service);
                
                updateKeyLoadMap(serverClientServiceMapToThisServer, serverClientService, loadValue, true);
            }
        }
    }
    
    /**
     * Create the childrenMap: neighbor -> sourceRegion -> service -> load
     * This will be used later to send the message to the children
     * 
     * @param clientServiceDemandMap 
     *               from the serverDemand or from the message Parent-To-Children
     * @param summaryInput
     */
    /**
     * @param clientServiceDemandMap
     * @param summaryInput
     */
    private void processServerDemand(Map<ServerClientService, Double> clientServiceDemandMap, ResourceSummary summaryInput) {
        Map<ServerClientService, Double> demandFromOtherRegion = new HashMap<>();
        
        LOGGER.info("Iteration {} Region {} has clientServiceDemandMap {}", newIteration, getRegionID(), clientServiceDemandMap);
        
        // Update the above maps
        for (Entry<ServerClientService, Double> serverClientServiceLoadEntry : clientServiceDemandMap.entrySet()) {
            ServerClientService serverClientService = serverClientServiceLoadEntry.getKey();
            RegionIdentifier demandClient = serverClientService.getClient();
                        
            ServiceIdentifier<?> demandService = serverClientService.getService();
            double demandLoad = serverClientServiceLoadEntry.getValue();
            
            // Skip if the demand value is 0
            if (compareDouble(demandLoad, 0) == 0) {
                continue;
            }
            
            // If the demand comes from the region itself
            if (getRegionID().equals(demandClient)) {
                // Store the demand to sortedServiceTupleMapToProcess
                Set<ServerClientService> serverClientServiceToProcess = sortedServiceTupleMapToProcess.getOrDefault(demandService, new HashSet<>());
                serverClientServiceToProcess.add(serverClientService);
                sortedServiceTupleMapToProcess.put(demandService, serverClientServiceToProcess);

                // Store the demand to sortedServiceTupleMapHasReceived
                Set<ServerClientService> serverClientServiceToStore = sortedServiceTupleMapHasReceived.getOrDefault(demandService, new HashSet<>());
                serverClientServiceToStore.add(serverClientService);
                sortedServiceTupleMapHasReceived.put(demandService, serverClientServiceToStore);
                serverClientServiceLoadMap.put(serverClientService, demandLoad);
                serverClientServiceSenderMap.put(serverClientService, getRegionID()); 
            } 
            // Update the demandFromOtherRegion
            else {
                demandFromOtherRegion.put(serverClientService, demandLoad);
            }
        }
        
        processLoad();
                
        // Find children and forward the demand
        for (Entry<ServerClientService, Double> entry : demandFromOtherRegion.entrySet()) {
            findChildrenAndSendMessage(entry.getKey(), entry.getValue(), summaryInput, getRegionID());
        }
    }
    

    /**
     * In case the region is making a request to itself
     * @param service
     * @param load
     */
    private void regionProcessWithoutRdiffParent(ServiceIdentifier<?> service, double load, RegionIdentifier loadOrigin, RegionIdentifier client) {
        updateKeyKeyLoadMap(getFlowLoadMap(), loadOrigin, service, load, true);

        RdiffTree dcTreeGivenService = dataCenterTreeMap.get(service);

        // If there is no data center tree
        // Or this region is the root of the data center tree
        // Then keep all the load
        if (null == dcTreeGivenService || getRegionID().equals(dcTreeGivenService.getRoot())) {
            // keep all the load
            updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
            
            // Create the tree if there isn't any
            if (null == dcTreeGivenService) {
                RdiffTree dcTree = new RdiffTree();
                dcTree.setRoot(getRegionID());
                dataCenterTreeMap.put(service, dcTree);
            }
        }
        // Has data center tree and this region is not the root
        else {
//            RegionIdentifier dataCenter = dcTreeGivenService.getRoot();
            RegionIdentifier parentInDC = dcTreeGivenService.getParent();
            double availableCapacity = getAvailableCapacity();

            // load <= availableCapacity and availableCapacity >= 0 (implicitly)
            // Can keep all the load
            if (compareDouble(load, availableCapacity) <= 0) {
                updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
            }
            // availableCapacity < 0 or load > availableCapacity
            else {
                double loadToDataCenterRoot = -Double.MAX_VALUE;

                // Don't have capacity
                if (compareDouble(availableCapacity, 0) < 0) {
                    loadToDataCenterRoot = load;
                }
                // Have capacity and can server partial
                else {
                    loadToDataCenterRoot = load - availableCapacity;
                    updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, availableCapacity, true);
                }

                // send the load to data center
                updateKeyKeyLoadMap(parentExcessLoadMap, parentInDC, new ServerClientService(getRegionID(), client, service),  loadToDataCenterRoot, true);
                updateKeyKeyLoadMap(getFlowLoadMap(), parentInDC, service, -loadToDataCenterRoot, true);
            } // end checking if can/cannot serve all or just partial load
        } // end if of checking tree and parent        
    }
    
    private void regionProcessWithRdiffParent(ServerClientService serverClientServiceFromParent, double load, RegionIdentifier loadOrigin, RegionIdentifier client) {
        RegionIdentifier parent = rdiffTreeMap.get(serverClientServiceFromParent).getParent();
        ServiceIdentifier<?> service = serverClientServiceFromParent.getService();
        
        updateKeyKeyLoadMap(getFlowLoadMap(), loadOrigin, service, load, true);
        
        double availableCapacity = getAvailableCapacity();

        // load <= availableCapacity and availableCapacity >= 0 (implicitly)
        // Can keep all the load
        if (compareDouble(load, availableCapacity) <= 0) {
            updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
            updateKeyKeyLoadMap(parentExcessLoadMap, parent, serverClientServiceFromParent,  0, true);
        }
        // availableCapacity < 0 or load > availableCapacity
        else {
            double loadToParent = -Double.MAX_VALUE;

            // Don't have capacity
            if (compareDouble(availableCapacity, 0) < 0) {
                loadToParent = load;
            }
            // Have capacity and can server partial
            else {
                loadToParent = load - availableCapacity;
                updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, availableCapacity, true);
            }

            // update parentExcessLoadMap and outgoingLoadMap
            ServerClientService serverClientService = new ServerClientService(serverClientServiceFromParent);
            updateKeyKeyLoadMap(parentExcessLoadMap, parent, serverClientService,  loadToParent, true);
            updateKeyKeyLoadMap(getFlowLoadMap(), parent, service, -loadToParent, true);
        } // end checking if can/cannot serve all or just partial load
    }


    /**
     * @precondition This region is not the client
     * @param serverClientServiceDemand
     * @param demandLoad
     * @param summaryInput
     */
    private void findChildrenAndSendMessage(ServerClientService serverClientServiceDemand, double demandLoad, ResourceSummary summaryInput, RegionIdentifier loadOrigin) {
        RegionIdentifier demandServer = serverClientServiceDemand.getServer();
        RegionIdentifier demandClient = serverClientServiceDemand.getClient();
        ServiceIdentifier<?> demandService = serverClientServiceDemand.getService();

        // update incoming load map
        updateKeyKeyLoadMap(getFlowLoadMap(), loadOrigin, demandService, demandLoad, true);
        
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
                    
                    // Skip if this is not an incoming flow
//                    if (!isIncomingFlowUsingTrafficType(networkService, networkServiceEntry.getValue(), applicationManager)) {
//                        continue;
//                    }
                    
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
                        
                        ServerClientService serverClientService = new ServerClientService(networkServer, demandClient, demandService);
                        updateKeyKeyLoadMap(childrenLoadMap, networkNeighbor, serverClientService, demandLoad, true);
                        updateKeyKeyLoadMap(getFlowLoadMap(), networkNeighbor, demandService, -demandLoad, true);
//                        updateRdiffTree(serverClientService, networkNeighbor, MofityTreeType.ADD_CHILD);
                    } // end of if matching source and service
                } // end for loop serviceNetworkMap
            } // end for loop neighborNetworkEntry 
        } // end for loop summaryInput        
    }


    private void updateRdiffTree(ServerClientService service, RegionIdentifier parent) { 
        RdiffTree rdiffTree = rdiffTreeMap.getOrDefault(service, new RdiffTree());
        
        rdiffTree.setParent(parent);
        
        rdiffTreeMap.put(service, rdiffTree);
    }
    
    /**
     * 
     * @param iteration
     * @param receivedMessageMap <sender -> message>
     * @param summaryInput
     */
    private void processChildrenToParentMessages(int iteration, Map<RegionIdentifier, RdiffDcopMessage> receivedMessageMap, ResourceSummary summaryInput) {
//        SortedMap<ServiceIdentifier<?>, Set<ServerClientService>> receivedFromDataCenterTree = new TreeMap<>(new SortServiceByPriorityComparator());
        
        // Store all Children-To-Parent messages
        // Build RdiffTree or DataCenter tree
        for (Map.Entry<RegionIdentifier, RdiffDcopMessage> msgEntry : receivedMessageMap.entrySet()) {
            RegionIdentifier sender = msgEntry.getKey();
            
            Map<ServerClientService, Double> demandFromOtherRegion = msgEntry.getValue().getMsgTypeClientServiceMap()
                                                                    .getOrDefault(RdiffMessageType.CHILDREN_TO_PARENT, new HashMap<>());
            for (Entry<ServerClientService, Double> entry : demandFromOtherRegion.entrySet()) {
               ServerClientService serverClientService = entry.getKey();
               ServiceIdentifier<?> demandService = serverClientService.getService();
               double loadService = entry.getValue();
                  
               // Store the information to sortedServiceTupleMapHasReceived first
               // If this request is in sortedServiceTupleMapToProcess
               // The else condition never happens
               // If receive such tuple, then ignore
               if (sortedServiceTupleMapToProcess.getOrDefault(demandService, new HashSet<>()).contains(serverClientService)) {
                   Set<ServerClientService> serverClientServiceStoredSet = sortedServiceTupleMapHasReceived.getOrDefault(demandService, new HashSet<>());
                   serverClientServiceStoredSet.add(serverClientService);
                   sortedServiceTupleMapHasReceived.put(demandService, serverClientServiceStoredSet);
                   serverClientServiceLoadMap.put(serverClientService, loadService);
                   serverClientServiceSenderMap.put(serverClientService, sender);
               }
            }
        } // end storing messages  
    }


    /**
     * Add root to the DC tree if there is not one
     * @param service
     * @param root
     */
    private void updateDataCenterTree(ServiceIdentifier<?> service, RegionIdentifier root, RegionIdentifier parent) {
        // Build the DataCenter
        RdiffTree dataCenterTree = dataCenterTreeMap.getOrDefault(service, new RdiffTree());
        if (!dataCenterTree.hasRoot()) {
            dataCenterTree.setRoot(root);
        }
        if (!dataCenterTree.hasParent()) {
            dataCenterTree.setParent(parent);
        }
        dataCenterTreeMap.put(service, dataCenterTree);        
    }
    
    /**
     * Override the old DC tree and write the last iteration of the current DCOP round.
     */
    protected void writeDataCenterTreeInformation() {
        lastIteration = newIteration + DCOP_ITERATION_LIMIT - 1;
        
        DcopReceiverMessage abstractTreeMsg = inbox.getMessageAtIterationOrDefault(TREE_ITERATION, new DcopReceiverMessage());
        if (abstractTreeMsg != null) {
            abstractTreeMsg.setIteration(lastIteration);
            abstractTreeMsg.addMessageToReceiver(getRegionID(), new RdiffDcopMessage(dataCenterTreeMap));
            inbox.putMessageAtIteration(TREE_ITERATION, abstractTreeMsg);
        }
        
        final DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        getDcopInfoProvider().setLocalDcopSharedInformation(messageToSend);
    }

    @Override
    protected double getAvailableCapacity() {
        return getRegionCapacity() - sumKeyKeyValues(getClientKeepLoadMap());
    }
}