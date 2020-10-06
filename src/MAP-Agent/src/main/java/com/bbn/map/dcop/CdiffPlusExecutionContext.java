package com.bbn.map.dcop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;

import org.protelis.lang.datatype.DeviceUID;
import org.protelis.lang.datatype.impl.ArrayTupleImpl;
import org.protelis.vm.CodePathFactory;
import org.protelis.vm.ExecutionEnvironment;
import org.protelis.vm.NetworkManager;
import org.protelis.vm.impl.AbstractExecutionContext;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.ap.ApLogger;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Protelis execution context for running the {@link DcopAlgorithm#CDIFF_PLUS}
 * algorithm.
 * 
 * @author jschewe
 *
 */
public class CdiffPlusExecutionContext extends AbstractExecutionContext<CdiffPlusExecutionContext> {
    private final NetworkManager networkManager;
    private final CodePathFactory codePathFactory;
    private final RegionIdentifier region;
    private final ApplicationManagerApi applicationManager;
    private final CdiffPlusAlgorithm algorithm;

    /**
     * Create a child context.
     * 
     * @param region
     *            see {@link #getRegion()}
     * @param applicationManager
     *            see {@link #getApplicationManager()}
     * @param algorithm
     *            used to access algorithm state
     * @param environment
     *            passed to parent constructor
     * @param networkManager
     *            passed to parent constructor
     * @param codePathFactory
     *            passed to parent constructor
     */
    public CdiffPlusExecutionContext(@Nonnull final RegionIdentifier region,
            @Nonnull final ApplicationManagerApi applicationManager,
            @Nonnull final CdiffPlusAlgorithm algorithm,
            @Nonnull final ExecutionEnvironment environment,
            @Nonnull final NetworkManager networkManager,
            @Nonnull final CodePathFactory codePathFactory) {
        super(environment, networkManager, codePathFactory);
        this.region = region;
        this.applicationManager = applicationManager;
        this.algorithm = algorithm;
        this.networkManager = networkManager;
        this.codePathFactory = codePathFactory;
    }

    /**
     * @return the application manager
     */
    public ApplicationManagerApi getApplicationManager() {
        return applicationManager;
    }

    /**
     * @return the current region
     */
    public RegionIdentifier getRegion() {
        return region;
    }

    /**
     * 
     * @return object for writing logging messages
     */
    public ApLogger getLogger() {
        return algorithm;
    }

    @Override
    public DeviceUID getDeviceUID() {
        return getRegion();
    }

    @Override
    public Number getCurrentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public double nextRandomDouble() {
        return Math.random();
    }

    /**
     * Get the resource summary of the region.
     * 
     * @return the resource summary of the region
     */
    public ResourceSummary getRegionSummary() {
        return this.algorithm.getResourceSummary();
    }

    /**
     * Get the capacity of a region.
     * 
     * @param summary
     *            ResourceSummary
     * @return capacity of the region
     */

    public Double getRegionCapacity(ResourceSummary summary) {
        double tempRegionCapacity;
        final NodeAttribute containersAttribute = MapUtils.COMPUTE_ATTRIBUTE;
        if (summary.getServerCapacity().containsKey(containersAttribute)) {
            tempRegionCapacity = summary.getServerCapacity().get(containersAttribute).doubleValue();
        } else {
            tempRegionCapacity = 0;
        }
        if (DcopAlgorithm.CDIFF_PLUS.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) {
            return tempRegionCapacity * AgentConfiguration.getInstance().getDcopCapacityThreshold();
        } else {
            return tempRegionCapacity;
        }

    }

    /**
     * Get the incoming load of a region through ResourceSymmary. This incoming
     * load is the summation of current load and the newly incoming load of this
     * region In the setup of the test, region ID of incomingLoadMap from
     * ResourceSymmary is unknown. We simply add all the loads from services.
     * When the incomingLoadMap from ResourceSymmary has region ID, this method
     * needs to be changed.
     * 
     * @param summary
     *            ResourceSummary
     * @param regionID
     *            ID of the region
     * @return the incoming load of the region
     */
    public Double getRegionIncomingLoad(ResourceSummary summary, RegionIdentifier regionID) {
        double regionIncomingLoad = 0;
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> incomingLoadMap = summary
                .getServerDemand();

        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : incomingLoadMap
                .entrySet()) {
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionEntry : serviceEntry.getValue()
                    .entrySet()) {

                regionIncomingLoad += regionEntry.getValue().getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0D);
            }

        }
        return regionIncomingLoad;
    }

    /**
     * convert data type ArrayTupleImpl to data type set.
     * 
     * @param a
     *            tuple in protelis consisting of region IDs of neighbors
     * @return set in Java consisting of region IDs of neighbors
     */
    public Set<RegionIdentifier> convertToSet(ArrayTupleImpl a) {
        Set<RegionIdentifier> set = new HashSet<RegionIdentifier>();
        int length = a.size();
        for (int i = 0; i < length; i++) {
            set.add((RegionIdentifier) a.get(i));
        }
        return set;
    }

    /**
     * fetch the hash code of region id.
     * 
     * @param a
     *            regionID
     * @return hashcode of regionID
     */
    public int hashValue(RegionIdentifier a) {
        return a.hashCode();
    }

    /**
     * indices of C message in array from protelis. Array has elements:
     * distance: the estimated distance from a sink or an at capacity node to
     * the nearest source availableLoad: available space of a sink. For sources
     * or at capacity nodes, availableLoad is 0 RegionId: Id of the region,
     * which is presented in string hashValue: hash code of the RegionId
     * totalAvaiLoad: accumulated available capacity of a sink or an at capacity
     * node and its children excessiveLoad: the excessive load the source to
     * which the node attaches to needs to clean
     */
    private static final int VALUE_INDEX_EXCESSIVE_LOAD = 5;
    private static final int VALUE_INDEX_AVAILABLE_LOAD = 1;
    private static final int VALUE_INDEX_REGIONID = 2;
    private static final int VALUE_INDEX_TOTALAVAILLOAD = 4;
    private static final int UNWRAP = 0;

    /**
     * filter C messages a region collects so that the total available load from
     * those C messages will not exceed the excessive load of the overloaded
     * region to which this region attaches. sinks with smaller distance
     * estimates will have a higher priority to take excessive load from the
     * source
     * 
     * @param temp
     *            tuple of C messages
     * @return union of C messages
     */
    public ArrayTupleImpl filterCmessage(ArrayTupleImpl temp) {
        temp = (ArrayTupleImpl) temp.sort();
        int length = temp.size();
        if (length == 0) {
            return temp;
        } else {
            ArrayTupleImpl firstElement = (ArrayTupleImpl) temp.get(0);
            double value = (double) firstElement.get(VALUE_INDEX_EXCESSIVE_LOAD);
            int index = 0;
            for (int i = 0; i < length; i++) {
                ArrayTupleImpl temp1 = (ArrayTupleImpl) temp.get(i);
                double tempValue = (double) temp1.get(VALUE_INDEX_AVAILABLE_LOAD);
                value = value - tempValue;
                index = i;
                if (value <= 0) {
                    break;
                }
            }
            if (value < 0) {
                temp = (ArrayTupleImpl) temp.subTuple(0, index + 1);
                temp = (ArrayTupleImpl) temp.set(index, lastElement(temp, index, value));
                return temp;
            } else {
                return temp;
            }
        }
    }

    /**
     * union of C messages.
     * 
     * @param a
     *            first C message
     * @param b
     *            second C message
     * @return union of C messages
     */
    public ArrayTupleImpl union(ArrayTupleImpl a, ArrayTupleImpl b) {
        ArrayTupleImpl temp = a.union(b);
        return temp;
    }

    /**
     * merge two tuples of C messages.
     * 
     * @param a
     *            first tuple
     * @param b
     *            second tuple
     * @return merged tuple
     */
    public ArrayTupleImpl mergeC(ArrayTupleImpl a, ArrayTupleImpl b) {
        ArrayTupleImpl temp = (ArrayTupleImpl) a.mergeAfter(b);
        return temp;
    }

    /**
     * remove duplicated messages from the same region.
     * 
     * @param a
     *            Tuple of C messages in previous round
     * @param b
     *            Tuple of C messages in current round
     * @return Tuple of C messages without duplicated messages in the same
     *         region
     */
    public ArrayTupleImpl noDuplicatedmessages(ArrayTupleImpl a, ArrayTupleImpl b) {
        ArrayTupleImpl newCmessages = (ArrayTupleImpl) b.subtract(a);
        int length1 = a.size();
        int length2 = newCmessages.size();
        if (length2 == 0) {
            return a;
        } else {
            for (int j = 0; j < length2; j++) {
                boolean flag = true;
                ArrayTupleImpl temp1 = (ArrayTupleImpl) newCmessages.get(j);
                RegionIdentifier tempId1 = (RegionIdentifier) temp1.get(VALUE_INDEX_REGIONID);
                for (int i = 0; i < length1; i++) {
                    ArrayTupleImpl temp = (ArrayTupleImpl) a.get(i);
                    RegionIdentifier tempId = (RegionIdentifier) temp.get(VALUE_INDEX_REGIONID);
                    if (tempId.equals(tempId1) && flag) {
                        a = (ArrayTupleImpl) a.set(i, temp1);
                        flag = false;
                    }
                }
                if (flag) {
                    a = (ArrayTupleImpl) a.append(temp1);
                }
            }
            return a;
        }
    }

    /**
     * @return an empty set
     */
    public Set<RegionIdentifier> emptySet() {
        Set<RegionIdentifier> a = new HashSet<RegionIdentifier>();
        return a;
    }

    /**
     * adjust the last element of the C message tuple. make the totalAvaiLoad
     * exactly the same as the excessive load of the source
     * 
     * @param a
     *            tuple in protelis consisting of C messages
     * @param b
     *            number of C messages this region will keep
     * @param c
     *            the last C message should provide a capacity of c + its
     *            original available capacity
     * @return the last C message in the form of tuple
     */
    public ArrayTupleImpl lastElement(ArrayTupleImpl a, int b, double c) {
        ArrayTupleImpl temp = (ArrayTupleImpl) a.get(b);
        double originalValue = (double) temp.get(VALUE_INDEX_AVAILABLE_LOAD);
        temp = (ArrayTupleImpl) temp.set(VALUE_INDEX_AVAILABLE_LOAD, originalValue + c);
        return temp;
    }

    /**
     * replace region's previous own C message in the tuple of C messages with
     * its current own C message.
     * 
     * @param a
     *            C messages in previous round
     * @param b
     *            region id
     * @param c
     *            new region's own C message
     * @param d
     *            default c message: []
     * @return the new tuple of C messages
     */
    public ArrayTupleImpl updateOwnCmessage(ArrayTupleImpl a, RegionIdentifier b, ArrayTupleImpl c, ArrayTupleImpl d) {
        boolean flag = true;
        int length = a.size();
        if (length == 0) {
            return c;
        } else {
            for (int i = 0; i < length; i++) {
                ArrayTupleImpl temp = (ArrayTupleImpl) a.get(i);
                RegionIdentifier tempId = (RegionIdentifier) temp.get(VALUE_INDEX_REGIONID);
                if (tempId.equals(b) && flag) {
                    flag = false;
                    a = (ArrayTupleImpl) a.set(i, c.get(UNWRAP)); // replace the
                                                                  // old C
                                                                  // message
                                                                  // with a
                                                                  // newer one
                } else if (tempId.equals(b) && !flag) {
                    a = (ArrayTupleImpl) a.set(i, d); // if there is another
                                                      // redundant C message,
                                                      // replacing it with []
                }
            }
        }
        return a;
    }

    /**
     * derive the new tuple C messages: replace chilren's C messages in the
     * previous round with those in current round.
     * 
     * @param a
     *            the region's current own C message
     * @param b
     *            tuple of C messages of the region in previous round
     * @param c
     *            tuple of C messages of the region in current round (may
     *            contain duplicated C messages from the same child due to
     *            asynchrony)
     * @return tuple of C messages
     */
    public ArrayTupleImpl updateCmessages(ArrayTupleImpl a, ArrayTupleImpl b, ArrayTupleImpl c) {
        int length = b.size();
        ArrayTupleImpl temp = (ArrayTupleImpl) c.subTupleEnd(length);
        return (ArrayTupleImpl) a.union(temp);
    }

    /**
     * remove duplicate C messages, a parent will only keep one C message from
     * one region.
     * 
     * @param a
     *            tuple of C messages
     * @param b
     *            default C message
     * @return tuple of C messages
     */
    public ArrayTupleImpl removeDuplicateCmeesages(ArrayTupleImpl a, ArrayTupleImpl b) {
        int length = a.size();
        for (int i = 0; i < length; i++) {
            boolean flag = true;
            ArrayTupleImpl temp = (ArrayTupleImpl) a.get(i);
            RegionIdentifier tempId = (RegionIdentifier) temp.get(VALUE_INDEX_REGIONID);
            for (int j = i + 1; j < length; j++) {
                ArrayTupleImpl temp1 = (ArrayTupleImpl) a.get(j);
                RegionIdentifier tempId1 = (RegionIdentifier) temp1.get(VALUE_INDEX_REGIONID);
                if (tempId1.equals(tempId)) {
                    flag = false;
                }
            }
            if (flag) {
                b = (ArrayTupleImpl) b.mergeAfter(a.subTuple(i, i + 1));
            }
        }
        return b;
    }

    /**
     * @param a
     *            received region plan
     * @param b
     *            region id
     * @param d
     *            set of neighbors
     * @return dcop plan for an underloaded region
     * 
     */
    public RegionPlan nonSourcePlan(RegionPlan a, RegionIdentifier b, Set<RegionIdentifier> d) {
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> planMap = a.getPlan();
        RegionIdentifier regionId = a.getRegion();
        // System.out.println("plan map is " + planMap + " region is " +
        // regionId);
        if (!planMap.isEmpty() && !regionId.equals(b)) {
            Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
            // System.out.println(" the plan map is " + planMap);
            for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> serviceEntry : planMap
                    .entrySet()) {
                ServiceIdentifier<?> temp = serviceEntry.getKey();
                Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
                for (Entry<RegionIdentifier, Double> regionEntry : serviceEntry.getValue().entrySet()) {
                    if (regionEntry.getKey().equals(b) && regionEntry.getValue() > 0) {
                        regionPlanBuilder.put(b, 1.0);
                        for (RegionIdentifier neighbor : d) {
                            regionPlanBuilder.put(neighbor, 0.0);
                        }
                    }
                }
                ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
                servicePlanBuilder.put(temp, regionPlan);
            }
            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
                    .build();
            final RegionPlan cdiffPlan = new RegionPlan(b, dcopPlan);
            return cdiffPlan;
        } else {
            return defaultPlan(d);
        }
    }

    /**
     * default dcop plan: take full incoming load and does not shed any load to
     * its neighbors. a sink or an at capacity node will use the default plan
     * 
     * @param neighborSet
     *            Set comprising the neighbors of the region
     * @return default dcop plan
     */
    public RegionPlan defaultPlan(Set<RegionIdentifier> neighborSet) {
        final ResourceSummary summary = algorithm.getResourceSummary();
        final Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        final Builder<RegionIdentifier, Double> regionPlanBuilder = new Builder<>();
        regionPlanBuilder.put(region, 1.0);
        for (RegionIdentifier neighbor : neighborSet) {
            regionPlanBuilder.put(neighbor, 0.0);
        }
        final ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();

        for (final ServiceIdentifier<?> serviceID : summary.getServerDemand().keySet()) {
            servicePlanBuilder.put(serviceID, regionPlan);
        }
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> defaultPlan = servicePlanBuilder
                .build();
        return new RegionPlan(summary.getRegion(), defaultPlan);
    }

    /**
     * compute the totalAvaiLoad from the tuple of C messages.
     * 
     * @param a
     *            Tuple in protelis consisting of C messages
     * @return total available capacity of a node and its children
     */
    public double accumulatedLoad(ArrayTupleImpl a) {
        int length = a.size();
        double sum = 0;
        for (int i = 0; i < length; i++) {
            ArrayTupleImpl temp = (ArrayTupleImpl) a.get(i);
            double load = (double) temp.get(VALUE_INDEX_AVAILABLE_LOAD);
            sum += load;
        }
        return sum;
    }

    /**
     * dcop plan for the source. allocate excessive load to its neighbors
     * according to their totalAvaiLoads
     * 
     * @param summary
     *            ResourceSummary
     * @param a
     *            tuple in protelis consisting of C messages
     * @param neighborSet
     *            set consisting of the neighbors of the region
     * @param b
     *            excessive load of the source
     * @return dcop plan for the source
     */
    public RegionPlan cdiffPlanSource(ResourceSummary summary,
            ArrayTupleImpl a,
            Set<RegionIdentifier> neighborSet,
            double b) {
        Map<ServiceIdentifier<?>, Double> incomingLoadMap = new HashMap<>();
        RegionIdentifier regionID = summary.getRegion();
        // create incoming load map
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> originalincomingLoadMap = summary
                .getServerDemand();
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serviceEntry : originalincomingLoadMap
                .entrySet()) {
            double tempLoad = 0;
            for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>> regionEntry : serviceEntry.getValue()
                    .entrySet()) {

                for (Entry<NodeAttribute, Double> nodeEntry : regionEntry.getValue().entrySet()) {
                    tempLoad += nodeEntry.getValue();
                }
            }
            incomingLoadMap.put(serviceEntry.getKey(), tempLoad);
        }
        Map<ServiceIdentifier<?>, Double> serviceRatioMap = new HashMap<>();
        double sum = incomingLoadMap.values().stream().mapToDouble(v -> v).sum();
        for (Entry<ServiceIdentifier<?>, Double> serEntry : incomingLoadMap.entrySet()) {
            ServiceIdentifier<?> keyService = serEntry.getKey();
            double valueLoad = serEntry.getValue();
            if (serEntry.getValue() == 0) {
                serviceRatioMap.put(keyService, 1.0);
            } else {
                serviceRatioMap.put(keyService, valueLoad / sum);
            }
        }

        // System.out.println("The incoming load map is " + incomingLoadMap + "
        // summation of the load is " + sum + " ratio map is " +
        // serviceRatioMap);
        // create load map
        // Map<ServiceIdentifier<?>, Double> loadMap = new HashMap<>();
        // final ImmutableMap<ServiceIdentifier<?>,
        // ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>>
        // LoadMap = summary.getServerLoad();
        // for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier,
        // ImmutableMap<NodeAttribute, Double>>> serviceEntry : LoadMap
        // .entrySet()) {
        // double tempLoad = 0;
        // for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>
        // regionEntry : serviceEntry.getValue().entrySet()) {
        // for (Entry<NodeAttribute, Double> nodeEntry :
        // regionEntry.getValue().entrySet()) {
        // tempLoad += nodeEntry.getValue();
        // }
        // }
        // loadMap.put(serviceEntry.getKey(), tempLoad);
        // }
        // System.out.println("The region load map is " + loadMap);
        // create total load map
        // loadMap.forEach( (k, v) -> incomingLoadMap.merge(k, v, Double::sum)
        // );
        // System.out.println("The merged load map is " + incomingLoadMap);

        // compute percentage of outgoing load for each service
        Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = new Builder<>();
        Builder<RegionIdentifier, Double> regionPlanBuilder;
        int length = a.size();
        for (Entry<ServiceIdentifier<?>, Double> serEntry : incomingLoadMap.entrySet()) {
            ServiceIdentifier<?> service = serEntry.getKey();
            double totalLoadFromThisService = serEntry.getValue();
            double tempLoadService = totalLoadFromThisService;
            regionPlanBuilder = new Builder<>();
            double ratio = 0;
            for (int i = 0; i < length; i++) {
                ArrayTupleImpl temp = (ArrayTupleImpl) a.get(i);
                if (temp.get(VALUE_INDEX_REGIONID) instanceof RegionIdentifier) {
                    RegionIdentifier neighbor = (RegionIdentifier) temp.get(VALUE_INDEX_REGIONID);
                    if (neighborSet.contains(neighbor)) {
                        double loadToshed = (double) temp.get(VALUE_INDEX_TOTALAVAILLOAD);
                        double singleRatio;
                        if (totalLoadFromThisService > 0) {
                            singleRatio = Math
                                    .min(Math.min(loadToshed * serviceRatioMap.get(service), totalLoadFromThisService)
                                            / tempLoadService, 1);
                            totalLoadFromThisService = totalLoadFromThisService
                                    - Math.min(loadToshed * serviceRatioMap.get(service), totalLoadFromThisService);
                        } else {
                            // singleRatio = Math.min((totalLoadFromThisService
                            // + loadToshed) * serviceRatioMap.get(service) /
                            // totalLoadFromThisService, 1);
                            singleRatio = 0;
                            // break;
                        }
                        regionPlanBuilder.put(neighbor, singleRatio);
                        ratio += singleRatio;
                    }
                }
            }
            regionPlanBuilder.put(regionID, Math.max(1 - ratio, 0));
            ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
            servicePlanBuilder.put(service, regionPlan);
        }
        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
                .build();
        final RegionPlan cdiffPlan = new RegionPlan(regionID, dcopPlan);
        return cdiffPlan;
    }

    @Override
    protected CdiffPlusExecutionContext instance() {
        return new CdiffPlusExecutionContext(getRegion(), getApplicationManager(), algorithm, getExecutionEnvironment(),
                networkManager, codePathFactory);
    }

}
