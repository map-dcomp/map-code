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
    import pandas as pd
    import csv
    import matplotlib.pyplot as plt
    import map_utils
    import multiprocessing


script_dir=os.path.abspath(os.path.dirname(__file__))

"""
Statistics for the whole system have a pid of -1
"""
system_stats_pid = -1

def get_logger():
    return logging.getLogger(__name__)


def bytes_to_gigabytes(b):
    return b / 1024 / 1024 / 1024


def pid_label(pid, pid_names):
    return f"{pid_names[pid]}_{pid}"


def make_pid_key(pid_names):
    """
    Create a key function that calls process_key with the result of pid_label.
    """
    def make_key(x_pid):
        x_label = pid_label(x_pid, pid_names)
        return process_key(x_label)
    return make_key
        

def process_key(x):
    """
    Compare process names preferring map-agent.
    """
    
    if x.startswith('map-agent'):
        return f"0_{x}"
    else:
        return f"9_{x}"


def is_interesting_process(unfiltered, pid, row):
    """
    Check if this is a graph that we should graph.

    Arguments:
        unfiltered(boolean): if true, return all processes
        pid(int): the process ID parsed as an int from 'row'
        row(dict): dictionary from csv.DictReader
    Returns:
        boolean: true if the process is "interesting"
    """
    if unfiltered:
        return True
    elif pid == system_stats_pid:
        # totals
        return True
    elif row['user'] == 'map':
        # map service
        return True
    elif re.search(r'iftop', row['cmdline']):
        return True
    elif row['name'] == 'fprobe':
        return True
    elif row['name'] == 'java' and re.search(r'map-dns', row['cmdline']):
        # DNS server
        return True
    elif row['docker'] == 'True':
        # docker container or docker system process
        return True
    else:
        return False
    

def organize_node_data(unfiltered, stats_dir):
    """
    Returned timestamps are unix epoch seconds.
    
    Arguments:
        unfiltered(boolean): if true, return all processes
        stats_dir(Path): base directory to read stats data from
    Returns:
        data_cpu(dict): ts -> pid -> cpu
        data_memory(dict): ts -> pid -> memory
        all_pids(set): all process ids
        pid_names(dict): pid -> display name
        network_data(dict): attr -> interface -> ts -> value
    """
    # timestamps are unix epoch seconds
    data_cpu = dict() # ts -> pid -> cpu
    data_memory = dict() # ts -> pid -> memory
    all_pids = set()
    pid_names = dict() # pid -> display name

    network_data = dict() # attr -> interface -> ts -> value

    for file in stats_dir.iterdir():
        match = re.match(r'^(\d+)\.csv$', file.name)
        net_match = re.match(r'^(\d+)_net\.csv$', file.name)
        if file.is_file() and match:
            timestamp = int(match.group(1))
            ts_data_cpu = dict() # pid -> cpu
            ts_data_memory = dict() # pid -> memory
            with open(file) as f:
                reader = csv.DictReader(f)
                for row in reader:
                    pid = int(row['pid'])
                    if is_interesting_process(unfiltered, pid, row):
                        cpu = float(row['cpu_user'])
                        # vms is not useful, it includes things such as file backed memory
                        memory = float(row['memory_rss'])
                        if pid != system_stats_pid:
                            cpu = cpu + float(row['cpu_sys'])

                            if row['name'] == 'java':
                                match = re.search(r'([^/\s]+)\.jar', row['cmdline'])
                                if match:
                                    name = match.group(1)
                                else:
                                    name = row['name']
                            else:
                                name = row['name']
                            pid_names[pid] = name

                        ts_data_cpu[pid] = cpu
                        ts_data_memory[pid] = memory
                        if pid != system_stats_pid:
                            all_pids.add(pid)

            # verify that we have a system entry
            if system_stats_pid not in ts_data_cpu:
                get_logger().warning("Missing system entry in %s, skipping", file)
                continue
                
            data_cpu[timestamp] = ts_data_cpu
            data_memory[timestamp] = ts_data_memory
            
        elif net_match:
            timestamp = int(net_match.group(1))
            with open(file) as f:
                reader = csv.DictReader(f)
                for row in reader:
                    interface = row['interface']
                    interface = interface.lower()
                    for attr, value in row.items():
                        if attr == 'interface':
                            continue
                        attr_data = network_data.get(attr, dict())
                        interface_data = attr_data.get(interface, dict())

                        try:
                            interface_data[timestamp] = int(value)
                        except ValueError:
                            # missing column
                            interface_data[timestamp] = 0
                        
                        attr_data[interface] = interface_data
                        network_data[attr] = attr_data
            
    return data_cpu, data_memory, all_pids, pid_names, network_data


