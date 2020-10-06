#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
    import matplotlib
    import map_utils


script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)


def bytes_to_gigabytes(b):
    return b / 1024 / 1024 / 1024


def is_interesting_process(pid, row):
    """
    Check if this is a graph that we should graph.

    Arguments:
        pid(int): the process ID parsed as an int from 'row'
        row(dict): dictionary from csv.DictReader
    Returns:
        boolean: true if the process is "interesting"
    """
    if pid == -1:
        # totals
        return True
    elif row['user'] == 'map':
        # map service
        return True
    elif row['name'] == 'java' and re.search(r'map-dns', row['cmdline']):
        # DNS server
        return True
    elif row['docker'] == 'True':
        # docker container or docker system process
        return True
    else:
        return False
    

def organize_node_data(stats_dir):
    data_cpu = dict() # ts -> pid -> cpu
    data_memory = dict() # ts -> pid -> memory
    all_pids = set()
    pid_names = dict() # pid -> display name

    for file in stats_dir.iterdir():
        if file.is_file() and file.suffix == '.csv':
            timestamp = int(file.stem)
            ts_data_cpu = dict() # pid -> cpu
            ts_data_memory = dict() # pid -> memory
            with open(file) as f:
                reader = csv.DictReader(f)
                for row in reader:
                    pid = int(row['pid'])
                    if is_interesting_process(pid, row):
                        cpu = float(row['cpu_user'])
                        # vms is not useful, it includes things such as file backed memory
                        memory = float(row['memory_rss'])
                        if pid != -1:
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
                        if pid != -1:
                            all_pids.add(pid)

            # verify that we have a system entry
            if -1 not in ts_data_cpu:
                get_logger().warn("Missing system entry in %s, skipping", file)
                continue
                
            data_cpu[timestamp] = ts_data_cpu
            data_memory[timestamp] = ts_data_memory
    return data_cpu, data_memory, all_pids, pid_names


def compute_node_differential_percent(all_pids, data):
    data_percent = dict() # timestamp -> pid -> data

    prev_values = None
    for timestamp, ts_data in sorted(data.items()):
        ts_percent = dict() # pid -> data
        if prev_values is not None:
            system_cur = float(ts_data[-1])
            system_prev = (prev_values[-1])
            system_diff = system_cur - system_prev

            for pid in all_pids:
                cur = ts_data.get(pid, None)
                prev = prev_values.get(pid, None)
                if cur is None or prev is None:
                    percent = 0
                else:
                    diff = cur - prev
                    if diff < 0:
                        print("Warning found negative usage difference at ", timestamp, " for ", pid, " diff ", diff)
                        percent = 0
                    else:
                        percent = float(diff) / system_diff        
                ts_percent[pid] = percent

        data_percent[timestamp] = ts_percent
        prev_values = ts_data

    return data_percent


def compute_node_absolute_percent(all_pids, data):
    data_percent = dict() # timestamp -> pid -> data

    for timestamp, ts_data in sorted(data.items()):    
        ts_percent = dict() # pid -> data
        for pid in all_pids:
            system_cur = float(ts_data[-1])
            cur = ts_data.get(pid, None)
            if cur is None:
                percent = 0
            else:
                percent = float(cur) / system_cur
            ts_percent[pid] = percent
        data_percent[timestamp] = ts_percent

    return data_percent


def write_percents(all_pids, pid_names, filename, data_percent):
    with open(filename, 'w') as f:
        writer = csv.writer(f)
        header = ['timestamp']
        for pid in sorted(all_pids):
            name = pid_names[pid]
            header.append(f"{name}_{pid}")
        writer.writerow(header)

        for timestamp, ts_percent in sorted(data_percent.items()):    
            row = [timestamp]
            for pid in sorted(all_pids):
                if pid in ts_percent:
                    row.append(ts_percent[pid])
                else:
                    row.append(0)
            writer.writerow(row)    


def write_cpu_plot(node_name, file_cpu, output):
    df = pd.read_csv(file_cpu)
    df['time_minutes'] = (df['timestamp'] - df['timestamp'].min()) / 60

    fig, ax = plt.subplots()
    ax.set_title(f"{node_name} Process CPU load")
    ax.set_ylabel('% CPU')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    ydata = list()
    labels = list()
    for process in sorted(df.columns):
        if process != 'timestamp' and process != 'time_minutes':
            ydata.append(df[process])
            labels.append(process)

    ax.stackplot(df['time_minutes'], ydata, labels=labels, colors=matplotlib.cm.get_cmap("tab20").colors)
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{file_cpu.stem}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


def write_memory_plot(node_name, file_memory, output):
    df = pd.read_csv(file_memory)
    df['time_minutes'] = (df['timestamp'] - df['timestamp'].min()) / 60

    fig, ax = plt.subplots()
    ax.set_title(f"{node_name} Process memory load")
    ax.set_ylabel('% memory')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    ydata = list()
    labels = list()
    for process in df.columns:
        if process != 'timestamp' and process != 'time_minutes':
            ydata.append(df[process])
            labels.append(process)

    ax.stackplot(df['time_minutes'], ydata, labels=labels, colors=matplotlib.cm.get_cmap("tab20").colors)
            
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{file_memory.stem}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    

def write_memory_absolute_plot(node_name, data_memory, all_pids, pid_names, output):
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
    first_timestamp = timestamps[0]
    minutes = [ (ts - first_timestamp) / 60 for ts in timestamps ]
    
    fig, ax = plt.subplots()
    ax.set_title(f"{node_name} Process memory")
    ax.set_ylabel('GB memory')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    ydata = list()
    labels = list()
    for pid in all_pids:
        plot_data = list()
        for timestamp in timestamps:
            ts_data = data_memory[timestamp]
            if pid in ts_data:
                memory = bytes_to_gigabytes(ts_data[pid])
            else:
                memory = 0
            plot_data.append(memory)
        label = f"{pid}_{pid_names[pid]}"
        ydata.append(plot_data)
        labels.append(label)

    ax.stackplot(minutes, ydata, labels=labels, colors=matplotlib.cm.get_cmap("tab20").colors)
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{node_name}_stats_memory_absolute.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    
def process_node_dir(output, node_dir):
    stats_dir = node_dir / 'system_stats'
    if not stats_dir.exists():
        # not a directory we care about
        return

    node_name = node_dir.stem
    get_logger().info("Processing node %s", node_name)
    
    (data_cpu, data_memory, all_pids, pid_names) = organize_node_data(stats_dir)
    percent_cpu = compute_node_differential_percent(all_pids, data_cpu)
    percent_memory = compute_node_absolute_percent(all_pids, data_memory)

    file_cpu = output / f"{node_name}_stats_cpu.csv"
    file_memory = output / f"{node_name}_stats_memory.csv"
    write_percents(all_pids, pid_names, file_cpu, percent_cpu)
    write_percents(all_pids, pid_names, file_memory, percent_memory)
    write_cpu_plot(node_name, file_cpu, output)
    write_memory_plot(node_name, file_memory, output)
    
    write_memory_absolute_plot(node_name, data_memory, all_pids, pid_names, output)


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    for node_dir in sim_output.iterdir():
        if node_dir.is_dir():
            process_node_dir(output, node_dir)
    

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

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

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
