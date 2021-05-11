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

This script is used to find time intervals for which there are missing network data collected from particular iftop instances for particular network interfaces.
The script does this by scanning the log files for a single node and relies on logging for IftopProcesor to be set to TRACE level.

In particular, this script scans for disprepancy situations where an iftop process for the virtual interface for a container displays netork data
while iftop processes for physical interfaces that have previously reported data for this container do not currently display the data.


To run on all nodes:
./all_node_iftop_missing_data_scan.sh --sim [run data folder] --output [missing data results output folder] 
See  iftop_missing_data.log in the output folder for a summary of results for all nodes. See each [node]-iftop_missing_data.log file for a log of relevant events on the node.

To run on a single node's log files:
./map_log_iftop_missing_data_scan.py -L [log file directory] -d [minimum interval direction to show in seconds] -d [smallest missing data interval to report in seconds]
The output will have logging of relevant events and a summary at the end.

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
    from pathlib import Path
    from time import gmtime, strftime
    from dateutil import parser
    from datetime import datetime, timedelta
    from pytz import timezone
    from dateutil import tz
    sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
    import map_utils
    import fileinput

script_dir = os.path.abspath(os.path.dirname(__file__))

# the maximum amount of time without seeing activity on a particular interface for a particular container before considering the flow finished
flow_finished_threshold_seconds = 10

# the minimum amount of time that a flow must be active for on a particular interface for a container before comparing it to other interfaces for discrepancies
min_flow_duration_for_discrepancy_check = 10

# the smallest amount of time that can be used to define the boundary between discrepancy intervals
min_time_between_discrepancy_intervals = 10

# minimum duration of a discrepancy interval to include it in output
min_discrepancy_interval_duration = 30


def get_logger():
    return logging.getLogger(__name__)

def datetime_to_epoch_s(date_time):
    epoch_s = date_time.timestamp()
    return epoch_m

def datetime_to_epoch_ms(date_time):
    epoch_s = date_time.timestamp()
    epoch_ms = int(epoch_s * 1000)
    return epoch_ms


def main(argv=None):
    
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-L", "--log-directory", dest="log_dir_path", help="Log file directory", required=True)
    parser.add_argument("-b", "--testbed", dest="testbed", help="The testbed used for the run. This is used to obtain time zone information.", default='')

    global min_discrepancy_interval_duration
    parser.add_argument("-d", "--min-interval-duration", dest="min_duration", help="The minimum missing data interval duration allowed for intervals to output", default=str(min_discrepancy_interval_duration))
 
    
    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    

    # parse arguments
    log_dir_path = Path(args.log_dir_path)
    testbed = (None if args.testbed == '' else args.testbed)
    min_discrepancy_interval_duration = float(args.min_duration)
    


    time_zone=None

    testbed_to_time_zone_map = {'emulab' : timezone('US/Mountain'), 'dcomp' : timezone('UTC')}
    if (testbed != None):
        if (testbed in testbed_to_time_zone_map):
            time_zone=testbed_to_time_zone_map.get(testbed.lower())
            get_logger().info("Found time zone for testbed '%s': %s.", testbed, time_zone)
        else:
            get_logger().fatal("Could not map testbed '%s' to timezone.", testbed)
            exit(1)


    parse_state = ParseState()


    get_logger().info("Log directory: %s", log_dir_path)

    logfiles = sorted(log_dir_path.glob("map-agent*.log"), key=lambda f: f.stat().st_mtime)
    with fileinput.input(files=logfiles) as log_file:
        log_file_time_reference_line = log_file.readline()

        ref_time = map_utils.log_line_to_time(log_file_time_reference_line, time_zone)
        
        if (ref_time == None):
            get_logger().fatal("Failed to parse time in log statements. You might need to specify a testbed to determine the timezone or remove the testbed specification if the log statements include the timezone.")
            exit(1)
            
        
        get_logger().info("  Ref line: %s", log_file_time_reference_line.strip())
        get_logger().info("  Ref time String: %s", map_utils.datetime_to_string(ref_time))
        get_logger().info("  Ref time epoch ms: %s",  str(datetime_to_epoch_ms(ref_time)))
        get_logger().info("\n\n")


        for line in log_file:
            line_time = map_utils.log_line_to_time(line, time_zone)

            if line_time:
                parse_line(ref_time, line_time, line, parse_state)
                clear_finished_flows(ref_time, line_time, parse_state)
                check_for_network_discrepancies(ref_time, line_time, parse_state)
            else:
                get_logger().debug("Line has no readable timestamp: %s", line)

            get_logger().debug("parse_state.active_containers: %s", str(parse_state.active_containers))

    get_logger().info("Outputting Summary")
    get_logger().info("Minutes with missing data: %s", str(parse_state.container_nic_discrepancy_times))


    disrepancy_initervals = compute_discrepancy_intervals(parse_state.container_nic_discrepancy_times,
                                        timedelta(seconds=min_time_between_discrepancy_intervals)/timedelta(minutes=1))
    filter_discrepancy_intervals(disrepancy_initervals, timedelta(seconds=min_discrepancy_interval_duration)/timedelta(minutes=1))
    get_logger().info("Intervals with missing data: %s", str(disrepancy_initervals))

    total_intervals = dict()
    greater_than_1 = dict()
    greater_than_3 = dict()
    for node, node_data in disrepancy_initervals.items():
        for nic, intervals in node_data.items():
            total_intervals[node] = total_intervals.get(node, 0) + len(intervals)
            for interval in intervals:
                if interval.duration > 1:
                    greater_than_1[node] = greater_than_1.get(node, 0) + 1
                if interval.duration > 3:
                    greater_than_3[node] = greater_than_3.get(node, 0) + 1

    get_logger().info("Summary total intervals: %s intervals greater than 1 minute: %s intervals greater than 3 minutes %s", total_intervals, greater_than_1, greater_than_3)

    get_logger().debug("Final parse_state: %s", str(parse_state))
    