def compute_node_differential_percent(all_pids, data):
    """Compute percentage of the system used at each timestamp as a
    difference from the previous timestamp.  This assumes that the
    data measured is a counter that increases over time and the
    difference between 2 timestamps is the useful information.

    """
    data_percent = dict() # timestamp -> pid -> data

    prev_values = None
    for timestamp, ts_data in sorted(data.items()):
        ts_percent = dict() # pid -> data
        if prev_values is not None:
            system_cur = float(ts_data[system_stats_pid])
            system_prev = (prev_values[system_stats_pid])
            system_diff = system_cur - system_prev

            for pid in all_pids:
                cur = ts_data.get(pid, None)
                prev = prev_values.get(pid, None)
                if cur is None or prev is None:
                    percent = 0
                else:
                    diff = float(cur - prev)
                    if diff < 0:
                        get_logger().warn("Found negative usage difference at %d for %d diff %f", timestamp, pid, diff)
                        percent = 0
                    else:
                        percent = diff / system_diff
                        if percent > 1:
                            get_logger().warn("Found diff (%f) that is greater than system diff (%f)", diff, system_diff)
                            
                ts_percent[pid] = percent * 100

        data_percent[timestamp] = ts_percent
        prev_values = ts_data

    return data_percent


def compute_node_absolute_percent(all_pids, data):
    """Compute percentage of the system that is used at each timestamp.
    This assumes that the values at each timestamp are absolute
    measurements.

    """
    data_percent = dict() # timestamp -> pid -> data

    for timestamp, ts_data in sorted(data.items()):    
        ts_percent = dict() # pid -> data
        for pid in all_pids:
            system_cur = float(ts_data[system_stats_pid])
            cur = ts_data.get(pid, None)
            if cur is None:
                percent = 0
            else:
                percent = float(cur) / system_cur
            ts_percent[pid] = percent * 100
        data_percent[timestamp] = ts_percent

    return data_percent


def write_percents(all_pids, pid_names, filename, data_percent):
    with open(filename, 'w') as f:
        writer = csv.writer(f)
        header = ['timestamp']
        for pid in sorted(all_pids):
            label = pid_label(pid, pid_names)
            header.append(label)
        writer.writerow(header)

        for timestamp, ts_percent in sorted(data_percent.items()):    
            row = [timestamp]
            for pid in sorted(all_pids):
                if pid in ts_percent:
                    row.append(ts_percent[pid])
                else:
                    row.append(0)
            writer.writerow(row)    


def write_cpu_plot(node_name, file_cpu, output, first_timestamp_sec):
    get_logger().debug("Writing plot for %s with first timestamp of %d from %s", node_name, first_timestamp_sec, file_cpu)
    
    df = pd.read_csv(file_cpu)
    df['time_minutes'] = (df['timestamp'] - first_timestamp_sec) / 60
    max_minutes = df['time_minutes'].max()

    fig, ax = map_utils.subplots()
    ax.set_title(f"{node_name} Process CPU load")
    ax.set_ylabel('% CPU')
    ax.set_xlabel('time (minutes)')
    ax.set_xlim(left=0, right=max_minutes)

    ydata = list()
    labels = list()
    for process in sorted(df.columns, key=process_key):
        if process != 'timestamp' and process != 'time_minutes':
            ydata.append(df[process])
            labels.append(process)

    ax.stackplot(df['time_minutes'], ydata, labels=labels)
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{file_cpu.stem}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


def write_memory_plot(node_name, file_memory, output, first_timestamp_sec):
    df = pd.read_csv(file_memory)
    df['time_minutes'] = (df['timestamp'] - first_timestamp_sec) / 60
    max_minutes = df['time_minutes'].max()

    fig, ax = map_utils.subplots()
    ax.set_title(f"{node_name} Process memory load")
    ax.set_ylabel('% memory')
    ax.set_xlabel('time (minutes)')
    ax.set_xlim(left=0, right=max_minutes)
    
    ydata = list()
    labels = list()
    for process in sorted(df.columns, key=process_key):
        if process != 'timestamp' and process != 'time_minutes':
            ydata.append(df[process])
            labels.append(process)

    ax.stackplot(df['time_minutes'], ydata, labels=labels)
            
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{file_memory.stem}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    

