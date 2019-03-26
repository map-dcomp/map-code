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
package com.bbn.map.ap;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.appmgr.api.ApplicationManagerRestApi;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationProfile;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Some utilities for working with the application manager.
 * 
 * @author jschewe
 *
 */
public final class ApplicationManagerUtils {

    private ApplicationManagerUtils() {
    }

    /**
     * Get the container parameters for a service. This looks in the application
     * manager for the service.
     * 
     * @param s
     *            the service to find the parameters for
     * @return the container parameters
     * @throws IllegalArgumentException
     *             if s is not an instance of {@link ApplicationCoordinates}
     * @throws NullPointerException
     *             if the application specification cannot be found or the
     *             container parameters are not specified
     * @see ApplicationManagerApi#getApplicationSpecification(ApplicationCoordinates)
     */
    @Nonnull
    public static ContainerParameters getContainerParameters(final ServiceIdentifier<?> s) {
        if (!(s instanceof ApplicationCoordinates)) {
            throw new IllegalArgumentException(
                    "Found unexpected type of service, should be ApplicationCoordinates was: "
                            + (s == null ? "NULL" : s.getClass()) + " toString: " + s);
        }

        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) s);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + s);

        final ApplicationProfile appProfile = appSpec.getProfile();
        final ContainerParameters params = appProfile.getContainerParameters();

        Objects.requireNonNull(params, "Container parameters for service " + s + " are null");
        return params;
    }

    /**
     * Ensure that the application manager has all of the information from the
     * specified service configurations. This will clear any information from
     * the application manager to avoid test data from conflicting with what is
     * being loaded.
     * 
     * @param serviceConfigurations
     *            the service configurations to load into the application
     *            manager
     * @see ApplicationManagerRestApi#clear()
     */
    public static void populateApplicationManagerFromServiceConfigurations(
            @Nonnull final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations) {

        final ApplicationManagerRestApi appManager = (ApplicationManagerRestApi) AppMgrUtils.getApplicationManager();

        // clear out anything that's currently loaded
        appManager.clear();

        serviceConfigurations.forEach((service, sconfig) -> {

            // make sure the service is defined in the application manager
            final ApplicationSpecification appSpec;
            if (null == appManager.getApplicationCoordinates(service)) {
                appSpec = new ApplicationSpecification(service);
            } else {
                appSpec = appManager.getApplicationSpecification(service);
            }

            // update the profile with data from the loaded service
            // configuration

            final ApplicationProfile profile = appSpec.getProfile();
            profile.setContainerParameters(sconfig.getContainerParameters());
            profile.setServiceHostname(sconfig.getHostname());
            profile.setServiceDefaultRegion(sconfig.getDefaultNodeRegion());
            appManager.upsertApplicationSpecification(appSpec);
        });

    }
}