def parse_line(ref_time, timestamp, line, parse_state):
    """
    Arguments:
    ref_time (datetime): the time being used a reference (run start time) for the current time in the run
    timestamp (datetime): time for the log statement currently being parsed in the log file
    line (str): the log line to parse
    parse_state (ParseState)
    """
    match = re.match(r'.*Start service: AppCoordinates {(.+), (.+), (.+)}', line)
    if match:
        service_group = match.group(1)
        service_name = match.group(2)
        service_version = match.group(3)
        service = (service_group, service_name, service_version)
        parse_state.last_service_start = service
        return


    match = re.match(r'.*\*\*\*\* Start service: Obtained container name \'(.+)\' and IP \'([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)\'.', line)
    if match:
        container_name = match.group(1)
        container_ip = match.group(2)

        container = (container_name, container_ip, parse_state.last_service_start)
        get_logger().info("%s (%s): container %s (%s) for service '%s' started", timestamp, str(to_minutes(ref_time, timestamp)),
            container[0], container_ip, parse_state.last_service_start)
        parse_state.active_containers.add(container)
        return


    #match = re.match(r'.*\*\*\* Stopping container \'(.+)\' for service \'AppCoordinates {(.+), (.+), (.+)}\'.*', line)
    match = re.match(r'.*Stopped container \(response code 204\): (.+)', line)
    if match:
        container_name = match.group(1)

        get_logger().info("%s (%s): container %s (%s) for service '%s' stopped", timestamp, str(to_minutes(ref_time, timestamp)),
            container_name, map_container_to_ip(container_name, parse_state.active_containers), map_container_to_service(container_name, parse_state.active_containers))

        # remove the container from the list with container_name
        parse_state.active_containers = set(filter(lambda c : c[0] != container_name, parse_state.active_containers))
        return


    match = re.match(r'.*\[Iftop processor for (.+)\] .+ com\.bbn\.map\.hifi_resmgr\.IftopProcessor\.(.+) \[\] {}- IftopParseThread: read line: (.*)', line)
    if match:
        network_interface = match.group(1)
        iftop_out_line = match.group(3)
        parse_iftop_out_line(ref_time, timestamp, network_interface, iftop_out_line, parse_state)
        return