def write_memory_absolute_plot(node_name, data_memory, all_pids, pid_names, output, first_timestamp_sec):
    """
    Write out the graph of absolute memory.
    
    Arguments:
        node_name(str): name of the node
        data_memory(dict): ts(int) -> pid(int) -> memory(float)
        all_pids(set): all process names
        pid_names(dict): pid(int) -> pid name(str)
        output(Path): where the graphs are being written
    """

    timestamps = sorted(data_memory.keys())
    minutes = [ (ts - first_timestamp_sec) / 60 for ts in timestamps ]
    max_minutes = max(minutes)
    
    fig, ax = map_utils.subplots()
    ax.set_title(f"{node_name} Process memory")
    ax.set_ylabel('GB memory')
    ax.set_xlabel('time (minutes)')
    ax.set_xlim(left=0, right=max_minutes)
    
    ydata = list()
    labels = list()
    for pid in sorted(all_pids, key=make_pid_key(pid_names)):
        plot_data = list()
        for timestamp in timestamps:
            ts_data = data_memory[timestamp]
            if pid in ts_data:
                memory = bytes_to_gigabytes(ts_data[pid])
            else:
                memory = 0
            plot_data.append(memory)
        label = pid_label(pid, pid_names)
        ydata.append(plot_data)
        labels.append(label)

    ax.stackplot(minutes, ydata, labels=labels)
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{node_name}_stats_memory_absolute.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    
def compute_network_differentials(network_data):
    """
    Arguments:
        network_data(dict): attr -> interface -> ts -> value
    Returns:
        network_data(dict): attr -> interface -> ts -> diff from previous per second
    """
    retval = dict()
    
    for attr, attr_data in network_data.items():
        ret_attr_data = retval.get(attr, dict())
        
        for interface, interface_data in attr_data.items():
            ret_interface_data = ret_attr_data.get(interface, dict())

            prev_ts = None
            prev_value = None
            for ts, value in sorted(interface_data.items()):
                if prev_value is not None:
                    diff_value = value - prev_value
                    diff_ts = ts - prev_ts
                    diff_per_second = diff_value / diff_ts
                    ret_interface_data[ts] = diff_per_second
                prev_value = value
                prev_ts = ts
                
            ret_attr_data[interface] = ret_interface_data
        retval[attr] = ret_attr_data
    return retval


def write_network_bandwidth_plot(output_dir, first_timestamp_sec, node_name, graph_label, file_label, bandwidth_data, ip_info):
    """
    Arguments:
        output_dir(Path): base directory for output
        label(str): label for the data
        bandwidth_data(dict): interface -> ts -> value
        first_timestamp_sec(int): the first timestamp for relative computation
        ip_info(dict): node -> ifce -> ip
    """
    node_ip_info = ip_info.get(node_name, dict())
    get_logger().debug("Got ip info for %s -> %s", node_name, node_ip_info)

    fig, ax = map_utils.subplots()
    ax.set_title(graph_label)
    ax.set_ylabel('mbps')
    ax.set_xlabel('time (minutes)')

    max_minutes=0
    for interface, interface_data in bandwidth_data.items():
        xdata = list()
        ydata = list()
        for ts, value in interface_data.items():
            rel_sec = ts - first_timestamp_sec
            rel_min = rel_sec / 60
            mbps = value / 1024 / 1024 * 8 # 1024 kb / byte, 1024 kb / mb, 8 bits per byte
            xdata.append(rel_min)
            ydata.append(mbps)
            max_minutes = max(max_minutes, rel_min)

        if interface in node_ip_info:
            ip = f" - {node_ip_info[interface]}"
        else:
            ip = ""
        ax.plot(xdata, ydata, label=f"{interface}{ip}")

    ax.set_xlim(left=0, right=max_minutes)
        
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / f"{node_name}_stats_network-{file_label}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


