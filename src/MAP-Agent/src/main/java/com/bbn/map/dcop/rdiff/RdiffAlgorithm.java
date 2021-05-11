package com.bbn.map.dcop.rdiff;

import java.util.Collections;
import java.util.Comparator;
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
import com.bbn.map.dcop.ServerClientServiceLoad;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.rdiff.RdiffDcopMessage.RdiffMessageType;
import com.bbn.map.ta2.RegionalTopology;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
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
                            
    // Send <ServerClientService -> Double> between regions
    private final Map<RegionIdentifier, Set<ServerClientServiceLoad>> excessLoadMapBackToParent = new HashMap<>();
    
    private final Map<RegionIdentifier, Set<ServerClientServiceLoad>> loadMapToChildren = new HashMap<>();
                
    // Store all tuples that region receives in a single DCOP iteration
    private final SortedSet<ServerClientServiceLoad> sortedServiceTupleMapHasReceived = new TreeSet<>(new SortServiceInTuple());
    
    private int newIteration;
    
    private final ResourceSummary summary;
    
    private final RegionalTopology topology;
    
    private DcopSharedInformation inbox;
        
    private int lastIteration;
    
    // Only modified by non-root regions. Root regions can compare regionID with getServer to determine if it is the root of the tuple or not
    private final Map<ServerClientService, RegionIdentifier> parentTreeMap = new HashMap<>();
    
    private final Map<ServerClientService, RegionIdentifier> pathToClients = new HashMap<>();
    
    private final Set<ServiceIdentifier<?>> selfRegionServices = new HashSet<>();
    
    private final SortedSet<ServerClientServiceLoad> keepLoadSet = new TreeSet<>(new SortServiceInTuple());
    
    private final Set<ServerClientServiceLoad> finalExcessLoad = new HashSet<>();

    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     * @param summary .
     * @param topology .
     */
    public RdiffAlgorithm(RegionIdentifier regionID, DcopInfoProvider dcopInfoProvider, ApplicationManagerApi applicationManager, ResourceSummary summary, RegionalTopology topology) {
        super(regionID, dcopInfoProvider, applicationManager);
        this.summary = summary;
        this.topology = topology;
    }


    /** Initialize newIteration by taking the max iteration from the inbox and increment it.
     *  @return 0 (or more if more than second DCOP run)
     */
    private void initialize() {        
        this.inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());

        LOGGER.info("Region {} has inbox {}", getRegionID(), inbox);
        
        if (inbox != null) {
            if (inbox.getIterationMessageMap().containsKey(TREE_ITERATION)) {
                newIteration = inbox.getIterationMessageMap().get(TREE_ITERATION).getIteration() + 1;
            } else {
                newIteration = 0;
            }
        }
                        
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        retrieveAllService(summary);
        
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
//            return defaultPlan(summary);
            RegionPlan defaultRegionPlan = defaultPlan(summary, 0);
            LOGGER.info("DCOP Run {} Region Plan Region {}: {}", newIteration, getRegionID(), defaultRegionPlan);
            return defaultRegionPlan;
        }
        
        selfRegionServices.addAll(summary.getServerDemand().keySet());
        
        // Get inferred demand for old services
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredDemand = inferTotalDemand(getDcopInfoProvider(), selfRegionServices);

        LOGGER.info("Iteration {} Region {} has Server Demand {}", newIteration, getRegionID(), summary.getServerDemand());
        LOGGER.info("Iteration {} Region {} has Server Demand Inferred {}", newIteration, getRegionID(), inferredDemand);

        LOGGER.info("Iteration {} Region {} has Network Demand {}", newIteration, getRegionID(), summary.getNetworkDemand());
        LOGGER.info("Iteration {} Region {} has Server Capacity {}", newIteration, getRegionID(), summary.getServerCapacity());
        LOGGER.info("Iteration {} Region {} has Network Capacity {}", newIteration, getRegionID(), summary.getNetworkCapacity());
                                                     
        processServerDemand(inferredDemand, summary);
                    
        for (int iteration = newIteration; iteration < DCOP_ITERATION_LIMIT + newIteration; iteration++) {                        
            LOGGER.info("Iteration {} Region {} has ParentMap {}", iteration, getRegionID(), excessLoadMapBackToParent);
            LOGGER.info("Iteration {} Region {} has ChildrenMap {}", iteration, getRegionID(), loadMapToChildren);
            LOGGER.info("Iteration {} Region {} has pathToClients {}", iteration, getRegionID(), pathToClients);
            LOGGER.info("Iteration {} Region {} has dataCenterTreeMap {}", iteration, getRegionID(), parentTreeMap);
            LOGGER.info("Iteration {} Region {} has selfRegionServices {}", iteration, getRegionID(), selfRegionServices);
            
            sortedServiceTupleMapHasReceived.clear();
                        
            sendAllMessages(iteration);
            
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
                return defaultPlan(summary, iteration);
            }
            
            for (Entry<RegionIdentifier, RdiffDcopMessage> entry : receivedRdiffMessageMap.entrySet()) {
                LOGGER.info("Iteration {} Region {} receives message from Region {}: {}", iteration, getRegionID(), entry.getKey(), entry.getValue());
            }
            processParentToChildrenMessages(iteration, receivedRdiffMessageMap, summary);
            processLoad(iteration);
            processChildrenToParentMessages(iteration, receivedRdiffMessageMap, summary);
            processLoad(iteration);
            
            LOGGER.info("Iteration {} Region {} has finalExcessLoad {}", iteration, getRegionID(), finalExcessLoad);
            // Update incoming load map from parent
            for (ServerClientServiceLoad swappedTuple : finalExcessLoad) {
                ServerClientService scsTuple = new ServerClientService(swappedTuple.getServer(), swappedTuple.getClient(), swappedTuple.getService());
                excessLoadMapBackToParent.computeIfAbsent(parentTreeMap.get(scsTuple), k -> new HashSet<>()).add(swappedTuple);
                updateKeyKeyLoadMap(getFlowLoadMap(), parentTreeMap.get(scsTuple), swappedTuple.getService(), -swappedTuple.getLoad(), true);
            }
            finalExcessLoad.clear();

            LOGGER.info("Iteration {} Region {} has flowMap {}", iteration, getRegionID(), getFlowLoadMap());
        } // end of DCOP iterations
        
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has flowLoadMap {}", lastIteration, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Iteration {} Region {} has getClientLoadMap {}", lastIteration, getRegionID(), getClientKeepLoadMap());

        RegionPlan rplan = computeRegionDcopPlan(summary, lastIteration, true);

        return rplan;
        
