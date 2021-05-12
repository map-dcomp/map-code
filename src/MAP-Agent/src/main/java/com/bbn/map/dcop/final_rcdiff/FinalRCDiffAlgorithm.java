package com.bbn.map.dcop.final_rcdiff;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

import com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType;
import com.bbn.map.dcop.final_rcdiff.FinalRCDiffPlan.PlanType;
import com.bbn.map.ta2.RegionalTopology;

import static com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType.SERVER_TO_CLIENT;
import static com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType.G;
import static com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType.C;
import static com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType.CLR;
import static com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage.FinalRCDiffMessageType.TREE;

/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#FINAL_RCDIFF}
 *
 */
public class FinalRCDiffAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinalRCDiffAlgorithm.class);
    
    private static final boolean READING_MESSAGES = true;
                        
    private final Map<RegionIdentifier, FinalRCDiffDcopMessage> messageMapToSend = new HashMap<>();
    
    private int currentDcopRun;
    
    private final ResourceSummary summary;
    
    private final RegionalTopology topology;
    
    private DcopSharedInformation inbox;
        
    //===========NEW VARIABLES
    private final Map<FinalRCDiffMessageType, SortedMap<RegionIdentifier, Set<FinalRCDiffMessageContent>>> receivedMessageMap = new HashMap<>();

    private final SortServiceByPriorityComparator descendingPriority= new SortServiceByPriorityComparator();
    
    /**
     * Positive value indicates planned demand shed <br>
     * Negative value indicates planned demand received
     */
    private SortedMap<ServiceIdentifier<?>, Double> delta = new TreeMap<>(descendingPriority);
        
    private double availableCapacity = 0;
    
    private FinalRCDiffRequest parentRequest = FinalRCDiffRequest.emptyRequest();
    
    private static SortProposal sortProposal = new SortProposal();
    
    private final SortedSet<FinalRCDiffProposal> childrenProposal = new TreeSet<>(sortProposal);
        
    private RegionIdentifier parent = null;
    
    private RegionIdentifier root = null;
    
    private final Set<FinalRCDiffPlan> outputs = new HashSet<>();
    
    private final Set<FinalRCDiffPlan> inputs = new HashSet<>();   
    
    private int roundCount = 0;
    
    private FinalRCDiffTree pathToClient = FinalRCDiffTree.emptyTree();
    
    private static final long SLEEP_TIME = AgentConfiguration.getInstance().getApRoundDuration().toMillis(); // In milliseconds
    
    private SortedSet<RegionIdentifier> sortedNeighbors = new TreeSet<>(new SortRegionIdentifierComparator());
    
    private static final boolean CLEAR_MESSAGES = true;
    
    private static final boolean NOT_CLEAR_MESSAGES = false;
        
    private static final long ALTERNATE_RC_DIFF_DURATION_SECONDS = 135;
    
    private final Map<FinalRCDiffPlan, LocalDateTime> inputTimer = new HashMap<>();
    
    private final Map<FinalRCDiffPlan, LocalDateTime> outputTimer = new HashMap<>();
    

    private final Duration timerThreshold;

    /**
     * Number of rounds needed for a stable plan <br>
     * Provided as an input to RC-DIFF algorithm
     */
    private static final short T = 5;
    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     * @param summary .
     * @param topology .
     */
    public FinalRCDiffAlgorithm(RegionIdentifier regionID, DcopInfoProvider dcopInfoProvider, ApplicationManagerApi applicationManager, ResourceSummary summary, RegionalTopology topology) {
        super(regionID, dcopInfoProvider, applicationManager);
        this.summary = summary;
        this.topology = topology;
        this.timerThreshold = AgentConfiguration.getInstance().getRcdiffTimerThreshold();
    }

    private static final Random RANDOM = new Random();
    
    /**
     * @return DCOP plan
     */
    public RegionPlan run() {
        Duration rcdiffDuration = AgentConfiguration.getInstance().getDcopRoundDuration().minus(AgentConfiguration.getInstance().getDcopEstimationWindow());
        
        if (rcdiffDuration.isZero() || rcdiffDuration.isNegative()) {
            rcdiffDuration = Duration.ofSeconds(ALTERNATE_RC_DIFF_DURATION_SECONDS);
            LOGGER.warn("RCDIFF duration is NEGATIVE. The duration is now set to {}", rcdiffDuration);
        }
        
        initialize();
        
        readPreviousDataCenterTree();
        
        writeIterationInformation(CLEAR_MESSAGES);
        
        LOGGER.info("DCOP Run {} has duration {}", currentDcopRun, rcdiffDuration);
        
        if (currentDcopRun == 0) {
//            return defaultPlan(summary);
            RegionPlan defaultRegionPlan = defaultPlan(summary);
            LOGGER.info("DCOP Run {} Region Plan Region {}: {}", currentDcopRun, getRegionID(), defaultRegionPlan);
            return defaultRegionPlan;
        }
                
        pathToClient.getSelfRegionServices().addAll(summary.getServerDemand().keySet());

        // Get inferred demand for old services
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredDemand = inferTotalDemand(getDcopInfoProvider(), pathToClient.getSelfRegionServices());
//        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredDemand = inferHardCodeTotalDemand(getDcopInfoProvider(), pathToClient.getSelfRegionServices(), 50D);


        LOGGER.info("DCOP Run {} Region {} has Server Demand {}", currentDcopRun, getRegionID(), summary.getServerDemand());
        LOGGER.info("DCOP Run {} Region {} has Server Demand Inferred {}", currentDcopRun, getRegionID(), inferredDemand);

        LOGGER.info("DCOP Run {} Region {} has Network Demand {}", currentDcopRun, getRegionID(), summary.getNetworkDemand());
        LOGGER.info("DCOP Run {} Region {} has Server Capacity {}", currentDcopRun, getRegionID(), summary.getServerCapacity());
        LOGGER.info("DCOP Run {} Region {} has Network Capacity {}", currentDcopRun, getRegionID(), summary.getNetworkCapacity());
        
        LOGGER.info("DCOP Run {} Region {} has RC-DIFF timer thresholds {}", currentDcopRun, getRegionID(), timerThreshold);
        LOGGER.info("DCOP Run {} Region {} has T rounds {}", currentDcopRun, getRegionID(), T);

        
//        final Duration rcdiffDuration = Duration.ofSeconds(RC_DIFF_DURATION_SECONDS);        

        final LocalDateTime stopTime = LocalDateTime.now().plus(rcdiffDuration);
                
        while (READING_MESSAGES) {
            // Keep updating neighbor set to check if any neighbor is disconnected
            retrieveNeighborSetFromNetworkLink(summary);
            readMessages(currentDcopRun);

            getFlowLoadMap().clear();
            
            // totalDemand is monotonically increasing 
            SortedMap<ServiceIdentifier<?>, Double> totalDemand = runServerToClientBlock(inferredDemand, sortedNeighbors);
            
            LOGGER.info("DCOP Run {} Region {} has delta {}", currentDcopRun, getRegionID(), delta);
            LOGGER.info("DCOP Run {} Region {} has totalDemand {}", currentDcopRun, getRegionID(), totalDemand);
            LOGGER.info("DCOP Run {} Region {} has region capacity {}", currentDcopRun, getRegionID(), getRegionCapacity());       
                        
            // Compute temporary excess load
            SortedMap<ServiceIdentifier<?>, Double> tempExcessMap = computeTempExcess(totalDemand, delta, getRegionCapacity());            
            // Compute excess load map
            SortedMap<ServiceIdentifier<?>, Double> excessLoadMap = maxElementWise(tempExcessMap, 0D);
            
            // Compute available capacity
            SortedMap<ServiceIdentifier<?>, Double> availCapMap = reverseSignElementWise(tempExcessMap);
            availCapMap = maxElementWise(availCapMap, 0D);
            availableCapacity = sumValues(availCapMap);
            
//            // Assume: all entries are either positive or negative, not both
//            double totalDelta = sumValues(delta);
//            
//            // If delta has negative values => received load => check for remaining available capacity after receiving those load
//            // After computing available capacity, compare with total demand => excess load map and available capacity
//            if (compareDouble(totalDelta, 0D) < 0) {
//                // Compute the remaining capacity after receiving some load
//                double remainingCap = Math.max(0, getRegionCapacity() + totalDelta);
//                // Compute excess load map from totalDemand
//                tempExcessMap = computeExcess(totalDemand, remainingCap);
//                // Update availableCapacity
//                availableCapacity = Math.max(remainingCap - sumValues(totalDemand), 0D);
//            }
//            else if (compareDouble(sumValues(delta), 0D) >= 0) {
//                // Compute the current excess load after shedding part of the load
//                SortedMap<ServiceIdentifier<?>, Double> curExcessAfterShedding = new TreeMap<>(descendingPriority);
//                curExcessAfterShedding.putAll(substractTwoMaps(totalDemand, delta));      
//                
//                // Compute excess load map after storing using avaialbleCapacity
//                tempExcessMap = computeExcess(curExcessAfterShedding, getRegionCapacity());
//                availableCapacity = Math.max(getRegionCapacity() - sumValues(curExcessAfterShedding), 0D);
//            }
            
            SortedMap<ServiceIdentifier<?>, Double> descendingExcessLoadMap = new TreeMap<>(descendingPriority);
            descendingExcessLoadMap.putAll(excessLoadMap);

            
            LOGGER.info("DCOP Run {} Region {} has excessLoad {}", currentDcopRun, getRegionID(), descendingExcessLoadMap);       
            LOGGER.info("DCOP Run {} Region {} has availCapMap {} with availableCapacity {}", currentDcopRun, getRegionID(), availCapMap, availableCapacity);

            runGblock(descendingExcessLoadMap, sortedNeighbors);
            
            runCblock(parentRequest, parent, sortedNeighbors, availableCapacity, inputs);
            
            runClearblock(outputs, inputs, parentRequest, descendingExcessLoadMap, sortedNeighbors, parent, root);
            
            if (AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDrops()) {
                if (compareDouble(RANDOM.nextDouble(),
                        1 - AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDropRate()) < 0) {
                    sendMessages();
                }
            } else {
                sendMessages();
            }

            LOGGER.info("Dcop Run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
            LOGGER.info("DCOP Run {} Region {} end the current cycle", currentDcopRun, getRegionID());

            if (LocalDateTime.now().isAfter(stopTime)) {
                break;
            }
            
            // Sleep to increase interval between two loops
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }              
        
        writeIterationInformation(NOT_CLEAR_MESSAGES);
        
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has flowLoadMap {}", currentDcopRun, getRegionID(), getFlowLoadMap());
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
        
//        return computeFinalRCDiffPlan(summary, currentDcopRun);
        return computeRegionDcopPlan(summary, currentDcopRun, true);
    }

    private SortedMap<ServiceIdentifier<?>, Double> computeTempExcess(SortedMap<ServiceIdentifier<?>, Double> demandMap, 
            SortedMap<ServiceIdentifier<?>, Double> deltaMap,
            double regionCap) {
        SortedMap<ServiceIdentifier<?>, Double> resultMap = new TreeMap<>(descendingPriority);
        
        SortedMap<ServiceIdentifier<?>, Double> capMap = new TreeMap<>(descendingPriority);
        Set<ServiceIdentifier<?>> allServices = getAllPlannedServices();
        allServices.stream().forEach(service -> capMap.put(service, 0D));
        
        double flag = sumValues(deltaMap);
        
        // Has negative entries
        // Distribute the cap to negative deltas if any
        if (compareDouble(flag, 0D) < 0) {
            // Distribute capacity to negative entries with the absolute value first. 
            // (If not, the result will have both positive and negative entries which is a problem)
            for (Entry<ServiceIdentifier<?>, Double> deltaEntry : deltaMap.entrySet()) {
                double negValue = deltaEntry.getValue();
                if (compareDouble(negValue, 0D) < 0) {
                    capMap.merge(deltaEntry.getKey(), Math.abs(negValue), Double::sum);
                    regionCap -= Math.abs(negValue); // Regions are guaranteed to have cap for the accepted load
                }
            }
            LOGGER.info("Dcop Run {} Region {} has negative deltaMap {}", currentDcopRun, getRegionID(), deltaMap);
            LOGGER.info("Dcop Run {} Region {} has capMap {} with remaining capacity {}", currentDcopRun, getRegionID(), capMap, regionCap);
        }
        
        // Now distribute the cap to the demand value
        for (Entry<ServiceIdentifier<?>, Double> demandEntry : demandMap.entrySet()) {
            ServiceIdentifier<?> service = demandEntry.getKey();
            double demand = demandEntry.getValue();
            
            capMap.merge(service, Double.min(demand, regionCap), Double::sum);
            regionCap -= Double.min(demand, regionCap);
        }
        LOGGER.info("Dcop Run {} Region {} has demandMap {}", currentDcopRun, getRegionID(), demandMap);
        LOGGER.info("Dcop Run {} Region {} has capMap {} with remaining capacity {} after distributing cap to demand", currentDcopRun, getRegionID(), capMap, regionCap);
        
        // If there is still remaining cap, then assign it to the first entry
        if (compareDouble(regionCap, 0D) > 0) {
            for (Entry<ServiceIdentifier<?>, Double> entry : capMap.entrySet()) {
                entry.setValue(entry.getValue() + regionCap);
                break;
            }
        }
        LOGGER.info("Dcop Run {} Region {} has capMap {} with remaining capacity {} after distributing to first entry", currentDcopRun, getRegionID(), capMap, regionCap);
        
        // resultMap = demandMap - deltaMap - capMap
        SortedMap<ServiceIdentifier<?>, Double> demandMinusDelta = substractTwoMaps(demandMap, deltaMap);
        resultMap.putAll(substractTwoMaps(demandMinusDelta, capMap));
        
        LOGGER.info("Dcop Run {} Region {} has tempExcessLoadMap {}", currentDcopRun, getRegionID(), resultMap);
        return resultMap;
        
            
        // From higher to lower priority service,
        // Only check the demand entry that is positive
        //      If there is no value in delta
        //          capMap += Double.min(regionCap, demand.value) // distribute cap to demand
        //          regionCap -= Double.min(regionCap, demand.value)
        //      If there is negative value in delta
        //          capMap += Double.min(regionCap, demand.value) // distribute cap to demand
        //          regionCap -= Double.min(regionCap, demand.value)
        
        // If there is still capacity
        // Assign the remaining capacity to the first entry
        
        // CASE 2: All entries in the deltaMap has positive values
        // Distribute cap = demand.value - delta_i
        // Distribute cap to the demand value of higher to lower priority service
//        else if (compareDouble(flag, 0D) > 0) {
            // capMap += Double.min(demand.value, regionCap)
            // regionCap -= Double.min(demand.value, regionCap)
//        }
        
        // resultMap = demandMap - deltaMap - capMap
    }

    private SortedMap<ServiceIdentifier<?>, Double> reverseSignElementWise(SortedMap<ServiceIdentifier<?>, Double> map) {
        SortedMap<ServiceIdentifier<?>, Double> result = new TreeMap<>(descendingPriority);
        
        for (Entry<ServiceIdentifier<?>, Double> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * -1.0);
        }
        
        return result;
    }

    private SortedMap<ServiceIdentifier<?>, Double> maxElementWise(SortedMap<ServiceIdentifier<?>, Double> map, double value) {
        SortedMap<ServiceIdentifier<?>, Double> result = new TreeMap<>(descendingPriority);
        
        for (Entry<ServiceIdentifier<?>, Double> entry : map.entrySet()) {
            result.put(entry.getKey(), Double.max(entry.getValue(), value));
        }
        
        return result;
    }

    private SortedMap<ServiceIdentifier<?>, Double> substractTwoMaps(SortedMap<ServiceIdentifier<?>, Double> mapOne, Map<ServiceIdentifier<?>, Double> mapTwo) {
        SortedMap<ServiceIdentifier<?>, Double> sortedMap = new TreeMap<>(new SortServiceByPriorityComparator());
        SortedSet<ServiceIdentifier<?>> services = new TreeSet<>(new SortServiceByPriorityComparator());
        services.addAll(mapOne.keySet());
        services.addAll(mapTwo.keySet());
        
        for (ServiceIdentifier<?> service : services) {
            sortedMap.put(service, mapOne.getOrDefault(service, 0D) - mapTwo.getOrDefault(service, 0D));
        }
        
        sortedMap.values().removeIf(v -> compareDouble(v, 0D) == 0);
        
        return sortedMap;
    }

    /**
     * REVIEWED
     * Compute new excess load map based on the the input load map and region capacity <br>
     * Keep higher priority service and push lower priority service away
     * @param inputMap
     * @param regionCapacity
     * @return
     */
    @SuppressWarnings("unused")
    private SortedMap<ServiceIdentifier<?>, Double> computeExcess(SortedMap<ServiceIdentifier<?>, Double> inputMap, double regionCapacity) {
        SortedMap<ServiceIdentifier<?>, Double> excessMap = new TreeMap<>(new SortServiceByPriorityComparator());

        double availCap = regionCapacity;
        
        // Traverse from higher to lower priority services
        // Keep service with higher priority before shed excess load
        for (Entry<ServiceIdentifier<?>, Double> entry : inputMap.entrySet()) {
            double load = entry.getValue();
            
            // If smaller than availCap, region can keep all of this service
            if (compareDouble(load, availCap) <= 0) {
                availCap -= load;
            }
            // If larger than availCap, region cannot keep all
            // Add the difference = load - availCap and this service to the excess load
            else {
                excessMap.put(entry.getKey(), load - availCap);
                availCap = 0D;
            }
        }
        
        return excessMap;
    }

    private SortedMap<ServiceIdentifier<?>, Double> runServerToClientBlock(
            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inputDemand,
            Set<RegionIdentifier> neighborSet) {
        
        SortedMap<ServiceIdentifier<?>, Double> totalSelfDemand = new TreeMap<>(new SortServiceByPriorityComparator()); 
        Map<RegionIdentifier, Set<FinalRCDiffMessageContent>> s2cMessageMap = new HashMap<>();
        
        // Loop through the server demand of new services
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : inputDemand.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> clientEntry : serviceEntry.getValue().entrySet()) {
                RegionIdentifier client = clientEntry.getKey();
                // I observe that in some first DCOP runs, the inferred demand doesn't have MapUtils.COMPUTE_ATTRIBUTE
                // Add this check to avoid null pointer exception
                if (!clientEntry.getValue().containsKey(MapUtils.COMPUTE_ATTRIBUTE)) {
                    continue;
                }
                
                double load = clientEntry.getValue().get(MapUtils.COMPUTE_ATTRIBUTE);
                
                // If self region is the client of this region => add to totalDemands
                if (client.equals(getRegionID())) {
                    updateKeyLoadMap(totalSelfDemand, serviceEntry.getKey(), load, true);
                    // Update self flow load map
                    updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), service, load, true);
                } 
                // Otherwise, send this demand to the neighbor with incoming network load
                else {                    
                    // Find neighbor and send the message here
                    FinalRCDiffServerToClient serverToClientTuple = FinalRCDiffServerToClient.of(getRegionID(), client, service, load);
                    RegionIdentifier neighbor = findNeighbor(getRegionID(), client, currentDcopRun, topology, getRegionID());            
                    if (neighbor != null) {
                        s2cMessageMap.computeIfAbsent(neighbor, k -> new HashSet<>()).add(serverToClientTuple);
                    }
                }
            }
        }
        
        // Loop through SERVER_TO_CLIENT messages
        for (Entry<RegionIdentifier, Set<FinalRCDiffMessageContent>> entry : receivedMessageMap.get(SERVER_TO_CLIENT).entrySet()) {
            for (FinalRCDiffMessageContent msgContent : entry.getValue()) {
                FinalRCDiffServerToClient s2cMsg = (FinalRCDiffServerToClient) msgContent;                
                // Add to self demand
                if (s2cMsg.getClient().equals(getRegionID())) {
                    updateKeyLoadMap(totalSelfDemand, s2cMsg.getService(), s2cMsg.getLoad(), true);
                    // Update self flow load map
                    updateKeyKeyLoadMap(getFlowLoadMap(), getRegionID(), s2cMsg.getService(), s2cMsg.getLoad(), true);
                }
                else {
                    RegionIdentifier neighbor = findNeighbor(s2cMsg.getServer(), s2cMsg.getClient(), currentDcopRun, topology, entry.getKey());       
                    if (neighbor != null) {
                        s2cMessageMap.computeIfAbsent(neighbor, k -> new HashSet<>()).add(FinalRCDiffServerToClient.deepCopy(s2cMsg));
                    }
                }
            }
        }
        
        clearObsoleteMessages(messageMapToSend, SERVER_TO_CLIENT);
        for (Entry<RegionIdentifier, Set<FinalRCDiffMessageContent>> entry : s2cMessageMap.entrySet()) {            
            for (FinalRCDiffMessageContent msg : entry.getValue()) {
                addMessage(entry.getKey(), SERVER_TO_CLIENT, msg);
            }
        }
        
        return totalSelfDemand;
    }

    private void runGblock(Map<ServiceIdentifier<?>, Double> excessLoadMap, Set<RegionIdentifier> neighborSet) {
        // If root, send G message with hop = 0
        if (compareDouble(sumValues(excessLoadMap), 0D) > 0) {
            parentRequest = FinalRCDiffRequest.of(getRegionID(), excessLoadMap, Integer.MAX_VALUE);
            parent = null;
            root = getRegionID();
            
            clearObsoleteMessages(messageMapToSend, G);
            for (RegionIdentifier neighbor : neighborSet) {
                addMessage(neighbor, G, FinalRCDiffRequest.of(getRegionID(), excessLoadMap, 0));
            }
            
            LOGGER.info("DCOP Run {} Region {} is a root with excessLoadMap {}", currentDcopRun, getRegionID(), excessLoadMap);
        }
        else {
//            FinalRCDiffRequest tempRequest = FinalRCDiffRequest.emptyRequest();
            
            LOGGER.info("DCOP Run {} Region {} is not root with excessLoadMap {}", currentDcopRun, getRegionID(), excessLoadMap);
            
            LOGGER.info("DCOP Run {} Region {} is not root with messages type G {}", currentDcopRun, getRegionID(), receivedMessageMap.get(G));
            
            // Maintain the current minHop
            // Maintain the set of parent request with that min hop value
            
            // If find a request with smaller hop, then clear the set, add this request to the set
            // If find a request with same hop, add this request to the set
            
            // After for loop, check if minHop = parent.getHop?
            // If yes, check among request if there is any request from the parent, if yes, then pick this request (not changing tree)
            
            
            SortedMap<Integer, Set<FinalRCDiffRequest>> hopReqMap = new TreeMap<>(); // maintain the set of requests for each hop count
            Map<FinalRCDiffRequest, RegionIdentifier> reqSenderMap = new HashMap<>(); // know the sender
                        
            for (Entry<RegionIdentifier, Set<FinalRCDiffMessageContent>> entry : receivedMessageMap.get(G).entrySet()) {
                RegionIdentifier sender = entry.getKey();
                
                for (FinalRCDiffMessageContent msgContent : entry.getValue()) {
                    FinalRCDiffRequest requestFromNeighbor = (FinalRCDiffRequest) msgContent;
                    // Do not consider request with self region as root since it is obsolete
                    // Do not consider empty request
                    if (!requestFromNeighbor.isEmptyRequest() && !getRegionID().equals(requestFromNeighbor.getRoot())) {
                        hopReqMap.computeIfAbsent(requestFromNeighbor.getHop(), k -> new HashSet<>()).add(requestFromNeighbor);
                        reqSenderMap.put(requestFromNeighbor, sender);
                    }
                    
                    // Only consider requests with total load > 0
                    // Link quality: Do not modify the comparison of number of hops here since region needs to pick the demand of the closet region
                    // Note that he value of hops will keep increasing
                    
                    // Do not forward the request with self region as the root (it should be an obsolete request)
//                    if (requestFromNeighbor.getHop() < tempRequest.getHop() &&
//                            compareDouble(sumValues(requestFromNeighbor.getLoadMap()), 0D) > 0 && )) {
//                        tempRequest = FinalRCDiffRequest.deepCopy(requestFromNeighbor);
//                        parent = sender;
//                        root = requestFromNeighbor.getRoot();
//                    }
                }
            }
                        
            // choose the request with the same parent and same root
            // then choose the request with the same parent
            boolean found = false;
            
            for (Entry<Integer, Set<FinalRCDiffRequest>> entry : hopReqMap.entrySet()) {
                // Find the request with the same parent and same roots
                for (FinalRCDiffRequest req : entry.getValue()) {
                    if (reqSenderMap.get(req).equals(parent) && req.getRoot().equals(root)) {
                        parentRequest = FinalRCDiffRequest.deepCopy(req);
                        found = true;
                        break;
                    }
                }

                // If not found, find the request with the same parent
                if (!found) {
                    for (FinalRCDiffRequest req : entry.getValue()) {
                        if (reqSenderMap.get(req).equals(parent)) {
                            parentRequest = FinalRCDiffRequest.deepCopy(req);
                            root = req.getRoot();
                            found = true;
                            break;
                        }
                    }
                }
                
                // If not found, get the first request
                if (!found) {
                    for (FinalRCDiffRequest req : entry.getValue()) {
                        parentRequest = FinalRCDiffRequest.deepCopy(req);
                        parent = reqSenderMap.get(req);
                        root = req.getRoot();
                        found = true;
                        break;
                    }
                }
                
                // Only loop for the smallest hop count     
                break;
            }
            
            // If can't find such parent request, then send empty request instead
            if (hopReqMap.isEmpty()) {
                parentRequest = FinalRCDiffRequest.emptyRequest();
                parent = null;
                root = null;
            }
            
            
            clearObsoleteMessages(messageMapToSend, G);
            for (RegionIdentifier neighbor : neighborSet) {
                // If parentRequest is empty, then forwarding empty request
                if (parentRequest.isEmptyRequest()) {
                    addMessage(neighbor, G, FinalRCDiffRequest.emptyRequest());
                }
                // Only increase the hop count if parent request is not empty
                // Link quality: the value of hops is increasing here and keeps increasing
                // Note that regions will pick the request with the smallest hop
                else {
                    addMessage(neighbor, G, FinalRCDiffRequest.of(parentRequest.getRoot(), parentRequest.getLoadMap(), parentRequest.getHop() + 1));
                }
            }
        }
        
        LOGGER.info("DCOP Run {} Region {} has parent request {} with parent {}", currentDcopRun, getRegionID(), parentRequest, parent);
    }
    
    private void runCblock(FinalRCDiffRequest prTuple, RegionIdentifier parentRegion, Set<RegionIdentifier> neighborSet, double availCap, Set<FinalRCDiffPlan> inputs) {
        childrenProposal.clear();
        
        // Not a root region
        if (!prTuple.isEmptyRequest() && !prTuple.isRoot(getRegionID())) {            
//            FinalRCDiffProposal selfProposal = FinalRCDiffProposal.of(getRegionID(), root, getRegionID(), prTuple.getHop(), Math.min(sumValues(prTuple.getLoadMap()), availCap));
//          childrenProposal.computeIfAbsent(selfProposal.getHop(), k -> new HashSet<>()).add(selfProposal);

            double delay = getDelay(parent);
            double datarate = getDatarate(parent);
            
            // Link quality: create tuple for self region here
            FinalRCDiffTuple selfTuple = FinalRCDiffTuple.of(delay, datarate, getRegionID(), prTuple.getHop());
            
            // Proposal cap
            // Increase availCap by the history of accepted input
            // Works for one data center
            for (FinalRCDiffPlan input : inputs) {
                if (input.getRoot().equals(prTuple.getRoot()) && input.getSink().equals(getRegionID())) {
                    availCap += input.getLoad();
                }
            }
            
            FinalRCDiffProposal selfProposal = FinalRCDiffProposal.of(getRegionID(), root, getRegionID(), selfTuple, Math.min(sumValues(prTuple.getLoadMap()), availCap));

//            childrenProposal.computeIfAbsent(selfTuple, k -> new HashSet<>()).add(selfProposal);
            childrenProposal.add(selfProposal);
        }
        
        // Read all C messages and add all of them to the childProposal map
        for (Entry<RegionIdentifier, Set<FinalRCDiffMessageContent>> entry : receivedMessageMap.get(C).entrySet()) {
            for (FinalRCDiffMessageContent msgContent : entry.getValue()) {
                FinalRCDiffProposal proposal = (FinalRCDiffProposal) msgContent;
                
                // Ignore proposal with 0 capacity
                if (compareDouble(proposal.getProposalCapacity(), 0D) == 0) {
                    continue;
                }
                
                // Only accepts proposal for the current root of this region or for this region (when this region is a root)
                if (!proposal.isEmptyProposal() && proposal.getRoot() != null && proposal.getRoot().equals(root)) {
                    childrenProposal.add(proposal);
                }
            }
        }
        
        LOGGER.info("DCOP Run {} Region {} has childrenProposal before trimming {} with parentRequest {}", currentDcopRun, getRegionID(), childrenProposal, prTuple);
        
        // Update the rate and datarate of key and proposal from children with the delay/datarate of self region with parent
        updateDelayDatarate(childrenProposal);
        SortedSet<FinalRCDiffProposal> modifiedProposal = removeDuplicateAndTrim(childrenProposal, sumValues(prTuple.getLoadMap()));
        
        LOGGER.info("DCOP Run {} Region {} has childrenProposal after trimming {}", currentDcopRun, getRegionID(), modifiedProposal);
        
        if (!prTuple.isRoot(getRegionID())) {
            modifiedProposal = replaceFirstElementWithSelfRegion(modifiedProposal);
        }
        
        clearObsoleteMessages(messageMapToSend, C);
        for (RegionIdentifier neighbor : neighborSet) {
            if (neighbor.equals(parentRegion)) {
                for (FinalRCDiffProposal childProposal : modifiedProposal) {
                        addMessage(parentRegion, C, childProposal);
                }
            }
            else {
                addMessage(neighbor, C, FinalRCDiffProposal.emptyProposal());
            }
        }
    }

    /**
     * delay = max(delayWithParent, currentDelay)
     * datarate = min(datarateWithParent, currentDatarate)
     * @param cProposal with updated delay and datarates
     */
    private void updateDelayDatarate(SortedSet<FinalRCDiffProposal> cProposal) {
        SortedSet<FinalRCDiffProposal> tempProposal = new TreeSet<>(sortProposal);
        tempProposal.addAll(cProposal);
        
        cProposal.clear();
        for (FinalRCDiffProposal entry : tempProposal) {
            double delay = Math.max(getDelay(parent), entry.getTuple().getDelay());
            double datarate = Math.min(getDatarate(parent), entry.getTuple().getDatarate());
            
            FinalRCDiffTuple updatedTuple = FinalRCDiffTuple.modifyRate(entry.getTuple(), delay, datarate);
            FinalRCDiffProposal updatedProposal = FinalRCDiffProposal.replaceTuple(entry, updatedTuple);
            cProposal.add(updatedProposal);
        }
    }

    private void runClearblock(Set<FinalRCDiffPlan> outputSet, Set<FinalRCDiffPlan> inputSet, FinalRCDiffRequest parentRequestTuple,
            SortedMap<ServiceIdentifier<?>, Double> excessLoadMap, Set<RegionIdentifier> neighborSet, RegionIdentifier parentRegion, RegionIdentifier root) {
        Map<RegionIdentifier, Set<FinalRCDiffPlan>> receivedOutputPlan = new HashMap<>();
        Map<RegionIdentifier, Set<FinalRCDiffPlan>> receivedInputPlan = new HashMap<>();
        
        Set<FinalRCDiffPlan> clonedInputSet = new HashSet<>();
        Set<FinalRCDiffPlan> clonedOutputSet = new HashSet<>();
        
        // De-committing input plan if self region is overloaded
        if (sumValues(excessLoadMap) > 0) {
            double totalDecommitedLoad = 0;
            // Avoid raising error when looping and modifying the set at the same time
            clonedInputSet.clear();
            clonedInputSet.addAll(inputSet);
            for (FinalRCDiffPlan inputPlan : clonedInputSet) {                
                if (compareDouble(sumValues(excessLoadMap), totalDecommitedLoad) <= 0) {
                    break;
                }
                else if (getRegionID().equals(inputPlan.getSink())) {
                    LOGGER.info("DCOP Run {} Region {} removes input tuple {} from the inputSet {} since it now has excess load {}", currentDcopRun, getRegionID(), inputPlan, inputSet, excessLoadMap);       
                    inputSet.remove(inputPlan);                    
                    
                    totalDecommitedLoad += inputPlan.getLoad();
                }
            }
        }
        
        // Delete obsolete output plan where self region is root, when region is overloaded again
        if (sumValues(excessLoadMap) > 0) {
            clonedOutputSet.clear();
            clonedOutputSet.addAll(outputSet);
            for (FinalRCDiffPlan outputPlan : clonedOutputSet) {
                if (getRegionID().equals(outputPlan.getRoot())) {
                    LOGGER.info("DCOP Run {} Region {} removes output tuple {} from the outputSet {} since it has excess load map {} and wants to recompute the plans", currentDcopRun, getRegionID(), outputPlan, outputSet, excessLoadMap);       
                    outputSet.remove(outputPlan);
                }
            }
        }
        
        // Read CLEAR messages from neighbor
        for (Entry<RegionIdentifier, Set<FinalRCDiffMessageContent>> entry : receivedMessageMap.get(CLR).entrySet()) {
            RegionIdentifier neighbor = entry.getKey();
            
            Set<FinalRCDiffPlan> outputTuples = getTuples(entry.getValue(), neighbor, getRegionID(), PlanType.OUTPUT);            
            Set<FinalRCDiffPlan> inputTuples = getTuples(entry.getValue(), getRegionID(), neighbor, PlanType.INPUT);

            receivedOutputPlan.computeIfAbsent(neighbor, k -> new HashSet<>()).addAll(outputTuples);
            receivedInputPlan.computeIfAbsent(neighbor, k -> new HashSet<>()).addAll(inputTuples);
        }
        
        LOGGER.info("DCOP Run {} Region {} has receivedOutputPlan {}", currentDcopRun, getRegionID(), receivedOutputPlan);       
        
        LOGGER.info("DCOP Run {} Region {} has receivedInputPlan {}", currentDcopRun, getRegionID(), receivedInputPlan);       
        
        // Loop over self input tuples
        clonedInputSet.clear();
        clonedInputSet.addAll(inputSet);
        for (FinalRCDiffPlan selfInput : clonedInputSet) {
            RegionIdentifier sender = selfInput.getSender();
            FinalRCDiffPlan outputFromSender = null;
            
            if (neighborSet.contains(sender)) {
                outputFromSender = getTuple(receivedOutputPlan.getOrDefault(sender, new HashSet<>()), selfInput.getRoot(), selfInput.getSink(), sender, getRegionID(), selfInput.getService(), selfInput.getLoad());
            }
            
            // If the parent is disconnected and outputFromParent is null (updated by parent)
            // Then remove the input for this parent
            if (!neighborSet.contains(sender) || outputFromSender == null) {
                // If there was no timer for this outputFromSender, start timer with current time
                // And ignore
                if (!outputTimer.containsKey(outputFromSender)) {
                    outputTimer.put(outputFromSender, LocalDateTime.now());
                    continue;
                }
                // Check the duration
                else {
                    LocalDateTime stopTime = outputTimer.get(outputFromSender).plus(timerThreshold);
                    
                    if (LocalDateTime.now().isAfter(stopTime)) {
                        LOGGER.info("DCOP Run {} Region {} removes input tuple {} from the inputSet {} since the output plan from sender {} is obsolete and now null", currentDcopRun, getRegionID(), selfInput, inputSet, sender);       
                        inputSet.remove(selfInput);
                        
//                        LOGGER.info("DCOP Run {} Region {} removes output tuple {} from the outputSet {}", currentDcopRun, getRegionID(), selfInput, outputSet);       
//                        outputSet.remove(selfInput); // NEW LINE
          
                        // If the region is not a sink, then continue to remove all output tuples to children for this (root, sink)
                        if (!selfInput.getSink().equals(getRegionID())) {
                            FinalRCDiffPlan outputToNeighbor = getTuple(outputSet, selfInput.getRoot(), selfInput.getSink(), getRegionID(), null, selfInput.getService(), selfInput.getLoad());
                           
                            LOGGER.info("DCOP Run {} Region {} removes output tuple {} from the outputSet {} since the output plan from sender {} is not obsolete and now null", currentDcopRun, getRegionID(), outputToNeighbor, outputSet, sender);       
                            outputSet.remove(outputToNeighbor);
                        }
                        outputTimer.remove(outputFromSender);
                    }
                }
            }            
        }
        
        // Loop over self output tuples
        clonedOutputSet.clear();
        clonedOutputSet.addAll(outputSet);
        for (FinalRCDiffPlan selfOutput : clonedOutputSet) {
            // Only consider output where self region is the sender
            if (!getRegionID().equals(selfOutput.getSender())) {continue;} // NEW LINE
            
            RegionIdentifier receiver = selfOutput.getReceiver();        
            
            FinalRCDiffPlan inputToReceiver = null;

            if (neighborSet.contains(receiver)) {
                inputToReceiver = getTuple(receivedInputPlan.getOrDefault(receiver, new HashSet<>()), selfOutput.getRoot(), selfOutput.getSink(), getRegionID(), receiver, selfOutput.getService(), selfOutput.getLoad());
            }
            
            // If the child is disconnected or the child doesn't want to receive this input
            // Remove from the output tuple
            // Check if receiver has received the output, but the input is null => child rejects the output 
            if (!neighborSet.contains(receiver) || inputToReceiver == null) {
                // If there was no timer for this outputFromSender, start timer with current time
                // And ignore
                if (!inputTimer.containsKey(inputToReceiver)) {
                    inputTimer.put(inputToReceiver, LocalDateTime.now());
                    continue;
                }
                else {
                    LocalDateTime stopTime = inputTimer.get(inputToReceiver).plus(timerThreshold);
                    
                    if (LocalDateTime.now().isAfter(stopTime)) {
                        LOGGER.info("DCOP Run {} Region {} removes output tuple {} from the outputSet {} since the child {} doesn't accept the plan", currentDcopRun, getRegionID(), selfOutput, outputSet, receiver);       
                        
                        outputSet.remove(selfOutput);
                        
                        // If self region is not root, then remove the input tuple since child doesn't want to receive this input
                        if (!selfOutput.getRoot().equals(getRegionID())) {
                            FinalRCDiffPlan intputFromNeighbor = getTuple(inputSet, selfOutput.getRoot(), selfOutput.getSink(), null, getRegionID(), selfOutput.getService(), selfOutput.getLoad());
                            LOGGER.info("DCOP Run {} Region {} removes input tuple {} from the inputSet {} since the child {} doesn't accept the plan", currentDcopRun, getRegionID(), intputFromNeighbor, inputSet);       

                            inputSet.remove(intputFromNeighbor);
                            
                        }
                        inputTimer.remove(inputToReceiver);
                    }
                }
            }
        }
        
        // Shedding excess demand to sink
        if (sumValues(excessLoadMap) > 0) {
//            SortedMap<Integer, Set<FinalRCDiffProposal>> clonedCP = new TreeMap<>(childrenProposal);
//            SortedMap<Integer, Set<FinalRCDiffProposal>> loopCP = new TreeMap<>(childrenProposal);
            SortedSet<FinalRCDiffProposal> clonedCP = new TreeSet<>(sortProposal);
            clonedCP.addAll(childrenProposal);        
            SortedSet<FinalRCDiffProposal> loopCP = new TreeSet<>(sortProposal);
            loopCP.addAll(childrenProposal);
            
            // Loop through the excess load map from higher to lower priority service
            for (Entry<ServiceIdentifier<?>, Double> entry : excessLoadMap.entrySet()) {
                ServiceIdentifier<?> service = entry.getKey();
                double excess = entry.getValue();
                
                // Update children proposal with the clonedCP for each service since the proposal will be modified by the previous service
                loopCP.clear();
                LOGGER.info("DCOP Run {} Region {} has ordered child proposals {}", currentDcopRun, getRegionID(), clonedCP);       
                for (FinalRCDiffProposal cloneEntry : clonedCP) {
                    loopCP.add(FinalRCDiffProposal.deepCopy(cloneEntry));
                }
                
                // Loop through the proposals
                for (FinalRCDiffProposal proposal : loopCP) { 
                        // Only consider the proposal where root region = self region ID (which is also a root)
                        if (getRegionID().equals(proposal.getRoot())) {
                            if (compareDouble(excess, 0D) > 0) {
                                // Shed all load to the sink if it is the data center
                                double shedLoad = proposal.getSink().equals(defaultRegion(service)) ? excess : Math.min(proposal.getProposalCapacity(), excess);
                                
                                FinalRCDiffPlan outputPlan = FinalRCDiffPlan.of(getRegionID(), shedLoad, proposal.getSink(), getRegionID(), proposal.getSender(), service, PlanType.OUTPUT);
                                LOGGER.info("DCOP Run {} Region {} adds output tuple {} to the outputSet {} from the proposal {} for the excess load {}", currentDcopRun, getRegionID(), outputPlan, outputSet, proposal, excessLoadMap);       
                                outputSet.add(outputPlan);
                                                                
                                // Only remove or modify the proposal if this sink is not the default region of this services
                                if (!proposal.getSink().equals(defaultRegion(service))) {
                                    // Modify or remove the proposal after using
                                    clonedCP.remove(proposal);
                                    
                                    // Add the modified proposal if not using all proposal capacity
                                    if (compareDouble(shedLoad, proposal.getProposalCapacity()) < 0) {
                                        clonedCP.add(FinalRCDiffProposal.replaceLoad(proposal, proposal.getProposalCapacity() - shedLoad));
                                    }
                                }
                               
                                excess -= shedLoad;
                            }
                            else {
                                break;
                            }
                        }
                }
            }
        }
        
        // Read output from the parent
        for (FinalRCDiffPlan receivedOutputFromParent : receivedOutputPlan.getOrDefault(parentRegion, new HashSet<>())) {
            FinalRCDiffPlan inputFromParent = getTuple(inputSet, receivedOutputFromParent.getRoot(), receivedOutputFromParent.getSink(), parentRegion, getRegionID(), receivedOutputFromParent.getService(), receivedOutputFromParent.getLoad());
            
            // TODO: only accept this input if the region has enough capacity
            // So far, the region only uses capacity for the proposal phase
            // One region might propose the same thing to different roots => Totally true in G block
            
            // If not already accepted this input, then accept it
            if (inputFromParent == null) {    
                FinalRCDiffPlan inputPlan = FinalRCDiffPlan.deepCopy(receivedOutputFromParent, PlanType.INPUT);
                
                // If this region is not the sink
                // Then just add the input to input set as the confirmation
                if (!getRegionID().equals(receivedOutputFromParent.getSink())) {
                    LOGGER.info("DCOP Run {} Region {} adds input tuple {} to the inputSet {} since it is not a sink and it hasn't accepted this plan before from the parent {}", currentDcopRun, getRegionID(), inputPlan, inputSet, parentRegion);
                    inputSet.add(inputPlan);
                }
                else if (compareDouble(availableCapacity, inputPlan.getLoad()) >= 0) {
                    LOGGER.info("DCOP Run {} Region {} adds input tuple {} to the inputSet {} since it hasn't accepted this plan before from the parent {}", currentDcopRun, getRegionID(), inputPlan, inputSet, parentRegion);
                    inputSet.add(inputPlan);
                    availableCapacity -= inputPlan.getLoad();
                }

//                // Add parent's output to me so that I can send it back to the parent
//                // The parent needs to see this output so that the parent knows I have already received the output
//                LOGGER.info("DCOP Run {} Region {} adds output tuple {} to the outputSet {}", currentDcopRun, getRegionID(), receivedOutputFromParent, outputSet);
//                outputSet.add(receivedOutputFromParent);
                
                if (!getRegionID().equals(receivedOutputFromParent.getSink())) {
                    RegionIdentifier child = getIntermediateChild(childrenProposal, receivedOutputFromParent.getRoot(), receivedOutputFromParent.getSink());
                    
                    // Change in child proposal => child = null
                    if (child != null) {                
                        FinalRCDiffPlan output = FinalRCDiffPlan.of(receivedOutputFromParent.getRoot(), receivedOutputFromParent.getLoad(), receivedOutputFromParent.getSink(), getRegionID(), child, receivedOutputFromParent.getService(), PlanType.OUTPUT);
                        LOGGER.info("DCOP Run {} Region {} adds output tuple {} to the outputSet {} for the child {} from the childrenProposal {}", currentDcopRun, getRegionID(), output, outputSet, child, childrenProposal);       
                        outputSet.add(output);
                    }
                    else {
                        LOGGER.info("DCOP Run {} Region {} cannot find child from childrenProposal {} for the root {} and sink {} from the childrenProposal {}", currentDcopRun, getRegionID(), childrenProposal, receivedOutputFromParent.getRoot(), receivedOutputFromParent.getSink(), childrenProposal); 
                    }
                 }
            }
        }
        
        // Update incoming flowLoadMap for sink regions
        for (FinalRCDiffPlan input : inputSet) {
//            if (getRegionID().equals(input.getReceiver())) {
//                updateKeyKeyLoadMap(getFlowLoadMap(), input.getSender(), input.getService(), input.getLoad(), true);
//            }
            
            if (getRegionID().equals(input.getSink())) {
                // The incoming load is from the root of this load
                updateKeyKeyLoadMap(getFlowLoadMap(), input.getRoot(), input.getService(), input.getLoad(), true);
            }

        }

        // Update outgoing flowLoadMap for root regions
        for (FinalRCDiffPlan output : outputSet) {
//            if (getRegionID().equals(output.getSender())) {
//                updateKeyKeyLoadMap(getFlowLoadMap(), output.getSink(), output.getService(), -output.getLoad(), true);
//            }
            
            if (getRegionID().equals(output.getRoot()) && getRegionID().equals(output.getSender())) {
                // The outgoing load is for the sink of this load
                updateKeyKeyLoadMap(getFlowLoadMap(), output.getSink(), output.getService(), -output.getLoad(), true);
            }
        }
        
        LOGGER.info("DCOP Run {} Region {} has flowMap Clear block {}", currentDcopRun, getRegionID(), getFlowLoadMap());
        
        Map<ServiceIdentifier<?>, Double> deltaPlus = new HashMap<>(); // receiving load
        Map<ServiceIdentifier<?>, Double> deltaMinus = new HashMap<>(); // sending load
        
        LOGGER.info("DCOP Run {} Region {} has inputSet {}", currentDcopRun, getRegionID(), inputSet);       
        LOGGER.info("DCOP Run {} Region {} has outputSet {}", currentDcopRun, getRegionID(), outputSet);   
        

        for (FinalRCDiffPlan input : inputSet) {
//            if (input.getSink().equals(getRegionID()) && !input.getRoot().equals(root) && !input.getSink().equals(defaultRegion(input.getService()))) {
            if (input.getSink().equals(getRegionID()) && !getRegionID().equals(root) && !input.getSink().equals(defaultRegion(input.getService()))) {
                deltaMinus.merge(input.getService(), -input.getLoad(), Double::sum);
//                deltaMinus += input.getLoad();
            }
        }
        
        for (FinalRCDiffPlan output : outputSet) {
            if (output.getRoot().equals(getRegionID())) {
                deltaPlus.merge(output.getService(), output.getLoad(), Double::sum);
//                deltaPlus += output.getLoad();
            }
        }
        
        Set<ServiceIdentifier<?>> services = new HashSet<>();
        services.addAll(excessLoadMap.keySet());
        services.addAll(deltaPlus.keySet());
        services.addAll(deltaMinus.keySet());
        
        delta.clear();
        // If there is excess load (root region)
        if (compareDouble(sumValues(excessLoadMap), 0D) > 0) {
            // If has shed all values in the current run
            if (compareDouble(sumValues(deltaPlus), sumValues(excessLoadMap)) == 0) {
                // Increase round count
                roundCount++;
                // If round count == T => shed all loads in the last T rounds
                // Then put all to delta
                if (roundCount == T) {
                    delta.putAll(deltaPlus);
                }
            }
            // Else reset round count
            else {
                roundCount = 0;
            }
        }
        else {
            for (ServiceIdentifier<?> service : services) {
                delta.put(service, deltaPlus.getOrDefault(service, 0D) + deltaMinus.getOrDefault(service, 0D));
            }
        }
        
        LOGGER.info("DCOP Run {} Region {} has delta minus {}", currentDcopRun, getRegionID(), deltaMinus);       
        LOGGER.info("DCOP Run {} Region {} has delta plus {}", currentDcopRun, getRegionID(), deltaPlus);
        LOGGER.info("DCOP Run {} Region {} has delta {}", currentDcopRun, getRegionID(), delta); 
        
        Set<FinalRCDiffPlan> unionInputOutput = new HashSet<>(inputSet);
        unionInputOutput.addAll(outputSet);
        
        clearObsoleteMessages(messageMapToSend, CLR);
        for (RegionIdentifier neighbor : sortedNeighbors) {
            for (FinalRCDiffPlan plan : unionInputOutput) {
                addMessage(neighbor, CLR, plan);
            }
        }   
    }
    
    /**
     * For each proposal, replace the sender with self region ID
     * @param map is the map of proposal
     * @return the new map with sender = region ID
     */
    private SortedSet<FinalRCDiffProposal> replaceFirstElementWithSelfRegion(SortedSet<FinalRCDiffProposal> set) {
        SortedSet<FinalRCDiffProposal> result = new TreeSet<>(sortProposal);
        
        for (FinalRCDiffProposal proposal : set) {            
            result.add(FinalRCDiffProposal.replaceSender(proposal, getRegionID()));
        }
        
        return result;
    }
    
    private Set<FinalRCDiffPlan> getTuples(Set<FinalRCDiffMessageContent> tuples, RegionIdentifier sender, RegionIdentifier receiver, PlanType type) {
        Set<FinalRCDiffPlan> results = new HashSet<>();
        for (FinalRCDiffMessageContent tuple : tuples) {
            FinalRCDiffPlan plan = (FinalRCDiffPlan) tuple;
            if (plan.getType() == type) {
                if (plan.getSender().equals(sender) && plan.getReceiver().equals(receiver)) {
                    results.add(FinalRCDiffPlan.deepCopy(plan));
                }
            }
        }
        
        return results;
    }

    private RegionIdentifier getIntermediateChild(SortedSet<FinalRCDiffProposal> childrenProposalMap, RegionIdentifier rootRegion, RegionIdentifier sinkRegion) {
        for (FinalRCDiffProposal childProposal : childrenProposalMap) {
                if (childProposal.getRoot().equals(rootRegion) && childProposal.getSink().equals(sinkRegion)) {
                    return childProposal.getSender();
                }            
        }
        
        return null;
    }

