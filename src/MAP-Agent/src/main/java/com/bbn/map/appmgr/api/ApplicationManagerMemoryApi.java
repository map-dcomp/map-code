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
package com.bbn.map.appmgr.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.bbn.map.common.MutableApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;

/**
 * A memory-only version of the application manager. All data goes away when the
 * JVM exits.
 * 
 * @author jschewe
 *
 */
public final class ApplicationManagerMemoryApi implements MutableApplicationManagerApi {

    private ApplicationManagerMemoryApi() {

    }

    private static final ApplicationManagerMemoryApi INSTANCE = new ApplicationManagerMemoryApi();

    /**
     * @return the singleton instance
     */
    public static ApplicationManagerMemoryApi getInstance() {
        return INSTANCE;
    }

    private final Map<ApplicationCoordinates, ApplicationSpecification> specifications = new HashMap<>();

    @Override
    public Collection<ApplicationSpecification> getAllApplicationSpecifications() {
        return Collections.unmodifiableCollection(specifications.values());
    }

    @Override
    public ApplicationSpecification getApplicationSpecification(final ApplicationCoordinates coordinates) {
        return specifications.get(coordinates);
    }

    @Override
    public ApplicationCoordinates getApplicationCoordinates(ApplicationCoordinates coordinates) {
        if (specifications.containsKey(coordinates)) {
            return coordinates;
        } else {
            return null;
        }
    }

    @Override
    public Collection<ApplicationCoordinates> getAllCoordinates() {
        return Collections.unmodifiableCollection(specifications.keySet());
    }

    @Override
    public void clear() {
        specifications.clear();
    }

    @Override
    public ApplicationSpecification save(final ApplicationSpecification spec) {
        specifications.put(spec.getCoordinates(), spec);
        return specifications.get(spec.getCoordinates());
    }

}
