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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.google.common.collect.ImmutableMap;


/**
 * Provides methods to use {@link ClientLoadTemplate}s and demand curves to produce {@link ClientLoad} objects.
 * 
 * @author awald
 */
public final class DemandTemplateUtils
{
    private static final Logger LOGGER = LogManager.getLogger(DemandTemplateUtils.class);
    
    private static final String SYMBOL_CURRENT_TIME = "{time}";
    private static final String SYMBOL_CURRENT_DURATION = "{duration}";
    
    
    private DemandTemplateUtils()
    {
    }
    
    
    /**
     * Generates a list of {@link ClientLoad} objects according to the given {@link ClientLoadTemplate}s
     * and symbol values.
     * 
     * @param templates
     *          the templates for generating client load
     * @param symbolValueMaps
     *          the symbol values for each point in time for which there is demand
     * @return a list of generated {@link ClientLoad} objects
     */
    public static List<ClientLoad> generateClientLoadFromTemplates(Set<ClientLoadTemplate> templates,
            Map<Long, Map<String, Number>> symbolValueMaps)
    {
        List<Long> times = new ArrayList<>();
        times.addAll(symbolValueMaps.keySet());
        Collections.sort(times);
        
        
        List<ClientLoad> loads = new LinkedList<>();
        
        Map<String, Number> symbolValues = new HashMap<>();

        for (int n = 0; n < times.size() - 1; n++)
        {
            long time = times.get(n);
            long duration = times.get(n + 1) - times.get(n);
            
            for (ClientLoadTemplate template : templates)
            {
                // update symbol values for current time
                symbolValues.clear();
                symbolValueMaps.getOrDefault(time, new HashMap<>()).forEach((symbol, value) ->
                {
                    symbolValues.put(symbol, value);
                });
                
                symbolValues.put(SYMBOL_CURRENT_TIME, time);
                symbolValues.put(SYMBOL_CURRENT_DURATION, duration);
                
                LOGGER.trace("generateClientLoadFromTemplates: symbolValues: {}", symbolValues);
                
                if (symbolValues != null)
                {                    
                    Long startTime = time;
                    
                    if (!template.getStartTime().isEmpty())
                    {
                        startTime = evaluateToLong(template.getStartTime(), ImmutableMap.of(SYMBOL_CURRENT_TIME, time));
                    }
                    
                    Long serverDuration = duration;
                    Long networkDuration = duration;
                    
                    if (!template.getServerDuration().isEmpty())
                    {
                        serverDuration = evaluateToLong(template.getServerDuration(), symbolValues);
                    }
                    
                    if (!template.getNetworkDuration().isEmpty())
                    {
                        networkDuration = evaluateToLong(template.getNetworkDuration(), symbolValues);
                    }
                    
                    Integer numClients = evaluateToInt(template.getNumClients(), symbolValues);
                    ApplicationCoordinates service = template.getService();
                    
                    Map<NodeAttribute, Double> nodeLoad = ImmutableMap.copyOf(
                            evaluate(template.getNodeLoad(), symbolValues));
                    Map<LinkAttribute, Double> networkLoad = ImmutableMap.copyOf(
                            evaluate(template.getNetworkLoad(), symbolValues));
                
                    if (startTime != null && startTime >= 0
                            && serverDuration != null && serverDuration >= 0
                            && networkDuration != null && networkDuration >= 0
                            && numClients != null
                            && service != null
                            && nodeLoad != null
                            && networkLoad != null)
                    {
                        ClientLoad load = new ClientLoad(startTime, serverDuration, networkDuration,
                                numClients, service, ImmutableMap.copyOf(nodeLoad), ImmutableMap.copyOf(networkLoad));
                        
        
                        loads.add(load);
                        
                        LOGGER.trace("Added ClientLoad at time {} for template {} with symbol values {}.",
                                time, template, symbolValues);
                    }
                    else
                    {
                        LOGGER.warn("Skipping ClientLoad at time {} for template {} with symbol values {}.",
                                time, template, symbolValues);
                    }
                }
            }
        }
        
        Collections.sort(loads, new Comparator<ClientLoad>()
        {
            @Override
            public int compare(ClientLoad a, ClientLoad b)
            {
                return Long.compare(a.getStartTime(), b.getStartTime());
            }
        });
        
        return loads;
    }
    