//    private FinalRCDiffPlan getTuple(Set<FinalRCDiffPlan> plans, RegionIdentifier root, RegionIdentifier sink, RegionIdentifier sender, RegionIdentifier receiver, ServiceIdentifier<?> service) {
//        for (FinalRCDiffPlan plan : plans) {
//            if (plan.getRoot().equals(root) && plan.getSink().equals(sink) && plan.getService().equals(service) &&
//                    (   (sender == null && plan.getReceiver().equals(receiver)) 
//                    || (plan.getSender().equals(sender) && receiver == null) 
//                    || (plan.getSender().equals(sender) && plan.getReceiver().equals(receiver))
//                    )
//               ) {
//                return plan;
//            }
//        }
//            
//        return null;
//    }
    
    private FinalRCDiffPlan getTuple(Set<FinalRCDiffPlan> plans, RegionIdentifier root, RegionIdentifier sink, RegionIdentifier sender, RegionIdentifier receiver, ServiceIdentifier<?> service, double load) {
        for (FinalRCDiffPlan plan : plans) {
            if (plan.getRoot().equals(root) && plan.getSink().equals(sink) && plan.getService().equals(service) && compareDouble(plan.getLoad(), load) == 0 &&
                    (   (sender == null && plan.getReceiver().equals(receiver)) 
                    || (plan.getSender().equals(sender) && receiver == null) 
                    || (plan.getSender().equals(sender) && plan.getReceiver().equals(receiver))
                    )
               ) {
                return plan;
            }
        }
            
        return null;
    }
    
    private void clearObsoleteMessages(Map<RegionIdentifier, FinalRCDiffDcopMessage> messageMapToSendMap, FinalRCDiffMessageType msgType) {
        for (RegionIdentifier neighbor : sortedNeighbors) {
            FinalRCDiffDcopMessage dcopMsg = messageMapToSendMap.get(neighbor);
            dcopMsg.clearMessages(msgType);
        }
        
    }

    /**
     * Trim the proposal map such that sum of proposed capacity is <= sumDemand <br>
     * Keep proposals with smaller hop count
     * @param childrenProposalSet .
     * @param sumDemand .
     * @return new set containing retained proposals
     */
    private SortedSet<FinalRCDiffProposal> removeDuplicateAndTrim(SortedSet<FinalRCDiffProposal> childrenProposalMap, double sumDemand) {
        SortedSet<FinalRCDiffProposal> trimmedProposals = new TreeSet<>(sortProposal);
        
        double totalProposedCap = 0D;
        for (FinalRCDiffProposal proposal : childrenProposalMap) {
            double proposalCap = proposal.getProposalCapacity();

            // If totalCap + cap < sumDemand, then add this tuple and increase the total cap
            if (compareDouble(totalProposedCap + proposalCap, sumDemand) <= 0) {
                trimmedProposals.add(FinalRCDiffProposal.deepCopy(proposal));
                totalProposedCap += proposalCap;
                LOGGER.info("DCOP Run {} Region {} adds proposal {} for trimmedProposals {}", currentDcopRun, getRegionID(), proposal, trimmedProposals);
            }
            // If totalCap + cap >= sumDemand, then add the tuple with difference to make sure totalProposeCap = sumDemand, then return the trimmed proposals
            else {
                FinalRCDiffProposal modifiedProposal = FinalRCDiffProposal.replaceLoad(proposal, sumDemand - totalProposedCap);
                trimmedProposals.add(modifiedProposal);
                LOGGER.info("DCOP Run {} Region {} adds proposal {} for trimmedProposals {}", currentDcopRun, getRegionID(), modifiedProposal, trimmedProposals);

                return trimmedProposals;
            }
            
        }
        
        return trimmedProposals;
    }

    private void readMessages(int dcopRun) {
        receivedMessageMap.clear();                
        
        for (FinalRCDiffMessageType msgType : FinalRCDiffMessageType.values()) {
            receivedMessageMap.put(msgType, new TreeMap<>(new SortRegionIdentifierComparator()));
        }

        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();
        
        LOGGER.info("DCOP Run {} Region {} has delta {}", currentDcopRun, getRegionID(), delta);
        
        final long start = System.currentTimeMillis();                     
        for (Entry<RegionIdentifier, DcopSharedInformation> infoEntry : allSharedInformation.entrySet()) {
//            LOGGER.info("DCOP Run {} Region {} is looking for messages from neighbors: {} in: {}", dcopRun, getRegionID(), getNeighborSet(), allSharedInformation);
            
            RegionIdentifier neighborSender = infoEntry.getKey();
            if (infoEntry.getValue() != null) {
                DcopReceiverMessage abstractMsgMap = infoEntry.getValue().getAsynchronousMessage();
                
                if (abstractMsgMap != null) {                    
                    if (abstractMsgMap.isSentTo(getRegionID()) && abstractMsgMap.getIteration() == dcopRun) {
                        GeneralDcopMessage abstractMessage = abstractMsgMap.getMessageForThisReceiver(getRegionID());
                        
                        if (abstractMessage != null) {
                            FinalRCDiffDcopMessage dcopMsg = (FinalRCDiffDcopMessage) abstractMessage;
                            for (Entry<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> msgEntry : dcopMsg.getMessageMap().entrySet()) {
                                SortedMap<RegionIdentifier, Set<FinalRCDiffMessageContent>> map = receivedMessageMap.get(msgEntry.getKey());
                                map.computeIfAbsent(neighborSender, k -> new HashSet<>()).addAll(msgEntry.getValue());
                                LOGGER.info("DCOP Run {} Region {} receives message from Region {} type {}: {}", dcopRun, getRegionID(), neighborSender, msgEntry.getKey(), msgEntry.getValue());       
                            }
                        }
                    }
                }
            }
        }
        
        final long end = System.currentTimeMillis();
        LOGGER.info("DCOP Run {} Region {} waits for messages took {} ms", dcopRun, getRegionID(), (end - start));
    }
    
    private <A> void addMessage(RegionIdentifier receiver, FinalRCDiffMessageType messageType, FinalRCDiffMessageContent messageContent) {        
        LOGGER.info("DCOP Run {} Region {} sends message to Region {} type {}: {}", currentDcopRun, getRegionID(), receiver, messageType, messageContent);
        FinalRCDiffDcopMessage messageToSend = messageMapToSend.get(receiver);
        messageToSend.addMessage(messageType, messageContent);       
    }
    
    /**
     * Send message to parent, children and other neighbors
     * Clear the messageMap after sending
     * @param iteration
     */
    private void sendMessages() {
        DcopReceiverMessage modularRcdiffMsgPerIteration = inbox.getAsynchronousMessage();
                
        for (Entry<RegionIdentifier, FinalRCDiffDcopMessage> entry : messageMapToSend.entrySet()) {
            RegionIdentifier receiver = entry.getKey();
            FinalRCDiffDcopMessage modularRcdiffMessage = entry.getValue();
            modularRcdiffMsgPerIteration.setMessageToTheReceiver(receiver, modularRcdiffMessage);
        }
        inbox.setAsynchronousMessage(modularRcdiffMsgPerIteration);
        
        getDcopInfoProvider().setLocalDcopSharedInformation(inbox); 
    }
    
    
    /**
     * Read Data Center tree information from the previous DCOP round
     */
    private void readPreviousDataCenterTree() {
        DcopReceiverMessage abstractMessage = inbox.getAsynchronousMessage();
        
        if (null != abstractMessage) {
            GeneralDcopMessage treeMsg = abstractMessage.getMessageForThisReceiver(getRegionID());
            
            if (treeMsg instanceof FinalRCDiffDcopMessage) {
                FinalRCDiffDcopMessage finalRCDiffMsg = (FinalRCDiffDcopMessage) treeMsg;
                
                if (finalRCDiffMsg.getMessageMap().containsKey(TREE)) {
                    FinalRCDiffTree dataCenterTree = (FinalRCDiffTree) finalRCDiffMsg.getMessageMap().get(TREE).iterator().next();
                    pathToClient = FinalRCDiffTree.deepCopy(dataCenterTree);
                }
            }
        }
    }

    private void writeIterationInformation(boolean isClear) {
        DcopReceiverMessage abstractMessage = inbox.getAsynchronousMessage();
        
        if (abstractMessage != null) {
            DcopReceiverMessage treeMsg = abstractMessage;
            
            if (isClear) {
                LOGGER.info("DCOP Run {} Region {} before clearing received messages {}", currentDcopRun - 1, getRegionID(), treeMsg);
                
                treeMsg.getReceiverMessageMap().clear();
                
                LOGGER.info("DCOP Run {} Region {} after clearing received messages {}", currentDcopRun - 1, getRegionID(), treeMsg);
            }
            
            treeMsg.setIteration(currentDcopRun);
            
            FinalRCDiffDcopMessage selfMsg = messageMapToSend.getOrDefault(getRegionID(), FinalRCDiffDcopMessage.emptyMessage());
            
            selfMsg.setDataCenterTree(pathToClient);
            
            treeMsg.setMessageToTheReceiver(getRegionID(), selfMsg);
            
            LOGGER.info("DCOP Run {} Region {} sets asynchronous messages {}", currentDcopRun, getRegionID(), treeMsg);
                        
            inbox.setAsynchronousMessage(treeMsg);
            
            LOGGER.info("DCOP Run {} Region {} write Dcop Run {}", currentDcopRun, getRegionID(), currentDcopRun);
        }
                
        LOGGER.info("DCOP Run {} Region {} write new Inbox {}", currentDcopRun, getRegionID(), inbox);
        
        getDcopInfoProvider().setLocalDcopSharedInformation(inbox);
    }

    /** Initialize newIteration by reading the inbox
     *  @return the run count or the first iteration count of the current DCOP run 
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
        
        currentDcopRun = inbox.getAsynchronousMessage().getIteration() + 1;
        
        LOGGER.info("DCOP Run {} Region {} reads inbox {}", currentDcopRun, getRegionID(), inbox);
                                        
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        retrieveAllService(summary);
        
        sortedNeighbors.addAll(getNeighborSet());
        
        sortedNeighbors.forEach(neighbor -> messageMapToSend.put(neighbor, FinalRCDiffDcopMessage.emptyMessage()));
//        sortedNeighbors.forEach(neighbor -> getFlowLoadMap().put(neighbor, new HashMap<>()));
        
        LOGGER.info("DCOP Run {} Region {} has Region Capacity {}", currentDcopRun, getRegionID(), getRegionCapacity());
        LOGGER.info("DCOP Run {} Region {} has Neighbor Set {}", currentDcopRun, getRegionID(), sortedNeighbors);
        LOGGER.info("DCOP Run {} Region {} has All Services {}", currentDcopRun, getRegionID(), getAllServiceSet());
    }
    
    /**
     * @param neighbor .
     * @return the delay of the link between self region and the neighbor
     */
    private double getDelay(RegionIdentifier neighbor) {        
        if (!summary.getNetworkCapacity().containsKey(neighbor)) {
            LOGGER.info("DCOP Run {} Region {} cannot find neighbor {} from network capacity {}", currentDcopRun, getRegionID(), neighbor, summary.getNetworkCapacity());
            return 0;
        }
        
        return summary.getNetworkCapacity().get(neighbor).getOrDefault(LinkAttribute.DELAY, 0D);
    }
    
    /**
     * @param neighbor .
     * @return the datarate of the link between self region and the neighbor
     */
    private double getDatarate(RegionIdentifier neighbor) {
        if (!summary.getNetworkCapacity().containsKey(neighbor)) {
            LOGGER.info("DCOP Run {} Region {} cannot find neighbor {} from network capacity {}", currentDcopRun, getRegionID(), neighbor, summary.getNetworkCapacity());
            return 0;
        }
        
        // Link quality: Assuming RX = TX
        return summary.getNetworkCapacity().get(neighbor).getOrDefault(LinkAttribute.DATARATE_RX, 0D);
    }

    @Override
    protected double getAvailableCapacity() {
        return 0;
    }
}