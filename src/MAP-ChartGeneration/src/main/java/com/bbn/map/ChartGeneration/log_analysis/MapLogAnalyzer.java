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
package com.bbn.map.ChartGeneration.log_analysis;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ChartGeneration.ChartGenerationUtils;




/**
 * Analyzes a Map log file by categorizing and counting log statements according to definitions in a configuration file.
 * 
 * @author awald
 *
 */
public class MapLogAnalyzer
{
    private static final Logger LOGGER = LogManager.getLogger(MapLogAnalyzer.class);
    
    private static final Double DCOP_MAX_ACCEPTABLE_MATCH_INTERVAL = 70.0;
    
    /**
     * The number of input log statements that need to be processed between every notification in the output logs
     * to show progress.
     */
    private static final int PROCESSING_LINE_NOTIFICATION_LOG_INTERVAL = 10000;
    
    
    
    // category matchers
    private List<CategoryMatcher> categoryMatcherSequence = new LinkedList<>();
    private Map<CategoryMatcher, String> categoryMatcherToLabelMap = new HashMap<>();
    private Map<CategoryMatcher, Integer> categoryMatcherToValueMap = new HashMap<>(); 
    private Map<CategoryMatcher, String> categoryMatcherOutputFileMap = new HashMap<>();   
    
    
    
    // matcher -> line matches
    private Map<CategoryMatcher, List<MatchedLine>> lineMatches = new HashMap<>();
    
    // matcher -> match count
    private Map<CategoryMatcher, Integer> categoryMatchCounts = new HashMap<>();
    
    // time -> category label -> category value
    private Map<Long, Map<String, Integer>> categoryMatchTimelineValues = new HashMap<>();
    
    
    // binned matches
    
    private long firstBinCenter = ChartGenerationUtils.DEFAULT_FIRST_BIN_CENTER;
    private long binSize = ChartGenerationUtils.DEFAULT_BIN_SIZE;
    
    // time bin -> matcher -> line matches
    private Map<Long, Map<CategoryMatcher, List<MatchedLine>>> binnedCategoryMatches = new HashMap<>();
    
    // time bin -> matcher -> line match count
    private Map<Long, Map<CategoryMatcher, Integer>> binnedCategoryMatchCounts = new HashMap<>();
    
    // time bin -> category label -> line match count
    private Map<Long, Map<String, Integer>> binnedCategoryLabelMatchCounts = new HashMap<>();
    
    
    
    // match time intervals
    
    // matcher -> time intervals
    private Map<CategoryMatcher, List<Long>> lineMatchTimeIntervals = new HashMap<>();
    
    // time bin -> matcher -> time intervals
    private Map<Long, Map<CategoryMatcher, List<Long>>> lineMatchBinnedTimeIntervals = new HashMap<>();
    
    // time bin -> matcher -> interval count
    private  Map<Long, Map<CategoryMatcher, Integer>> lineMatchTimeIntervalBinCounts = new HashMap<>();
    
    // time interval bin -> category label -> category value
    private Map<Long, Map<String, Integer>> lineMatchTimeIntervalLabelBinCounts = new HashMap<>();
    
    private Map<String, List<IntervalRangeSummary>> significantIntervals = new HashMap<>();
    
    // the minimum amount of time that must be spent within time intervals
    // of a certain length to consider these intervals significant
    private static final double TIME_WITHIN_INTERVAL_THRESHOLD = 0.1; 
    
    // the minimum amount of logarithmic space between consecutive ranges of time intervals
    // required to consider these ranges separate
    private static final double TIME_INTERVAL_RANGE_MIN_SEPARATION_FACTOR = 2.0; 
    
    
    private Long startTime = null;
    

