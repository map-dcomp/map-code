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
Creates graphs for comparing container QueueLength and CPU over time for each container in a run to see how DNS resolution is affecting loading efficiency.
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
    import csv
    import datetime
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


def find_first_time(data):
    '''
    @return first_time, all_times
    '''

    first_time = None
    all_times = set()
    for app, app_values in data.items():
        for attr, attr_values in app_values.items():
            for container_name, time_values in attr_values.items():
                all_times.update(time_values.keys())
                min_time = min(time_values.keys())
                get_logger().debug("min_time %s count %s container %s attr %s app %s", min_time, len(time_values.keys()), container_name, attr, app)
                if first_time is None:
                    first_time = min_time
                else:
                    first_time = min(first_time, min_time)

    return first_time, list(sorted(all_times))


def accumulate_expected_queue_length(container_processing_latency):
    '''
    container_processing_latency: service -> container name -> container instance -> time -> queue length delta (+1 or -1)
    @return container_expected_queue_length: service -> container name -> time -> queue length
    '''

    container_expected_queue_length = dict()

    for service, service_data in container_processing_latency.items():
        for container, container_data in service_data.items():
            for container_instance_data in container_data:
                instance_queue_length = 0

                request_event_times = sorted(list(container_instance_data.keys()))

                # start queue length at 0
                if len(request_event_times) > 0:
                    container_expected_queue_length.setdefault(service, dict()).setdefault(container, dict())[request_event_times[0] - 1] = 0

                for request_event_time in request_event_times:
                    container_expected_queue_length.setdefault(service, dict()).setdefault(container, dict())[request_event_time - 1] = instance_queue_length
                    instance_queue_length += container_instance_data[request_event_time]
                    container_expected_queue_length.setdefault(service, dict()).setdefault(container, dict())[request_event_time] = instance_queue_length

                # end queue length at 0
                if len(request_event_times) > 0:
                    container_expected_queue_length.setdefault(service, dict()).setdefault(container, dict())[request_event_times[len(request_event_times) - 1] + 1] = 0

    return container_expected_queue_length



