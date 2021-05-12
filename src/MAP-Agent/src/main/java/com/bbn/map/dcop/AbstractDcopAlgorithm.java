package com.bbn.map.dcop;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.ap.TotalDemand;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dcop.final_rcdiff.FinalRCDiffProposal;
import com.bbn.map.dcop.final_rcdiff.FinalRCDiffTuple;
import com.bbn.map.ta2.RegionalLink;
import com.bbn.map.ta2.RegionalTopology;
import com.bbn.map.utils.MAPServices;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.ComparisonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * @author khoihd
 *
 */
public abstract class AbstractDcopAlgorithm {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDcopAlgorithm.class);

    /**
     *  Tolerance used in comparing double values.
     */
    public static final double DOUBLE_TOLERANCE = ComparisonUtils.NODE_ATTRIBUTE_COMPARISON_TOLERANCE;
    /**
     *  The iteration where regions write data center tree.
     */
    protected static final int TREE_ITERATION = Integer.MIN_VALUE;
    /**
     *  The number of dcop iteration limit.
     */
    protected static final int DCOP_ITERATION_LIMIT = AgentConfiguration.getInstance().getDcopIterationLimit();
    /**
     *  Minimum container count enforced by RLG.
     */
    private static final double MINIMUM_SERVICE_CAPACITY = 1D;
    /**
     *  Set of network neighbors from network capacity.
     */
    private final Set<RegionIdentifier> neighborSet = new HashSet<>();
    
    private final Set<ServiceIdentifier<?>> allServiceSet = new HashSet<>();
    
    /** Positive if incoming and negative if outgoing
     */
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> flowLoadMap = new HashMap<>();
        
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> clientLoadMap = new HashMap<>();
                
    private RegionIdentifier regionID;
    
    private DcopInfoProvider dcopInfoProvider;
    
    private ApplicationManagerApi applicationManager;
    
    private double regionCapacity;

    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    protected AbstractDcopAlgorithm(RegionIdentifier regionID, DcopInfoProvider dcopInfoProvider, ApplicationManagerApi applicationManager) {
        this.regionID = regionID;
        this.setDcopInfoProvider(dcopInfoProvider);
        this.setApplicationManager(applicationManager);
    }
    

    /**
     * Implementation where the algorithm runs.
     * @return Dcop lan
     */
    protected abstract RegionPlan run();
    
    
    /**
     * Agent is waiting for and reading messages.
     * @param iteration
     *            the iteration where the agents are waiting for 
     * @return a mapping from neighbor -> AbstractDcopMessage
     * @throws InterruptedException
     *             when the sleeping thread is interrupted
     */
    protected Map<RegionIdentifier, GeneralDcopMessage> waitForMessagesFromNeighbors(int iteration)
            throws InterruptedException {
        final long start = System.currentTimeMillis();
        
        final Duration apRoundDuration = AgentConfiguration.getInstance().getApRoundDuration();
        final Map<RegionIdentifier, GeneralDcopMessage> receivedMsgMap = new HashMap<>();
        int noMessageToRead = getNeighborSet().size();

        final Duration timeout = AgentConfiguration.getInstance().getDcopSynchronousMessageTimeout();
        final LocalDateTime stopTime = LocalDateTime.now().plus(timeout); 
        do {
            receivedMsgMap.clear();

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();
                                    
            LOGGER.info("Looking for messages at iteration {} from neighbors: {} in: {} to: {}", iteration,
                    getNeighborSet(), allSharedInformation, getRegionID());
            
            for (RegionIdentifier neighbor : getNeighborSet()) {                
                if (null != allSharedInformation.get(neighbor)) {
                    DcopReceiverMessage abstractMsg = allSharedInformation.get(neighbor).getMessageAtIteration(iteration);
                    
                    if (null != abstractMsg) {
                        if (abstractMsg.isSentTo(getRegionID())) {
                            receivedMsgMap.put(abstractMsg.getSender(), abstractMsg.getMessageForThisReceiver(getRegionID()));
                        }
                    }
                }
            }
            if (LocalDateTime.now().isAfter(stopTime)) {
                LOGGER.warn("Region {} times out when waiting for message after {} seconds", getRegionID(),
                        timeout.getSeconds());
                break;
            }
            
            // only wait if the region hasn't received all messages
            if (receivedMsgMap.size() < noMessageToRead) {
                Thread.sleep(apRoundDuration.toMillis());
            }
        } 
        while (receivedMsgMap.size() < noMessageToRead);

        final long end = System.currentTimeMillis();
        LOGGER.info("Wait for messages took {} ms", (end - start));
        
        return receivedMsgMap;
    }
    
    /**
     * Determine if a flow contains zero TX and RX.
     * @param networkLink .
     * @return true if TX != 0 OR RX != 0;
     */
    protected boolean isNonZeroFlow(ImmutableMap<LinkAttribute, Double> networkLink) {
        double rx = networkLink.get(LinkAttribute.DATARATE_RX);
        double tx = networkLink.get(LinkAttribute.DATARATE_TX);
        
        return compareDouble(tx, 0) != 0 || compareDouble(rx, 0) != 0;
    }
    
    
    /**
     * Given neighbor: source -> destination, the destination is at neighbor's side. <br>
     * So if the client is the destination (and source is the server), then the client is at neighbor's side <br>
     * It means the flow from neighbor is the incoming flow
     * 
     * Example:
     * 
     * A - B - C - D - ClientD
     * Network demand at B: 
     *      A={RegionNetworkFlow: [D <-> A server: A]
     *      C={RegionNetworkFlow: [A <-> D server: A]
     * @param flow .
     * @return true if the destination is the client.
     */
    protected boolean isIncomingFlowUsingFlow(RegionNetworkFlow flow) { 
        RegionIdentifier server = flow.getServer();
        
        return flow.getSource().equals(server) ? true : false;
    }
    
    /**
     * Get client from the flow.
     * @param flow .
     * @return client of the flow
     */
    protected RegionIdentifier getClient(RegionNetworkFlow flow) {
        RegionIdentifier regionOne = flow.getSource();
        RegionIdentifier regionTwo = flow.getDestination();
        RegionIdentifier server = flow.getServer();
        
        if (StringRegionIdentifier.UNKNOWN != server) {
            return regionOne.equals(server) ? regionTwo : regionOne;
        }
        
        return null;
    }
    
    /**
     * Get server from the flow.
     * @param flow .
     * @return server of the flow
     */
    protected RegionIdentifier getServer(RegionNetworkFlow flow) {
        RegionIdentifier regionOne = flow.getSource();
        RegionIdentifier regionTwo = flow.getDestination();
        RegionIdentifier server = flow.getServer();
        
        if (StringRegionIdentifier.UNKNOWN != server) {
            return regionOne.equals(server) ? regionOne : regionTwo;
        }
        
        return null;
    }
    
    /**
     * @param keyLoadMap
     *          incomingLoadMap/keepLoadMap or value of outgoingLoadMap
     * @param key 
     *          key of the map 
     * @param value the value to be added 
     * @param isAddingUp true if adding up the value
     * @param <A> .
     *           
     */
    protected <A> void updateKeyLoadMap(Map<A, Double> keyLoadMap, A key, double value, boolean isAddingUp) {        
        if (isAddingUp) {
            keyLoadMap.merge(key, value, Double::sum);
        } else {
            keyLoadMap.put(key, value);
        }
    }
    
    /**
     * @param keykeyLoadMap .
     * @param outerKey .
     * @param innerKey .
     * @param load need to be added to
     * @param isAddingUp true if adding up the value
     * @param <A> .
     * @param <B> .
     */
    protected <A, B> void updateKeyKeyLoadMap(Map<A, Map<B, Double>> keykeyLoadMap, A outerKey, B innerKey, double load, boolean isAddingUp) {
        Map<B, Double> keyLoadMap = keykeyLoadMap.getOrDefault(outerKey, new HashMap<>());
        updateKeyLoadMap(keyLoadMap, innerKey, load, isAddingUp);
        keykeyLoadMap.put(outerKey, keyLoadMap);
    }
    
    /**
     * @param map .
     * @param keyA .
     * @param keyB .
     * @param keyC .
     * @param load .
     * @param isAddingUp true if adding up the value
     * @param <A> .
     * @param <B> .
     * @param <C> .
     */
    protected <A, B, C> void updateKeyKeyKeyLoadMap(Map<A, Map<B, Map<C, Double>>> map, A keyA, B keyB, C keyC, double load, boolean isAddingUp) {
        Map<B, Map<C, Double>> keyLoadMap = map.getOrDefault(keyA, new HashMap<>());
        updateKeyKeyLoadMap(keyLoadMap, keyB, keyC, load, isAddingUp);
        map.put(keyA, keyLoadMap);
    }
    
    /**
     * @param service .
     * @precondition service is not UNKNOWN
     * @return priority of service
     */
    protected int getPriority(ServiceIdentifier<?> service){
        return AppMgrUtils.getApplicationSpecification(getApplicationManager(),service).getPriority();
    }
    
    /**
     * This function is used to replaced the compareDouble(double, double).
     * If |a - b| is <= DOUBLE_TOLERANCE, then return 0; // a == b
     * @param a .
     * @param b .
     * @return
     *   Double.compare(a, b) if the absolute difference is > DOUBLE_TOLERANCE, return 0 otherwise
     */
    public static int compareDouble(double a, double b) {
        double absDiff = Math.abs(a - b);
        
        if (Double.compare(absDiff, DOUBLE_TOLERANCE) <= 0) return 0;
        
        return Double.compare(a, b);
    }
    
    /**
     * @param summary .
     * @return the plan by default when this region processes all the load
     */
    protected RegionPlan defaultPlan(ResourceSummary summary) {
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        
        for (final ApplicationSpecification spec : AppMgrUtils.getApplicationManager().getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                // add to overflow plan
                Builder<RegionIdentifier, Double> regionPlan = defaultRegionPlanBuilder(service);
                servicePlanBuilder.put(service, regionPlan.build());
            }
        }

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> defaultPlan = servicePlanBuilder.build();
        LOGGER.info("Plan by default " + defaultPlan);      
        
        final RegionPlan rplan = new RegionPlan(summary.getRegion(), defaultPlan);
        
        return rplan;
    }
    
    /**
     * @param service .
     * @return default region plan that pushes all load of this service to server
     */
    private Builder<RegionIdentifier, Double> defaultRegionPlanBuilder(ServiceIdentifier<?> service) {
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
        
        RegionIdentifier defaultRegion = defaultRegion(service);
        
//        if (defaultRegion != null && !getRegionID().equals(defaultRegion)) {
//            regionPlanBuilder.put(defaultRegion, 1D);
//            regionPlanBuilder.put(getRegionID(), 0D);
//            for (RegionIdentifier neighbor : neighborSet) {
//                if (!neighbor.equals(defaultRegion)) {
//                    regionPlanBuilder.put(neighbor, 0D);
//                }
//            } 
//        }
//        else {
//            regionPlanBuilder.put(getRegionID(), 1D);
//            neighborSet.forEach(neighbor -> regionPlanBuilder.put(neighbor, 0D));
//        }
        
        regionPlanBuilder.put(defaultRegion, 1D);
        return regionPlanBuilder;
    }
    
    private Builder<RegionIdentifier, Double> keepAlRegionPlan(ServiceIdentifier<?> service) {
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
        
        regionPlanBuilder.put(getRegionID(), 1D);
//        neighborSet.forEach(neighbor -> regionPlanBuilder.put(neighbor, 0D));
        
        return regionPlanBuilder;
    }
    
    /**
     * @param service .
     * @return the default region of the service
     */
    protected static RegionIdentifier defaultRegion(ServiceIdentifier<?> service) {
        return AppMgrUtils.getApplicationSpecification(AppMgrUtils.getApplicationManager(), service).getServiceDefaultRegion();
    }
    
    /**
     * 
     * @param rawCompute
     *            the raw compute value
     * @param network
     *            the network value used to determine what percentage of the raw
     *            compute value belongs to each client
     * @return the
     */
    protected ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> allocateComputeBasedOnNetwork(
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> rawCompute,
            final ImmutableMap<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> network) {

        // ignore the "source" of the compute numbers
        final Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> aggregatedCompute = new HashMap<>();
        rawCompute.forEach((service, data) -> {
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                final Map<NodeAttribute, Double> aggregateData = aggregatedCompute.computeIfAbsent(service,
                        k -> new HashMap<>());
                data.forEach((ignore, attrData) -> {
                    attrData.forEach((attr, value) -> {
                        aggregateData.merge(attr, value, Double::sum);
                    });
                });
            } // not AP or UNMANAGED
        });

        // compute sum of RX and TX for each service from each source by service
        // TODO: make sure if the network demand is zero, then the inferred demand is zero as well 
        final Map<ServiceIdentifier<?>, Double> networkPerService = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> networkPerServicePerSource = new HashMap<>();
        network.forEach((neighbor, sourceData) -> {
            sourceData.forEach((flow, flowData) -> {                
                if (!isIncomingFlowUsingFlow(flow)) {
                    return;
                }

                final RegionIdentifier client;
                final RegionIdentifier server;
                if (flow.getSource().equals(flow.getServer())) {
                    client = flow.getDestination();
                    server = flow.getSource();
                } else if (flow.getDestination().equals(flow.getServer())) {
                    client = flow.getSource();
                    server = flow.getDestination();
                } else {
                    client = null;
                    server = flow.getServer();
                }
                
                
                final Map<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> plannedServiceFlowData = flowData
                        .entrySet().stream().filter(entry -> !MAPServices.UNPLANNED_SERVICES.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                if (null == client && !plannedServiceFlowData.isEmpty()) {
                    // only warn when there are planned services for which we
                    // cannot find the client
                    LOGGER.warn(
                            "Unable to find client in flow {} <-> {} with server {}. Skipping computation of inferred demand for this flow",
                            flow.getSource(), flow.getDestination(), flow.getServer());
                }

                if (null != client && regionID.equals(server)) {
                    plannedServiceFlowData.forEach((service, serviceData) -> {
                        final double serviceNetworkDemand = serviceData.getOrDefault(LinkAttribute.DATARATE_RX, 0D)
                                + serviceData.getOrDefault(LinkAttribute.DATARATE_TX, 0D);
                        networkPerService.merge(service, serviceNetworkDemand, Double::sum);

                        final Map<RegionIdentifier, Double> networkPerSource = networkPerServicePerSource
                                .computeIfAbsent(service, k -> new HashMap<>());
                        networkPerSource.merge(client, serviceNetworkDemand, Double::sum);
                    });
                } // client not null and server is in this region
            });
        });

        // compute server value based on percentage of aggregatedCompute and
        // percentage of network from each source for a service
        final ImmutableMap.Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> finalCompute = ImmutableMap
                .builder();
        networkPerServicePerSource.forEach((service, networkPerSource) -> {
            if (aggregatedCompute.containsKey(service)) {
                final ImmutableMap.Builder<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> finalComputePerSource = ImmutableMap
                        .builder();

                final Map<NodeAttribute, Double> aggregateComputeForService = aggregatedCompute.get(service);
                final double serviceValue = networkPerService.get(service);
                networkPerSource.forEach((source, sourceValue) -> {
                    final double rawPercentage = sourceValue / serviceValue;
                    // Can get NaN if sourceValue and serviceValue are both
                    // zero.
                    /// If sourceValue is non-zero, then serviceValue cannot be
                    // zero because of how it's computed, therefore we will not
                    // see Infinity.
                    final double percentage = Double.isNaN(rawPercentage) ? 0 : rawPercentage;

                    final ImmutableMap.Builder<NodeAttribute, Double> finalComputeForSource = ImmutableMap.builder();
                    aggregateComputeForService.forEach((attr, value) -> {
                        final double sourceComputeValue = value * percentage;
                        finalComputeForSource.put(attr, sourceComputeValue);
//                        finalComputeForSource.put(attr, 6.0);
                    });

                    finalComputePerSource.put(source, finalComputeForSource.build());
                }); // foreach network source

                finalCompute.put(service, finalComputePerSource.build());
            } // have compute values for this service
        }); // foreach service with network data

        return finalCompute.build();
    }
    
    /**
     * @return true if the region has available capacity
     */
    protected boolean hasAvailableCapacity() {
        return compareDouble(getAvailableCapacity(), 0) > 0;
    }
    
    /**
     * @return current available capacity
     */
    protected abstract double getAvailableCapacity();
    
    /**
     * Compute the total capacity.
     * @param summary s.
     * @postcondition regionCapacity contains the TASK_CONTAINERS of the server
     *                of this region
     */
    protected void retrieveAggregateCapacity(ResourceSummary summary) {
        final NodeAttribute containersAttribute = MapUtils.COMPUTE_ATTRIBUTE;
                
        double capacity = 0D;
        
        if (summary.getServerCapacity().containsKey(containersAttribute)) {
            capacity = summary.getServerCapacity().get(containersAttribute).doubleValue();
        }
        
//        regionCapacity = capacity;
        
        // (region capacity - (number of services in region - 1) * 1.0 min service capacity) * DCOP threshold
        int numServiceInRegionMinusOne = summary.getServerDemand().size();
        
        regionCapacity = (capacity - (numServiceInRegionMinusOne - 1) * MINIMUM_SERVICE_CAPACITY) * AgentConfiguration.getInstance().getDcopCapacityThreshold();
        
        // Region capacity might be negative if numServiceInRegionMinusOne is large and capacity is small
        if (compareDouble(regionCapacity, 0D) < 0) {
            regionCapacity = 0D;
        }
     }
    
    /**
     * @param <V> value which is a sub-type of Number
     * @param map input
     * @return sum of values
     */
    protected <V extends Number> double sumValues(Map<?, V> map) {
        return map.values().stream().mapToDouble(Number::doubleValue).sum();
    }
    
    /**
     * @param <K1> key1
     * @param <K2> key2
     * @param <V> sub-type of Number
     * @param generalMap .
     * @return a sum of values of all inner map
     */
    protected <K1, K2, V extends Number> double sumKeyKeyValues(Map<K1, Map<K2, V>> generalMap) {
        double sumLoad = 0;
        
        for (Entry<K1, Map<K2, V>> entry : generalMap.entrySet()) {
            sumLoad += sumValues(entry.getValue());
        }
        
        return sumLoad;
    }

    
    /**
     * @param <K1> key1
     * @param <K2> key2
     * @param <K3> key3
     * @param <V> sub-type of Number
     * @param generalMap .
     * @return a sum of double values
     */
    protected <K1, K2, K3, V extends Number> double sumKeyKeyKeyValues(Map<K1, Map<K2, Map<K3, V>>> generalMap) {
        double sumLoad = 0;
        
        for (Entry<K1, Map<K2, Map<K3, V>>> entry : generalMap.entrySet()) {
            sumLoad += sumKeyKeyValues(entry.getValue());
        }
        
        return sumLoad;
    }
    
    /**
     * @param loadMap .
     * @return true if the total load is greater than 0
     */
    protected boolean hasExcess(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadMap) {
        return compareDouble(sumKeyKeyValues(loadMap), 0) > 0;
    }
    
    /**
     * Convert map: Object -> ServiceIdentifier<?>.
     * @param map is the input to be converted from Object -> ServiceIdentifier<?>
     * @return a converted map
     */
    protected Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> convertObjectToServiceMap(Map<Object, Map<RegionIdentifier, Double>> map) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceMap = new HashMap<>();
        
        for(Entry<Object, Map<RegionIdentifier, Double>> entry : map.entrySet()) {
            serviceMap.put((ServiceIdentifier<?>) entry.getKey(), new HashMap<>(entry.getValue()));
        }
        
        return serviceMap;
    }
    
    /**
     * Convert map: Object -> Integer.
     * @param map is the input to be converted from Object -> Integer
     * @return a converted map
     */
    protected Map<Integer, Map<RegionIdentifier, Double>> convertObjectToIntegerMap(Map<Object, Map<RegionIdentifier, Double>> map) {
        Map<Integer, Map<RegionIdentifier, Double>> serviceMap = new HashMap<>();
        
        for(Entry<Object, Map<RegionIdentifier, Double>> entry : map.entrySet()) {
            serviceMap.put((Integer) entry.getKey(), new HashMap<>(entry.getValue()));
        }
        
        return serviceMap;
    }
    
    
    /**
     * @param summary .
     * @postcondition neighborSet contains the set of neighbors
     */
    protected void retrieveNeighborSetFromNetworkLink(ResourceSummary summary) {
//        neighborSet.addAll(summary.getNetworkCapacity().keySet());
        neighborSet.addAll(dcopInfoProvider.getAllDcopSharedInformation().keySet());
        neighborSet.remove(regionID);
        neighborSet.remove(RegionIdentifier.UNKNOWN);
        LOGGER.info("My neighbors are: {}", getNeighborSet().toString());
    }
    
    /**
     * Get all valid services from network demand for computing DCOP plans. <br>
     * Getting from network demand since it reflects the services in the inferred server demand.
     * @param summary .
     * @postcondition allServiceSet for DCOP plan
     */
    protected void retrieveAllService(ResourceSummary summary) {
        for (Entry<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> entry : summary.getNetworkDemand().entrySet()) {
            for (Entry<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> innerEntry : entry.getValue().entrySet()) {
                for (ServiceIdentifier<?> service : innerEntry.getValue().keySet()) {
                    if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                        allServiceSet.add(service);
                    }
                }
            }
        }
        LOGGER.info("All services in network demand are: {}", allServiceSet);
    }


    /**
     * @return the neighborSet
     */
    protected Set<RegionIdentifier> getNeighborSet() {
        return neighborSet;
    }


    /**
     * @return the dcopInfoProvider
     */
    protected DcopInfoProvider getDcopInfoProvider() {
        return dcopInfoProvider;
    }


    /**
     * @param dcopInfoProvider the dcopInfoProvider to set
     */
    protected void setDcopInfoProvider(DcopInfoProvider dcopInfoProvider) {
        this.dcopInfoProvider = dcopInfoProvider;
    }


    /**
     * @return the applicationManager
     */
    protected ApplicationManagerApi getApplicationManager() {
        return applicationManager;
    }


    /**
     * @param applicationManager the applicationManager to set
     */
    protected void setApplicationManager(ApplicationManagerApi applicationManager) {
        this.applicationManager = applicationManager;
    }


    /**
     * @return the regionID
     */
    protected RegionIdentifier getRegionID() {
        return regionID;
    }


    /**
     * @param regionID the regionID to set
     */
    protected void setRegionID(RegionIdentifier regionID) {
        this.regionID = regionID;
    }
    
    /**
     * @return keepLoadMap
     */
