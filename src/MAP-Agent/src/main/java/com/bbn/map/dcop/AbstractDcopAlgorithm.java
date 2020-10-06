package com.bbn.map.dcop;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
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
    protected static final double DOUBLE_TOLERANCE = 1E-6;
    /**
     *  The iteration where regions write data center tree.
     */
    protected static final int TREE_ITERATION = Integer.MIN_VALUE;
    /**
     *  The number of dcop iteration limit.
     */
    protected static final int DCOP_ITERATION_LIMIT = AgentConfiguration.getInstance().getDcopIterationLimit();
    /**
     *  Set of network neighbors from network capacity.
     */
    private final Set<RegionIdentifier> neighborSet = new HashSet<>();
    
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
        final Duration apRoundDuration = AgentConfiguration.getInstance().getApRoundDuration();
        final Map<RegionIdentifier, GeneralDcopMessage> receivedMsgMap = new HashMap<>();
        int noMessageToRead = getNeighborSet().size();

        do {
            receivedMsgMap.clear();

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> allSharedInformation = getDcopInfoProvider().getAllDcopSharedInformation();
                                    
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
            // only wait if the region hasn't received all messages
            if (receivedMsgMap.size() < noMessageToRead) {
                Thread.sleep(apRoundDuration.toMillis());
            }
        } while (receivedMsgMap.size() < noMessageToRead);

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
    protected int compareDouble(double a, double b) {
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
        Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();

        regionPlanBuilder.put(getRegionID(), 1.0);
        for (RegionIdentifier neighbor : getNeighborSet()) {
            regionPlanBuilder.put(neighbor, 0.0);
        }

        ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();

        for (ServiceIdentifier<?> serviceID : summary.getServerDemand().keySet()) {
            servicePlanBuilder.put(serviceID, regionPlan);
        }

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> defaultPlan = servicePlanBuilder.build();
        LOGGER.info("Plan by default " + defaultPlan);
        
        return new RegionPlan(summary.getRegion(), defaultPlan);
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
                        networkPerService.merge(service, serviceData.getOrDefault(LinkAttribute.DATARATE_RX, 0D),
                                Double::sum);
                        networkPerService.merge(service, serviceData.getOrDefault(LinkAttribute.DATARATE_TX, 0D),
                                Double::sum);

                        final Map<RegionIdentifier, Double> networkPerSource = networkPerServicePerSource
                                .computeIfAbsent(service, k -> new HashMap<>());
                        networkPerSource.merge(client, serviceData.getOrDefault(LinkAttribute.DATARATE_RX, 0D),
                                Double::sum);
                        networkPerSource.merge(client, serviceData.getOrDefault(LinkAttribute.DATARATE_TX, 0D),
                                Double::sum);
                    });
                } // client not null
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
        
        if (summary.getServerCapacity().containsKey(containersAttribute)) {
            regionCapacity = summary.getServerCapacity().get(containersAttribute).doubleValue();
        }
        
        regionCapacity *= AgentConfiguration.getInstance().getDcopCapacityThreshold();
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
        neighborSet.addAll(summary.getNetworkCapacity().keySet());
        neighborSet.remove(regionID);
        LOGGER.info("My neighbors are: {}", getNeighborSet().toString());
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
        Map<ServiceIdentifier<?>, Double> keepLoadMap = new HashMap<>();
        getClientKeepLoadMap().forEach((client, map) -> map.forEach((service, load) -> updateKeyLoadMap(keepLoadMap, service, load, true)));
        
        // Return default plan if totalLoads coming is 0
        Map<ServiceIdentifier<?>, Double> incomingLoadMap = new HashMap<>();
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> outgoingLoadMap = new HashMap<>();
        
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
        
        if (compareDouble(sumValues(incomingLoadMap), 0) == 0) {
            RegionPlan defaultRegionPlan = defaultPlan(summary);
            LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), defaultRegionPlan);
            return defaultRegionPlan;
        }
    
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        Builder<RegionIdentifier, Double> regionPlanBuilder;
    
        for (Entry<ServiceIdentifier<?>, Double> serviceEntry : incomingLoadMap.entrySet()) {
            ServiceIdentifier<?> service = serviceEntry.getKey();
            double totalLoadFromThisService = serviceEntry.getValue();
    
            regionPlanBuilder = new Builder<>();
            if (!outgoingLoadMap.containsKey(service) || compareDouble(totalLoadFromThisService, 0) == 0) {
                for (RegionIdentifier neighbor : getNeighborSet()) {
                    regionPlanBuilder.put(neighbor, 0.0);
                }
                regionPlanBuilder.put(getRegionID(), 1.0);
            } else {
                
                double totalRatio = 0;
                
                for (RegionIdentifier neighbor : getNeighborSet()) {
                    double load = outgoingLoadMap.get(service).getOrDefault(neighbor, 0.0);
                    regionPlanBuilder.put(neighbor, load / totalLoadFromThisService);
                    totalRatio += load / totalLoadFromThisService;
                }
                
                if (isRootKeepTheRest) {
                    regionPlanBuilder.put(getRegionID(), 1 - totalRatio);
                } else {
                    regionPlanBuilder.put(getRegionID(), keepLoadMap.getOrDefault(service, 0D) / totalLoadFromThisService);
                }
            }
            ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
            
            if (compareDouble(sumValues(regionPlan), 1) != 0) {
                LOGGER.info("DCOP Run {} Region Plan Region {} DOES NOT ADD UP TO ONE: {}", lastIteration, getRegionID(), regionPlan);
            }
    
            servicePlanBuilder.put(service, regionPlan);
        }
    
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
                .build();
    
        LOGGER.info("DCOP Run {} Region Plan Region {}: {}", lastIteration, getRegionID(), dcopPlan);
    
        final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);
    
        return rplan;
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
}