    /**
     * Runs a Map Log Analysis on the given log files with the given set of category matchers,
     * and produces a single set of summary and results files.
     * 
     * @param categoryMatcherFile
     *          the file defining the match categories
     * @param outputFolder
     *          the output folder to write log analysis results to
     * @param logFiles
     *          the log files to analyze
     */
    public void run(File categoryMatcherFile, File outputFolder, File... logFiles)
    {   
        LOGGER.info("Matcher file: {}", categoryMatcherFile);
        LOGGER.info("Log Files: {}", Arrays.toString(logFiles));
        LOGGER.info("Output Folder: {}", outputFolder);
        
        if (categoryMatcherFile != null)
        {
            loadCategoryMatchers(categoryMatcherFile);
        }
        
        if (logFiles != null)
        {
            for (File logFile : logFiles)
            {
                scanLogFile(logFile);
            }
        }
        
        countMatches();
        
        binMatches(firstBinCenter, binSize);
        
        computeCategoryMatchTimeIntervals();
        binTimeIntervals(firstBinCenter, 10);
        
        significantIntervals.clear();
        significantIntervals = summarizeIntervals();

        outputSummary();
        outputMatches(outputFolder);
        outputLogAnalysisToCSV(outputFolder);
        
        outputSummary(new File(outputFolder + File.separator + "results_summary.txt"));
        
        LOGGER.info("Finished analysis of log files: {}.\n\n\n\n", Arrays.toString(logFiles));
    }
    
    private void outputLogAnalysisToCSV(File outputFolder)
    {
        if (outputFolder.exists() || outputFolder.mkdirs())
        {
            List<String> categoryLabels = new ArrayList<>();
            
            for (CategoryMatcher matcher : categoryMatcherSequence)
            {
                categoryLabels.add(categoryMatcherToLabelMap.get(matcher));
            }
            
            outputDataToCSV(new File(outputFolder + File.separator + "category_match_occurrences.csv"), categoryLabels, categoryMatchTimelineValues);
            outputDataToCSV(new File(outputFolder + File.separator + "binned_category_match_counts.csv"), categoryLabels, binnedCategoryLabelMatchCounts);
            outputDataToCSV(new File(outputFolder + File.separator + "binned_match_time_interval_counts.csv"), categoryLabels, lineMatchTimeIntervalLabelBinCounts);
        }
        else
        {
            LOGGER.error("Could not create output directory: {}", outputFolder);
        }
    }
    
    /**
     * Sets parameters used for binning.
     * 
     * @param firstBinCenter
     *          the value at the center of the first bin
     * @param binSize
     *          the size of each bin
     */
    public void configureBinning(long firstBinCenter, long binSize)
    {
        this.firstBinCenter = firstBinCenter;
        this.binSize = binSize;
    }
    
    
    
    /**
     * Stores summary information about a range of time intervals for messages of a certain category.
     * 
     * @author awald
     *
     */
    private static class IntervalRangeSummary
    {
        private double start;
        private double end;
        private int count;
        
        IntervalRangeSummary(double start, double end, int count)
        {
            this.start = start;
            this.end = end;
            this.count = count;
        }
        
        public double getStart()
        {
            return start;
        }
        
        public double getEnd()
        {
            return end;
        }
        
        public double getMiddle()
        {
            return ((start + end) / 2.0);
        }
        
        public double getHalfRange()
        {
            return ((end - start) / 2.0);
        }
        
        public int getCount()
        {
            return count;
        }
        