//    protected Map<ServiceIdentifier<?>, Double> getKeepLoadMap() {
//        return keepLoadMap;
//    }

    /**
     * @return the flowLoadMap
     */
    protected Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> getFlowLoadMap() {
        return flowLoadMap;
    }

    
    /**
     * @param summary .
     * @param lastIteration .
     * @param isRootKeepTheRest If true, let the overloaded region keeps all the remaining load if can't shed all. Used in timeout case.
     * @return DCOP plan
     */
    protected RegionPlan computeRegionDcopPlan(ResourceSummary summary, int lastIteration, boolean isRootKeepTheRest) {
//        Map<ServiceIdentifier<?>, Double> keepLoadMap = new HashMap<>();
        
        Map<ServiceIdentifier<?>, Double> incomingLoadMap = new HashMap<>();
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> outgoingLoadMap = new HashMap<>();
        
        // Compute incoming and outgoing load map
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> neighborEntry : getFlowLoadMap().entrySet()) {
            RegionIdentifier neighbor = neighborEntry.getKey();
            for (Entry<ServiceIdentifier<?>, Double> serviceEntry : neighborEntry.getValue().entrySet()) {
                ServiceIdentifier<?> service = serviceEntry.getKey();
                double load = serviceEntry.getValue();
                
                // incomingLoad
                if (compareDouble(load, 0) > 0) {
                    updateKeyLoadMap(incomingLoadMap, service, load, true);
                } 
                // outgoingLoadMap
                else {
                    updateKeyKeyLoadMap(outgoingLoadMap, service, neighbor, -load, true);
                }
            }
        }
        
        double totalIncoming = sumValues(incomingLoadMap);
        double totalOutgoing = sumKeyKeyValues(outgoingLoadMap);
        LOGGER.info("DCOP Run {} Region {} has totalIncoming {}", lastIteration, getRegionID(), totalIncoming);
        LOGGER.info("DCOP Run {} Region {} has totalOutgoing {}", lastIteration, getRegionID(), totalOutgoing);
        
        if (compareDouble(totalIncoming - totalOutgoing, getRegionCapacity()) > 0) {
            // Only print out the warning for non data center regions
            if (!getDatacenters().contains(getRegionID())) {
                LOGGER.info("DCOP Run {} Region {} has MORE LOAD THAN CAPACITY: {} > {}", lastIteration, getRegionID(), (totalIncoming - totalOutgoing), getRegionCapacity());
            }
        }
        
        if (compareDouble(sumValues(incomingLoadMap), 0) == 0) {
            RegionPlan defaultRegionPlan = defaultPlan(summary);
            LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), defaultRegionPlan);
            return defaultRegionPlan;
        }

        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();

        for (Entry<ServiceIdentifier<?>, Double> serviceEntry : incomingLoadMap.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            double totalLoadFromThisService = serviceEntry.getValue();

            Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
            // No incoming load
            if (compareDouble(totalLoadFromThisService, 0) == 0) {
                regionPlanBuilder = defaultRegionPlanBuilder(service);
            }
            // Keep all if there is incoming load but no outgoing load
            else if (!outgoingLoadMap.containsKey(service)) {
                regionPlanBuilder = keepAlRegionPlan(service);
            }
            // There is incoming load and outgoing load
            else {
                double totalRatio = 0;
                
                // Create a set of neighbors with zero rate
                // Create a list of neighbors with non-zero rate which self ID will be the last element if in the list

                // Set the last region to 1D - totalLoad
                
                Set<RegionIdentifier> regionsWithZeroRatio = new HashSet<>();
                List<RegionIdentifier> regionsWithNonZeroRate = new ArrayList<>();
               
                
                Set<RegionIdentifier> regionPlanSet = new HashSet<>();
                regionPlanSet.addAll(outgoingLoadMap.getOrDefault(service, new HashMap<>()).keySet());
                
                // Create a set of regions with zero rate and the list of regions with non-zero rate
//                for (RegionIdentifier neighbor : getNeighborSet()) {
                
                for (RegionIdentifier region : regionPlanSet) {
                    double load = outgoingLoadMap.get(service).getOrDefault(region, 0D);
                    double ratio = load / totalLoadFromThisService;
                    if (compareDouble(ratio, 0D) == 0) {
                        regionsWithZeroRatio.add(region);
                    }
                    else {
                        regionsWithNonZeroRate.add(region);
                        totalRatio += ratio;
                    }
                }
                
                // Add self region to either zero or non-zero 
                if (compareDouble(1D - totalRatio, 0D) == 0) {
                    regionsWithZeroRatio.add(getRegionID());
                }
                else {
                    // self region is the last element
                    regionsWithNonZeroRate.add(getRegionID());
                }
                
                // Create plan for regions with 0 rate
                for (RegionIdentifier region : regionsWithZeroRatio) {
                    regionPlanBuilder.put(region, 0D);
                }
                
                // List of regions with non-zero ratio
                double finalTotalRatio = 0D;
                for (int i = 0; i < regionsWithNonZeroRate.size(); i++) {
                    RegionIdentifier region = regionsWithNonZeroRate.get(i);
                    
                    if (i == regionsWithNonZeroRate.size() - 1) {
                        regionPlanBuilder.put(region, 1D - finalTotalRatio);
                    }
                    else {
                        double load = outgoingLoadMap.get(service).getOrDefault(region, 0D);
                        double ratio = load / totalLoadFromThisService;
                        regionPlanBuilder.put(region, ratio);
                        finalTotalRatio += ratio;
                    }
                }
                      
//                for (RegionIdentifier neighbor : getNeighborSet()) {
//                    double load = outgoingLoadMap.get(service).getOrDefault(neighbor, 0D);
//                    regionPlanBuilder.put(neighbor, load / totalLoadFromThisService);
//                    totalRatio += load / totalLoadFromThisService;
//                }
//                
//                
//                // Ignore isRootKeepTheRest == False since it will never happen
//                if (isRootKeepTheRest) {
//                    regionPlanBuilder.put(getRegionID(), 1 - totalRatio);
//                } else {
//                    regionPlanBuilder.put(getRegionID(), keepLoadMap.getOrDefault(service, 0D) / totalLoadFromThisService);
//                }
            }
            ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
            
            if (compareDouble(sumValues(regionPlan), 1) != 0) {
                LOGGER.info("DCOP Run {} Region Plan Region {} DOES NOT ADD UP TO ONE: {}", lastIteration, getRegionID(), regionPlan);
            }

            servicePlanBuilder.put(service, regionPlan);
        }
        