def parse_iftop_out_line(ref_time, timestamp, network_interface, line, parse_state):
    """
    Arguments:
    ref_time (datetime): the time being used a reference (run start time) for the current time in the run
    timestamp (datetime): time for the log statement currently being parsed in the log file
    network_interface (str): the networ interface for the iftop instance
    line (str): the line of iftop output to parse
    parse_state (ParseState)
    """
    match = re.match(r'.*?([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+):([0-9]+)\s+(<=|=>)\s+([0-9\.]+)([a-zA-Z]+)\s+([0-9\.]+)([a-zA-Z]+)\s+([0-9\.]+)([a-zA-Z]+)\s+([0-9\.]+)([a-zA-Z]+)', line)

    #match = re.match(r'.*([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+):([0-9]+)\s+(<=|=>)\s+([0-9|\.]+)([a-zA-Z]+)\s+([0-9|\.]+)([a-zA-Z]+).*', line)

    if match:
        #get_logger().info("iftop line match: '%s'", line)
        
        host_ip = match.group(1)
        host_port = match.group(2)
        direction = match.group(3)
        last2s_value = float(match.group(4))
        last2s_unit = match.group(5)
        last10s_value = float(match.group(6))
        last10s_unit = match.group(7)
        last40s_value = float(match.group(8))
        last40s_unit = match.group(9)
        cumulative_value = float(match.group(10))
        cumulative_unit = match.group(11)

        last2s_bps = to_bits_per_second(last2s_value, last2s_unit)

        if (host_port == "7000"):
            if (last2s_bps > 1000):
                for container in parse_state.active_containers:
                    get_logger().debug("compare %s, %s", container, host_ip)
                    
                    if (host_ip == container[1]):
                        traffic_time_list = parse_state.container_network_activity.setdefault(container[0], dict()).setdefault(network_interface, list())

                        if (len(traffic_time_list) == 0):
                            get_logger().info("%s (%s): container %s (%s): nic %s: traffic started (%s %s = %s Mbps)", timestamp, str(to_minutes(ref_time, timestamp)),
                                              container[0], host_ip, network_interface, last2s_value, last2s_unit, int(last2s_bps / 1024 / 1024 * 1000) / 1000.0)
                        
                        traffic_time_list.insert(0, timestamp)
                
    
    return


def check_for_network_discrepancies(ref_time, timestamp, parse_state):
    """
    Checks previous data in parse_state for data missing on a physcial interface for a container while data is found for the container on its interface.

    Arguments:
    ref_time (datetime): the time being used a reference (run start time) for the current time in the run
    timestamp (datetime): time for the log statement currently being parsed in the log file
    parse_state (ParseState)
    """
    for container,network_activity in parse_state.container_network_activity.items():
        nics = list(network_activity.keys())
        nics.sort()

        for n1 in range(0, len(nics)):
            nic1 = nics[n1]

            if (is_container_interface(nic1)):

                # check if the flow duration for this container is long enough to expect the data to appear on other interfaces
                if (get_flow_duration(network_activity[nic1]) > timedelta(seconds=min_flow_duration_for_discrepancy_check)):

                    # look through list of interfaces on which network data for this container was ever seen to see if it appears now
                    for n2 in range(0, len(nics)):
                        nic2 = nics[n2]
                        
                        if (not is_container_interface(nic2)):

                            get_logger().debug("len(network_activity[%s]) = %s, parse_state.container_nic_missing_data_status = %s",
                                    nic2, str(len(network_activity[nic2])), str(parse_state.container_nic_missing_data_status))

                            if (len(network_activity[nic2]) == 0):
                                container_ip = map_container_to_ip(container, parse_state.active_containers)
                                
                                record_discrepancy_minutes(ref_time, timestamp, False, container, nic2, parse_state)

                                if (not is_nic_data_missing(container, nic2, parse_state)):
                                    get_logger().warn("%s (%s) : Found traffic for container '%s' (%s) on nic '%s', but not on nic '%s', which previously had data for this container.",
                                                timestamp, str(to_minutes(ref_time, timestamp)), container, container_ip, nic1, nic2)
                                    set_nic_data_missing(container, nic2, True, parse_state)
                            else:
                                set_nic_data_missing(container, nic2, False, parse_state)
                                record_discrepancy_minutes(ref_time, timestamp, True, container, nic2, parse_state)





def filter_discrepancy_intervals(container_nic_discrepancy_intervals, min_interval_minutes):
    """
    Arguments:
        container_nic_discrepancy_intervals (dict): container (str) -> nic (str) -> Interval containing the missing data intervals
        min_interval_minutes (float): the smallest interval to keep in minutes
    """
    for container,nic_intervals in container_nic_discrepancy_intervals.items():
        for nic,intervals in nic_intervals.items():
            intervals[:] = [i for i in intervals if i.duration >= min_interval_minutes]


def compute_discrepancy_intervals(container_nic_discrepancy_times, min_minutes_between_intervals):
    """
    Arguments:
        container_nic_discrepancy_times (dict): container (str) -> nic (str) -> datetime showing discrepancy times for a container, nic pair
        min_minutes_between_intervals (float): minimum number of minutes to use a boundary between consecutive intervals
    Returns:
        dict: container, nic, Interval
    """
    container_nic_discrepancy_intervals = dict()

    for container,nic_times in container_nic_discrepancy_times.items():
        for nic,times in nic_times.items():
            if (len(times) >= 1):
                start = times[0][0]
                end = times[0][0]

                for t in range(1, len(times)):
                    current = times[t][0]
                    data_present = times[t][1]
                    

                    if (current - end < min_minutes_between_intervals and not data_present):
                        # continue interval
                        end = current 
                    else:
                        # end interval
                        interval = Interval(start, end)
                        container_nic_discrepancy_intervals.setdefault(container, dict()).setdefault(nic, list()).append(interval)

                        # start new interval
                        start = current
                        end = current
                        
            interval = Interval(start, end)
            container_nic_discrepancy_intervals.setdefault(container, dict()).setdefault(nic, list()).append(interval)

    return container_nic_discrepancy_intervals
                
    