        @Override
        public String toString()
        {            
            if (getStart() == getEnd())
            {
                return (getEnd() / 1000 + " (" + getCount() + ")");
            }
            else
            {
                return (getStart() / 1000 + "-" + getEnd() / 1000 + " (" + getCount() + ")");
            }
        }
    }
    
    
    private Map<String, List<IntervalRangeSummary>> summarizeIntervals()
    {
        Map<String, List<IntervalRangeSummary>> significantIntervals = new HashMap<>();
        
        List<Long> times = new LinkedList<>();
        times.addAll(lineMatchTimeIntervalLabelBinCounts.keySet());
        Collections.sort(times);
        
        Set<String> labels = new HashSet<>();
        Map<String, Long> intervalRangeStart = new HashMap<>();
        Map<String, Integer> intervalRangeCount = new HashMap<>();
        Map<String, Long> intervalPrev = new HashMap<>();
        
        for (int n = 0; n < times.size(); n++)
        {
            Long timeInterval = times.get(n);
            Map<String, Integer> labelIntervalCount = lineMatchTimeIntervalLabelBinCounts.get(timeInterval);
            
            for (Map.Entry<String, Integer> entry : labelIntervalCount.entrySet())
            {     
                String label = entry.getKey();
                labels.add(label);
                Integer count = entry.getValue();
                
                long prev = (intervalPrev.containsKey(label) ? intervalPrev.get(label) : -1);
                long current = timeInterval;
                long rangeStart = (intervalRangeStart.containsKey(label) ? intervalRangeStart.get(label) : current);
                
                if (count * timeInterval >= TIME_WITHIN_INTERVAL_THRESHOLD)
                {
                    if (prev < 0 || current > prev * TIME_INTERVAL_RANGE_MIN_SEPARATION_FACTOR)
                    {
                        LOGGER.trace(" start interval range {}, {}", label, timeInterval);
                        if (prev >= 0)
                        {
                            // mark interval range as significant
                            significantIntervals.computeIfAbsent(label, k -> new LinkedList<>())
                                .add(new IntervalRangeSummary(rangeStart, prev, intervalRangeCount.getOrDefault(label, 0)));
                        }
                        
                        // start new interval range
                        intervalRangeStart.put(label, timeInterval);
                        intervalRangeCount.put(label, 0);
                    }                       
                    
                    intervalPrev.put(label, timeInterval);
                    intervalRangeCount.merge(label, count, Integer::sum);
                }
            }
        }
        
        
        for (String label : labels)
        {             
            long prev = (intervalPrev.containsKey(label) ? intervalPrev.get(label) : -1);
            long rangeStart = (intervalRangeStart.containsKey(label) ? intervalRangeStart.get(label) : -1);
            
            if (prev >= 0 && rangeStart >= 0)
            {
                LOGGER.trace("label: {}, prev: {}, rangeStart: {}", label, prev, rangeStart);
                significantIntervals.computeIfAbsent(label, k -> new LinkedList<>())
                    .add(new IntervalRangeSummary(rangeStart, prev, intervalRangeCount.getOrDefault(label, 0)));
            }
        }
        
        return significantIntervals;
    }
    

    private void outputSummary()
    {
        StringBuilder sb = new StringBuilder();
                
        for (CategoryMatcher matcher : categoryMatcherSequence)
        {
            String label = categoryMatcherToLabelMap.get(matcher);
            
            sb.append(label);
            sb.append(" * ");
            sb.append(categoryMatchCounts.getOrDefault(matcher, 0));
            
            sb.append("\n   significant intervals: ");
            sb.append(significantIntervals.getOrDefault(label, new LinkedList<>()));
            
            sb.append("\n");
        }
        
        LOGGER.info("Match counts: \n{}", sb.toString());
    }
    
