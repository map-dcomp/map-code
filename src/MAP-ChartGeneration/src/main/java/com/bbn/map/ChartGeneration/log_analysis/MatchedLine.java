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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores information for a log message when it is matched into a particular category.
 * 
 * @author awald
 *
 */
public class MatchedLine
{
    private static final Logger LOGGER = LogManager.getLogger(MatchedLine.class);
    
    
    private int lineNumber;
    private Long timestamp;
    private String lineText;
    
    
    /**
     * Creates a new {@link MatchedLine}.
     * 
     * @param lineNumber
     *          the index of the line in the log file
     * @param timestamp
     *          the timestamp for the log message
     * @param lineText
     *          the text of the log message
     */
    public MatchedLine(int lineNumber, long timestamp, String lineText)
    {
        this.lineNumber = lineNumber;
        this.lineText = lineText;
        this.timestamp = timestamp;
    }
    
    /**
     * Parses the timestamp from the text of a line.
     * 
     * @param lineText
     *          the text of a line
     * @return the timestamp or null if the timestamp could not be found
     */
    public static Long parseTimestamp(String lineText)
    {
        int endIndex = lineText.indexOf(" [");
        
        if (endIndex > 0)
        {
            String timeStr = lineText.substring(0, endIndex);
            
            try
            {
                long time = Long.parseLong(timeStr);
                LOGGER.trace("parseTimestamp: Parsed long time stamp from '{}' to {}", timeStr, time);
                return time;
                
            } catch (NumberFormatException e)
            {
                try
                {
                    long time;
                    
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
                    LocalDateTime dateTime = LocalDateTime.parse(timeStr, formatter);
                    time = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                    LOGGER.trace("parseTimestamp: Parsed formatted time stamp from '{}' to {}", timeStr, time);
                    return time;
                    
                } catch (DateTimeParseException e2)
                {
                    LOGGER.trace("parseTimestamp: Failed to parse time stamp from '{}'", timeStr);
                    return null;
                }
            }
        }
        else
        {
            LOGGER.trace("parseTimestamp: Failed to parse time stamp from '{}'", lineText);
            return null;
        }
    }

    /**
     * @return the index of the line in the log file
     */
    public int getLineNumber()
    {
        return lineNumber;
    }

    /**
     * @return the timestamp of the log message
     */
    public Long getTimestamp()
    {
        return timestamp;
    }

    /**
     * @return the tex tof the log message
     */
    public String getLineText()
    {
        return lineText;
    }
    
    
    @Override
    public String toString()
    {
        return (lineNumber + ": " + lineText);
    }
}
