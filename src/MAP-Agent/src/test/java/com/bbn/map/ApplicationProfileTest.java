/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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
package com.bbn.map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.MutableApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.bbn.map.common.value.DependencyDemandFunction;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for {@link ApplicationProfile}.
 * 
 * @author jschewe
 *
 */
public class ApplicationProfileTest {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new ExternalResource() {
                @Override
                public void before() {
                    final MutableApplicationManagerApi appManager = AppMgrUtils.getMutableApplicationManager();
                    final ApplicationSpecification service1Spec = new ApplicationSpecification(service1);
                    appManager.save(service1Spec);
                }
            });

    private final ApplicationCoordinates service1 = new ApplicationCoordinates("test.com.bbn", "service1", "1");

    /**
     * Make sure that we can get an application specification out of the
     * application manager by application coordinate.
     */
    @Test
    public void obtainProfileUsingCoordinates() {
        final ApplicationSpecification imageRecSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification(service1);

        assertNotNull(imageRecSpec);
    }

    /**
     * Test that storing
     * {@link ApplicationSpecification#getContainerParameters()} works.
     */
    @Test
    public void storeContainerParameters() {
        final MutableApplicationManagerApi applicationManager = AppMgrUtils.getMutableApplicationManager();
        final ApplicationSpecification service1Spec = applicationManager.getApplicationSpecification(service1);

        final ImmutableMap<NodeAttribute<?>, Double> computeCapacity = ImmutableMap.of(NodeMetricName.TASK_CONTAINERS,
                1D);
        final ImmutableMap<LinkAttribute<?>, Double> networkCapacity = ImmutableMap.of(LinkMetricName.DATARATE_TX, 100D,
                LinkMetricName.DATARATE_RX, 100D);
        final ContainerParameters containerParams = new ContainerParameters(computeCapacity, networkCapacity);

        service1Spec.setContainerParameters(containerParams);
        applicationManager.save(service1Spec);

        final ApplicationSpecification actualSpec = applicationManager.getApplicationSpecification(service1);
        assertNotNull(actualSpec);

        final ContainerParameters actualParams = actualSpec.getContainerParameters();
        assertNotNull(actualParams);
    }

    private final ApplicationCoordinates service2 = new ApplicationCoordinates("test.com.bbn", "service2", "1");

    private void createService2() {
        final ApplicationSpecification service2Spec = new ApplicationSpecification(service2);
        final MutableApplicationManagerApi appManager = AppMgrUtils.getMutableApplicationManager();
        appManager.save(service2Spec);

    }

    /**
     * Test that dependencies can be properly stored.
     */
    @Test
    public void storeDependencyFunction() {
        final MutableApplicationManagerApi applicationManager = AppMgrUtils.getMutableApplicationManager();

        createService2();

        final ApplicationSpecification service1Spec = applicationManager.getApplicationSpecification(service1);

        final ApplicationSpecification service2Spec = applicationManager.getApplicationSpecification(service2);

        final Dependency dep1to2 = new Dependency();
        dep1to2.setDependentApplication(service2Spec);

        final DependencyDemandFunction func = new DependencyDemandFunction();
        func.setNodeAttributeMultiplier(Collections.singletonMap(NodeMetricName.CPU, 2D));
        dep1to2.setDemandFunction(func);

        service1Spec.setDependencies(Collections.singleton(dep1to2));

        applicationManager.save(service1Spec);

        final ApplicationSpecification actualSpec = applicationManager.getApplicationSpecification(service1);
        assertNotNull(actualSpec);

        final Set<Dependency> actualDependencies = actualSpec.getDependencies();
        assertThat(actualDependencies, notNullValue());
        assertThat(actualDependencies, hasSize(1));

        final Dependency actualDep = actualDependencies.iterator().next();
        assertThat(actualDep, notNullValue());

        assertThat(actualDep, is(dep1to2));
    }
}
