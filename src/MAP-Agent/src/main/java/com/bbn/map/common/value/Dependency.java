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
package com.bbn.map.common.value;

import java.io.Serializable;
import java.util.Objects;

/**
 * A dependency relationship between two applications.
 */
public class Dependency implements Serializable {

    private static final long serialVersionUID = 1L;

    private ApplicationSpecification dependentApplication;

    /**
     * 
     * @return the application that is being depended upon
     */
    public ApplicationSpecification getDependentApplication() {
        return dependentApplication;
    }

    /**
     * 
     * @param v
     *            see {@link #getDependentApplication()}
     */
    public void setDependentApplication(final ApplicationSpecification v) {
        dependentApplication = v;
    }

    private DependencyDemandFunction demandFunction;

    /**
     * 
     * @return the function that defines the relationship to the
     *         {@link #getDependentApplication()}
     */
    public DependencyDemandFunction getDemandFunction() {
        return demandFunction;
    }

    /**
     * 
     * @param v
     *            see {@link #getDemandFunction()}
     */
    public void setDemandFunction(final DependencyDemandFunction v) {
        demandFunction = v;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDependentApplication(), getDemandFunction());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!getClass().equals(obj.getClass())) {
            return false;
        } else {
            final Dependency other = (Dependency) obj;
            return Objects.equals(getDependentApplication(), other.getDependentApplication())
                    && Objects.equals(getDemandFunction(), other.getDemandFunction());
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append(" [dependentApplication=");
        builder.append(getDependentApplication());
        builder.append(", demandFunction=");
        builder.append(getDemandFunction());
        builder.append("]");
        return builder.toString();
    }

}
