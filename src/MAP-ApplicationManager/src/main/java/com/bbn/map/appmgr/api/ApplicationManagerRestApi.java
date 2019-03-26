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
package com.bbn.map.appmgr.api;

import io.swagger.annotations.ApiOperation;

import java.util.Collection;
import java.util.List;

import org.neo4j.ogm.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bbn.map.appmgr.exception.NotFoundException;
import com.bbn.map.appmgr.lambda.LambdaTransformer;
import com.bbn.map.appmgr.repository.ApplicationCoordinatesRepository;
import com.bbn.map.appmgr.repository.ApplicationSpecificationRepository;
import com.bbn.map.appmgr.repository.DependencyRepository;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationProfile;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.google.common.base.Strings;

@RestController
@CrossOrigin
@RequestMapping(value = "/api/${api.version}")
public class ApplicationManagerRestApi implements ApplicationManagerApi {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationManagerRestApi.class);
    
    @Autowired
    private ApplicationSpecificationRepository appSpecs;
    
    @Autowired
    private ApplicationCoordinatesRepository appCoordinates;
    
    @Autowired
    private DependencyRepository deps;
    
    @Autowired
    private LambdaTransformer lambdaTransformer;
    
    @Autowired
    Session neoSession;
     
    public ApplicationManagerRestApi() { }
    
    @Override
    public Collection<ApplicationSpecification> getAllApplicationSpecifications() {
        
        return getAllApplicationSpecifications(false);
    }
    
    @RequestMapping(value = "/apps/specs", method = RequestMethod.PUT)
    @ApiOperation(value = "Create or update an application specification")
    public ApplicationSpecification upsertApplicationSpecification(@RequestBody ApplicationSpecification spec) {
        
        logger.debug("upsert application specification: " + spec);
        
        return appSpecs.save(spec);
    }
    
    @RequestMapping(value = "/apps/specs/all", method = RequestMethod.GET)
    @ApiOperation(value = "Get all application specifications")
    public Collection<ApplicationSpecification> getAllApplicationSpecifications(@RequestParam(name = "deep", defaultValue="false") boolean isDeep) {
        
        if (isDeep) {
            return appSpecs.getAllDeep();
        }

        return appSpecs.getAllShallow();
    }
    
    @RequestMapping(value = "/apps/specs", method = RequestMethod.DELETE)
    @ApiOperation(value = "Delete an application specification")
    public ApplicationSpecification deleteApplicationSpecification(@RequestBody ApplicationCoordinates coordinates) {
        
        logger.debug("delete an app spec for coordinates: " + coordinates);
        
        List<ApplicationSpecification> result;
        
        result = appSpecs.getByGroupArtifactVersionDeep(coordinates.getGroup(), coordinates.getArtifact(), coordinates.getVersion());
        
        if (result.isEmpty()) {
            throw new NotFoundException("No application specification found for " + coordinates);
        }
        
        ApplicationSpecification toDelete = result.get(0);
        
        appSpecs.delete(toDelete);
        
        return toDelete;
    }
    
    @Override
    public ApplicationSpecification getApplicationSpecification(ApplicationCoordinates coordinates) {
        return getApplicationSpecification(coordinates, false);
    }
        
    
    @RequestMapping(value = "/apps/specs", method = RequestMethod.GET)
    @ApiOperation(value = "Get an application specification by its unique coordinates")
    public ApplicationSpecification getApplicationSpecification(@RequestBody ApplicationCoordinates coordinates, @RequestParam(name = "deep", defaultValue="false") boolean isDeep) {
        
        logger.debug("get app spec by coordinates: " + coordinates);
        
        List<ApplicationSpecification> result;
        
        if (isDeep) {
            result = appSpecs.getByGroupArtifactVersionDeep(coordinates.getGroup(), coordinates.getArtifact(), coordinates.getVersion());
        } else {
            result = appSpecs.getByGroupArtifactVersionShallow(coordinates.getGroup(), coordinates.getArtifact(), coordinates.getVersion());
        }
        
        if (result.isEmpty()) {
            throw new NotFoundException("No application specification found for " + coordinates);
        }
        
        return result.get(0);
    }
    
    @RequestMapping(value = "/deps", method = RequestMethod.PUT)
    @ApiOperation(value = "Create or update a dependency")
    public Dependency upsertDependency(@RequestBody Dependency dep) {
        
        logger.debug("upsert dependeny: " + dep);
        
        return deps.save(dep);
    }
    
    @RequestMapping(value = "/deps/all", method = RequestMethod.GET)
    @ApiOperation(value = "Get all registered dependencies")
    @Override
    public Iterable<Dependency> getAllDependencies() {
        
        return deps.findAll();
    }
    
    @RequestMapping(value = "/deps/specs", method = RequestMethod.GET)
    @ApiOperation(value = "Get a dependency by its unique coordinates")
    @Override
    public Dependency getDependency(@RequestBody ApplicationCoordinates coordinates) {
        
        logger.debug("get dependency by coordinates: " + coordinates);
        
        List<Dependency> result = deps.getByGroupArtifactVersion(coordinates.getGroup(), coordinates.getArtifact(), coordinates.getVersion());
        
        if (result.isEmpty()) {
            throw new NotFoundException("No dependency found for " + coordinates);
        }
        
        return result.get(0);
    }
    
    @RequestMapping(value = "/apps/deps", method = RequestMethod.DELETE)
    @ApiOperation(value = "Delete a dependency")
    public Dependency deleteDependency(@RequestBody ApplicationCoordinates coordinates) {
        
        logger.debug("delete a dependency for coordinates: " + coordinates);
        
        List<Dependency> result;
        
        result = deps.getByGroupArtifactVersion(coordinates.getGroup(), coordinates.getArtifact(), coordinates.getVersion());
        
        if (result.isEmpty()) {
            throw new NotFoundException("No dependency found for " + coordinates);
        }
        
        Dependency toDelete = result.get(0);
        
        deps.delete(toDelete);
        
        return toDelete;
    }
    
    @RequestMapping(value = "/apps/specs/profile/clientpressure/{v}", method = RequestMethod.GET)
    @ApiOperation(value = "Apply client pressure lambda for a given application")
    public Double executeClientPressure(@RequestBody ApplicationCoordinates coordinates, @PathVariable("v") Double v) {
        
        ApplicationSpecification spec = getApplicationSpecification(coordinates);
        
        if (spec == null) {
            throw new NotFoundException(coordinates.toString());
        }
        
        ApplicationProfile profile = spec.getProfile();
        
        java.util.Objects.requireNonNull(profile);
        
        if (Strings.isNullOrEmpty(profile.getClientPressureLambda())) {
            logger.debug("empty client pressure lambda in spec for " + coordinates + " - returning v");
            
            return v;
        }
        
        return lambdaTransformer.apply(v, profile.getClientPressureLambda());
    }
    
    @RequestMapping(value = "/apps/coordinates", method = RequestMethod.GET)
    @ApiOperation(value = "Get one application coordinates")
    @Override
    public ApplicationCoordinates getApplicationCoordinates(@RequestBody ApplicationCoordinates coordinates) {
        
        logger.debug("get app spec by coordinates: " + coordinates);
        
        return appCoordinates.findByCoordinates(coordinates);
    }
    
    @RequestMapping(value = "/apps/coordinates/all", method = RequestMethod.GET)
    @ApiOperation(value = "Get coordinates for all registered applications")
    @Override
    public Collection<ApplicationCoordinates> getAllCoordinates() {
        
        return neoSession.loadAll(ApplicationCoordinates.class);
    }
    
    @Override
    public void clear() {
        appSpecs.deleteAll();
        appCoordinates.deleteAll();
        deps.deleteAll();
    }
    
}
