#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
# the exception of the dcop implementation identified below (see notes).
# 
# Dispersed Computing (DCOMP)
# Mission-oriented Adaptive Placement of Task and Data (MAP) 
# 
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
# TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#BBN_LICENSE_END
#!/usr/bin/env python3

"""
Script for analyzing a MAP log files in accordance to a specified number of seconds into a run using the first log statement as a reference time 0.0.

Features:
-Convert time in seconds into run into HIFI log file timestamps using the first timetamp as a reference of time 0.0 (--run-seconds-start, -s and --interval, -i).
-Convert time in seconds into run into epoch milliseconds using the first timetamp as a reference of time 0.0 (--run-seconds-start, -s and --interval, -i).
-Extract range of log statements from a log during a certain time period of seconds into the run (--run-seconds-start, -s and --interval, -i).
 If start seconds is not specified, starts at time 0.0 and if interval is not specified, extraction continues until the end of the log file.
 The first and last log statements processed are output beneath "Log Output" in the terminal.
-Apply a filter to log statements to output only log statements that contain a match to a regular expression (--regex-filter, -f).
-Precede each output log statements with a number indicating seconds into the run (--show-run-seconds, -t).
-Insert delay warnings into output file to indicate delays between consecutive log statements greater than a specified amount of seconds (--identify-log-delays, -d).



Examples:

Convert a range of seconds into run specified by start time (300 seconds) and duration (60 seconds) into log statement time and epoch milliseconds.
Specifying an ouput file is optional.
    ./map_log_run_time_inspection.py -L map-agent.log -s 300 -i 60
    ./map_log_run_time_inspection.py -L map-agent.log -s 300 -i 60 -o map-agent_5min-6min.log


Extract log statements for [Node-serverX1.map.dcomp] thread from map-agent.log to map-agent_serverx1_thread.log for the full duration of the run,
showing seconds into run for each log statement, and identifying delays between consecutive statements >= 30 seconds.
    ./map_log_run_time_inspection.py -L map-agent.log -d 30 -f "Node-serverX1.map.dcomp" -t -o map-agent_serverx1_thread.log


Make a copy of the log file but with each statement preceded with its time into the run in seconds.
    ./map_log_run_time_inspection.py -L map-agent.log -t -o map-agent_with_seconds.log

"""



import warnings

with warnings.catch_warnings():
    import re
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    import matplotlib.pyplot as plt
    import numpy as np
    import pandas as pd
    from time import gmtime, strftime
    from dateutil import parser
    from datetime import datetime, timedelta
    from pytz import timezone
    from dateutil import tz
    from pathlib import Path
    import map_utils

script_dir = os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)

def datetime_to_epoch_s(date_time):
    epoch_s = date_time.timestamp()
    return epoch_m

def datetime_to_epoch_ms(date_time):
    epoch_s = date_time.timestamp()
    epoch_ms = int(epoch_s * 1000)
    return epoch_ms


def record_output_line_time(ref_time, line_time, times_list):
    """
    Record the time of an output line for plotting.
    
    Arguments:
        ref_time(datetime): the reference time used for the run
        line_time(datetime): the time of the log statement
        times_list(list): the list of times to add to
    """

    minutes = (line_time - ref_time)/timedelta(minutes=1)
    times_list.append(minutes)


def plot_log_line_occurrence(output_file, title, occurrence_times):
    """
    Outputs a plot of the occurrences of the log messages in time
    
    Arguments:
        output_file(str): file path to write the plot to
        title(str): title of the plot
        occurrence_times(list(float)): list of log line occurrence times in minutes
    """

    fig, ax = map_utils.subplots()
    ax.set_title(f'{title}')
    ax.set_ylabel('')
    ax.set_xlabel('time (minutes)')

    ax.errorbar(occurrence_times, [0] * len(occurrence_times), yerr=1, fmt='o', elinewidth=1, label="{0}".format(""))
    ax.set_xlim(left=0, right=max(occurrence_times))

    fig.savefig(Path(output_file).as_posix(), format='png', bbox_extra_artists=(), bbox_inches='tight')


