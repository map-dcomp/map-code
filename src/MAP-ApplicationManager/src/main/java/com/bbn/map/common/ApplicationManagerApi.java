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
package com.bbn.map.common;

import java.util.Collection;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;

/**
 * API for talking to the application manager.
 */
public interface ApplicationManagerApi {

    /**
     * @return all application specifications.
     */
    Collection<ApplicationSpecification> getAllApplicationSpecifications();

    /**
     * @return one application specification based on its unique coordinates.
     * 
     * @param coordinates
     *            The coordinates of the application to be obtained
     * 
     */
    ApplicationSpecification getApplicationSpecification(ApplicationCoordinates coordinates);

    /**
     * @return one application coordinate. Can be used to validate whether an
     *         application is registered.
     * 
     * @param coordinates
     *            An application coordinates object
     * 
     */
    ApplicationCoordinates getApplicationCoordinates(ApplicationCoordinates coordinates);

    /**
     * @return all registered application coordinates.
     * 
     */
    Collection<ApplicationCoordinates> getAllCoordinates();

    /**
     * @return all registered dependencies.
     * 
     */
    Iterable<Dependency> getAllDependencies();

    /**
     * @return one dependency.
     * 
     * @param coordinates
     *            An application coordinates object
     * 
     */
    Dependency getDependency(ApplicationCoordinates coordinates);

    /**
     * Remove all objects from the application manager. This is most useful when
     * running tests in the same JVM.
     */
    void clear();

}
