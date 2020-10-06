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

"""
Plot load or SHORT demand for each service on the same graph along with capacity.

creates graphs:
  - {load, demand, capacity}-{attr}.png
  - {load, demand, capacity}-{attr}-{app}.png
  - {load, demand, capacity}-{attr}-{region}.png
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
    import traceback

    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    
    import matplotlib.pyplot as plt
    import numpy as np
    import pandas as pd
    import map_utils
    import multiprocessing


script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)


def process_load_or_demand(time, node_name, data, compute_data):
    # data: attr -> app -> ncp -> timestamp -> value
    for appStr, app_compute_data in compute_data.items():
        match = re.match(r'^AppCoordinates {\S+,\s*(\S+),\s*\S+}$', appStr)
        if match:
            app = match.group(1)

            for source, source_compute_data in app_compute_data.items():
                for attr, value in source_compute_data.items():
                    attr_compute_data = data.get(attr, dict())
                    app_compute_data = attr_compute_data.get(app, dict())
                    node_compute_data = app_compute_data.get(node_name, dict())
                    
                    time_value = node_compute_data.get(time, 0)
                    node_compute_data[time] = time_value + float(value)
                    
                    app_compute_data[node_name] = node_compute_data
                    attr_compute_data[app] = app_compute_data
                    data[attr] = attr_compute_data
        else:
            get_logger().warn("Could not parse app name from %s", appStr)

            
def process_allocated_capacity(time, name, data, allocated_capacity):
    for attr, value in allocated_capacity.items():
        attr_compute_data = data.get(attr, dict())
        node_compute_data = attr_compute_data.get(node_name, dict())

        time_value = node_compute_data.get(time, 0)
        node_compute_data[time] = time_value + float(value)

        attr_compute_data[app] = node_compute_data
        data[attr] = attr_compute_data

        
def process_container_report(data_capacity, time, container_name_short, container_report):
    app = container_report['service']['artifact']
    compute_capacity = container_report['computeCapacity']
    for attr, value in compute_capacity.items():
        # data: attr -> app -> container -> timestamp -> value
        attr_compute_data = data_capacity.get(attr, dict())
        app_compute_data = attr_compute_data.get(app, dict())
        node_compute_data = app_compute_data.get(container_name_short, dict())

        time_value = node_compute_data.get(time, 0)
        node_compute_data[time] = time_value + float(value)

        app_compute_data[container_name_short] = node_compute_data
        attr_compute_data[app] = app_compute_data
        data_capacity[attr] = attr_compute_data

            
def process_resource_report(name, data_load, data_demand, data_capacity, node_containers, time, resource_report):
    
    compute_load = resource_report['computeLoad']
    process_load_or_demand(time, name, data_load, compute_load)

    compute_demand = resource_report['computeDemand']
    process_load_or_demand(time, name, data_demand, compute_demand)

    for container_name, container_report in resource_report['containerReports'].items():
        container_name_short = container_name.split('.')[0]
        node_containers.add(container_name_short)
        process_container_report(data_capacity, time, container_name_short, container_report)


def sum_values(data):
    """
    Interpolate values where missing and sum data across NCPs

    Args:
        data (dict): attr -> app -> ncp -> timestamp -> value
    
    Returns:
        dict: attr -> app -> timestamp -> value
    """

    # first compute all times across all data
    all_times = set()
    for attr, attr_data in data.items():
        for app, app_data in attr_data.items():
            for node_name, time_data in app_data.items():
                all_times.update(time_data.keys())
    all_times = list(sorted(all_times))
    
    result = dict()
    for attr, attr_data in data.items():
        result_attr_data = dict()
        
        for app, app_data in attr_data.items():
            values_summed = np.zeros(len(all_times))
            for node_name, time_data in app_data.items():
                values_ar = map_utils.fill_missing_times(all_times, time_data)
                values_summed = np.add(values_summed, values_ar)
                
            result_attr_data[app] = dict(zip(all_times, values_summed))
        result[attr] = result_attr_data
    return result


def sum_capacity_values_by_region(data, node_region, container_node):
    """
    Interpolate capacity values where missing and sum data per region.

    Args:
        data (dict): attr -> app -> ncp -> timestamp -> value
        node_region (dict): ncp -> region
        container_node (dict): container -> ncp
    
    Returns:
        dict: region -> attr -> app -> timestamp -> value
    """

    # first compute all times across all data
    all_times = set()
    for attr, attr_data in data.items():
        for app, app_data in attr_data.items():
            for node_name, time_data in app_data.items():
                all_times.update(time_data.keys())
    all_times = list(sorted(all_times))

    regions = set(node_region.values())
    
    result = dict()
    for sum_region in regions:
        region_data = dict()
        
        for attr, attr_data in data.items():
            result_attr_data = dict()

            for app, app_data in attr_data.items():
                values_summed = np.zeros(len(all_times))

                for container_name, time_data in app_data.items():
                    ncp = container_node[container_name]
                    region = node_region[ncp]

                    if region == sum_region:
                        values_ar = map_utils.fill_missing_times(all_times, time_data)
                        values_summed = np.add(values_summed, values_ar)

                result_attr_data[app] = dict(zip(all_times, values_summed))
            region_data[attr] = result_attr_data
        result[sum_region] = region_data
        
    return result


def sum_values_by_region(data, node_region):
    """
    Interpolate values where missing and sum data per region.

    Args:
        data (dict): attr -> app -> ncp -> timestamp -> value
        node_region (dict): ncp -> region
    
    Returns:
        dict: region -> attr -> app -> timestamp -> value
    """

    # first compute all times across all data
    all_times = set()
    for attr, attr_data in data.items():
        for app, app_data in attr_data.items():
            for node_name, time_data in app_data.items():
                all_times.update(time_data.keys())
    all_times = list(sorted(all_times))

    regions = set(node_region.values())
    
    result = dict()
    for sum_region in regions:
        region_data = dict()
        
        for attr, attr_data in data.items():
            result_attr_data = dict()

            for app, app_data in attr_data.items():
                values_summed = np.zeros(len(all_times))

                for ncp, time_data in app_data.items():
                    region = node_region[ncp]

                    if region == sum_region:
                        values_ar = map_utils.fill_missing_times(all_times, time_data)
                        values_summed = np.add(values_summed, values_ar)

                result_attr_data[app] = dict(zip(all_times, values_summed))
            region_data[attr] = result_attr_data
        result[sum_region] = region_data
        
    return result


def output_graphs(output, capacity, data, label):
    """
    Create a graph per node attribute.
    Creates files with names <label>-<attribute>.png
    
    Args:
        output (Path): output directory
        data (dict): attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity: attr -> app -> timestamp -> value
    """

    try:
        for attr, attr_data in data.items():
            frames = list()

            fig, ax = plt.subplots()
            map_utils.set_figure_size(fig)
            ax.set_title(f"{attr} {label}")
            ax.set_xlabel("Time (minutes)")
            ax.grid(alpha=0.5, axis='y')

            if attr in capacity and 'QueueLength' != attr:
                for app, app_data in sorted(capacity[attr].items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        first_timestamp = timestamps[0]
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                        series_label=f"{app} capacity"
                        frames.append(pd.DataFrame(list(values), index=times, columns=[series_label]))
                        ax.plot(times, values, label=series_label)

            for app, app_data in sorted(attr_data.items()):
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    first_timestamp = timestamps[0]
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                    frames.append(pd.DataFrame(list(values), index=times, columns=[app]))
                    ax.plot(times, values, label=app)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)

            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}.csv", index_label="relative minutes")
    except:
        get_logger().exception("Unexpected error")
        
        
def output_graphs_per_region(output, capacity, data, label):
    """
    Create a graph per node attribute.
    Creates files with names <label>-<attribute>-<region>.png
    
    Args:
        output (Path): output directory
        data (dict): region -> attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity: region -> attr -> app -> timestamp -> value
    """

    try:
        for region, region_data in data.items():
            capacity_region = capacity[region]
            
            for attr, attr_data in region_data.items():
                frames = list()
                fig, ax = plt.subplots()
                map_utils.set_figure_size(fig)
                ax.set_title(f"{attr} {label} in {region}")
                ax.set_xlabel("Time (minutes)")
                ax.grid(alpha=0.5, axis='y')

                if attr in capacity_region and 'QueueLength' != attr:
                    for app, app_data in sorted(capacity_region[attr].items()):
                        if len(app_data) > 0:
                            pairs = sorted(app_data.items())
                            timestamps, values = zip(*pairs)
                            first_timestamp = timestamps[0]
                            times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                            series_label=f"{app} capacity"
                            frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                            ax.plot(times, values, label=series_label)

                for app, app_data in sorted(attr_data.items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        first_timestamp = timestamps[0]
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[app]))
                        ax.plot(times, values, label=app)

                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}-{region}.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
                
                # write out CSV of the plot data
                df = pd.concat(frames, axis=1)
                df.to_csv(output / f"{label}-{attr}-{region}.csv", index_label="relative minutes")
    except:
        get_logger().exception("Unexpected error")


def output_region_graphs_for_app(output, capacity, data, label, app):
    """
    Create a graph per node attribute for the specified app with a series for each region.
    Creates files with names <label>-<attribute>-<app>.png
    
    Args:
        output (Path): output directory
        data (dict): region -> attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity (dict): region -> attr -> app -> timestamp -> value
        app (str): service to generate the graph for
    """

    try:
        all_regions = set(capacity.keys())
        all_attrs = set()
        for region, region_data in data.items():
            all_attrs.update(region_data.keys())

        for attr in all_attrs:
            frames = list()
            fig, ax = plt.subplots()
            map_utils.set_figure_size(fig)
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")
            ax.grid(alpha=0.5, axis='y')

            for region in sorted(all_regions):
                region_data = data.get(region, dict())
                attr_data = region_data.get(attr, dict())
                capacity_region = capacity.get(region, dict())

                if attr in capacity_region and 'QueueLength' != attr:
                    app_data = capacity_region[attr].get(app, dict())
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        first_timestamp = timestamps[0]
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                        series_label=f"{region} capacity"
                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                        ax.plot(times, values, label=series_label)

                app_data = attr_data.get(app, dict())
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    first_timestamp = timestamps[0]
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[region]))
                    ax.plot(times, values, label=region)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}-{app}.csv", index_label="relative minutes")
    except:
        get_logger().exception("Unexpected error")
        

def output_capacity_graph(output, capacity):
    """
    Create a capacity graph per node attribute.
    Creates files with names capacity-<attribute>.png
    
    Args:
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
    """

    try:
        label='capacity'
        for attr, attr_data in capacity.items():
            frames = list()
            fig, ax = plt.subplots()
            map_utils.set_figure_size(fig)
            ax.set_title(f"{attr} {label}")
            ax.set_xlabel("Time (minutes)")
            ax.grid(alpha=0.5, axis='y')

            if 'QueueLength' == attr:
                # graphing queue length capacity doesn't work well because we've hardcoded it to be a big number
                continuen

            for app, app_data in sorted(attr_data.items()):
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    first_timestamp = timestamps[0]
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                    series_label=f"{app} capacity"
                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                    ax.plot(times, values, label=series_label)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)

            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}.csv", index_label="relative minutes")
            
    except:
        get_logger().exception("Unexpected error")


def output_capacity_graph_per_region(output, capacity):
    """
    Create a capacity graph per node attribute per region.
    Creates files with names capacity-<attribute>-<region>.png

    Args:
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
    """

    try:
        label='capacity'
        for region, region_data in capacity.items():
            for attr, attr_data in region_data.items():
                frames = list()
                fig, ax = plt.subplots()
                map_utils.set_figure_size(fig)
                ax.set_title(f"{attr} {label} in {region}")
                ax.set_xlabel("Time (minutes)")
                ax.grid(alpha=0.5, axis='y')

                if 'QueueLength' == attr:
                    # graphing queue length capacity doesn't work well because we've hardcoded it to be a big number
                    continue

                for app, app_data in sorted(attr_data.items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        first_timestamp = timestamps[0]
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                        series_label=f"{app} capacity"
                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                        ax.plot(times, values, label=series_label)

                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}-{region}.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
                
                # write out CSV of the plot data
                df = pd.concat(frames, axis=1)
                df.to_csv(output / f"{label}-{attr}-{region}.csv", index_label="relative minutes")
    except:
        get_logger().exception("Unexpected error")


def output_region_capacity_graph_for_app(output, capacity, app):
    """
    Create a graph per node attribute for the specified service with a series for each region.
    Creates files with names capacity-<attribute>-<app>.png
    
    Args:
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
        app (str): name of the service to generate the graph for
    """

    try:
        label='capacity'

        all_regions = set(capacity.keys())
        all_attrs = set()
        for region, region_data in capacity.items():
            all_attrs.update(region_data.keys())

        for attr in all_attrs:
            if 'QueueLength' == attr:
                # graphing queue length capacity doesn't work well because we've hardcoded it to be a big number
                continue

            frames = list()
            fig, ax = plt.subplots()
            map_utils.set_figure_size(fig)
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")
            ax.grid(alpha=0.5, axis='y')

            for region in sorted(all_regions):
                region_data = capacity.get(region, dict())
                attr_data = region_data.get(attr, dict())

                app_data = attr_data.get(app, dict())
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    first_timestamp = timestamps[0]
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in timestamps]

                    series_label=f"{region} capacity"
                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                    ax.plot(times, values, label=series_label)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}-{app}.csv", index_label="relative minutes")
    except:
        get_logger().exception("Unexpected error")
            
        
def fill_in_missing_containers(all_apps, data_capacity, node_all_times, node_containers):
    """
    Make sure there is an entry for all containers at all times.
    This ensures that the graphs seen fewer containers when they are deallocated.
    """
    all_attrs = list(data_capacity.keys())
    all_containers = set()
    for node_attr, attr_data in data_capacity.items():
        for app, app_data in attr_data.items():
            all_containers.update(app_data.keys())

    # fill in zeros for missing container names
    for attr in all_attrs:
        for app in all_apps:
            for container_name_short in node_containers:
                attr_compute_data = data_capacity.get(attr, dict())
                app_compute_data = attr_compute_data.get(app, dict())
                node_compute_data = app_compute_data.get(container_name_short, dict())
                
                for time in node_all_times:
                    if time not in node_compute_data:
                        node_compute_data[time] = 0
                        
                app_compute_data[container_name_short] = node_compute_data
                attr_compute_data[app] = app_compute_data
                data_capacity[attr] = attr_compute_data
                    

def fill_in_missing_apps(all_apps, data, ncp, node_all_times):
    """Make sure there is an entry for all apps at all times for a node.
    This ensures that the graphs seen 0 load/demand when there are no
    applications running on a node.

    Arguments:
    all_apps (list): all applications known
    data (dict): node_attr -> app -> ncp_name -> timestamp -> value (modified)
    node_all_times (list): all times for this node
    """
    all_attrs = list(data.keys())

    # fill in zeros for applications
    for attr in all_attrs:
        for app in all_apps:
            attr_compute_data = data.get(attr, dict())
            app_compute_data = attr_compute_data.get(app, dict())
            node_compute_data = app_compute_data.get(ncp, dict())

            for time in node_all_times:
                if time not in node_compute_data:
                    node_compute_data[time] = 0

            app_compute_data[ncp] = node_compute_data
            attr_compute_data[app] = app_compute_data
            data[attr] = attr_compute_data

                
def process_node(all_apps, data_load, data_demand, data_capacity, node_dir, container_node):
    node_all_times = set()
    node_containers = set()
    node_name_dir = map_utils.find_ncp_folder(node_dir)
    if node_name_dir is None:
        get_logger().debug("No NCP folder found in %s", node_name_dir)
        return

    ncp = map_utils.node_name_from_dir(node_name_dir)
    
    get_logger().debug("Processing ncp folder %s from %s. NCP name: %s", node_name_dir, node_dir, ncp)
    for time_dir in node_name_dir.iterdir():
        if not time_dir.is_dir():
            continue

        time = int(time_dir.stem)
        node_all_times.add(time)

        resource_report_file = time_dir / 'resourceReport-SHORT.json'
        if resource_report_file.exists():
            try:
                with open(resource_report_file, 'r') as f:
                    resource_report = json.load(f)
                process_resource_report(ncp, data_load, data_demand, data_capacity, node_containers, time, resource_report)
            except json.decoder.JSONDecodeError:
                get_logger().warning("Problem reading %s, skipping", resource_report_file)

    fill_in_missing_apps(all_apps, data_load, ncp, node_all_times)
    fill_in_missing_apps(all_apps, data_demand, ncp, node_all_times)
            
    fill_in_missing_containers(all_apps, data_capacity, node_all_times, node_containers)

    for container in node_containers:
        container_node[container] = ncp


def gather_region_info(sim_output):
    """
    Arguments:
        sim_output (Path): the simulation output directory
    Returns:
        dict: node name (str) to region name (str)
    """
    node_regions = dict()
    scenario = sim_output / 'inputs/scenario'
    if not scenario.exists():
        return node_regions
    
    for node_info in scenario.glob('*.json'):
        if not node_info.is_file():
            continue

        try:
            with open(node_info, 'r') as f:
                node_data = json.load(f)
        except json.decoder.JSONDecodeError:
            get_logger().warning("Problem reading node information %s, skipping", node_info)
        if 'region' in node_data:
            node_name = node_info.stem
            node_regions[node_name] = node_data['region']
        
    return node_regions


def gather_all_apps(sim_output):
    new_location = sim_output / 'inputs/scenario/service-configurations.json'
    old_location = sim_output / 'inputs/service-configurations.json'
    if new_location.exists():
        location = new_location
    elif old_location.exists():
        location = old_location
    else:
        raise RuntimeError("Cannot find path to service-configurations.json")
    
    with open(location) as f:
        services = json.load(f)

    all_apps = set()
    for service_config in services:
        service = service_config['service']
        app = service['artifact']
        all_apps.add(app)
    return all_apps


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    all_apps = gather_all_apps(sim_output)

    # container_name -> ncp_name
    container_node = dict()
    
    # node_attr -> app -> ncp_name -> timestamp -> value
    data_load = dict() 
    data_demand = dict()
    # node_attr -> app -> container_name -> timestamp -> value
    data_capacity = dict()
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        process_node(all_apps, data_load, data_demand, data_capacity, node_dir, container_node)

    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()

        ### region graphs
        
        # ncp_name -> region_name
        node_region = gather_region_info(sim_output)
        get_logger().debug("node_region: %s", node_region)

        data_load_per_region_result = pool.apply_async(func=sum_values_by_region, args=[data_load, node_region])
        data_demand_per_region_result = pool.apply_async(func=sum_values_by_region, args=[data_demand, node_region])
        capacity_per_region_result = pool.apply_async(func=sum_capacity_values_by_region, args=[data_capacity, node_region, container_node])

        data_load_per_region = data_load_per_region_result.get()
        data_demand_per_region = data_demand_per_region_result.get()
        capacity_per_region = capacity_per_region_result.get()

        results.append(pool.apply_async(func=output_graphs_per_region, args=[output, capacity_per_region, data_load_per_region, 'load']))
        results.append(pool.apply_async(func=output_graphs_per_region, args=[output, capacity_per_region, data_demand_per_region, 'demand']))
        results.append(pool.apply_async(func=output_capacity_graph_per_region, args=[output, capacity_per_region]))

        for app in all_apps:
            results.append(pool.apply_async(func=output_region_graphs_for_app, args=[output, capacity_per_region, data_load_per_region, 'load', app]))
            results.append(pool.apply_async(func=output_region_graphs_for_app, args=[output, capacity_per_region, data_demand_per_region, 'demand', app]))
            results.append(pool.apply_async(func=output_region_capacity_graph_for_app, args=[output, capacity_per_region, app]))

            
        ### total graphs
        
        #get_logger().debug("Data load: %s", data_load)
        # node_attr -> app -> timestamp -> value
        data_load_summed_result = pool.apply_async(func=sum_values, args=[data_load])
        #get_logger().debug("Data load summed: %s", data_load_summed)
        
        # node_attr -> app -> timestamp -> value
        data_demand_summed_result = pool.apply_async(func=sum_values, args=[data_demand])
        
        #get_logger().debug("Data capacity: %s", data_capacity)
        # node_attr -> app -> timestamp -> value
        data_capacity_summed_result = pool.apply_async(func=sum_values, args=[data_capacity])
        #get_logger().debug("Data capacity summed: %s", data_capacity_summed)
        
        data_load_summed = data_load_summed_result.get()
        data_demand_summed = data_demand_summed_result.get()
        data_capacity_summed = data_capacity_summed_result.get()
        
        results.append(pool.apply_async(func=output_graphs, args=[output, data_capacity_summed, data_load_summed, "load"]))
        results.append(pool.apply_async(func=output_graphs, args=[output, data_capacity_summed, data_demand_summed, "demand"]))
        results.append(pool.apply_async(func=output_capacity_graph, args=[output, data_capacity_summed]))

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
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)

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