//      
//      for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> entry : summary.getServerDemand().entrySet()) {
//          ImmutableMap.Builder<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionBuilder = ImmutableMap.builder();
//
//          for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> innerEntry : entry.getValue().entrySet()) {
//              regionBuilder.put(new StringRegionIdentifier("D"), innerEntry.getValue());
//          }
//          
//          finalCompute.put(entry.getKey(), regionBuilder.build());
//      }
//      
//      final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = finalCompute.build();  
    }

    /** 
     * Process all the stored message from the top service
     */
    private void processLoad(int iteration) {
        LOGGER.info("Iteration {} Region {} has sortedServiceTupleMapHasReceived {}", iteration, getRegionID(), sortedServiceTupleMapHasReceived);
        
        for (ServerClientServiceLoad tuple : sortedServiceTupleMapHasReceived) {
            processLoadWithSwapping(tuple, iteration);
        }
        
        sortedServiceTupleMapHasReceived.clear();
    }
    

    private void processParentToChildrenMessages(int iteration, Map<RegionIdentifier, RdiffDcopMessage> receivedMessageMap, ResourceSummary summaryInput) {
        // Traverse the receivedMessages with client = getRegionID()
        //  + If the process is highest priority then process.
        //    ++ Delete it from the ToProcess
        //    ++ Remove the key topServicePriority if toProcess is empty
        //  + Otherwise store them
        
        for (Map.Entry<RegionIdentifier, RdiffDcopMessage> msgEntry : receivedMessageMap.entrySet()) {
            RegionIdentifier parent = msgEntry.getKey();
            Set<ServerClientServiceLoad> receivedDemandTuples = msgEntry.getValue().getMessages(RdiffMessageType.PARENT_TO_CHILDREN);
            
            for (ServerClientServiceLoad tuple : receivedDemandTuples) {
               RegionIdentifier demandClient = tuple.getClient();
               ServiceIdentifier<?> demandService = tuple.getService();
               
               updateParentTreeMap(new ServerClientService(tuple.getServer(), demandClient, demandService), parent);
               
               // If this region is the client
               // Add to sortedServiceTupleMapHasReceived to process later
               if (getRegionID().equals(demandClient)) {
                   sortedServiceTupleMapHasReceived.add(tuple);
                   updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), tuple.getService(), tuple.getLoad(), true);
               } 
               // If this region is not the client
               // Then forward the message to the children found from network demand 
               else {
//                   findChildrenToForwardDemand(tuple, summaryInput);
                   findChildrenToForwardRdiffDemand(tuple, iteration, parent);
               }
            }
        } // end traversing <sender -> message>        
    }
    
    private void findChildrenToForwardRdiffDemand(ServerClientServiceLoad tuple, int iteration, RegionIdentifier parentRegion) {
        RegionIdentifier child  = findNeighbor(tuple.getServer(), tuple.getClient(), iteration, topology, parentRegion);
        
        if (child != null) {
            loadMapToChildren.computeIfAbsent(child, k -> new HashSet<>()).add(ServerClientServiceLoad.deepCopy(tuple));
            updatePathToClient(tuple, child);
        }
        
        // Stop looking if found one
        return ;
    }


    /**
     * 
     * @param iteration
     * @param receivedMessageMap <sender -> message>
     * @param summaryInput
     */
    private void processChildrenToParentMessages(int iteration, Map<RegionIdentifier, RdiffDcopMessage> receivedMessageMap, ResourceSummary summaryInput) {        
        // Store all Children-To-Parent messages
        // Build RdiffTree or DataCenter tree
        for (Map.Entry<RegionIdentifier, RdiffDcopMessage> msgEntry : receivedMessageMap.entrySet()) {
            RegionIdentifier child = msgEntry.getKey();
            
            Set<ServerClientServiceLoad> demandFromOtherRegion = msgEntry.getValue().getMsgTypeClientServiceMap().getOrDefault(RdiffMessageType.CHILDREN_TO_PARENT, new HashSet<>());
            for (ServerClientServiceLoad entry : demandFromOtherRegion) {
               ServiceIdentifier<?> demandService = entry.getService();
               
               // Keep all if this region is the data center
               if (getRegionID().equals(entry.getServer())) {
                   LOGGER.info("Iteration {} Region {} adds {} to keepLoadSet {} because it is the server", iteration, getRegionID(), entry, sortedServiceTupleMapHasReceived);
                   keepLoadSet.add(entry);
               }
               else {
                   LOGGER.info("Iteration {} Region {} adds {} to sortedServiceTupleMapHasReceived {}", iteration, getRegionID(), entry, sortedServiceTupleMapHasReceived);
                   sortedServiceTupleMapHasReceived.add(entry);
                   LOGGER.info("Iteration {} Region {} has sortedServiceTupleMapHasReceived {}", iteration, getRegionID(), sortedServiceTupleMapHasReceived);
               }
               
               updateKeyKeyLoadMap(getFlowLoadMap(), child, demandService, entry.getLoad(), true);
            }
        } // end storing messages  
    }

    /**
     * Send message to parent, children and other neighbors
     * @param iteration
     */
    private void sendAllMessages(int iteration) {
        inbox.removeMessageAtIteration(iteration - 2);
        
        // forward load message to children
        for (Entry<RegionIdentifier, Set<ServerClientServiceLoad>> entry : loadMapToChildren.entrySet()) {
            RegionIdentifier children = entry.getKey();
            Set<ServerClientServiceLoad> msgSendToChildren = entry.getValue();
            
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(children, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.PARENT_TO_CHILDREN, msgSendToChildren);
            rdiffMsgPerIteration.setMessageToTheReceiver(children, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} PARENT_TO_CHILDREN message {}", iteration, getRegionID(), children, msgSendToChildren);
        }
        
        // send excess load message back to parent
        for (Entry<RegionIdentifier, Set<ServerClientServiceLoad>> entry : excessLoadMapBackToParent.entrySet()) {
            RegionIdentifier parent = entry.getKey();
            Set<ServerClientServiceLoad> msgSendToChildren = entry.getValue();
            
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(parent, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.CHILDREN_TO_PARENT, msgSendToChildren);
            rdiffMsgPerIteration.setMessageToTheReceiver(parent, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} CHILDREN_TO_PARENT message {}", iteration, getRegionID(), parent, msgSendToChildren);
        }
        
        // send empty message to other neighbor
        Set<RegionIdentifier> nonParentChildren = new HashSet<>(getNeighborSet());
        nonParentChildren.removeAll(loadMapToChildren.keySet());
        nonParentChildren.removeAll(excessLoadMapBackToParent.keySet());
        
        for (RegionIdentifier receiver : nonParentChildren) {
            DcopReceiverMessage rdiffMsgPerIteration = inbox.getMessageAtIterationOrDefault(iteration, new DcopReceiverMessage(getRegionID(), iteration));
            RdiffDcopMessage rdiffDcopMsg = (RdiffDcopMessage) rdiffMsgPerIteration.getMessageForThisReceiverOrDefault(receiver, new RdiffDcopMessage());
            
            rdiffDcopMsg.addMessage(RdiffMessageType.EMPTY, new HashSet<>());
            rdiffMsgPerIteration.setMessageToTheReceiver(receiver, rdiffDcopMsg);
            
            inbox.setMessageAtIteration(iteration, rdiffMsgPerIteration);
            
            LOGGER.info("Iteration {} Region {} sends Region {} EMPTY message {}", iteration, getRegionID(), receiver, rdiffDcopMsg);
        }
        
        excessLoadMapBackToParent.clear();
        loadMapToChildren.clear();
        
        writeDataCenterTreeInformation();
        
        getDcopInfoProvider().setLocalDcopSharedInformation(inbox);
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
                    pathToClients.putAll(msg.getPathToClientMap());
                    selfRegionServices.addAll(msg.getServiceSet());
                }
            }
        }
    }

    /**
     * @param inferredServerDemand .
     * @param summaryInput .
     */
    private void processServerDemand(final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand, ResourceSummary summaryInput) {
        SortedMap<ServiceIdentifier<?>, Set<ServerClientServiceLoad>> sortedDemand = new TreeMap<>(new SortServiceByPriorityComparator());
        Set<ServerClientServiceLoad> demandFromOtherClientRegion = new HashSet<>();

        // traverse the serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inferredServerDemand.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            // traverse the source -> noteAttribte -> load
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> clientEntry : serviceEntry.getValue().entrySet()) {
                RegionIdentifier client = clientEntry.getKey();
                
                double loadValue = clientEntry.getValue().get(MapUtils.COMPUTE_ATTRIBUTE);
                
                ServerClientServiceLoad tuple = ServerClientServiceLoad.of(getRegionID(), client, service, loadValue);
                sortedDemand.computeIfAbsent(service, k -> new HashSet<>()).add(tuple);                
            }
        }
        
       LOGGER.info("Iteration {} Region {} has sorted self demand {}", newIteration, getRegionID(), sortedDemand);
        
        // Loop through sorted demand
        for (Entry<ServiceIdentifier<?>, Set<ServerClientServiceLoad>> entry : sortedDemand.entrySet()) {
            for (ServerClientServiceLoad tuple : entry.getValue()) {                                       
                // Skip if the demand value is 0
                if (compareDouble(tuple.getLoad(), 0D) == 0) {
                    continue;
                }
                
                // Add all services from inferred server demand
                // Query the aggregate demand and ignore null total demand
//                selfRegionServices.add(tuple.getService());
                
                // If the demand comes from the region itself
                if (getRegionID().equals(tuple.getClient())) {
                    // Keep all demand in this data center
                    keepLoadSet.add(tuple);
                    updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), tuple.getService(), tuple.getLoad(), true);
                } 
                // Update the demandFromOtherRegion
                else {
                    demandFromOtherClientRegion.add(ServerClientServiceLoad.deepCopy(tuple));
                }
                
                // Add positive load to positive incoming self flowLoadMap