    private void outputSummary(File outputSummaryFile)
    {
        try
        {
            if (outputSummaryFile.exists() || outputSummaryFile.createNewFile())
            {
                try (BufferedWriter out = Files.newBufferedWriter(outputSummaryFile.toPath()))
                {                        
                    Long simulationDuration = null;
                    boolean runFinished = false;
                    
                    Map<String, Boolean> criteria = new HashMap<>();
//                    criteria.put("Run Finished", false);
                    criteria.put("DCOP working", true);
                    criteria.put("No bad exceptions", true);
                    
                    StringBuilder errorStringBuilder = new StringBuilder("Errors\n");
                    
                    for (CategoryMatcher matcher : categoryMatcherSequence)
                    {
                        String label = categoryMatcherToLabelMap.get(matcher);

                        if (lineMatches.containsKey(matcher))
                        {
                            MatchedLine last = lineMatches.get(matcher).get(lineMatches.get(matcher).size() - 1);
                            simulationDuration = Math.max((simulationDuration != null ? simulationDuration : 0), last.getTimestamp() - startTime);
                        }
                        
                        switch (label)
                        {
                            case "All":
                                break;
                        
                            case "SimulationRunner":
                                if (lineMatches.containsKey(matcher))
                                {
                                    for (MatchedLine match : lineMatches.get(matcher))
                                    {
                                        if (match.getLineText().contains("The simulation has finished"))
                                        {
//                                            criteria.computeIfPresent("Run finished", (k, v) -> true);
                                            runFinished = true;
                                            simulationDuration = match.getTimestamp() - startTime;
                                        }
                                    }
                                }
                                break;
                        
                            case "DCOP":
                                if (significantIntervals.containsKey(label))
                                {
                                    for (IntervalRangeSummary interval : significantIntervals.get(label))
                                    {
                                        if (interval.getEnd() > DCOP_MAX_ACCEPTABLE_MATCH_INTERVAL * 1000)
                                        {
                                            errorStringBuilder.append("   Found large time interval between " + label + " messages (s): " + interval + "\n");
                                            criteria.computeIfPresent("DCOP working", (k, v) -> false);
                                        }    
                                    }
                                }
                                break;
                                
                            case "NullPointerException":
                            case "OptionalDataException":
                                if (lineMatches.containsKey(matcher) && lineMatches.get(matcher).size() > 0)
                                {
                                    errorStringBuilder.append("   Found " + label + "\n");
                                    criteria.computeIfPresent("No bad exceptions", (k, v) -> false);
                                }
                                break;
                                
                            default:
                                break;
                        }
                    }
                    
                    boolean success = !criteria.values().contains(false);
                    
                    out.write((success ? "SUCCESS" : "FAIL"));      
                    
                    out.write("\n\n");
                    
                    out.write("Summary\n");
                    out.write("   Run result: " + (success ? "SUCCESS" : "FAIL") + "\n");
                    
                    if (simulationDuration != null)
                    {
                        out.write("   Run duration (s): " + (simulationDuration / 1000.0) + "\n");
                    }
                    
                    out.write("   Run finished: " + runFinished + "\n");
                    
                    
                    out.write("\n\n\n");
                    
                    out.write("Requirement satisfaction\n");
                    
                    for (Map.Entry<String, Boolean> entry : criteria.entrySet())
                    {
                        String criteriumLabel = entry.getKey();
                        Boolean result = entry.getValue();
                        
                        out.write("   " + criteriumLabel + ": " + result + "\n");
                    }

                    out.write("\n\n\n");
                    
                    out.write(errorStringBuilder.toString());
                    
                    out.write("\n\n\n\n\n");
                    
                    out.write("Matches\n\n");
                    
                    for (CategoryMatcher matcher : categoryMatcherSequence)
                    {
                        String label = categoryMatcherToLabelMap.get(matcher);
                        
                        int matches = categoryMatchCounts.getOrDefault(matcher, 0);
                        out.write(label + " [" + matcher.toString() + "]");
                        
                        out.write("\n   matches: " + matches);
                        
                        if (matches > 0)
                        {
                            out.write("\n   first match time (s): " + (lineMatches.get(matcher).get(0).getTimestamp() - startTime) / 1000.0);
                            out.write("\n   last match time (s): " + (lineMatches.get(matcher).get(lineMatches.get(matcher).size() - 1).getTimestamp() - startTime) / 1000.0);
                            out.write("\n   significant time intervals between matches (s): " + significantIntervals.getOrDefault(label, new LinkedList<>()));
//                          out.write("\n   match time intervals: " + lineMatchTimeIntervals.get(matcher));
                        }

                        out.write("\n\n");
                    }
                    
                    out.flush();
                }
                
                LOGGER.info("Output summary file: {}", outputSummaryFile);
            }
            else
            {
                LOGGER.error("Could not create output file: {}", outputSummaryFile);
            }
        } catch (IOException e)
        {
            LOGGER.error("Could not write to output file: {}.", outputSummaryFile, e);
        }
    }
    
    
    private void countMatches()
    {
        lineMatches.forEach((matcher, matches) ->
        {
            categoryMatchCounts.merge(matcher, matches.size(), Integer::sum);
        });
    }
    
    private void binMatches(long firstBinCenter, long binSize)
    {
        binnedCategoryMatches.clear();
        binnedCategoryMatchCounts.clear();
        binnedCategoryLabelMatchCounts.clear();
        
        lineMatches.forEach((matcher, matches) ->
        {
            matches.forEach((match) ->
            {
                Long time = match.getTimestamp();
                Long binTime = (time != null ? ChartGenerationUtils.getBinForTime(time, firstBinCenter, binSize) : null);
                
                Map<CategoryMatcher, List<MatchedLine>> timeMatcherBin = binnedCategoryMatches.computeIfAbsent(binTime, k -> new HashMap<>());
                List<MatchedLine> binMatches = timeMatcherBin.computeIfAbsent(matcher, k -> new LinkedList<>());
                
                binMatches.add(match);
            });
        });
        
        binnedCategoryMatches.forEach((binTime, categoryBinMatches) ->
        {
            Map<CategoryMatcher, Integer> categoryCountsForBin = binnedCategoryMatchCounts.computeIfAbsent(binTime, k -> new HashMap<>());

            categoryBinMatches.forEach((matcher, matches) ->
            {
                categoryCountsForBin.merge(matcher, matches.size(), Integer::sum);
            });
        });
        
        binnedCategoryMatchCounts.forEach((binTime, categoryLabelMatches) ->
        {
            Map<String, Integer> categoryCountsForLabelBin = binnedCategoryLabelMatchCounts.computeIfAbsent(binTime, k -> new HashMap<>());
            
            categoryLabelMatches.forEach((matcher, count) ->
            {
                categoryCountsForLabelBin.merge(categoryMatcherToLabelMap.get(matcher), count, Integer::sum);
            });
        });
    }
    
