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
package com.bbn.map.ap;

import java.io.Serializable;

import com.bbn.map.dcop.DcopSharedInformation;

/**
 * Object that wraps {@link DcopSharedInformation} and allows it to be safely
 * shared via AP. This class ensures that the internal
 * {@link DcopSharedInformation} is never directly accessed and therefore makes
 * the object effectively immutable.
 * 
 * @author jschewe
 *
 */
public class ImmutableDcopSharedInformation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final DcopSharedInformation message;

    /**
     * 
     * @param message
     *            {@link #getMessage()}
     */
    public ImmutableDcopSharedInformation(final DcopSharedInformation message) {
        this.message = new DcopSharedInformation(message);
    }

    /**
     * 
     * @return a copy of the internal message
     */
    public DcopSharedInformation getMessage() {
        return new DcopSharedInformation(message);
    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o == null) {
            return false;
        } else if (getClass().equals(o.getClass())) {
            final ImmutableDcopSharedInformation other = (ImmutableDcopSharedInformation) o;
            return equivalentTo(other.message);
        } else {
            return false;
        }
    }

    /**
     * This allows one to do an equality check without needing to construct
     * another instance of this object.
     * 
     * @param other
     *            the object to compare to
     * @return if the internal message is equal to {@code other}
     */
    public boolean equivalentTo(final DcopSharedInformation other) {
        return message.equals(other);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[ " + message.toString() + " ]";
    }
}
