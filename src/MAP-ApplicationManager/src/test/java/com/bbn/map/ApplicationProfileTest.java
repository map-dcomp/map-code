/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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
package com.bbn.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import com.bbn.map.appmgr.ApplicationManagerMain;
import com.bbn.map.appmgr.api.ApplicationManagerRestApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationProfile;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@RunWith(SpringRunner.class)
@SpringBootTest
@SpringBootConfiguration
@Import(ApplicationManagerMain.class)
public class ApplicationProfileTest {

    @Autowired
    private ApplicationManagerRestApi applicationManager;

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT", justification = "the test doesn't care about the result of getClientPressureLambda")
    @Test
    public void obtainProfileUsingCoordinates() {

        // For DCOP, coordinates will be provided in resource summaries, and can
        // be preloaded as required, such as in an initalization step
        ApplicationCoordinates imageRecCoords = new ApplicationCoordinates("test.com.bbn", "image-recognition-high", "1");

        ApplicationSpecification imageRecSpec = applicationManager.getApplicationSpecification(imageRecCoords);

        assertNotNull(imageRecSpec);

        ApplicationProfile imageRecProfile = imageRecSpec.getProfile();

        assertNotNull(imageRecProfile);

        assertEquals(Double.valueOf(10.0), imageRecProfile.getDemandToLoadRatio());

        imageRecProfile.getClientPressureLambda();

    }

    @Test
    public void storeContainerParameters() {
        final ApplicationCoordinates imageRecCoords = new ApplicationCoordinates("test.com.bbn", "image-recognition-high",
                "1");

        final ApplicationSpecification imageRecSpec = applicationManager.getApplicationSpecification(imageRecCoords);

        final ImmutableMap<NodeAttribute<?>, Double> computeCapacity = ImmutableMap.of(NodeMetricName.TASK_CONTAINERS,
                1D);
        final ImmutableMap<LinkAttribute<?>, Double> networkCapacity = ImmutableMap.of(LinkMetricName.DATARATE, 100D);
        final ContainerParameters containerParams = new ContainerParameters(computeCapacity, networkCapacity);

        imageRecSpec.getProfile().setContainerParameters(containerParams);
        applicationManager.upsertApplicationSpecification(imageRecSpec);

        final ApplicationSpecification actualSpec = applicationManager.getApplicationSpecification(imageRecCoords);
        assertNotNull(actualSpec);

        final ApplicationProfile actualProfile = actualSpec.getProfile();
        assertNotNull(actualProfile);

        final ContainerParameters actualParams = actualProfile.getContainerParameters();
        assertNotNull(actualParams);
    }
}