    /**
     * Converts a Map of String expressions to a corresponding Map of values using the given symbol values.
     * 
     * @param <A>
     *          the type of attributes that map to expressions and values
     * @param attrExpressions
     *          attributes and their expressions, which consist of symbols and operators, to evaluate
     * @param symbolValues
     *          a map from symbols to values
     * @return the resulting map of attributes to evaluated expressions 
     */
    public static <A> Map<A, Double> evaluate(Map<A, String> attrExpressions, Map<String, Number> symbolValues)
    {
        Map<A, Double> evaluatedExpressionMap = new HashMap<>();
        
        attrExpressions.forEach((attr, expr) ->
        {
            evaluatedExpressionMap.put(attr, evaluate(expr, symbolValues));
        });
        
        return evaluatedExpressionMap;
    }
    
    
    /**
     * Converts a String expression to a integer value using the given symbol values.
     * 
     * @param expression
     *          the expression, which consists of symbols and operators, to evaluate
     * @param symbolValues
     *          a map from symbols to values
     * @return the result of evaluating the given expression with symbols replaced
     *          with their corresponding values and rounding to the nearest integer 
     */
    public static Integer evaluateToInt(String expression, Map<String, Number> symbolValues)
    {
        Double result = evaluate(expression, symbolValues);
        return (result != null ? result.intValue() : null);
    }
    
    
    /**
     * Converts a String expression to a long value using the given symbol values.
     * 
     * @param expression
     *          the expression, which consists of symbols and operators, to evaluate
     * @param symbolValues
     *          a map from symbols to values
     * @return the result of evaluating the given expression with symbols replaced
     *          with their corresponding values and rounding to the nearest long 
     */
    public static Long evaluateToLong(String expression, Map<String, Number> symbolValues)
    {
        Double result = evaluate(expression, symbolValues);
        
        return (result != null ? Math.round(result) : null);
    }
    
    
    /**
     * Converts a String expression to a numerical value using the given symbol values.
     * 
     * @param expression
     *          the expression, which consists of symbols and operators, to evaluate
     * @param symbolValues
     *          a map from symbols to values
     * @return the result of evaluating the given expression with symbols replaced
     *          with their corresponding values 
     */
    public static Double evaluate(final String expression, Map<String, ? extends Number> symbolValues)
    {
        String currentExpression = expression;
        
        // substitute values in for symbols
        for (Map.Entry<String, ? extends Number> symbolValue : symbolValues.entrySet())
        {
            currentExpression = currentExpression.replaceAll(Pattern.quote(symbolValue.getKey()), " " + symbolValue.getValue().toString() + " ");
        }
        
        Pattern subExpressionPattern = Pattern.compile(Pattern.quote("(") + "[^\\(\\)]*" + Pattern.quote(")"));
        Pattern timesDividePattern = Pattern.compile("[-]?[^\\s\\*/\\+-]+" + "\\s*" + "[\\*/]" + "\\s*" + "[-]?[^\\s\\*/\\+-]+");
        Pattern addSubtractPattern = Pattern.compile("[-]?[^\\s\\*/\\+-]+" + "\\s*" + "[\\+-]" + "\\s*" + "[-]?[^\\s\\*/\\+-]+");
        
        boolean parsing = true;
        
        while (parsing)
        {            
            Matcher subExpressionMatcher = subExpressionPattern.matcher(currentExpression);

            String subExpression = currentExpression;
            int subExpresionStart = 0;
            int subExpresionEnd = currentExpression.length();
            
            if (subExpressionMatcher.find())
            {
                subExpression = subExpressionMatcher.group();
                subExpression = subExpression.substring(1, subExpression.length() - 1);
                subExpresionStart = subExpressionMatcher.start();
                subExpresionEnd = subExpressionMatcher.end();
            }
            else
            {
                parsing = false;
            }
            
            subExpression = subExpression.replaceAll("\\A\\s*-\\s*-", "");
            subExpression = subExpression.replaceAll("-\\s*-", "+");
            subExpression = subExpression.replaceAll("\\A\\s*-\\s*", "-");
            
            // evaluate all multiplication and division in subExpression
            String timesExpression = subExpression;
            while (true)
            {
                Matcher timesExpressionMatcher = timesDividePattern.matcher(timesExpression);
                if (timesExpressionMatcher.find())
                {
                    String timesSubExpression = timesExpressionMatcher.group();
                    int timesSubExpresionStart = timesExpressionMatcher.start();
                    int timesSubExpresionEnd = timesExpressionMatcher.end();
                    
                    String[] operands = timesSubExpression.split("(\\*|/)");
                    
                    if (operands.length == 2)
                    {
                        double a = Double.NaN;
                        double b = Double.NaN;
                        
                        try
                        {
                            a = Double.parseDouble(operands[0].trim());
                            b = Double.parseDouble(operands[1].trim());
                        }
                        catch (NumberFormatException e)
                        {
                            LOGGER.error("Failed to parse operands: {}", Arrays.toString(operands));
                            return null;
                        }
                        
                        if (timesSubExpression.matches(".*\\*.*"))
                        {
                            timesSubExpression = Double.toString(a * b);
                        }
                        else if (timesSubExpression.matches(".*/.*"))
                        {
                            timesSubExpression = Double.toString(a / b);
                        }
                        else
                        {
                            LOGGER.error("Expected operator * or / in '{}'");
                            return null;
                        }
                    }
                    else
                    {
                        LOGGER.error("Not exactly 2 operands in '{}'", timesSubExpression);
                        return null;
                    }
                    
                    timesExpression = replace(timesExpression, timesSubExpresionStart, timesSubExpresionEnd, timesSubExpression);
                }
                else
                {
                    break;
                }
            }
            
            // evaluate all addition and subtraction in subExpression
            String addExpression = timesExpression;
            while (true)
            {
                Matcher addExpressionMatcher = addSubtractPattern.matcher(addExpression);
                if (addExpressionMatcher.find())
                {
                    String addSubExpression = addExpressionMatcher.group();
                    int addSubExpresionStart = addExpressionMatcher.start();
                    int addSubExpresionEnd = addExpressionMatcher.end();
                    
                    Matcher m = Pattern.compile("\\A\\s*[-]?[^\\s\\*/\\+-]+").matcher(addSubExpression);
                    m.find();
                    int indexBeforeOperator = m.end();
                    
                    String operator = "+";
                    int splitIndex = addSubExpression.indexOf(operator, indexBeforeOperator);
                    
                    if (splitIndex == -1)
                    {
                        operator = "-";
                        splitIndex = addSubExpression.indexOf(operator, indexBeforeOperator);
                    }
                    
                    if (splitIndex != -1)
                    {
                        String[] operands = {addSubExpression.substring(0, splitIndex),
                                addSubExpression.substring(splitIndex+1)};
                        
                        double a = Double.NaN;
                        double b = Double.NaN;
                        
                        try
                        {
                            a = Double.parseDouble(operands[0].trim());
                            b = Double.parseDouble(operands[1].trim());
                        }
                        catch (NumberFormatException e)
                        {
                            LOGGER.error("Failed to parse operands: {}", Arrays.toString(operands));
                            return null;
                        }
                        
                        if (addSubExpression.matches(Pattern.quote(operands[0]) + "\\+" + Pattern.quote(operands[1])))
                        {
                            addSubExpression = Double.toString(a + b);
                        }
                        else if (addSubExpression.matches(Pattern.quote(operands[0]) + "-" + Pattern.quote(operands[1])))
                        {
                            addSubExpression = Double.toString(a - b);
                        }
                        else
                        {
                            LOGGER.error("Expected operator + or - in '{}'", addSubExpression);
                            return null;
                        }
                    }
                    else
                    {
                        LOGGER.error("Not exactly 2 operands in '{}'", addSubExpression);
                        return null;
                    }
                    
                    addExpression = replace(addExpression, addSubExpresionStart, addSubExpresionEnd, addSubExpression);
                }
                else
                {
                    break;
                }
            }
            
            double a = Double.NaN;
            try
            {
                a = Double.parseDouble(addExpression.trim());
            }
            catch (NumberFormatException e)
            {
                LOGGER.error("Failed to parse '{}'", addExpression);
                return null;
            }
            
            // update the subexpression
            currentExpression = replace(currentExpression, subExpresionStart, subExpresionEnd, " " + addExpression + " ");
        }
        
        Double result = null;
        
        try
        {
            result = Double.parseDouble(currentExpression.trim());
        }
        catch (NumberFormatException e)
        {
            LOGGER.error("Failed to parse final expression: {}", result);
            return null;
        }
        
        return result;
        
        
//        String[] multiplyArguments = expression.split("\\s*" + Pattern.quote("*") + "\\s*");
//        
//        double product = 1.0;
//        
//        for (String multiplyArgument : multiplyArguments)
//        {
//            String[] addArguments = multiplyArgument.split("\\s*" + Pattern.quote("+") + "\\s*");
//            
//            double sum = 0.0;
//            
//            for (String addArgument : addArguments)
//            {
//                Number value = symbolValues.get(addArgument);
//                
//                if (value == null)
//                {
//                    try
//                    {
//                        value = Double.parseDouble(addArgument);
//                    }
//                    catch (NumberFormatException e2)
//                    {
//                        return null;
//                    }
//                }
//                
//                sum += value.doubleValue();
//            }
//            
//            product *= sum;
//        }
//        
//        LOGGER.trace("Converted expression '{}' to value {} using symbol values {}.", expression, product, symbolValues);
//        
//        return product;
    }
    
    
    private static String replace(String input, int start, int end, String substiution)
    {
        String result = input.substring(0, start) + substiution + input.substring(end, input.length());
        LOGGER.trace("Replacing '{}' indices {} to {} with {} -> '{}'", input, start, end, substiution, result);
        
        return result;
    }
}