//                updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), tuple.getService(), tuple.getLoad(), true);
            }
        }
                        
        // Find children and forward the demand
        for (ServerClientServiceLoad entry : demandFromOtherClientRegion) {
//            findChildrenToForwardDemand(entry, summaryInput);
            findChildrenToForwardRdiffDemand(entry, newIteration, getRegionID());
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
//    private void processServerDemand(SortedMap<ServiceIdentifier<?>, Set<ServerClientServiceLoad>> sortedDemand, ResourceSummary summaryInput) {
//        Set<ServerClientServiceLoad> demandFromOtherClientRegion = new HashSet<>();
//        
//        LOGGER.info("Iteration {} Region {} has clientServiceDemandMap {}", newIteration, getRegionID(), sortedDemand);
//        
//        // Loop through the sorted map
//        for (Entry<ServiceIdentifier<?>, Set<ServerClientServiceLoad>> entry : sortedDemand.entrySet()) {
//                for (ServerClientServiceLoad tuple : entry.getValue()) {                                       
//                // Skip if the demand value is 0
//                if (compareDouble(tuple.getLoad(), 0D) == 0) {
//                    continue;
//                }
//                
//                // Add all services to self region services
//                selfRegionServices.add(tuple.getService());
//                
//                // If the demand comes from the region itself
//                if (getRegionID().equals(tuple.getClient())) {
//                    // Region keeps all the load since it is the data center
////                    updateKeyKeyLoadMap(getClientKeepLoadMap(), getRegionID(), tuple.getService(), tuple.getLoad(), true);
//                    keepLoadSet.add(tuple);
//                } 
//                // Update the demandFromOtherRegion
//                else {
//                    demandFromOtherClientRegion.add(ServerClientServiceLoad.deepCopy(tuple));
//                }
//                
//                // Add positive load to positive incoming self flowLoadMap
//                updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), tuple.getService(), tuple.getLoad(), true);
//            }
//        }
//                        
//        // Find children and forward the demand
//        for (ServerClientServiceLoad entry : demandFromOtherClientRegion) {
//            findChildrenAndSendMessage(entry, summaryInput);
//        }
//    }
    

    /**
     * In case the region is making a request to itself
     * @param service
     * @param load
     */
//    private void regionProcessWithoutRdiffParent(ServiceIdentifier<?> service, double load, RegionIdentifier client) {
//        RdiffTree dcTreeGivenService = parentTreeMap.get(service);
//
//        // If there is no data center tree
//        // Or this region is the root of the data center tree
//        // Then keep all the load
//        if (null == dcTreeGivenService || getRegionID().equals(dcTreeGivenService.getRoot())) {
//            // keep all the load
//            updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
//            
//            // Create the tree if there isn't any
//            if (null == dcTreeGivenService) {
//                RdiffTree dcTree = new RdiffTree();
//                dcTree.setRoot(getRegionID());
//                parentTreeMap.put(service, dcTree);
//            }
//        }
//        // Has data center tree and this region is not the root
//        else {
////            RegionIdentifier dataCenter = dcTreeGivenService.getRoot();
//            RegionIdentifier parentInDC = dcTreeGivenService.getParent();
//            double availableCapacity = getAvailableCapacity();
//
//            // load <= availableCapacity and availableCapacity >= 0 (implicitly)
//            // Can keep all the load
//            if (compareDouble(load, availableCapacity) <= 0) {
//                updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, load, true);
//            }
//            // availableCapacity < 0 or load > availableCapacity
//            else {
//                double loadToDataCenterRoot = -Double.MAX_VALUE;
//
//                // Don't have capacity
//                if (compareDouble(availableCapacity, 0) < 0) {
//                    loadToDataCenterRoot = load;
//                }
//                // Have capacity and can server partial
//                else {
//                    loadToDataCenterRoot = load - availableCapacity;
//                    updateKeyKeyLoadMap(getClientKeepLoadMap(), client, service, availableCapacity, true);
//                }
//
//                // send the load to data center
//                updateKeyKeyLoadMap(parentExcessLoadMap, parentInDC, new ServerClientService(getRegionID(), client, service),  loadToDataCenterRoot, true);
//                updateKeyKeyLoadMap(getFlowLoadMap(), parentInDC, service, -loadToDataCenterRoot, true);
//            } // end checking if can/cannot serve all or just partial load
//        } // end if of checking tree and parent        
//    }
    
    /**
     * @precondition self region is not the server of the tuple
     * @param tuple .
     * @param iteration .
     */
    private void processLoadWithSwapping(ServerClientServiceLoad tuple, int iteration) {        
        RegionIdentifier client = tuple.getClient();
        ServiceIdentifier<?> service = tuple.getService();
        double load = tuple.getLoad();
        double availableCapacity = getAvailableCapacity();
        
        LOGGER.info("Iteration {} Region {} processes {} with keepLoadSet {}", iteration, getRegionID(), tuple, keepLoadSet);
        
        // If load < availableCapacity => keep all the load
        if (compareDouble(load, availableCapacity) <= 0) {
            keepLoadSet.add(tuple);
            LOGGER.info("Iteration {} Region {} has keepLoadSet {}", iteration, getRegionID(), tuple, keepLoadSet);
        }
        // load > availableCapacity
        // keep availableCapacity and send load - availableCapacity
        else {
            double loadToParent = load - availableCapacity;
            // Only keep if availableCapacity > 0
            if (compareDouble(availableCapacity, 0) > 0) {
                keepLoadSet.add(ServerClientServiceLoad.of(tuple.getServer(), client, service, availableCapacity));
            }
            
            ServerClientServiceLoad excessTuple = ServerClientServiceLoad.of(tuple.getServer(), client, service, loadToParent);
            
            LOGGER.info("Iteration {} Region {} has keepLoadSet before swapping {}", iteration, getRegionID(), keepLoadSet);
            
            // Swap the excess tuple with current keep tuple
            // Add the swapped tuple to finalExcessLoad
            Set<ServerClientServiceLoad> excessTuplesAfterSwapping = swapPriorityService(keepLoadSet, excessTuple, iteration);
            
            finalExcessLoad.addAll(excessTuplesAfterSwapping);
        } // end checking if can/cannot serve all or just partial load
    }

    //Swap higher with lower priority tuples and return lower priority tuples as excess load map to parent
    private Set<ServerClientServiceLoad> swapPriorityService(SortedSet<ServerClientServiceLoad> keepLoad, ServerClientServiceLoad tuple, int iteration) {
        Set<ServerClientServiceLoad> excessTupleSet = new HashSet<>();
        SortedSet<ServerClientServiceLoad> sortedKeepSet = new TreeSet<>(Collections.reverseOrder(new SortServiceInTuple()));
        sortedKeepSet.addAll(keepLoad);
        SortedSet<ServerClientServiceLoad> copyStoredKeepSet = new TreeSet<>(Collections.reverseOrder(new SortServiceInTuple()));
        copyStoredKeepSet.addAll(keepLoad);
        
        ServerClientServiceLoad leftOver = ServerClientServiceLoad.deepCopy(tuple);
        
        // Traverse from lower to higher keep tuple
        for (ServerClientServiceLoad keepTuple : sortedKeepSet) {
            if (getPriority(keepTuple) >= getPriority(leftOver)) {
                LOGGER.info("Iteration {} Region {} cannot find tuple with lower priority than {} from copyStoredKeepSet {}", iteration, getRegionID(), leftOver, copyStoredKeepSet);
                LOGGER.info("Iteration {} Region {} adds tuple {} to excessTupleSet {}", iteration, getRegionID(), leftOver, excessTupleSet);
                excessTupleSet.add(ServerClientServiceLoad.deepCopy(leftOver));
                
                LOGGER.info("Iteration {} Region {} has copyStoredKeepSet after swapping {}", iteration, getRegionID(), copyStoredKeepSet);
                LOGGER.info("Iteration {} Region {} has excessTupleSet after swapping {}", iteration, getRegionID(), excessTupleSet);

                break;
            }
            
            LOGGER.info("Iteration {} Region {} finds keep tuple with priority={}: {} smaller than left over tuple priority={}: {} from sortedKeepSet {}",
                    iteration, getRegionID(), getPriority(keepTuple), keepTuple, getPriority(leftOver), leftOver, copyStoredKeepSet);
            
            // Found a keep tuple with larger load than the leftOver
            if (compareDouble(leftOver.getLoad(), keepTuple.getLoad()) <= 0) {
                LOGGER.info("Iteration {} Region {} removes tuple {} from copyStoredKeepSet {}", iteration, getRegionID(), keepTuple, copyStoredKeepSet);
                copyStoredKeepSet.remove(keepTuple);
                
                LOGGER.info("Iteration {} Region {} adds tuple {} to copyStoredKeepSet {}", iteration, getRegionID(), leftOver, copyStoredKeepSet);
                copyStoredKeepSet.add(leftOver);
                
                if (compareDouble(leftOver.getLoad(), keepTuple.getLoad()) < 0) {
                    ServerClientServiceLoad modifiedKeepTuple = ServerClientServiceLoad.of(keepTuple.getServer(), keepTuple.getClient(), keepTuple.getService(), keepTuple.getLoad() - leftOver.getLoad());
                    LOGGER.info("Iteration {} Region {} adds tuple {} to copyStoredKeepSet {}", iteration, getRegionID(), modifiedKeepTuple, copyStoredKeepSet);
                    copyStoredKeepSet.add(modifiedKeepTuple);
                }

                ServerClientServiceLoad excessTuple = ServerClientServiceLoad.of(keepTuple.getServer(), keepTuple.getClient(), keepTuple.getService(), leftOver.getLoad());
                LOGGER.info("Iteration {} Region {} adds tuple {} to excessTupleSet {}", iteration, getRegionID(), excessTuple, excessTupleSet);
                excessTupleSet.add(excessTuple);
                leftOver = ServerClientServiceLoad.of(null, null, null, 0D);
                LOGGER.info("Iteration {} Region {} has changed left over tuple to {}", iteration, getRegionID(), leftOver);
                
                LOGGER.info("Iteration {} Region {} has copyStoredKeepSet after swapping {}", iteration, getRegionID(), copyStoredKeepSet);
                LOGGER.info("Iteration {} Region {} has excessTupleSet after swapping {}", iteration, getRegionID(), excessTupleSet);

                break;
            }
            else {
                LOGGER.info("Iteration {} Region {} removes tuple {} from copyStoredKeepSet {}", iteration, getRegionID(), keepTuple, copyStoredKeepSet);
                copyStoredKeepSet.remove(keepTuple);
                
                ServerClientServiceLoad modifiedKeepTuple = ServerClientServiceLoad.of(leftOver.getServer(), leftOver.getClient(), leftOver.getService(), keepTuple.getLoad());
                LOGGER.info("Iteration {} Region {} adds tuple {} to copyStoredKeepSet {}", iteration, getRegionID(), modifiedKeepTuple, copyStoredKeepSet);
                copyStoredKeepSet.add(modifiedKeepTuple);
                
                LOGGER.info("Iteration {} Region {} adds tuple {} to excessTupleSet {}", iteration, getRegionID(), keepTuple, excessTupleSet);
                excessTupleSet.add(keepTuple);
                
                leftOver = ServerClientServiceLoad.of(leftOver.getServer(), leftOver.getClient(), leftOver.getService(), leftOver.getLoad() - keepTuple.getLoad());
                LOGGER.info("Iteration {} Region {} has changed left over tuple to {}", iteration, getRegionID(), leftOver);
                                 
                LOGGER.info("Iteration {} Region {} has copyStoredKeepSet after swapping {}", iteration, getRegionID(), copyStoredKeepSet);
                LOGGER.info("Iteration {} Region {} has excessTupleSet after swapping {}", iteration, getRegionID(), excessTupleSet);
            }
        }
        
        if (compareDouble(leftOver.getLoad(), 0D) > 0 && !excessTupleSet.contains(leftOver)) {
            LOGGER.info("Iteration {} Region {} adds tuple {} to excessTupleSet {}", iteration, getRegionID(), leftOver, excessTupleSet);
            excessTupleSet.add(leftOver);
        }
        
        keepLoadSet.clear();
        keepLoadSet.addAll(copyStoredKeepSet);
        
        LOGGER.info("Iteration {} Region {} has keepLoadSet after swapping {}", iteration, getRegionID(), keepLoadSet);
        
        // Temporary return the input tuple
//        excessTuples.add(ServerClientServiceLoad.deepCopy(tuple));
        return excessTupleSet;
    }
    
    private int getPriority(ServerClientServiceLoad tuple) {
        return getPriority(tuple.getService());
    }


    /**
     * @precondition This region is not the client
     * @param serverClientServiceDemand
     * @param demandLoad
     * @param summaryInput
     */
//    private void findChildrenToForwardDemand(ServerClientServiceLoad serverClientServiceDemand, ResourceSummary summaryInput) {
//        RegionIdentifier demandServer = serverClientServiceDemand.getServer();
//        RegionIdentifier demandClient = serverClientServiceDemand.getClient();
//        ServiceIdentifier<?> demandService = serverClientServiceDemand.getService();
//        double demandLoad = serverClientServiceDemand.getLoad();
//        
//        for (Entry<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkNeighborEntry : summaryInput.getNetworkDemand().entrySet()) {    
//            RegionIdentifier networkNeighbor = networkNeighborEntry.getKey();
//            
//            // Skip self networkDemand
//            if (networkNeighbor.equals(getRegionID())) {
//                continue;
//            }
//            
//            // Check if this tuple is in the pathToClients
//            if (pathToClients.containsKey(new ServerClientService(demandServer, demandClient, demandService))) {
//                RegionIdentifier child = pathToClients.get(new ServerClientService(demandServer, demandClient, demandService));
//                loadMapToChildren.computeIfAbsent(child, k -> new HashSet<>()).add(ServerClientServiceLoad.deepCopy(serverClientServiceDemand));
//                // Adding load to the negative outgoing flowLoadMap
//                updateKeyKeyLoadMap(getFlowLoadMap(), child, demandService, -demandLoad, true);
//                // Stop looking if found one
//                return ;
//            }
//            
//            // Otherwise, look for the child in the network demand
//            for (Entry<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> networkClientEntry : networkNeighborEntry.getValue().entrySet()) {
//                RegionNetworkFlow flow = networkClientEntry.getKey();
//                RegionIdentifier networkClient = getClient(networkClientEntry.getKey());
//                ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> networkServiceMap = networkClientEntry.getValue();
//                RegionIdentifier networkServer = getServer(flow);
//                
//                for (Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> networkServiceEntry : networkServiceMap.entrySet()) {
//                    ServiceIdentifier<?> networkService = networkServiceEntry.getKey();
//                      
//                    if (!isIncomingFlowUsingFlow(flow)) {
//                        continue;
//                    }
//
//                    // FOUND THE MATCH <service, client> in both serverDemand and networkDemand
//                    // Send all load to this neighbor which is a children
//                    // Update the incomingLoadMap, and outgoingLoadMap
//                    if (demandClient.equals(networkClient) && demandService.equals(networkService) && demandServer.equals(networkServer)) {  
//                        // If the flow is 0, then keep all the load
//                        if (!isNonZeroFlow(networkServiceEntry.getValue())) {
//                            updateKeyKeyLoadMap(getClientKeepLoadMap(), demandClient, demandService, demandLoad, true);
//                            continue;
//                        }
//                        
//                        loadMapToChildren.computeIfAbsent(networkNeighbor, k -> new HashSet<>()).add(ServerClientServiceLoad.deepCopy(serverClientServiceDemand));
//                        
//                        updateKeyKeyLoadMap(getFlowLoadMap(), networkNeighbor, demandService, -demandLoad, true);
//                        
//                        // Add new 
//                        updatePathToClient(serverClientServiceDemand, networkNeighbor);
//                        
//                        // Stop looking if found one
//                        return ;
//                    } // end of if matching source and service
//                } // end for loop serviceNetworkMap
//            } // end for loop neighborNetworkEntry 
//        } // end for loop summaryInput        
//    }


    /**
     * If first time see this service, client => create the tree and adds path to client
     * @param service
     * @param parent
     */
    private void updatePathToClient(ServerClientServiceLoad tuple, RegionIdentifier child) { 
        ServerClientService s2s = new ServerClientService(tuple.getServer(), tuple.getClient(), tuple.getService());
        
        if (!pathToClients.containsKey(s2s)) {
            pathToClients.put(s2s, child);
        }
    }

    /**
     * Add root to the DC tree if there is not one
     * @param tuple
     * @param root
     */
    private void updateParentTreeMap(ServerClientService tuple, RegionIdentifier parent) {
        parentTreeMap.putIfAbsent(new ServerClientService(tuple), parent);        
    }
    
    /**
     * @param summary .
     * @param lastIteration .
     * @param isRootKeepTheRest If true, let the overloaded region keeps all the remaining load if can't shed all. Used in timeout case.
     * @return DCOP plan
     */
//    private RegionPlan computeRdiffRegionPlan(ResourceSummary summary, int lastIteration) {        
//        // Return default plan if totalLoads coming is 0
//        Map<ServiceIdentifier<?>, Double> incomingLoadMap = new HashMap<>();
//        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> outgoingLoadMap = new HashMap<>();
//        
//        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> neighborEntry : getFlowLoadMap().entrySet()) {
//            RegionIdentifier neighbor = neighborEntry.getKey();
//            for (Entry<ServiceIdentifier<?>, Double> serviceEntry : neighborEntry.getValue().entrySet()) {
//                ServiceIdentifier<?> service = serviceEntry.getKey();
//                double load = serviceEntry.getValue();
//                
//                // incomingLoad
//                if (compareDouble(load, 0) > 0) {
//                    updateKeyLoadMap(incomingLoadMap, service, load, true);
//                } 
//                // outgoingLoadMap
//                else {
//                    updateKeyKeyLoadMap(outgoingLoadMap, service, neighbor, -load, true);
//                }
//            }
//        }        
//        
//        if (compareDouble(sumValues(incomingLoadMap), 0) == 0) {
//            RegionPlan defaultRegionPlan = defaultPlan(summary);
//            LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), defaultRegionPlan);
//            return defaultRegionPlan;
//        }
//    
//        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
//        Builder<RegionIdentifier, Double> regionPlanBuilder;
//    
//        for (Entry<ServiceIdentifier<?>, Double> serviceEntry : incomingLoadMap.entrySet()) {
//            ServiceIdentifier<?> service = serviceEntry.getKey();
//            double totalLoadFromThisService = serviceEntry.getValue();
//    
//            regionPlanBuilder = new Builder<>();
//            if (!outgoingLoadMap.containsKey(service) || compareDouble(totalLoadFromThisService, 0) == 0) {
//                for (RegionIdentifier neighbor : getNeighborSet()) {
//                    regionPlanBuilder.put(neighbor, 0.0);
//                }
//                regionPlanBuilder.put(getRegionID(), 1.0);
//            } 
//            else {
//                
//                double totalRatio = 0;
//                
//                for (RegionIdentifier neighbor : getNeighborSet()) {
//                    double load = outgoingLoadMap.get(service).getOrDefault(neighbor, 0.0);
//                    regionPlanBuilder.put(neighbor, load / totalLoadFromThisService);
//                    totalRatio += load / totalLoadFromThisService;
//                }
//                
//                regionPlanBuilder.put(getRegionID(), 1 - totalRatio);
////                else {
////                    regionPlanBuilder.put(getRegionID(), keepLoadMap.getOrDefault(service, 0D) / totalLoadFromThisService);
////                }
//            }
//            ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
//            
//            if (compareDouble(sumValues(regionPlan), 1) != 0) {
//                LOGGER.info("DCOP Run {} Region Plan Region {} DOES NOT ADD UP TO ONE: {}", lastIteration, getRegionID(), regionPlan);
//            }
//    
//            servicePlanBuilder.put(service, regionPlan);
//        }
//        
//        // Create DCOP plan for services that are not in servicePlanBuilder
//        Set<ServiceIdentifier<?>> serviceNotInDcopPlan = new HashSet<>(getAllServiceSet());
//        serviceNotInDcopPlan.removeAll(servicePlanBuilder.build().keySet());
//        
//        for (ServiceIdentifier<?> service : serviceNotInDcopPlan) {
//            Builder<RegionIdentifier, Double> planBuilder = new Builder<>();
//            planBuilder.put(getRegionID(), 1D);
//            getNeighborSet().forEach(neighbor -> planBuilder.put(neighbor, 0D));
//            
//            ImmutableMap<RegionIdentifier, Double> regionPlan = planBuilder.build();
//            servicePlanBuilder.put(service, regionPlan);
//        }
//    
//        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
//                .build();
//    
//        LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), dcopPlan);
//    
//        final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);
//    
//        return rplan;
//    }
    

    /**
     * Sort tuple by service priority from higher to lower
     * @author khoihd
     *
     */
    private class SortServiceInTuple implements Comparator<ServerClientServiceLoad> {
        @Override
        public int compare(ServerClientServiceLoad o1, ServerClientServiceLoad o2) {
            int compareServicePriority = Integer.compare(getPriority(o2.getService()), getPriority(o1.getService()));
            int compareLoad = compareDouble(o2.getLoad(), o1.getLoad()); 
            int compareClient = o2.getClient().getName().compareTo(o1.getClient().getName());
            int compareServer = o2.getServer().getName().compareTo(o1.getServer().getName());
            
            if (compareServicePriority != 0) {
                return compareServicePriority;
            }
            else if (compareLoad != 0) {
                return compareLoad;
            }
            else if (compareClient != 0) {
                return compareClient;
            }
            else {
                return compareServer;
            }
        }
    }
    
    /**
     * Override the old DC tree and write the last iteration of the current DCOP round.
     */
    protected void writeDataCenterTreeInformation() {
        lastIteration = newIteration + DCOP_ITERATION_LIMIT - 1;
        
        DcopReceiverMessage abstractTreeMsg = inbox.getMessageAtIterationOrDefault(TREE_ITERATION, new DcopReceiverMessage());
        if (abstractTreeMsg != null) {
            abstractTreeMsg.setIteration(lastIteration);
            abstractTreeMsg.addMessageToReceiver(getRegionID(), new RdiffDcopMessage(new HashMap<>(), parentTreeMap, pathToClients, selfRegionServices));
            inbox.putMessageAtIteration(TREE_ITERATION, abstractTreeMsg);
        }
        
        getDcopInfoProvider().setLocalDcopSharedInformation(inbox);
    }

    @Override
    protected double getAvailableCapacity() {
        double totalLoad = 0D;
        
        for (ServerClientServiceLoad tuple : keepLoadSet) {
            totalLoad += tuple.getLoad();
        }
        
        return Math.max(getRegionCapacity() - totalLoad, 0D);        
    }
}