/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.rlg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration.RlgAlgorithm;
import com.bbn.map.AgentConfiguration.RlgPriorityPolicy;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.rlg.RlgTestUtils.RegionData;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Class for testing RLG priority policy implementations.
 * 
 * See {@link ServicePriorityManager}.
 * 
 * @author awald
 *
 */
public class RlgPriorityTest {
    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    private static final Logger LOGGER = LoggerFactory.getLogger(RlgPriorityTest.class);

    private static final int NODES = 8;
    private static final int CONTAINERS_PER_NODE = 4;
    private static final Map<NodeAttribute, Double> CAPACITY_PER_NODE = ImmutableMap.of(NodeAttribute.TASK_CONTAINERS,
            CONTAINERS_PER_NODE * 1.0);

    /**
     * Simulated time interval between RLG rounds in milliseconds.
     */
    private static final long RLG_ROUND_TIME_INTERVAL_MS = 3000;

    /**
     * Tests RLG's ability to behave in accordance to a particular priority
     * policy.
     * 
     * Currently, this test runs RLG STUB with Greedy Group priority policy. The
     * test creates a set of {@link ApplicationSpecification}s consisting of a
     * certain amount services for each of a set of priority values. This is
     * referred to as the {@code priorityDistribution} of services.
     * 
     * The test starts with a full set of services to be fully loaded (given a
     * load equal to region capacity) by adding all services to
     * {@code remainingServices}. After executing RLG rounds with the load
     * applied to each service in {@code remainingServices}, service allocation
     * (resource utilization) proportions are calculated, and the service with
     * highest proportion of access to resources is removed from
     * remainingServices. A new demand scenario with load applied to only to the
     * new remainingServices is run. Demand scenarios are run until
     * remainingServices is empty.
     * 
     * The proportions for each demand scenario are used to attempt to recover
     * the original service priority order, and this order is then compared to
     * the correct service priority order.
     */
    @Test
    public void test() {
        // create ApplicationSpecifications for the specified distribution of
        // service priority values
        // service priority -> quantity in number of services
        Map<Integer, Integer> priorityDistribution = new HashMap<>();
        // CHECKSTYLE:OFF
        priorityDistribution.put(1, 1);
        priorityDistribution.put(2, 1);
        priorityDistribution.put(5, 1);
        priorityDistribution.put(7, 1);
        priorityDistribution.put(10, 1);
        // CHECKSTYLE:ON
        LOGGER.info("Priority distribution: {}", priorityDistribution);

        Set<ApplicationSpecification> serviceSpecs = generateServiceSpecsForPriorities(priorityDistribution);
        Set<ServiceIdentifier<?>> services = serviceSpecs.stream().map(s -> s.getCoordinates())
                .collect(Collectors.toSet());

        Map<Integer, List<ServiceIdentifier<?>>> servicePriorityGroups = new HashMap<>();
        Map<ServiceIdentifier<?>, Integer> serviceToPriorityMap = new HashMap<>();
        List<Integer> servicePriorityList = new ArrayList<>();
        serviceSpecs.forEach((spec) -> {
            serviceToPriorityMap.put(spec.getCoordinates(), spec.getPriority());
            servicePriorityGroups.computeIfAbsent(spec.getPriority(), k -> new ArrayList<>())
                    .add(spec.getCoordinates());
        });
        servicePriorityList.addAll(servicePriorityGroups.keySet());
        Collections.sort(servicePriorityList);
        LOGGER.debug("serviceSpecs: {}", serviceSpecs);

        final double maxServiceLoad = NODES * CONTAINERS_PER_NODE;

        List<Map<ServiceIdentifier<?>, Double>> allDemandScenarioAllocationProportions = new LinkedList<>();
        Set<ServiceIdentifier<?>> remainingServices = new HashSet<>();
        remainingServices.addAll(services);

        // run tests by eliminating services one by one
        for (int t = 0; remainingServices.size() > 0; t++) {
            // initialize region data
            RegionData initialRegionData = RlgTestUtils.createInitialRegionData(new StringRegionIdentifier("A"), NODES,
                    CAPACITY_PER_NODE, CONTAINERS_PER_NODE);
            NodeIdentifier rlgNode = initialRegionData.getActiveNodes().iterator().next();
            List<RegionData> regionDataHistory = new LinkedList<>();
            regionDataHistory.add(initialRegionData);
            LOGGER.debug("RLG node: {}", rlgNode);

            // create object for interfacing with RLG
            RlgTestWrapper rlg = new RlgTestWrapper(initialRegionData.getRegion(), rlgNode, serviceSpecs,
                    RlgAlgorithm.STUB, RlgPriorityPolicy.GREEDY_GROUP);
            rlg.initializeDcopPlan(services);
            LOGGER.debug("Using DCOP plan: {}", rlg.getDcopPlan());

            LOGGER.debug("Beginnning demand scenario {}", t);

            RegionData currentRegionData = initialRegionData;
            Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> inputDemand = computeInputDemand(services,
                    remainingServices, maxServiceLoad);
            LoadBalancerPlan rlgPlan = rlg.executeRound(currentRegionData.getResourceReports());

            final int maxRounds = 200;
            int maxConsecutiveConstantAllocationRounds = 10;
            int consecutiveConstantAllocationRounds = 0;
            Map<ServiceIdentifier<?>, Integer> prevReportServiceInstances = null;

            int round = 0;
            for (; consecutiveConstantAllocationRounds < maxConsecutiveConstantAllocationRounds
                    && round < maxRounds; round++) {
                if (round + 1 >= maxRounds) {
                    Assert.fail("Container allocation failed to converge by round " + maxRounds);
                }

                currentRegionData = regionDataHistory.get(regionDataHistory.size() - 1);

                // update RegionData in accordance to plan
                RegionData newRegionData = RlgTestUtils.updateRegionData(RLG_ROUND_TIME_INTERVAL_MS * round,
                        currentRegionData, rlgPlan, inputDemand);
                Map<ServiceIdentifier<?>, Integer> reportServiceInstances = RlgTestUtils
                        .countServiceInstances(newRegionData.getResourceReports());

                // execute RLG to produce a new plan
                rlgPlan = rlg.executeRound(currentRegionData.getResourceReports());
                Map<ServiceIdentifier<?>, Integer> rlgPlanServiceInstances = RlgTestUtils
                        .countServiceInstances(rlgPlan);
                LOGGER.debug("Round {} rlgPlan: {}", round, rlgPlan);
                LOGGER.debug("Round {}\n   inputDemand: {}\n   rlgPlanServiceInstances: {}   ({})", round,
                        inputDemandToString(inputDemand), instancesToString(rlgPlanServiceInstances, false),
                        consecutiveConstantAllocationRounds);

                // determine the number of consecutive rounds for which
                // allocation has reached the plan and remained constant
                if (reportServiceInstances.equals(prevReportServiceInstances)
                        && reportServiceInstances.equals(rlgPlanServiceInstances)) {
                    consecutiveConstantAllocationRounds++;
                } else {
                    consecutiveConstantAllocationRounds = 0;
                }

                regionDataHistory.add(newRegionData);
                prevReportServiceInstances = reportServiceInstances;
            }

            Map<ServiceIdentifier<?>, Double> allocationProportions = computeServiceAllocationProportions(
                    prevReportServiceInstances, remainingServices);
            List<ServiceIdentifier<?>> servicesDescedingByAllocationProportion = sortDescending(allocationProportions);

            LOGGER.debug(
                    "Services descending by allocation proportions {}, removing service of highest proportion ({}) from remaining services: {}.",
                    servicesDescedingByAllocationProportion, servicesDescedingByAllocationProportion.get(0),
                    remainingServices);

            remainingServices.remove(servicesDescedingByAllocationProportion.get(0));
            allDemandScenarioAllocationProportions.add(allocationProportions);

            LOGGER.debug("Ending demand scenario {} at round {} with instances {} and allocation proportions {}\n\n", t,
                    round, instancesToString(prevReportServiceInstances, false),
                    proportionsToString(allocationProportions, false));
        }

        LOGGER.info("Expected service proportions: {}", proportionsToString(
                computeServiceAllocationProportions(serviceToPriorityMap, serviceToPriorityMap.keySet()), true));

        for (int t = 0; t < allDemandScenarioAllocationProportions.size(); t++) {
            LOGGER.info("allDemandScenarioAllocationProportions {}: {}", t,
                    proportionsToString(allDemandScenarioAllocationProportions.get(t), true));
        }

        List<ServiceIdentifier<?>> expectedServicePriorityOrder = sortDescending(serviceToPriorityMap);
        List<ServiceIdentifier<?>> predictedServicePriorityOrder = predictPriorityOrder(
                allDemandScenarioAllocationProportions);
        LOGGER.info("expectedServicePriorityOrder: {}", expectedServicePriorityOrder);
        LOGGER.info("predictedServicePriorityOrder: {}", predictedServicePriorityOrder);

        Assert.assertArrayEquals(expectedServicePriorityOrder.toArray(), predictedServicePriorityOrder.toArray());
    }