    private void binTimeIntervals(long firstBinCenter, long binSize)
    {        
        lineMatchBinnedTimeIntervals.clear();
        lineMatchTimeIntervalBinCounts.clear();
        lineMatchTimeIntervalLabelBinCounts.clear();
        
        lineMatchTimeIntervals.forEach((matcher, timeIntervals) ->
        {
            timeIntervals.forEach((timeInterval) ->
            {
                Long binTime = (timeInterval != null ? ChartGenerationUtils.getBinForTime(timeInterval, firstBinCenter, binSize) : null);
                        
                Map<CategoryMatcher, List<Long>> timeIntervalsForTimeBin = lineMatchBinnedTimeIntervals.computeIfAbsent(binTime, k -> new HashMap<>());
                List<Long> timeIntervalsForCategory = timeIntervalsForTimeBin.computeIfAbsent(matcher, k -> new LinkedList<>());
                timeIntervalsForCategory.add(timeInterval);
            });
        });
        
        lineMatchBinnedTimeIntervals.forEach((binTime, categoryTimeIntervals) ->
        {
            Map<CategoryMatcher, Integer> categoryTimeIntervalCounts = lineMatchTimeIntervalBinCounts.computeIfAbsent(binTime, k -> new HashMap<>());
            
            categoryTimeIntervals.forEach((matcher, timeIntervals) ->
            {
                categoryTimeIntervalCounts.merge(matcher, timeIntervals.size(), Integer::sum);
            });
        });
        
        lineMatchTimeIntervalBinCounts.forEach((binTime, categoryLabelTimeIntervalCounts) ->
        {
            Map<String, Integer> categoryCountsForLabelBin = lineMatchTimeIntervalLabelBinCounts.computeIfAbsent(binTime, k -> new HashMap<>());
            
            categoryLabelTimeIntervalCounts.forEach((matcher, count) ->
            {
                categoryCountsForLabelBin.merge(categoryMatcherToLabelMap.get(matcher), count, Integer::sum);
            });
        });
    }
    
    private void computeCategoryMatchTimeIntervals()
    {
        lineMatchTimeIntervals.clear();
        
        lineMatches.forEach((matcher, matches) ->
        {
            List<Long> timeIntervals = lineMatchTimeIntervals.computeIfAbsent(matcher, k -> new LinkedList<>());
            
            MatchedLine prevMatch = null;
            
            for (MatchedLine match : matches)
            {
                if (prevMatch != null)
                {
                    Long timeInterval = (match.getTimestamp() != null && prevMatch.getTimestamp() != null ? match.getTimestamp() - prevMatch.getTimestamp() : null);
                    
                    if (timeInterval >= 0)
                    {
                        timeIntervals.add(timeInterval);
                    }
                    else
                    {
                        LOGGER.warn("Found log statements out of time order:\n    {}\n    {}\n", match, prevMatch);
                    }
                }
                
                prevMatch = match;
            }
        });
    }
    