def output_graphs(first_time, output, data, container_expected_queue_lengths, load_name, container_queue_length_capacity):
    '''
    first_time (int): reference time in ms for run
    output: output folder for graphs
    data: resource report data
    container_expected_queue_lengths: estimated queeu lengths from processing latency csv files
    container_queue_length_capacity: queue length capacity for containers
    '''
    _, all_times = find_first_time(data)

    all_times_minutes = [ (float(t) - first_time) / 1000.0 / 60.0 for t in all_times ]
    get_logger().debug("First time %s", first_time)
    max_minutes = max(all_times_minutes)

    for app, app_values in data.items():
        container_data = dict()

        for attr, attr_values in app_values.items():
            if attr == "CPU" or attr == "QueueLength":
                for container_name, time_values in attr_values.items():
                    container_data.setdefault(container_name, dict())[attr] = time_values

        app_total_queue_lengths = 0
        app_total_queue_lengths_within_capacity = 0

        for container_name, attr_data in container_data.items():
            fig, ax = map_utils.subplots()
            ax.set_title(f"{load_name} for service: {app}, container: {container_name}")
            ax.set_xlabel('time (minutes)')
            ax.set_xlim(left=0, right=max_minutes)

            queue_lengths = 0
            queue_lengths_within_capacity = 0

            if container_queue_length_capacity:
                ax.set_ylim(top=2.0)

            for attr, time_values in attr_data.items():
                time_values_minutes = dict()
                for timestamp, value in time_values.items():
                    time = (float(timestamp) - first_time) / 1000.0 / 60.0
                    get_logger().debug("time %s timestamp %s first_time %s", time, timestamp, first_time)

                    if attr == "QueueLength" and container_queue_length_capacity != None:
                        queue_lengths += value
                        queue_lengths_within_capacity += min(value, container_queue_length_capacity)
                        time_values_minutes[time] = value / container_queue_length_capacity
                    else:
                        time_values_minutes[time] = value


                yinterp = map_utils.fill_missing_times(all_times_minutes, time_values_minutes)

                times = sorted(list(time_values_minutes.keys()))
                values = list()

                for time in times:
                    values.append(time_values_minutes[time])

                if attr == "QueueLength":
                    #plt.scatter(times, values, label=attr, color='red', marker='o')
                    plt.plot(all_times_minutes, yinterp, label=attr, color='red')
                else:
                    #plt.scatter(times, values, label=attr, marker='o')
                    plt.plot(all_times_minutes, yinterp, label=attr)

                get_logger().debug(f'container_expected_queue_lengths.keys(): {container_expected_queue_lengths.keys()}')


            # plot estimated queue lengthss
            if app in container_expected_queue_lengths.keys():
                times = sorted(list(container_expected_queue_lengths[app][container_name].keys()))
                times_minutes = [(float(time) - first_time) / 1000.0 / 60.0 for time in times]

                if container_queue_length_capacity != None:
                    queue_lengths = [container_expected_queue_lengths[app][container_name][time] / container_queue_length_capacity for time in times]
                else:
                    queue_lengths = [container_expected_queue_lengths[app][container_name][time] for time in times]

                plt.plot(times_minutes, queue_lengths, label='Estimated queue length', color='black')


            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"container-{load_name}-{app}-{container_name}-CPU_QueueLength.png"
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)

            get_logger().info(f'container {container_name}: queue_lengths: {queue_lengths}, queue_lengths_within_capacity: {queue_lengths_within_capacity}, percent queue lengths within capacity: {[queue_lengths_within_capacity / queue_lengths if queue_lengths > 0 else float("NaN")]}')

            app_total_queue_lengths += queue_lengths
            app_total_queue_lengths_within_capacity += queue_lengths_within_capacity

        get_logger().info(f'app {app}: queue_lengths: {app_total_queue_lengths}, queue_lengths_within_capacity: {app_total_queue_lengths_within_capacity}, percent queue lengths within capacity: {app_total_queue_lengths_within_capacity / app_total_queue_lengths}')


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("-c", "--container-capacity", dest="container_queue_length_capacity", help="QueueLength capacity per container", default=None)
    parser.add_argument("--process-latency-data", dest="process_latency_data", action='store_true', help="If present, show queue length estimate from processing_latency.csv files.")

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    first_timestamp_ms = first_timestamp.timestamp() * 1000

    get_logger().info("Run started at %s", first_timestamp)

    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    container_queue_length_capacity = int(args.container_queue_length_capacity) if args.container_queue_length_capacity else None

    process_latency_data = args.process_latency_data


    # app -> node_attr -> container_name -> timestamp -> value
    data_load = dict()
    data_demand = dict()
    container_processing_latency = dict()

    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue

        get_logger().debug("Processing node %s", node_dir)
        agent_dir = node_dir / 'agent'
        if not agent_dir.is_dir():
            continue

        get_logger().debug("\tAgent dir %s", agent_dir)
        for node_name_dir in agent_dir.iterdir():
            if not node_name_dir.is_dir():
                continue

            for time_dir in node_name_dir.iterdir():
                if not time_dir.is_dir():
                    continue

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


        container_data_dir = agent_dir / 'container_data'

        if process_latency_data:
            for container_data_service_dir in container_data_dir.iterdir():
                match = re.search(r'^(.+)\.([^\._]+)_(.+)$', container_data_service_dir.name)

                if match:
                    artifact = match.group(2)
                    for container_data_service_name_dir in container_data_service_dir.iterdir():
                        match = re.search(r'^([^\.]+)\.map\.dcomp$', container_data_service_name_dir.name)

                        if match:
                            container_name = match.group(1)
                            for container_data_service_name_instance_dir in container_data_service_name_dir.iterdir():
                                processing_latency_file = container_data_service_name_instance_dir / 'app_metrics_data' / 'processing_latency.csv'

                                container_instance_latency_data = dict()

                                with open(processing_latency_file, "r") as f:
                                    reader = csv.DictReader(f)

                                    for row in reader:
                                        event = row['event']
                                        time_start= int(row['time_received'])
                                        time_end = int(row['time_ack_sent'])

                                        if event == 'request_success':
                                            container_instance_latency_data[time_start] = 1
                                            container_instance_latency_data[time_end] = -1

                                container_processing_latency.setdefault(artifact, dict()).setdefault(container_name, list()).append(container_instance_latency_data)



    container_expected_queue_lengths = accumulate_expected_queue_length(container_processing_latency)
    output_graphs(first_timestamp_ms, output, data_load, container_expected_queue_lengths, "load", container_queue_length_capacity)


if __name__ == "__main__":
    sys.exit(main())