    private List<ServiceIdentifier<?>> predictPriorityOrder(
            List<Map<ServiceIdentifier<?>, Double>> allTestAllocationProportions) {
        List<ServiceIdentifier<?>> priorityOrder = new LinkedList<>();

        for (int n = 0; n < allTestAllocationProportions.size(); n++) {
            priorityOrder.add(sortDescending(allTestAllocationProportions.get(n)).get(0));
        }

        return priorityOrder;
    }

    private static <S, N extends Comparable<N>> List<S> sortDescending(Map<S, N> map) {
        List<S> items = new LinkedList<>();
        items.addAll(map.keySet());

        Collections.sort(items, new Comparator<S>() {
            @Override
            public int compare(S a, S b) {
                return map.get(b).compareTo(map.get(a));
            }
        });

        return items;
    }

    private Map<ServiceIdentifier<?>, Double> computeServiceAllocationProportions(
            Map<ServiceIdentifier<?>, Integer> serviceAllocation,
            Set<ServiceIdentifier<?>> consideredServices) {
        Map<ServiceIdentifier<?>, Double> proportions = new HashMap<>();
        int total = 0;

        for (ServiceIdentifier<?> service : consideredServices) {
            total += serviceAllocation.getOrDefault(service, 0);
        }

        for (ServiceIdentifier<?> service : consideredServices) {
            proportions.put(service, serviceAllocation.get(service) * 1.0 / total);
        }

        return proportions;
    }

