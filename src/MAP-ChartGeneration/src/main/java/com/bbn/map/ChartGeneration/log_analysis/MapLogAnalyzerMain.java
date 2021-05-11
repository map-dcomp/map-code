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
package com.bbn.map.ChartGeneration.log_analysis;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.utils.MapLoggingConfigurationFactory;


/**
 * Class used to run and test the {@link MapLogAnalyzier} class.
 * 
 * @author awald
 *
 */
public final class MapLogAnalyzerMain
{    
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }
    
    private static final Logger LOGGER = LogManager.getLogger(MapLogAnalyzer.class);
    
    private MapLogAnalyzerMain()
    {
    }
    
    
    /**
     * Runs a {@link MapLogAnalyzier}.
     * 
     * @param args
     *          match file path, log file or folder path, output folder path
     */
    public static void main(String[] args)
    {
        MapLogAnalyzer mla = new MapLogAnalyzer();
        
        int p = 0;
        String categoryMatcherFilePath = (p < args.length ? args[p++] : null);
        String logPath = (p < args.length ? args[p++] : null);
        String outputFolderPath = (p < args.length ? args[p++] : null);
        
        boolean error = false;

        File categoryMatcherFile = (categoryMatcherFilePath != null ? new File(categoryMatcherFilePath) : null);
        File logFileFolder = (logPath != null ? new File(logPath) : null);
        File outputFolder = (outputFolderPath != null ? new File(outputFolderPath) : null);
        
        if (logFileFolder != null && (!logFileFolder.exists()))
        {
            LOGGER.info("Log file " + logFileFolder + " does not exist.");
            error = true;
        }
        
        if (categoryMatcherFile != null && (!categoryMatcherFile.exists() || !categoryMatcherFile.isFile()))
        {
            LOGGER.info("Match file " + categoryMatcherFile + " does not exist or is not a file.");
            error = true;
        }
        
        
        if (!error)
        {
            if (logFileFolder.isDirectory())
            {
                List<File> analysisLogFiles = new ArrayList<>();
                
                File[] files = logFileFolder.listFiles(new FileFilter()
                {
                    @Override
                    public boolean accept(File file)
                    {
                        return file.getName().matches("(map-.*|map)\\.log") && file.isFile();
                    }
                });
                
                if (files != null)
                {
                    for (File file : files)
                    {
                        analysisLogFiles.add(file);
                    }
                }
                
                
                File[] nodeFolders = logFileFolder.listFiles(new FileFilter()
                {
                    @Override
                    public boolean accept(File file)
                    {
                        return file.isDirectory();
                    }
                });
                
                LOGGER.debug("nodeFolders: {}", Arrays.toString(nodeFolders));
                
                if (nodeFolders != null)
                {
                    for (File nodeFolder : nodeFolders)
                    {
                        File agentLogFile = nodeFolder.toPath().resolve("agent").resolve("map-agent.log").toFile();
                        
                        if (agentLogFile.exists() && agentLogFile.isFile())
                        {
                            analysisLogFiles.add(agentLogFile);
                        }
                        
                        
                        File clientLogFile = nodeFolder.toPath().resolve("client").resolve("map-client.log").toFile();
                        File clientPreStartLogFile = nodeFolder.toPath().resolve("client").resolve("map-client_pre_start.log").toFile();
                        
                        if (clientLogFile.exists() && clientLogFile.isFile())
                        {
                            analysisLogFiles.add(clientLogFile);
                        }
                        
                        if (clientPreStartLogFile.exists() && clientPreStartLogFile.isFile())
                        {
                            analysisLogFiles.add(clientPreStartLogFile);
                        }
                        
                        
    
                        File dnsLogFile = nodeFolder.toPath().resolve("dns").resolve("map-dns.log").toFile();
                        
                        if (dnsLogFile.exists() && dnsLogFile.isFile())
                        {
                            analysisLogFiles.add(dnsLogFile);
                        }
                    }
                }

                LOGGER.info("Log files found for analysis: {}", analysisLogFiles);
//                String outputFolderName = file.getName().replaceAll("(map\\-|\\.log)", "");
                File[] analysisLogFilesArray = new File[analysisLogFiles.size()];
                
                for (int n = 0; n < analysisLogFilesArray.length; n++)
                {
                    analysisLogFilesArray[n] = analysisLogFiles.get(n);
                }
                
                mla.run(categoryMatcherFile, outputFolder, analysisLogFilesArray);
            }
            else if (logFileFolder.isFile())
            {
                mla.run(categoryMatcherFile, outputFolder, logFileFolder);
            }
                        
            System.exit(0);
        }
        
        System.exit(1);
    }
}