def gather_network_ips(sim_output):
    """
    Determine the IP address for each interface on each node.
    If the information isn't available, then the result will be an empty dictionary.
    Use of the dictionary should always check for the information existing
    
    Arguments:
        sim_output(Path): directory that the sim output is in
    
    Returns:
        dict: node_name -> ifce_name -> ip
    """

    network_info = sim_output / "inputs/network-information.txt"
    if not network_info.exists():
        # no information
        return dict()

    info = dict()
    node_name = None
    ifce_name = None
    node_name_re = re.compile(r'^(\S+)\s+\|\s+CHANGED')
    ifce_name_re = re.compile(r'^\d+:\s+([^@]+)(@\S+)?: \<')
    ip_re = re.compile(r'^\s+inet ([0-9\.]+)/')
    with open(network_info) as f:
        for line in f:
            node_name_match = node_name_re.match(line)
            if node_name_match:
                node_name = node_name_match.group(1).split('.')[0]
                ifce_name = None
                get_logger().debug("Found node %s", node_name)
                continue
                
            ifce_name_match = ifce_name_re.match(line)
            if ifce_name_match:
                ifce_name = ifce_name_match.group(1)
                get_logger().debug("Found interface %s", ifce_name)
                continue
                
            ip_match = ip_re.match(line)
            if ip_match:
                ip = ip_match.group(1)
                if node_name is not None and ifce_name is not None:
                    node_name = node_name.lower()
                    ifce_name = ifce_name.lower()
                    get_logger().debug("Found ip %s on %s", ip, node_name)
                    node_info = info.get(node_name, dict())
                    node_info[ifce_name] = ip
                    info[node_name] = node_info
                else:
                    get_logger().warning("Found network interface name %s and node_name (%s) or ifce_name (%s) is missing", ip, node_name, ifce_name)

                ifce_name = None
                
    return info
        
    
def process_node_dir(unfiltered, output, node_dir, first_timestamp_sec, ip_info):
    try:
        stats_dir = node_dir / 'system_stats'
        if not stats_dir.exists():
            # not a directory we care about
            return

        node_name = node_dir.stem.lower()
        get_logger().info("Processing node %s", node_name)

        (data_cpu, data_memory, all_pids, pid_names, network_data) = organize_node_data(unfiltered, stats_dir)
        percent_cpu = compute_node_differential_percent(all_pids, data_cpu)
        percent_memory = compute_node_absolute_percent(all_pids, data_memory)

        file_cpu = output / f"{node_name}_stats_cpu.csv"
        file_memory = output / f"{node_name}_stats_memory.csv"
        write_percents(all_pids, pid_names, file_cpu, percent_cpu)
        write_percents(all_pids, pid_names, file_memory, percent_memory)
        write_cpu_plot(node_name, file_cpu, output, first_timestamp_sec)
        write_memory_plot(node_name, file_memory, output, first_timestamp_sec)

        write_memory_absolute_plot(node_name, data_memory, all_pids, pid_names, output, first_timestamp_sec)

        network_per_second = compute_network_differentials(network_data)

        if 'rx_bytes' in network_per_second:
            write_network_bandwidth_plot(output, first_timestamp_sec, node_name, 'RX mbps', 'rx', network_per_second['rx_bytes'], ip_info)
            
        if 'tx_bytes' in network_per_second:
            write_network_bandwidth_plot(output, first_timestamp_sec, node_name, 'TX mbps', 'tx', network_per_second['tx_bytes'], ip_info)
            
    except:
        get_logger().exception("Unexpected error")


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)

    ip_info = gather_network_ips(sim_output)
    get_logger().debug("ip_info: %s", ip_info)

    first_timestamp_secs = first_timestamp.timestamp()
    
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()

        for node_dir in sim_output.iterdir():
            if node_dir.is_dir():
                results.append(pool.apply_async(func=process_node_dir, args=[args.unfiltered, output, node_dir, first_timestamp_secs, ip_info]))

        for result in results:
            result.wait()

            
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    class ArgumentParserWithDefaults(argparse.ArgumentParser):
        '''
        From https://stackoverflow.com/questions/12151306/argparse-way-to-include-default-values-in-help
        '''
        def add_argument(self, *args, help=None, default=None, **kwargs):
            if help is not None:
                kwargs['help'] = help
            if default is not None and args[0] != '-h':
                kwargs['default'] = default
                if help is not None:
                    kwargs['help'] += ' (default: {})'.format(default)
            super().add_argument(*args, **kwargs)
        
    parser = ArgumentParserWithDefaults(formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("--debug", dest="debug", help="Enable interactive debugger on error", action='store_true')
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    parser.add_argument("--unfiltered", dest="unfiltered", help="Show all processes, not just MAP processes", action='store_true')

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    if 'multiprocessing' in sys.modules:
        import multiprocessing_logging
        multiprocessing_logging.install_mp_handler()

    if args.debug:
        import pdb, traceback
        try:
            return main_method(args)
        except:
            extype, value, tb = sys.exc_info()
            traceback.print_exc()
            pdb.post_mortem(tb)    
    else:
        return main_method(args)
            
        
if __name__ == "__main__":
    sys.exit(main())
