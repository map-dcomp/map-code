/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
/**
 * Copyright 2015 Raytheon BBN Technologies
 */
package com.bbn.map.appmgr.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.appmgr.api.ApplicationManagerMemoryApi;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.MutableApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.bbn.map.common.value.DependencyDemandFunction;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utilities for dealing with the application manager.
 */
public final class AppMgrUtils {

    private AppMgrUtils() {

    }

    /**
     * @return the application manager instance
     */
    public static ApplicationManagerApi getApplicationManager() {
        return ApplicationManagerMemoryApi.getInstance();
    }

    /**
     * 
     * @return mutable application manager instance
     */
    public static MutableApplicationManagerApi getMutableApplicationManager() {
        return ApplicationManagerMemoryApi.getInstance();
    }

    /**
     * Get the specification for a service using the default instance of
     * application manager.
     * 
     * @param s
     *            the service to find the specification for
     * @return the specification
     * @see AppMgrUtils#getApplicationSpecification(ApplicationManagerApi,
     *      ServiceIdentifier)
     */
    @Nonnull
    public static ApplicationSpecification getApplicationSpecification(final ServiceIdentifier<?> s) {
        return getApplicationSpecification(getApplicationManager(), s);
    }

    /**
     * Get the specification for a service.
     * 
     * @param s
     *            the service to find the specification for
     * @param applicationManager
     *            where to look for the application specification
     * @return the specification
     * @throws IllegalArgumentException
     *             if s does not wrap an {@link ApplicationCoordinates} object.
     *             This should never fail inside MAP.
     * @throws NullPointerException
     *             if the application specification cannot be found or the
     *             container parameters are not specified
     * 
     * @see ApplicationManagerApi#getApplicationSpecification(ApplicationCoordinates)
     */
    public static ApplicationSpecification getApplicationSpecification(final ApplicationManagerApi applicationManager,
            final ServiceIdentifier<?> s) {
        final Object identifier = s.getIdentifier();
        if (!(identifier instanceof ApplicationCoordinates)) {
            throw new IllegalArgumentException(
                    "Found unexpected identifier for service, should be ApplicationCoordinates was: "
                            + (identifier == null ? "NULL" : identifier.getClass()) + " toString: " + identifier);
        }

        final ApplicationSpecification appSpec = applicationManager
                .getApplicationSpecification((ApplicationCoordinates) s);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + s);