//        // Create DCOP plan for services that are not in servicePlanBuilder
//        Set<ServiceIdentifier<?>> serviceNotInDcopPlan = new HashSet<>(getAllServiceSet());
//        serviceNotInDcopPlan.removeAll(servicePlanBuilder.build().keySet());        
//        
//        for (ServiceIdentifier<?> service : serviceNotInDcopPlan) {
//            Builder<RegionIdentifier, Double> defaultRegionPlanBuilder = defaultRegionPlanBuilder(service);
//            servicePlanBuilder.put(service, defaultRegionPlanBuilder.build());            
//        }
        
        for (final ApplicationSpecification spec : AppMgrUtils.getApplicationManager().getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            
            // If the service is not UNPLANNED, and the service is not in the servicePlanBuilder
            if (!MAPServices.UNPLANNED_SERVICES.contains(service) && !servicePlanBuilder.build().keySet().contains(service)) {
                // add to overflow plan
                Builder<RegionIdentifier, Double> regionPlan = defaultRegionPlanBuilder(service);
                servicePlanBuilder.put(service, regionPlan.build());
            }
        }

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder.build();

        LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), dcopPlan);

        final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);

        return rplan;
    }
    
    /**
     * Server - - RegionID - child - - Client .
     * @param server is the data center
     * @param client is the client
     * @param iteration current DCOP run or iteration
     * @param topology is used to retrieve the path from server to client
     * @param parent parent region
     * @return the child
     */
    protected RegionIdentifier findNeighbor(RegionIdentifier server, RegionIdentifier client, int iteration, RegionalTopology topology, RegionIdentifier parent) {
        if (topology == null) {return null;}
        
        // Path is: server - node1 - node2 - ... - nodeN - client
        for (RegionalLink entry : topology.getPath(server, client)) {
            LOGGER.info("Iteration {} Region {} has links from server {} to client {}: {}", iteration, regionID, server, client, entry);
            
            // If this region is the server 
            // Then there is only one edge: server (self region) - node1 
            if (getRegionID().equals(server)) {
                if (isLinkContains(entry, getRegionID())) {
                    return getOtherRegion(entry, getRegionID());
                }
            }
            // Otherwise, return the link with self region but not parent (not return the parent - regionID) link
            else {
                // Look for the link that contains self region, but does not contain the parent
                if (isLinkContains(entry, getRegionID()) && !isLinkContains(entry, parent)) {
                    return getOtherRegion(entry, getRegionID());
                }
            }            
        }
        
        return null;
    }
    
    /**
     * Check if a link has a region.
     * @param link .
     * @param region .
     * @return true if link has the region
     */
    private boolean isLinkContains(RegionalLink link, RegionIdentifier region) {
        return region.equals(link.getLeft()) || region.equals(link.getRight());
    }
    
    /**
     * Return the other region. Assume that the link has the region
     * @param link .
     * @param region .
     * @return the other region, which supposed to be the child
     */
    private RegionIdentifier getOtherRegion(RegionalLink link, RegionIdentifier region) {
        RegionIdentifier left = link.getLeft();
        RegionIdentifier right = link.getRight();
        
        return left.equals(region) ? right : left; 
    }

    /**
     * Provided attribute -> load for each service.
     * Build service -> regionID -> attribute -> load
     * 
     * Provided flow -> attribute -> double for each service
     * Build neighbor -> flow -> service -> attribute -> double
     * @param dcopInfoProvider .
     * @param serviceSet .
     * @return inferred server demand
     */
    protected ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferTotalDemand(
            DcopInfoProvider dcopInfoProvider, Set<ServiceIdentifier<?>> serviceSet) {
        
        // Server demand builder
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> sDemandBuilder = new Builder<>();
        
        // Build sDemand builder
        for (ServiceIdentifier<?> service : serviceSet) {
            TotalDemand totalDemand = dcopInfoProvider.getTotalDemandForService(service);
            sDemandBuilder.put(service, totalDemand.getServerDemand());
        }
        return sDemandBuilder.build();
    }
    
    /**
     * Provided attribute -> load for each service.
     * Build service -> regionID -> attribute -> load
     *
     * Provided flow -> attribute -> double for each service
     * Build neighbor -> flow -> service -> attribute -> double
     * @param dcopInfoProvider .
     * @param serviceSet .
     * @param hardcodeDemand hard-coded demand value
     * @return inferred server demand with hard-coded value
     */
    protected ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferHardCodeTotalDemand(
            DcopInfoProvider dcopInfoProvider, Set<ServiceIdentifier<?>> serviceSet, double hardcodeDemand) {

        // Server demand builder
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> sDemandBuilder = new Builder<>();

        // Build sDemand builder
        for (ServiceIdentifier<?> service : serviceSet) {
            TotalDemand totalDemand = dcopInfoProvider.getTotalDemandForService(service);
//                sDemandBuilder.put(service, totalDemand.getServerDemand());

            Builder<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> hardCodedDemand = new Builder<>();

            for (Map.Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> entry : totalDemand.getServerDemand().entrySet()) {
                Builder<NodeAttribute, Double> demandBuilder = new Builder<>();

                for (Entry<NodeAttribute, Double> demandEntry : entry.getValue().entrySet()) {
                    demandBuilder.put(demandEntry.getKey(), hardcodeDemand);
                }
                hardCodedDemand.put(entry.getKey(), demandBuilder.build());
            }
            sDemandBuilder.put(service, hardCodedDemand.build());
        }

        return sDemandBuilder.build();
    }
    
    /**
     * @author khoihd
     *
     */
    public class SortServiceByPriorityComparator implements Comparator<ServiceIdentifier<?>> {
        @Override
        public int compare(ServiceIdentifier<?> o1, ServiceIdentifier<?> o2) {
            // Sort by Descending
            return Integer.compare(getPriority(o2), getPriority(o1));
        }
    }
    
    /**
     * @author khoihd
     *
     */
    public class ServiceAscendingPriorityComparator implements Comparator<ServiceIdentifier<?>> {
        @Override
        public int compare(ServiceIdentifier<?> o1, ServiceIdentifier<?> o2) {
            // Sort by Ascending
            return Integer.compare(getPriority(o1), getPriority(o2));
        }
    }
    
    /**
     * @author khoihd
     * Prioritize tuples with smaller hops, larger data rate, smaller link delay, then sink region in lexicographic order
     */
    public static class SortRCDiffTuple implements Comparator<FinalRCDiffTuple>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = 440858726558744647L;

        @Override
        public int compare(FinalRCDiffTuple left, FinalRCDiffTuple right) {            
            int compare = 0;

            // Choose tuples with smaller delay first
            // Then tuple with higher data rate
            // Then tuple with smaller hops
            // Then tuple with regions based on alphabetical
            
            compare = Integer.compare(left.getHop(), right.getHop());
            if (compare == 0) {
                compare = compareDouble(right.getDatarate(), left.getDatarate());      
            }
            if (compare == 0) {
                compare = compareDouble(left.getDelay(), right.getDelay());
            }
            if (compare == 0) {
                compare = compareRegions(left.getChild(), right.getChild());
            }
            
            return compare;
        }
    }
    
    /**
     * @author khoihd
     * Prioritize tuples with smaller hops, then sink region in lexicographic order
     */
    public static class SortLexicographicRCDiffTuple implements Comparator<FinalRCDiffTuple>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = 440858726558744647L;

        @Override
        public int compare(FinalRCDiffTuple left, FinalRCDiffTuple right) {            
            int compare = 0;
            
            // Smaller hop is better
            compare = Integer.compare(left.getHop(), right.getHop());
            if (compare == 0) {
                compare = compareRegions(left.getChild(), right.getChild());
            }
            
            return compare;
        }
    }
    
    /**
     * @author khoihd
     * Prioritize tuples with smaller delay, larger data rate, smaller hops, then sink region in lexicographic order
     */
    public static class SortProposal implements Comparator<FinalRCDiffProposal>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = 6817675667929766458L;

        @Override
        public int compare(FinalRCDiffProposal leftProposal, FinalRCDiffProposal rightProposal) {            
            FinalRCDiffTuple left = leftProposal.getTuple();
            FinalRCDiffTuple right = rightProposal.getTuple();
            
            return new SortRCDiffTuple().compare(left, right);
        }
    }
    
    /**
     * @author khoihd
     * Prioritize tuples with smaller hops, then sink region in lexicographic order
     */
    public static class SortLexicographicProposal implements Comparator<FinalRCDiffProposal>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = -3630937797770575945L;

        @Override
        public int compare(FinalRCDiffProposal leftProposal, FinalRCDiffProposal rightProposal) {            
            FinalRCDiffTuple left = leftProposal.getTuple();
            FinalRCDiffTuple right = rightProposal.getTuple();
            
            return new SortLexicographicRCDiffTuple().compare(left, right);

        }
    }
    
    /**
     * @author khoihd
     *
     */
    public static class SortRegionIdentifierComparator implements Comparator<RegionIdentifier>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = -1592957603621470995L;

        @Override
        public int compare(RegionIdentifier region1, RegionIdentifier region2) {
            return region1.getName().compareTo(region2.getName());
        }
    }
    
    /**
     * Compare regions in lexicographic ordering. The data center is consider to be 'greater' than non-datacenter region
     * @param left .
     * @param right .
     * @return
     */
    private static int compareRegions(RegionIdentifier left, RegionIdentifier right) {
        Set<RegionIdentifier> datacenters = getDatacenters();
        
        int compare = 0;
        if (datacenters.contains(left) && datacenters.contains(right)) {
            compare = left.getName().compareTo(right.getName());     
        }
        // If the left child is one of the data centers => prioritize the right child
        else if (datacenters.contains(left)) {
            compare = 1;
        }
        // If the right child is one of the data centers => prioritize the left child
        else if (datacenters.contains(right)) {
            compare = -1;
        }
        else {
            compare = left.getName().compareTo(right.getName());     
        }
        
        return compare;
    }
    
    /**
     * .
     * @return all data centers
     */
    protected static Set<RegionIdentifier> getDatacenters() {
        Set<RegionIdentifier> datacenters = new HashSet<>();
        for (ServiceIdentifier<?> service : getAllPlannedServices()) {
            datacenters.add(defaultRegion(service));
        }
        
        return datacenters;
    }

    
    /**
     * @return all planned services
     */
    protected static Set<ServiceIdentifier<?>> getAllPlannedServices() {
        Set<ServiceIdentifier<?>> plannedServices = new HashSet<>();
        for (final ApplicationSpecification spec : AppMgrUtils.getApplicationManager().getAllApplicationSpecifications()) {
            final ApplicationCoordinates service = spec.getCoordinates();
            if (!MAPServices.UNPLANNED_SERVICES.contains(service)) {
                plannedServices.add(service);
            }
        }
        
        return plannedServices;
    }


    /**
     * @return the regionCapacity
     */
    public double getRegionCapacity() {
        return regionCapacity;
    }


    /**
     * @return the clientLoadMap
     */
    public Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> getClientKeepLoadMap() {
        return clientLoadMap;
    }

    /**
     * @return the allServiceSet
     */
    public Set<ServiceIdentifier<?>> getAllServiceSet() {
        return allServiceSet;
    }
}