    private void loadCategoryMatchers(File matchSequenceFile)
    {
        categoryMatcherSequence.clear();
        categoryMatcherToLabelMap.clear();
        categoryMatcherToValueMap.clear();
        categoryMatcherOutputFileMap.clear();
        
        try (BufferedReader br = Files.newBufferedReader(matchSequenceFile.toPath()))
        {
            String line = br.readLine();
            
            while (line != null)
            {                
                if (!line.matches("\\s*"))  // if line is not whitespace
                {
                    String[] parts = line.split("\\s?->\\s?");
                    
                    if (parts.length == 2)
                    {
                        String categoryExpression = parts[0];
                        String category = parts[1];
    
                        String[] parts2 = category.split("\\s?:\\s?");
                        String categoryLabel = category;
                        String categoryOutputConfig = null;
                        
                        if (parts2.length == 2)
                        {
                            categoryLabel = parts2[0];
                            categoryOutputConfig = parts2[1];
                        }
                        
                        CategoryMatcher matcher = new RegexCategoryMatcher(categoryExpression);
                        categoryMatcherToLabelMap.put(matcher, categoryLabel);
                        
                        if (categoryOutputConfig != null)
                        {
                            String[] categoryOutputConfigParts = categoryOutputConfig.split("\\s");
                            
                            for (String part : categoryOutputConfigParts)
                            {
                                if (part.equals("|"))
                                {
                                    categoryMatcherOutputFileMap.put(matcher, categoryLabel + ".txt");
                                }
                                else
                                {
                                    try
                                    {
                                        Integer categoryValue = Integer.parseInt(part);
                                        categoryMatcherToValueMap.put(matcher, categoryValue);
                                    } catch (NumberFormatException e)
                                    {
                                        
                                    }
                                }
                            }
                        }
                        
                        categoryMatcherSequence.add(matcher);
                    }
                    else
                    {
                        LOGGER.error("Category line '{}' has invalid syntax.", line);
                    }
                }
                
                line = br.readLine();
            }
            
            
            // fill in unspecified values in categoryMatcherToValueMap
            int v = 1;
            for (CategoryMatcher matcher : categoryMatcherSequence)
            {
                if (!categoryMatcherToValueMap.containsKey(matcher))
                {
                    Collection<Integer> values = categoryMatcherToValueMap.values();
                    
                    while (values.contains(v))
                    {
                        v++;
                    }
                    
                    categoryMatcherToValueMap.put(matcher, v);
                }
            }
            
        } catch (IOException e)
        {
            LOGGER.error("Could load category matchers.", e);
        }
        
        LOGGER.error("Loaded category matchers:\n{}", categoryMatchersToString());
    }
    
