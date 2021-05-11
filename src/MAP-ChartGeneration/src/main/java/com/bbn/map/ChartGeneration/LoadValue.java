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
package com.bbn.map.ChartGeneration;

import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Stores a load value, demand value, and the corresponding capacity
 * 
 * @author awald
 *
 */
/* package */ class LoadValue {
    private double load;
    private double demand;
    private double capacity;

    /**
     * Constructs a {@link LoadValue} initialized to 0 for load and 0 for capacity.
     */
    LoadValue() {
        this(0.0, 0.0, 0.0);
    }

    /**
     * Constructs a {@link LoadValue} with the given load and capacity.
     * 
     * @param load
     *            the load to store in this {@link LoadValue}
     * @param demand
     *            the demand to store in this {@link LoadValue}
     * @param capacity
     *            the corresponding capacity to store in this {@link LoadValue}
     */
    LoadValue(double load, double demand, double capacity) {
        this.load = load;
        this.demand = demand;
        this.capacity = capacity;
    }

    /**
     * @return the load value
     */
    public double getLoad() {
        return load;
    }   
    
    /**
     * @return the demand value
     */
    public double getDemand() {
        return demand;
    }

    /**
     * @return the capacity value
     */
    public double getCapacity() {
        return capacity;
    }

    /**
     * Sets the load value.
     * 
     * @param load
     *            the new load value to store
     */
    public void setLoad(double load) {
        this.load = load;
    }

    /**
     * Sets the demand value.
     * 
     * @param demand
     *            the new demand value to store
     */
    public void setDemand(double demand) {
        this.demand = demand;
    }

    /**
     * Sets the capacity value.
     * 
     * @param capacity
     *            the new capacity that corresponds to load
     */
    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    /**
     * Adds a {@link LoadValue} to this {@link LoadValue}.
     * 
     * @param v
     *            the {@link LoadValue} to add
     */
    public void add(LoadValue v) {
        this.load += v.load;
        this.demand += v.demand;
        this.capacity += v.capacity;
    }

    @Override
    public String toString() {
        return "[" + load + ", " + demand + ", " + capacity + "]";
    }

    @SuppressFBWarnings(value = "FE_FLOATING_POINT_EQUALITY", justification = "Needed for equality check")
    @Override
    public boolean equals(Object v) {
        if (null == v) {
            return false;
        } else if (v == this) {
            return true;
        } else if (v instanceof LoadValue) {
            final LoadValue other = (LoadValue) v;
            return this.load == other.load && this.demand == other.demand && this.capacity == other.capacity;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(load, demand, capacity);
    }
    
    /**
     * Creates a new {@link LoadValue} by summing the components of two {@link LoadValue}s together
     * 
     * @param a
     *      the first {@link LoadValue}
     * @param b
     *      the second {@link LoadValue}
     * @return the sum {@link LoadValue}
     */
    public static LoadValue sum(LoadValue a, LoadValue b)
    {
        return new LoadValue(a.load + b.load, a.demand + b.demand, a.capacity + b.capacity);
    }
}
