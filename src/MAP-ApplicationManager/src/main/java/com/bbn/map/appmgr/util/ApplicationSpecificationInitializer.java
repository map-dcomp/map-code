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
package com.bbn.map.appmgr.util;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bbn.map.appmgr.repository.ApplicationSpecificationRepository;
import com.bbn.map.appmgr.repository.DependencyRepository;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationProfile;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;

/*
 * for now, load a few predefined specs into database
 * 
 * TODO: import specs from resources JSON, cypher queries or CSV
 * 
 */
@Component
public class ApplicationSpecificationInitializer {
    
    @Autowired
    ApplicationSpecificationRepository asr;
    
    @Autowired
    DependencyRepository dr;
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationSpecificationInitializer.class);

    public ApplicationSpecificationInitializer() { }
    
    @PostConstruct
    private void initialize() {
        
        Dependency opencv = new Dependency(new ApplicationCoordinates("test.nu.pattern", "opencv", "2.4.9-7"));
        Dependency imagerec = new Dependency(new ApplicationCoordinates("test.com.bbn", "image-recognition", "1"));
        ApplicationSpecification imagerecHigh = new ApplicationSpecification(new ApplicationCoordinates("test.com.bbn", "image-recognition-high", "1"));
        ApplicationSpecification imagerecLow = new ApplicationSpecification(new ApplicationCoordinates("test.com.bbn", "image-recognition-low", "1"));
        Dependency fig = new Dependency(new ApplicationCoordinates("test.com.bbn", "federated-information-gateway", "1"));
        Dependency imagerecConfigLow = new Dependency(new ApplicationCoordinates("test.com.bbn", "image-recognition-configuration-low", "0.0.1"));
        Dependency imagerecConfigHigh = new Dependency(new ApplicationCoordinates("test.com.bbn", "image-recognition-configuration-high", "0.0.1"));

        imagerec.getDependencies().add(fig);
        imagerec.getDependencies().add(opencv);
        
        imagerecHigh.getDependencies().add(imagerec);
        imagerecHigh.getDependencies().add(imagerecConfigLow);
        
        imagerecLow.getDependencies().add(imagerec);
        imagerecLow.getDependencies().add(imagerecConfigLow);
        
        // set load to demand ratio in profiles
        
        ApplicationProfile irhp = new ApplicationProfile(10.0);
        irhp.setDataSizePerLoadUnit(2.0);
        irhp.setClientPressureLambda("3 * v");
        
        imagerecHigh.setProfile(irhp);
        imagerecLow.setProfile(new ApplicationProfile(5.0));
     
        dr.save(opencv);
        dr.save(imagerec);
        
        dr.save(imagerecConfigLow);
        dr.save(imagerecConfigHigh);
        
        dr.save(fig);

        asr.save(imagerecHigh);
        asr.save(imagerecLow);

        asr.save(new ApplicationSpecification(new ApplicationCoordinates("test.com.bbn", "orthomosaic", "2.4")));
        
        logger.info("predefined app specs initialized");
    }
}