def main(argv=None):
    
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-L", "--log-file", dest="log_file_path", help="Log file with timestamps", required=True)
    parser.add_argument("-b", "--testbed", dest="testbed", help="The testbed used for the run. This is used to obtain time zone information.", default='')
    parser.add_argument("-s", "--run-seconds-start", dest="run_seconds_start", help="Number of seconds into run to start inspection", default='0')
    parser.add_argument("-i", "--interval", dest="run_seconds_interval", help="Interval number of seconds within run to inspect", default='')
    parser.add_argument("-d", "--identify-log-delays", dest="log_warn_delay", help="Print a warning between consecutive log statements separated by a delay larger than the value specified in seconds.", default='')
    parser.add_argument("-f", "--regex-filter", dest="regex_filter", help="Pattern to use for filtering of log statements", default='')
    parser.add_argument("-t", "--show-run-seconds", dest="show_run_seconds", action="store_true", help="Adds the number of seconds into the run to each log statement", default='')    
    parser.add_argument("-o", "--output-file", dest="log_output_file_path", help="Log statements output from inspection interval", default='')  
    parser.add_argument("-p", "--occurrence-plot", dest="occurrence_plot_output_path", help="Create a graph showing occurrences of log messages in time", default='')
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    
    
    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    

    # parse arguments
    log_file_path = args.log_file_path
    testbed = (None if args.testbed == '' else args.testbed)
    run_seconds_start = float(args.run_seconds_start)
    run_seconds_interval = (None if args.run_seconds_interval == '' else float(args.run_seconds_interval))
    log_warn_delay = (None if args.log_warn_delay == '' else timedelta(seconds=float(args.log_warn_delay)))
    regex_filter = (None if args.regex_filter == '' else re.compile(args.regex_filter))
    show_run_seconds = args.show_run_seconds
    log_output_file_path = args.log_output_file_path
    occurrence_plot_output_path = (None if args.occurrence_plot_output_path == '' else args.occurrence_plot_output_path)

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)


    time_zone=None

    #testbed_to_time_zone_map = {'emulab' : tz.tzoffset('MDT', -21600), 'dcomp' : tz.tzoffset('UTC', 0)}
    #testbed_to_time_zone_map = {'emulab' : timezone(-timedelta(hours=6), 'mdt'), 'dcomp' : timezone.utc}
    testbed_to_time_zone_map = {'emulab' : timezone('US/Mountain'), 'dcomp' : timezone('UTC')}
    if (testbed != None):
        if (testbed in testbed_to_time_zone_map):
            time_zone=testbed_to_time_zone_map.get(testbed.lower())
            get_logger().info("Found time zone for testbed '%s': %s.", testbed, time_zone)
        else:
            get_logger().fatal("Could not map testbed '%s' to timezone.", testbed)
            exit(1)


    


    get_logger().info("Log File: %s", log_file_path)

    
    with open(log_file_path, "r") as log_file:
        log_line_occurrence_times = list()

        ref_time = first_timestamp            
        
        get_logger().info("  Ref time String: %s", map_utils.datetime_to_string(ref_time))
        get_logger().info("  Ref time epoch ms: %s",  str(datetime_to_epoch_ms(ref_time)))

        get_logger().info("")
        get_logger().info("")

        start_time_within_run = ref_time + timedelta(seconds=run_seconds_start)
        get_logger().info("At %s seconds into run: ", str(run_seconds_start))
        get_logger().info("  Time within run: %s", map_utils.datetime_to_string(start_time_within_run))
        get_logger().info("    Epoch ms: %s", str(datetime_to_epoch_ms(start_time_within_run)))

        get_logger().info("")


        end_time_within_run = (None if run_seconds_interval == None else (ref_time + timedelta(seconds=(run_seconds_start+run_seconds_interval))))
            
        if (end_time_within_run != None):
            get_logger().info("At " + str(run_seconds_start + run_seconds_interval) + " seconds into run: ")
            get_logger().info("  Time within run: %s", map_utils.datetime_to_string(end_time_within_run))
            get_logger().info("    Epoch ms: %s", str(datetime_to_epoch_ms(end_time_within_run)))

        get_logger().info("\n\n\n")
        
        get_logger().info("<==================== Log Output ====================>\n")

        if (log_output_file_path != ''):
            log_output_file = open(log_output_file_path, "w")
            log_output_file.write("Log statements for run time range \n")
            log_output_file.write("  Start Time (s):  " + str(run_seconds_start) + "\n")

            if (run_seconds_interval != None):
                log_output_file.write("  End Time   (s):  " + str(run_seconds_start + run_seconds_interval) + "\n")

            log_output_file.write("\n")

            if (log_warn_delay != None):
                log_output_file.write("  Max time delay without warning (s):  " + str(log_warn_delay) + "\n")
                

            log_output_file.write("====================================================================================================\n\n\n\n")
        else:
            log_output_file = None

        log_lines_printed = 0
        prev_line = ''
        prev_line_time = None
        
        last_line_matches = False

        for line in log_file:
            output_line = line
            
            line_time = map_utils.log_line_to_time(line, time_zone)

            if (line_time != None):
                if (line_time >= start_time_within_run and (regex_filter == None or re.search(regex_filter, line))):
                    line_prefix = ""
                    if (show_run_seconds):
                        line_run_seconds = (line_time - ref_time).seconds
                        output_line = str(line_run_seconds) + ":   " + output_line

                    if (log_warn_delay != None and prev_line_time != None):
                        log_delay = line_time - prev_line_time
                        if (log_output_file != None and log_delay > log_warn_delay):
                            log_output_file.write("<--- Time delay " + str(log_delay) + " > " + str(log_warn_delay) + " --->\n")                    
                    
                    if (log_lines_printed < 1):
                        get_logger().info(line)
                    if (log_output_file != None):
                        log_output_file.write(output_line)
                        record_output_line_time(ref_time, line_time, log_line_occurrence_times)

                    prev_line = line
                    prev_line_time = line_time
                    log_lines_printed += 1
                    last_line_matches = True
                else:
                    last_line_matches = False

                if (end_time_within_run != None and line_time > end_time_within_run):
                    break
            else:
                # write a statements without a valid timestamp if and only if the the most recent line with a valid time was printed
                # this allows multiline log statements such as exceptions to be printed entirely
                if (last_line_matches):
                    if (log_output_file != None):
                        log_output_file.write(output_line)


                
        get_logger().info("  ......")
        get_logger().info(prev_line)

        # close the output file if it was specified
        if (log_output_file != None):
            log_output_file.close()


        if (occurrence_plot_output_path != None):
            if (regex_filter == None):
                title = "Times of all log lines"
            else:
                title = "Times of log lines matching regex '" + regex_filter.pattern + "'"
        
            plot_log_line_occurrence(occurrence_plot_output_path, title, log_line_occurrence_times)



if __name__ == "__main__":
    sys.exit(main())
