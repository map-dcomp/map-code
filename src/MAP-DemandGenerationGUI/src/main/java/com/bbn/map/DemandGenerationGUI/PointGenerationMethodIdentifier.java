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
package com.bbn.map.DemandGenerationGUI;

/**
 * Enumerated type for identifying the different methods for generating demand points.
 * 
 * @author awald
 *
 */
public enum PointGenerationMethodIdentifier
{
    /**
     * Identifies method that evenly spaces points across the line distance.
     */
    POINT_GEN_METHOD_TIME_VALUE_LINE_DISTANCE ("Time Value Line Distance"),
    
    /**
     * Identifies method that evenly spaces points in time.
     */
    POINT_GEN_METHOD_TIME_DISTANCE ("Time Distance"),
    
    /**
     * Identifies method that evenly spaces points in time using a given interval in milliseconds.
     * This method produces points aligned in time across all lines.
     */
    POINT_GEN_METHOD_COMMON_TIME_INTERVAL ("Common Time Interval"),
    
    /**
     * Identifies method that evenly spaces points across changes in Y value.
     */
    POINT_GEN_METHOD_VALUE_DISTANCE ("Value Distance"),
    
    /**
     * Identifies method that simply copies the user placed points for the generated points.
     */
    POINT_GEN_METHOD_COPY_USER_POINTS ("Copy User Points");
    

    
    private final String label;
    
    PointGenerationMethodIdentifier(String label)
    {
        this.label = label;
    }
    
    /** 
     * @return the String label for this point generation method.
     */
    public String getLabel()
    {
        return label;
    }
    
    @Override
    public String toString()
    {
        return getLabel();
    }
}