    private String categoryMatchersToString()
    {
        StringBuilder sb = new StringBuilder();
        
        for (CategoryMatcher cm : categoryMatcherSequence)
        {
            String label = categoryMatcherToLabelMap.get(cm);
            Integer value = categoryMatcherToValueMap.get(cm);
            
            sb.append(cm + " -> " + label + " : " + value);
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
   

    private void scanLogFile(File log)
    {   
        LOGGER.info("Scanning log file: {}", log);
        
        try (BufferedReader br = Files.newBufferedReader(log.toPath()))
        {
            int lineIndex = 0;
            String line = br.readLine();
            
            
            while (line != null)
            {
                LOGGER.trace("scanLogFile: read line {}: {}", lineIndex, line);
                StringBuilder logStatementBuilder = new StringBuilder(line);
                
                line = br.readLine();
                
                if (lineIndex % PROCESSING_LINE_NOTIFICATION_LOG_INTERVAL == 0)
                {
                    LOGGER.info("Processing line {}: {}", lineIndex, line);
                }
                
                // append continued statement lines together
                while (line != null && !line.matches("\\A(\\d*|\\d*-\\d*-\\d* \\d*:\\d*:\\d*,\\d*) \\[.*\\].*\r?\n?"))
                {   
                    logStatementBuilder.append("\n" + line);                   
                    line = br.readLine();
                }
                
                processLogStatement(lineIndex, logStatementBuilder.toString());
                lineIndex++;
            }
        } catch (IOException e)
        {
            LOGGER.error("Problem scanning log file: {}", log, e);
        }
        
        lineMatches.forEach((matcher, matches) ->
        {
            Collections.sort(matches, new Comparator<MatchedLine>()
            {
                @Override
                public int compare(MatchedLine a, MatchedLine b)
                {
                    if (a.getTimestamp() == null || b.getTimestamp() == null || a.getTimestamp().equals(b.getTimestamp()))
                    {
                        return (a.getLineNumber() - b.getLineNumber());
                    }
                    
                    if (a.getTimestamp() < b.getTimestamp())
                    {
                        return -1;
                    }
                    
                    if (a.getTimestamp() > b.getTimestamp())
                    {
                        return 1;
                    }
                    
                    return 0;
                }
            });
        });
    }
    
    
    private Long prevTimestamp = null;
    
    private void processLogStatement(int lineNumber, String line)
    {        
        String line2 = line.replaceAll("[\r|\n]", "");
        
        for (Map.Entry<CategoryMatcher, String> entry : categoryMatcherToLabelMap.entrySet())
        {
            CategoryMatcher matcher = entry.getKey();
            String label = entry.getValue();
            
            if (matcher.matches(line2))
            {
                LOGGER.trace("processLogStatement: Matcher {} matched line '{}'", matcher, line2);
                List<MatchedLine> lineNodes = lineMatches.computeIfAbsent(matcher, k -> new ArrayList<>());
                
                Long timestamp = MatchedLine.parseTimestamp(line);
                
                if (timestamp == null)
                {
                    timestamp = prevTimestamp;
                }
                
                if (startTime != null)
                {
                    startTime = Math.min(startTime, timestamp);
                }
                else
                {
                    startTime = timestamp;
                }
                
                MatchedLine a = new MatchedLine(lineNumber, timestamp, line);
                lineNodes.add(a);
                
                Map<String, Integer> labelValues = categoryMatchTimelineValues.computeIfAbsent(a.getTimestamp(), k -> new HashMap<>());
                labelValues.put(label, categoryMatcherToValueMap.get(matcher));
                
                prevTimestamp = timestamp;
            }
            else
            {
                LOGGER.trace("processLogStatement: Matcher {} did not match line '{}'", matcher, line2);                
            }
        }
    }
    
    
    private void outputMatches(File outputFolder)
    {
        if (outputFolder.exists() || outputFolder.mkdirs())
        {        
            lineMatches.forEach((matcher, matches) ->
            {
                String outFilename = categoryMatcherOutputFileMap.get(matcher);
                LOGGER.debug("Creating output file for matches of {}: {}", categoryMatcherToLabelMap.get(matcher), outFilename);
                
                if (outFilename != null)
                {
                    File outFile = new File(outputFolder + File.separator + outFilename);
                    
                    try 
                    {
                        if (outFile.createNewFile())
                        {    
                            try (BufferedWriter writer = Files.newBufferedWriter(outFile.toPath()))
                            {
                               String label = categoryMatcherToLabelMap.get(matcher);
                                
                                // write header
                                writer.write(label);
                                writer.write("\n\n");
                                writer.write("matcher: " + matcher.toString());
                                writer.write("\n");
                                writer.write("matches: " + lineMatches.get(matcher).size());
                                writer.write("\n");
                                writer.write("significant time intervals between matches (s): " + significantIntervals.getOrDefault(label, new LinkedList<>()));
                                writer.write("\n____________________________________________________\n\n\n\n\n\n");
                                
                                for (int n = 0; n < lineMatches.get(matcher).size(); n++)
                                {
                                    String statement = lineMatches.get(matcher).get(n).getLineText();
                                    writer.write(statement);
                                    writer.write("\n");
                                }
                            }
                        }
                    } catch (IOException e)
                    {
                        LOGGER.error("Error writing to file: {}", outFile, e);
                    }
                }
            });
        }
        else
        {
            LOGGER.error("Unable to create output directory: {}", outputFolder);
        }
    }
    
    
    
    private void outputDataToCSV(File outputFile, List<String> categoryMatcherSequenceLabels, Map<Long, Map<String, Integer>> data) {
        List<String> labels = categoryMatcherSequenceLabels;
        
        LOGGER.info("Output results CSV file: {}", outputFile);

        LOGGER.trace("outputDataToCSV: labels: {}", labels);

        String[] header = new String[1 + labels.size()];
        int n = 0;
        header[n] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;

        for (String label : labels) {
            n++;
            header[n] = label;
        }

        CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format)) {
            List<Long> times = new ArrayList<>();

            for (long time : data.keySet())
                times.add(time);

            Collections.sort(times);

            for (long time : times) {
                List<Object> row = new ArrayList<>();
                row.add(time);

                for (String label : labels) {                    
                    if (data.get(time).containsKey(label)) {
                        row.add(data.get(time).get(label));
                    } else {
                        row.add(ChartGenerationUtils.EMPTY_CELL_VALUE);
                    }
                }

                printer.printRecord(row);
            }

            LOGGER.info("Outputted data to file: {}", outputFile);

        } catch (IOException e) {
            LOGGER.error("Error writing to CSV file: {}", e);
        }
    }
}
