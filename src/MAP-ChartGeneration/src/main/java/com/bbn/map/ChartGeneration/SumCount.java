/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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

/**
 * Stores a sum and a count of the number of times that the sum was added to.
 * 
 * @author awald
 *
 */
public class SumCount {
    private LoadValue sum = new LoadValue(0.0, 0.0, 0.0);
    private int count = 0;

    /**
     * Constructs a new SumCount with values initialized to 0.
     */
    public SumCount() {
        this(new LoadValue(0.0, 0.0, 0.0), 0);
    }

    /**
     * Constructs a new SumCount with the given Value sum and count.
     * 
     * @param sum
     *            the Value sum
     * @param count
     *            the number of values that contributed to sum
     */
    public SumCount(LoadValue sum, int count) {
        this.sum = sum;
        this.count = count;
    }

    /**
     * Adds to sum and increments count.
     * 
     * @param value
     *            the amount to add to sum
     */
    public void addToSum(LoadValue value) {
        sum.add(value);
        count++;
    }

    /**
     * 
     * @return the current value of sum
     */
    public LoadValue getSum() {
        return sum;
    }

    /**
     * 
     * @return the number of times that sum was added to
     */
    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "(" + sum + ", " + count + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SumCount)
            return (this.sum.equals(((SumCount) other).sum) && this.count == ((SumCount) other).count);

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sum, count);
    }
}
