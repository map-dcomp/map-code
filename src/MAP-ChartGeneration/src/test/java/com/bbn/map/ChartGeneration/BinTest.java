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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test for binning.
 * 
 * @author awald
 *
 */
public class BinTest {
    /**
     * Tests if the time binning function is working properly.
     */
    @Test
    public void test() {
        long result;

        final long binSize1 = 10;
        final long time1 = 56;
        final long firstBinCenter1 = 5;
        result = ChartGenerationUtils.getBinForTime(time1, firstBinCenter1, binSize1);
        final long expectedBin1 = 55;
        assertEquals(result, expectedBin1);

        final long time2 = 49;
        final long binSize2 = 100;
        final long firstBinCenter2 = 0;
        result = ChartGenerationUtils.getBinForTime(time2, firstBinCenter2, binSize2);
        final long expectedBin2 = 0;
        assertEquals(result, expectedBin2);

        final long time3 = 50;
        result = ChartGenerationUtils.getBinForTime(time3, firstBinCenter2, binSize2);
        final long expectedBin3 = 100;
        assertEquals(result, expectedBin3);

        final long time4 = 60;
        final long firstBinCenter3 = 11;
        final long expectedBin4 = 11;
        result = ChartGenerationUtils.getBinForTime(time4, firstBinCenter3, binSize2);
        assertEquals(result, expectedBin4);

        final long time5 = 61;
        final long expectedBin5 = 111;
        result = ChartGenerationUtils.getBinForTime(time5, firstBinCenter3, binSize2);
        assertEquals(result, expectedBin5);
    }

}