class Interval:
    def __init__(self, start, end):
        self.start = start
        self.end = end
        self.duration = end - start

    def __str__(self):
        return "{:.2f}".format(self.start) + "-" + "{:.2f}".format(self.end) + " (" + "{:.2f}".format(self.duration) + ")"

    def __repr__(self):
        return str(self)


def record_discrepancy_minutes(ref_time, timestamp, data_present, container, network_interface, parse_state):
    """
    Argument:
        ref_time (datetime): the time being used a reference (run start time) for the current time in the run
        timestamp (datetime): time for the log statement currently being parsed in the log file
        data_present (boolean): True if data was found on the interface and False otherwise
        network_interface (str): the network interface checked for the presence or absense of data.
        parse_state (ParseState)
    """
    minutes = to_minutes(ref_time, timestamp)
    time_list = parse_state.container_nic_discrepancy_times.setdefault(container, dict()).setdefault(network_interface, list())

    data_point = (minutes, data_present)

    if (len(time_list) > 0):
        last_time = time_list[len(time_list) - 1][0]

        if (minutes - last_time > 0.1):
            time_list.append(data_point)
    else:
        time_list.append(data_point)
        
    


def is_nic_data_missing(container, nic, parse_state):
    get_logger().debug("parse_state.container_nic_missing_data_status = %s", parse_state.container_nic_missing_data_status)
    nic_statuses = parse_state.container_nic_missing_data_status.get(container)

    if nic_statuses:
        nic_status = nic_statuses.get(nic)

        if nic_status:
            return nic_status
        else:
            return False
    else:
        return False

def set_nic_data_missing(container, nic, value, parse_state):
    parse_state.container_nic_missing_data_status.setdefault(container, dict())[nic] = value



def map_container_to_ip(container_name, containers):
    get_logger().debug("containers = %s, container_name = %s", containers, container_name)
    for container in containers:
        if (container[0] == container_name):
            return container[1]

    return None

def map_container_to_service(container_name, containers):
    for container in containers:
        if (container[0] == container_name):
            return container[2]
    return None


def get_flow_duration(network_data_times_list):
    if (len(network_data_times_list) < 2):
        return timedelta(seconds=0)

    last = network_data_times_list[0]
    first = network_data_times_list[len(network_data_times_list) - 1]

    return (last - first)


def is_container_interface(network_interface):
    return network_interface.startswith("veth")


def to_bits_per_second(value, unit):
    unit_mulipliers = {"b" : 1, "Kb" : 1024, "Mb" : 1024 * 1024, "Gb" : 1024 * 1024 * 1024}
    multiplier = unit_mulipliers[unit]

    if (multiplier == None):
        get_logger().error("Invalid unit: '%s'", unit)

    return (value * multiplier)


def to_minutes(ref_time, current_time):
    return (current_time - ref_time) / timedelta(minutes=1)


def clear_finished_flows(ref_time, current_time, parse_state):
    """
    Clears network data for interfaces that have not had network data seen for at least flow_finished_threshold_seconds amount of time. This is as if a flow of network data has ended.

    Arguments:
        ref_time (datetime): the time being used a reference (run start time) for the current time in the run
        current_time (datetime): time for the log statement currently being parsed in the log file
    """
    for container,network_activity in parse_state.container_network_activity.items():
        for nic,activity_times in network_activity.items():
            if (len(activity_times) > 0):
                last_time = activity_times[0]

                time_delta = current_time - last_time
                if (time_delta > timedelta(seconds=flow_finished_threshold_seconds)):
                    get_logger().info("%s (%s): container %s (%s): nic %s: traffic ended", current_time, str(to_minutes(ref_time, current_time)), container,
                                      map_container_to_ip(container, parse_state.active_containers), nic)
                    activity_times.clear()


    

class ParseState:
    def __init__(self):
        self.last_service_start = None
        self.active_containers = set()
        self.container_network_activity = {}
        self.container_nic_missing_data_status = {}
        self.container_nic_discrepancy_times = {}

    def __str__(self):
        return "active_containers: " + str(self.active_containers) + "\n" + "container_network_activity: " + str(self.container_network_activity)

    



if __name__ == "__main__":
    sys.exit(main())
