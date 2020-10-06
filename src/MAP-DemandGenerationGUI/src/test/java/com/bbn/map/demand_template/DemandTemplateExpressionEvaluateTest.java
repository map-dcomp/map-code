/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
package com.bbn.map.demand_template;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Class for testing the expression evaluator in {@link DemandTemplateUtils}.
 * 
 * @author awald
 *
 */
public class DemandTemplateExpressionEvaluateTest
{
    private static final double TOLERANCE = 0.000001;
    
    /**
     * Attempts to evaluate a set of valid expressions to numeric values and a set of invalid expressions to null.
     */
    @Test
    public void test()
    {
        Map<String, Number> symbolValues = new HashMap<>();
        
        //CHECKSTYLE:OFF
        
        // Test valid expressions
        Assert.assertEquals(1.0, DemandTemplateUtils.evaluate("1.0", symbolValues), TOLERANCE);
        Assert.assertEquals(-1.0, DemandTemplateUtils.evaluate("-1.0", symbolValues), TOLERANCE);
        
        Assert.assertEquals(1.0, DemandTemplateUtils.evaluate("-1.0+2", symbolValues), TOLERANCE);
        Assert.assertEquals(1.0, DemandTemplateUtils.evaluate("-1.0 + 2", symbolValues), TOLERANCE);
        Assert.assertEquals(-1.0, DemandTemplateUtils.evaluate("     1.0      -      2    ", symbolValues), TOLERANCE);
        Assert.assertEquals(1.0, DemandTemplateUtils.evaluate("- -1.0", symbolValues), TOLERANCE);
        
        Assert.assertEquals(-0.75, DemandTemplateUtils.evaluate("-3.0 / 4.0", symbolValues), TOLERANCE);
        Assert.assertEquals(-12.0, DemandTemplateUtils.evaluate("-3.0 * 4.0", symbolValues), TOLERANCE);

        Assert.assertEquals(5.0, DemandTemplateUtils.evaluate("(5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(-5.0, DemandTemplateUtils.evaluate("(-5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(-5.0, DemandTemplateUtils.evaluate("-(5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(5.0, DemandTemplateUtils.evaluate("--(5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(5.0, DemandTemplateUtils.evaluate("-(-5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(-5.0, DemandTemplateUtils.evaluate("-(--5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(5.0, DemandTemplateUtils.evaluate("--(--5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(-5.0, DemandTemplateUtils.evaluate("--(---5.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(100.0, DemandTemplateUtils.evaluate("  (  100  )      ", symbolValues), TOLERANCE);
        Assert.assertEquals(-100.0, DemandTemplateUtils.evaluate(" - (  100  )      ", symbolValues), TOLERANCE);
        Assert.assertEquals(-6.06, DemandTemplateUtils.evaluate(" - 3.0 * (  1 / 100  + 1 ) * 2.0      ", symbolValues), TOLERANCE);
        
        
        
        // Test invalid expressions
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate(" ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("    ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("            ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("  (    )      ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("(1 + 2", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("(1", symbolValues));
        
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("1.0+", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("1.0-", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("1.0*", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("1.0/", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("*1.0", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("/1.0", symbolValues));
        
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("(", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate(")", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("()", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("1.(1-1)", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("(1-1).0", symbolValues));
        
        
        
        // Test symbol replacement
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("[app1]", symbolValues));
        symbolValues.put("[app1]", 1.0);
        Assert.assertEquals(1.0, DemandTemplateUtils.evaluate("[app1]", symbolValues), TOLERANCE);
        symbolValues.put("[app2]", 4.0);
        symbolValues.put("[app3]", 15.0);
        Assert.assertEquals(4.0, DemandTemplateUtils.evaluate("([app2])", symbolValues), TOLERANCE);
        Assert.assertEquals(38.0, DemandTemplateUtils.evaluate("([app2] - -[app3])*([app1] + 1.0)", symbolValues), TOLERANCE);
        Assert.assertEquals(60.0, DemandTemplateUtils.evaluate("[app3] / ([app1] / [app2])", symbolValues), TOLERANCE);
        Assert.assertEquals(3.75, DemandTemplateUtils.evaluate("[app3] / [app1] / [app2]", symbolValues), TOLERANCE);
        Assert.assertEquals(3.75, DemandTemplateUtils.evaluate("   [app3]*[app1]  /[app2]   ", symbolValues), TOLERANCE);
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("   [app1][app2]   ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("   [app1]//[app2]   ", symbolValues));
        Assert.assertTrue(null == DemandTemplateUtils.evaluate("   [app1]++[app2]   ", symbolValues));
        Assert.assertEquals(19.0, DemandTemplateUtils.evaluate("   [app3]--[app2]   ", symbolValues), TOLERANCE);
        
        //CHECKSTYLE:ON
    }
}