    private String proportionsToString(Map<ServiceIdentifier<?>, Double> serviceProportions, boolean sortByValue) {
        Map<ServiceIdentifier<?>, Number> map = new HashMap<>();

        serviceProportions.forEach((service, instances) -> {
            map.put(service, instances);
        });

        return serviceNumMapToString(map, sortByValue);
    }

    private String instancesToString(Map<ServiceIdentifier<?>, Integer> rlgPlanServiceInstances, boolean sortByValue) {
        Map<ServiceIdentifier<?>, Number> map = new HashMap<>();

        rlgPlanServiceInstances.forEach((service, instances) -> {
            map.put(service, instances);
        });

        return serviceNumMapToString(map, sortByValue);
    }

    private String serviceNumMapToString(Map<ServiceIdentifier<?>, Number> rlgPlanServiceInstances,
            boolean sortByValue) {
        StringBuilder sb = new StringBuilder();

        List<ApplicationCoordinates> services = new LinkedList<>();
        services.addAll(rlgPlanServiceInstances.keySet().stream().map(s -> (ApplicationCoordinates) s)
                .collect(Collectors.toSet()));
        Collections.sort(services, (sortByValue ? new Comparator<ApplicationCoordinates>() {
            @Override
            public int compare(ApplicationCoordinates a, ApplicationCoordinates b) {
                return Double.compare(rlgPlanServiceInstances.get(b).doubleValue(),
                        rlgPlanServiceInstances.get(a).doubleValue());
            }
        } : new Comparator<ApplicationCoordinates>() {
            @Override
            public int compare(ApplicationCoordinates a, ApplicationCoordinates b) {
                return a.getArtifact().compareTo(b.getArtifact());
            }
        }));

        for (int n = 0; n < services.size(); n++) {
            if (n > 0) {
                sb.append(", ");
            }

            sb.append(services.get(n).getArtifact());
            sb.append(" [").append(rlgPlanServiceInstances.get(services.get(n))).append("]");

        }

        return sb.toString();
    }

    private String inputDemandToString(Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> inputDemand) {
        StringBuilder sb = new StringBuilder();

        List<ApplicationCoordinates> services = new LinkedList<>();
        services.addAll(inputDemand.keySet().stream().map(s -> (ApplicationCoordinates) s).collect(Collectors.toSet()));
        Collections.sort(services, new Comparator<ApplicationCoordinates>() {
            @Override
            public int compare(ApplicationCoordinates a, ApplicationCoordinates b) {
                return a.getArtifact().compareTo(b.getArtifact());
            }
        });

        for (int n = 0; n < services.size(); n++) {
            if (n > 0) {
                sb.append(", ");
            }

            sb.append(services.get(n).getArtifact());
            sb.append(" [").append(inputDemand.get(services.get(n)).get(NodeAttribute.TASK_CONTAINERS)).append("]");

        }

        return sb.toString();
    }

    private Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> computeInputDemand(
            Set<ServiceIdentifier<?>> allServices,
            Set<ServiceIdentifier<?>> loadedServices,
            double commonLoad) {
        Map<ServiceIdentifier<?>, Double> result = new HashMap<>();

        allServices.forEach((service) -> {
            if (loadedServices.contains(service)) {
                result.put(service, commonLoad);
            } else {
                result.put(service, 0.0);
            }
        });

        return computeInputDemand(result);
    }

    private Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> computeInputDemand(
            Map<ServiceIdentifier<?>, Double> loadValues) {
        Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> result = new HashMap<>();

        loadValues.forEach((service, load) -> {
            result.computeIfAbsent(service, k -> new HashMap<>()).put(NodeAttribute.TASK_CONTAINERS, load);
        });

        return result;
    }

    private Set<ApplicationSpecification> generateServiceSpecsForPriorities(
            Map<Integer, Integer> priorityDistribution) {
        Set<ApplicationSpecification> appSpecs = new HashSet<>();

        priorityDistribution.forEach((priority, serviceCount) -> {
            for (int s = 0; s < serviceCount; s++) {
                ApplicationSpecification appSpec = new ApplicationSpecification(
                        new ApplicationCoordinates("com.bbn", "app_" + "P" + priority + "_" + s, "1.0"));
                appSpec.setPriority(priority);

                appSpecs.add(appSpec);
            }
        });

        return appSpecs;
    }

    // private int randomInclusive(int min, int max)
    // {
    // return (RANDOM.nextInt(max - min + 1) + min);
    // }

    // private int[] randInts(int size, int sum)
    // {
    // int[] ints = new int[size];
    //
    // for (int n = 0; n < sum; n++)
    // {
    // ints[RANDOM.nextInt(ints.length)]++;
    // }
    //
    // return ints;
    // }

}
