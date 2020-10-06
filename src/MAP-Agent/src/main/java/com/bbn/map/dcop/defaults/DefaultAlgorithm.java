package com.bbn.map.dcop.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author khoihd
 * @see {@link com.bbn.map.AgentConfiguration.DcopAlgorithm#DEFAULT_PLAN}
 *
 */
public class DefaultAlgorithm extends AbstractDcopAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAlgorithm.class);
    
    private int currentDcopRun;
    
    private ResourceSummary summary;
    
    private DcopSharedInformation inbox;
    
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> rootKeepLoadMap = new HashMap<>();
            
    /**
     * @param regionID .
     * @param dcopInfoProvider .
     * @param applicationManager .
     */
    public DefaultAlgorithm(RegionIdentifier regionID,
            DcopInfoProvider dcopInfoProvider,
            ApplicationManagerApi applicationManager) {
        super(regionID, dcopInfoProvider, applicationManager);
    }

    /**
     * @return DCOP plan
     */
    public RegionPlan run() {
        initialize();
        
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> inferredServerDemand = allocateComputeBasedOnNetwork(
                summary.getServerDemand(), summary.getNetworkDemand());
        
        // Service - Client -> Double
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> demandMap = aggregateDemandMap(inferredServerDemand);
        
        LOGGER.info("DCOP Run {} Region {} has network demand {}", currentDcopRun, getRegionID(), summary.getNetworkDemand());
        
        LOGGER.info("DCOP Run {} Region {} has server demand {}", currentDcopRun, getRegionID(), summary.getServerDemand());
        
        LOGGER.info("DCOP Run {} Region {} has inferred server demand {}", currentDcopRun, getRegionID(), inferredServerDemand);
        
        LOGGER.info("DCOP Run {} Region {} has demand {}", currentDcopRun, getRegionID(), demandMap);
        
        storeDemandLoadMap(demandMap, getRegionID());    
                                                
        createClientKeepLoadMap();
        
        LOGGER.info("AFTER DCOP FOR LOOP, Dcop run {} Region {} has getClientLoadMap {}", currentDcopRun, getRegionID(), getClientKeepLoadMap());
        
        return defaultPlan(summary);
    }
    
    /**
     * Update keepLoadMap and return the excessLoadMap
     * @param loadMap is the load map that region needs to store
     * @return the excessLoadMap
     */
    private void storeDemandLoadMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> loadMap, RegionIdentifier root) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> excessLoad = deepCopyMap(loadMap);
        
        SortedSet<ServiceIdentifier<?>> sortedServiceSet = new TreeSet<>(new SortServiceByPriorityComparator());
        sortedServiceSet.addAll(excessLoad.keySet());
                        
        // Store the load to keepLoadMap AND rootKeepLoadMap.get(getRegionID())
        rootKeepLoadMap.put(getRegionID(), new HashMap<>());
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> localKeepLoadMap = rootKeepLoadMap.get(getRegionID());
 
        for (ServiceIdentifier<?> service : sortedServiceSet) {
            for (Entry<RegionIdentifier, Double> entry : excessLoad.get(service).entrySet()) {
                RegionIdentifier client = entry.getKey();
                
                // Can't use the function getAvailableCapacity() because it depends on the keepLoadMap
                double availableCapacity = getRegionCapacity() - sumKeyKeyValues(localKeepLoadMap);
                
                // Break if region has no available capacity
                if (compareDouble(availableCapacity, 0) <= 0) {
                    break;
                }
                
//                double serviceLoad = excessLoad.get(service);
                double serviceLoad = entry.getValue();
                
                // If availableCapacity >= serviceLoad
                // Store all the load 
                if (compareDouble(availableCapacity, serviceLoad) >= 0) {
                    updateKeyKeyLoadMap(localKeepLoadMap, service, client, serviceLoad, true);
                    excessLoad.get(service).put(client, 0.0);
                } 
                // If availableCapacity <= serviceLoad
                // Store availableCapacity, reduce the loadMap by availableCapacity
                // Then break since there is no available capacity
                else {
                    updateKeyKeyLoadMap(localKeepLoadMap, service, client, availableCapacity, true);
                    excessLoad.get(service).put(client, serviceLoad - availableCapacity);
                }       
            }
            
            excessLoad.get(service).values().removeIf(v -> compareDouble(v, 0) == 0);
        }        
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
    
    private <A, B, C> Map<A, Map<B, C>> deepCopyMap(Map<A, Map<B, C>> originalMap) {
        Map<A, Map<B, C>> copiedMap = new HashMap<>();
        originalMap.forEach((key, map) -> copiedMap.put(key, new HashMap<>(map)));
        return copiedMap;
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

    /** Initialize newIteration by taking the max iteration from the inbox and increment it.
     *  @return 0 (or more if more than second DCOP run)
     */
    private void initialize() {        
        inbox = getDcopInfoProvider().getAllDcopSharedInformation().get(getRegionID());
                
        currentDcopRun = inbox.getAsynchronousMessage().getIteration();
        
        LOGGER.info("DCOP Run {} Region {} read inbox {}",currentDcopRun, getRegionID(), inbox);
                
        summary = getDcopInfoProvider().getRegionSummary(ResourceReport.EstimationWindow.LONG);
                
        retrieveAggregateCapacity(summary);
        retrieveNeighborSetFromNetworkLink(summary);
        
        LOGGER.info("DCOP Run {} Region {} has Region Capacity {}", currentDcopRun, getRegionID(), getRegionCapacity());
    }

    @Override
    protected double getAvailableCapacity() {
        return 0;
    }
}