        return appSpec;
    }

    /**
     * Get the container parameters for a service. This looks in the application
     * manager for the service.
     * 
     * @param s
     *            the service to find the parameters for
     * @return the container parameters
     * @throws NullPointerException
     *             if the container parameters are not specified
     * @see #getApplicationSpecification(ServiceIdentifier)
     */
    @Nonnull
    public static ContainerParameters getContainerParameters(final ServiceIdentifier<?> s) {
        final ApplicationSpecification appSpec = getApplicationSpecification(s);

        final ContainerParameters params = appSpec.getContainerParameters();

        Objects.requireNonNull(params, "Container parameters for service " + s + " are null");
        return params;
    }

    /**
     * Ensure that the application manager has all of the information from the
     * specified service configurations. This will clear any information from
     * the application manager to avoid test data from conflicting with what is
     * being loaded.
     * 
     * Also make sure that services in {@link MAPServices#UNPLANNED_SERVICES}
     * exist with default values to avoid issues with others retrieving these
     * specifications.
     * 
     * Public for testing.
     * 
     * @param serviceConfigurations
     *            the service configurations to load into the application
     *            manager
     * @see MutableApplicationManagerApi#clear()
     */
    public static void populateApplicationManagerFromServiceConfigurations(
            @Nonnull final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations) {

        final MutableApplicationManagerApi appManager = getMutableApplicationManager();

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

            appSpec.setContainerParameters(sconfig.getContainerParameters());
            appSpec.setServiceHostname(sconfig.getHostname());
            appSpec.setServiceDefaultRegion(sconfig.getDefaultNodeRegion());
            appSpec.setReplicable(sconfig.isReplicable());
            appSpec.setPriority(sconfig.getPriority());
            appSpec.setImageName(sconfig.getImageName());
            appSpec.setServerPort(sconfig.getServerPort());
            appManager.save(appSpec);
        });

        // make sure all unmanaged services exist
        MAPServices.UNPLANNED_SERVICES.forEach(service -> {
            final ApplicationSpecification apSpec = appManager.getApplicationSpecification(service);
            if (null == apSpec) {
                // use default values
                appManager.save(new ApplicationSpecification(service));
            }
        });

    }

    /**
     * Load the dependencies for applications into the application manager from
     * the specified file.
     * 
     * @param dependencyFile
     *            the file to read from
     * @throws IOException
     *             if there is a problem reading the file
     */
    private static void loadServiceDepencies(final Path dependencyFile) throws IOException {
        if (!Files.exists(dependencyFile)) {
            return;
        }

        final MutableApplicationManagerApi appManager = getMutableApplicationManager();
        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
        try (BufferedReader reader = Files.newBufferedReader(dependencyFile)) {
            final ImmutableList<ParsedDependency> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<ParsedDependency>>() {
                    });

            list.forEach(dep -> {
                final ApplicationSpecification srcApp = appManager.getApplicationSpecification(dep.application);
                Objects.requireNonNull(srcApp,
                        String.format("The application %s is not known to the application manager", dep.application));

                final ApplicationSpecification destApp = appManager
                        .getApplicationSpecification(dep.dependentApplication);
                Objects.requireNonNull(srcApp,
                        String.format("The dependent application %s is not known to the application manager",
                                dep.dependentApplication));

                final DependencyDemandFunction func = new DependencyDemandFunction();
                func.setNodeAttributeMultiplier(dep.nodeAttributeMultipliers);
                func.setLinkAttributeMultiplier(dep.linkAttributeMultipliers);

                func.setStartComputationParameters(dep.startStartMultiplier, dep.startServerDurationMultiplier,
                        dep.startNetworkDurationMultiplier, dep.startConstant);
                func.setServerDurationComputationParameters(dep.serverDurationServerMultiplier,
                        dep.serverDurationNetworkMultiplier, dep.serverDurationConstant);
                func.setNetworkDurationComputationParameters(dep.networkDurationServerMultiplier,
                        dep.networkDurationNetworkMultiplier, dep.networkDurationConstant);

                final Dependency dependency = new Dependency();
                dependency.setDependentApplication(destApp);
                dependency.setDemandFunction(func);

                srcApp.addDependency(dependency);
                appManager.save(srcApp);
            });

        }
    }

    // CHECKSTYLE:OFF - data class for JSON parsing
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by JSON parser")
    private static final class ParsedDependency {
        public ApplicationCoordinates application;
        public ApplicationCoordinates dependentApplication;
        public Map<NodeAttribute, Double> nodeAttributeMultipliers;
        public Map<LinkAttribute, Double> linkAttributeMultipliers;

        public double startStartMultiplier;
        public double startServerDurationMultiplier;
        public double startNetworkDurationMultiplier;
        public long startConstant;

        public double networkDurationServerMultiplier;
        public double networkDurationNetworkMultiplier;
        public long networkDurationConstant;

        public double serverDurationServerMultiplier;
        public double serverDurationNetworkMultiplier;
        public long serverDurationConstant;

    }
    // CHECKSTYLE:ON

    /**
     * Load data into the application manager.
     * 
     * @param serviceConfigurationFile
     *            the file to read the service configuration information from.
     *            It is OK if the file does not exist.
     * @param dependencyFile
     *            the file to read application dependencies from. It is OK if
     *            the file does not exist.
     * @return the service configurations that were loaded. One should prefer to
     *         use the application manager interfaces instead of these. Access
     *         to this list is provided for some initialization information.
     * @throws IOException
     *             if there is an error reading the files
     * @see {@link ServiceConfiguration#parseServiceConfigurations(Path)}
     * @see {@link #loadApplicationDependencies(Path)}
     */
    public static ImmutableMap<ApplicationCoordinates, ServiceConfiguration> loadApplicationManager(
            @Nonnull final Path serviceConfigurationFile,
            @Nonnull final Path dependencyFile) throws IOException {
        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations = ServiceConfiguration
                .parseServiceConfigurations(serviceConfigurationFile);
        populateApplicationManagerFromServiceConfigurations(serviceConfigurations);
        loadServiceDepencies(dependencyFile);

        return serviceConfigurations;
    }

}
