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
Creates container-{load or demand}-{app}-{attribute}.png

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
    from pathlib import Path
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def process_load_or_demand(data, container_name, load_or_demand, app, time):
    for source, source_load in load_or_demand.items():
        for node_attr, node_value in source_load.items():
            app_values = data.get(app, dict())
            attr_values = app_values.get(node_attr, dict())
            container_values = attr_values.get(container_name, dict())

            get_logger().debug("attr: %s value: %s", node_attr, node_value)
            prev_value = container_values.get(time, 0)
            container_values[time] = float(node_value) + prev_value

            attr_values[container_name] = container_values
            app_values[node_attr] = attr_values
            data[app] = app_values
    

def process_container_report(data_load, data_demand, container_name, time, container_report):
    if time == 0:
        # skip the zero time reports
        return
    
    compute_load = container_report['computeLoad']
    app = container_report['service']['artifact']
    process_load_or_demand(data_load, container_name, compute_load, app, time)

    compute_demand = container_report['computeDemand']
    process_load_or_demand(data_demand, container_name, compute_demand, app, time)


def find_all_times(data):
    '''
    @return all_times
    '''
    
    all_times = set()
    for app, app_values in data.items():
        for attr, attr_values in app_values.items():
            for container_name, time_values in attr_values.items():
                all_times.update(time_values.keys())
                min_time = min(time_values.keys())
                get_logger().debug("min_time %s count %s container %s attr %s app %s", min_time, len(time_values.keys()), container_name, attr, app)

    return list(sorted(all_times))
            
def output_graphs(first_timestamp_ms, output, data, load_name):
    all_times = find_all_times(data)
    all_times_minutes = [ (float(t) - first_timestamp_ms) / 1000.0 / 60.0 for t in all_times ]
    max_minutes = max(all_times_minutes)
    
    for app, app_values in data.items():
        for attr, attr_values in app_values.items():
            fig, ax = map_utils.subplots()
            ax.set_title(f"{load_name} for service: {app} attribute: {attr}")
            ax.set_xlabel('time (minutes)')
            ax.set_xlim(left=0, right=max_minutes)

            plot_data = dict()
            for container_name, time_values in attr_values.items():
                time_values_minutes = dict()
                for timestamp, value in time_values.items():
                    time = (float(timestamp) - first_timestamp_ms) / 1000.0 / 60.0
                    get_logger().debug("time %s timestamp %s first_time %s", time, timestamp, first_timestamp_ms)
                    time_values_minutes[time] = value

                yinterp = map_utils.fill_missing_times(all_times_minutes, time_values_minutes)
                
                plot_data[container_name] = yinterp

            series_labels, ydata = zip(*sorted(plot_data.items()))
            plt.stackplot(all_times_minutes, ydata, labels=series_labels)
            
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
            
            output_name = output / f"container-{load_name}-{app}-{attr}.png"
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)


def add_time_dir_data(time_dir, data_load, data_demand):
    get_logger().debug("\t\tProcessing time %s", time_dir)
    resource_report_file = time_dir / 'resourceReport-SHORT.json'
    if resource_report_file.exists():
        try:
            with open(resource_report_file, 'r') as f:
                resource_report = json.load(f)
            time = int(time_dir.stem)
            if 'containerReports' in resource_report:
                for container_name, container_report in resource_report['containerReports'].items():
                    container_name_short = container_name.split('.')[0]
                    process_container_report(data_load, data_demand, container_name_short, time, container_report)
        except json.decoder.JSONDecodeError:
            get_logger().warning("Problem reading %s, skipping", resource_report_file)



def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)
    first_timestamp_ms = first_timestamp.timestamp() * 1000
    
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    
    # app -> node_attr -> container_name -> timestamp -> value
    data_load = dict() 
    data_demand = dict() 
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue

        get_logger().debug("Processing node %s", node_dir)
        agent_dir = node_dir / 'agent'

        if agent_dir.is_dir():
            get_logger().debug("\tAgent dir %s", agent_dir)
            for node_name_dir in agent_dir.iterdir():
                if not node_name_dir.is_dir():
                    continue

                for time_dir in node_name_dir.iterdir():
                    if not time_dir.is_dir():
                        continue

                    add_time_dir_data(time_dir, data_load, data_demand)
        else:
            for time_dir in node_dir.iterdir():
                if not time_dir.is_dir():
                    continue

                add_time_dir_data(time_dir, data_load, data_demand)

                            

    output_graphs(first_timestamp_ms, output, data_load, "load")
    output_graphs(first_timestamp_ms, output, data_demand, "demand")


if __name__ == "__main__":
    sys.exit(main())








