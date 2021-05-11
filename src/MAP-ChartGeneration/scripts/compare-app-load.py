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
Plot load, LONG and SHORT demand for each service on the same graph along with capacity.

creates graphs:
  - {load, demand-{long,short}, capacity}-{attr}.png
  - {load, demand-{long,short}, capacity}-{attr}-{app}.png
  - {load, demand-{long,short}, capacity}-{attr}-{region}.png
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
    import datetime


script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)


def process_load_or_demand(time, node_name, data, compute_data):
    # data: attr -> app -> ncp -> timestamp -> value
    for appStr, app_compute_data in compute_data.items():
        app = map_utils.get_service_artifact(appStr)
        if app:
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

    if data_load is not None:
        compute_load = resource_report['computeLoad']
        process_load_or_demand(time, name, data_load, compute_load)

    compute_demand = resource_report['computeDemand']
    process_load_or_demand(time, name, data_demand, compute_demand)

    if data_capacity is not None:
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


def output_graphs(first_timestamp, output, capacity, data, label):
    """
    Create a graph per node attribute.
    Creates files with names <label>-<attribute>.png
    
    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        data (dict): attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity: attr -> app -> timestamp -> value
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    
    try:
        for attr, attr_data in data.items():
            frames = list()

            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label}")
            ax.set_xlabel("Time (minutes)")

            max_minutes = 0
            stack_xs = None
            stack_ys = list()
            stack_labels = list()
            total_capacity = None
            if attr in capacity and 'QueueLength' != attr:
                for app, app_data in sorted(capacity[attr].items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                        max_minutes = max(max_minutes, max(times))

                        series_label=f"{app} capacity"
                        frames.append(pd.DataFrame(list(values), index=times, columns=[series_label]))
                        ax.plot(times, values, label=series_label)

                        if total_capacity is None:
                            total_capacity = app_data.copy()
                        else:
                            total_capacity = {k: total_capacity.get(k, 0) + app_data.get(k, 0) for k in set(total_capacity) | set(app_data)}

            app_timestamps = None
            for app, app_data in sorted(attr_data.items()):
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                    max_minutes = max(max_minutes, max(times))

                    if app_timestamps is None:
                        app_timestamps = timestamps

                    frames.append(pd.DataFrame(list(values), index=times, columns=[app]))
                    ax.plot(times, values, label=app)

                    if stack_xs is None:
                        stack_xs = times
                    stack_ys.append(values)
                    stack_labels.append(app)

            ax.set_xlim(left=0, right=max_minutes)
                    
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)

            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}.csv", index_label="relative minutes")

            if app_timestamps is not None and total_capacity is not None:
                # create stacked plot
                fig, ax = map_utils.subplots()
                ax.set_title(f"{attr} {label}")
                ax.set_xlabel("Time (minutes)")
                ax.set_xlim(left=0, right=max_minutes)
                ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

                total_capacity = map_utils.fill_missing_times(app_timestamps, total_capacity)
                ax.plot(stack_xs, total_capacity, label="Total allocated capacity")

                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}_stacked.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
            elif 'QueueLength' != attr:
                get_logger().warning("Missing data to draw stackplot for %s %s", label, attr)
            

    except:
        get_logger().exception("Unexpected error")
        
        
def output_graphs_per_region(first_timestamp, output, capacity, data, label):
    """
    Create a graph per node attribute.
    Creates files with names <label>-<attribute>-<region>.png
    
    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        data (dict): region -> attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity: region -> attr -> app -> timestamp -> value
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000

    max_minutes = 0
    try:
        for region, region_data in data.items():
            capacity_region = capacity[region]
            
            for attr, attr_data in region_data.items():
                frames = list()
                fig, ax = map_utils.subplots()
                ax.set_title(f"{attr} {label} in {region}")
                ax.set_xlabel("Time (minutes)")
                
                stack_xs = None
                stack_ys = list()
                stack_labels = list()
                total_capacity = None
                if attr in capacity_region and 'QueueLength' != attr:
                    for app, app_data in sorted(capacity_region[attr].items()):
                        if len(app_data) > 0:
                            pairs = sorted(app_data.items())
                            timestamps, values = zip(*pairs)
                            times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                            max_minutes = max(max_minutes, max(times))

                            series_label=f"{app} capacity"
                            frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                            ax.plot(times, values, label=series_label)

                            if total_capacity is None:
                                total_capacity = app_data.copy()
                            else:
                                total_capacity = {k: total_capacity.get(k, 0) + app_data.get(k, 0) for k in set(total_capacity) | set(app_data)}

                app_timestamps = None
                for app, app_data in sorted(attr_data.items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                        max_minutes = max(max_minutes, max(times))

                        if app_timestamps is None:
                            app_timestamps = timestamps
                        
                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[app]))
                        ax.plot(times, values, label=app)

                        if stack_xs is None:
                            stack_xs = times
                        stack_ys.append(values)
                        stack_labels.append(app)

                ax.set_xlim(left=0, right=max_minutes)                        
                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}-{region}.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
                
                # write out CSV of the plot data
                df = pd.concat(frames, axis=1)
                df.to_csv(output / f"{label}-{attr}-{region}.csv", index_label="relative minutes")

                if app_timestamps is not None and total_capacity is not None:
                    # create stacked plot
                    fig, ax = map_utils.subplots()
                    ax.set_title(f"{attr} {label} in {region}")
                    ax.set_xlabel("Time (minutes)")
                    ax.set_xlim(left=0, right=max_minutes)                        
                    ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

                    total_capacity = map_utils.fill_missing_times(app_timestamps, total_capacity)
                    ax.plot(stack_xs, total_capacity, label="Total allocated capacity")

                    handles, labels = ax.get_legend_handles_labels()
                    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                    output_name = output / f"{label}-{attr}-{region}_stacked.png"
                    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                    plt.close(fig)
                elif 'QueueLength' != attr:
                    get_logger().warning("Missing data to draw stackplot for %s %s in %s", label, attr, region)
                
    except:
        get_logger().exception("Unexpected error")


def output_region_graphs_for_app(first_timestamp, output, capacity, data, label, app):
    """
    Create a graph per node attribute for the specified app with a series for each region.
    Creates files with names <label>-<attribute>-<app>.png
    
    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        data (dict): region -> attr -> app -> timestamp -> value
        label (str): used to label the graph and generate the filenames
        capacity (dict): region -> attr -> app -> timestamp -> value
        app (str): service to generate the graph for
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000

    max_minutes = 0
    try:
        all_regions = set(capacity.keys())
        all_attrs = set()
        for region, region_data in data.items():
            all_attrs.update(region_data.keys())

        for attr in all_attrs:
            stack_xs = None
            stack_ys = list()
            stack_labels = list()
            
            frames = list()
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")

            for region in sorted(all_regions):
                region_data = data.get(region, dict())
                attr_data = region_data.get(attr, dict())
                capacity_region = capacity.get(region, dict())

                if attr in capacity_region and 'QueueLength' != attr:
                    app_data = capacity_region[attr].get(app, dict())
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                        max_minutes = max(max_minutes, max(times))

                        series_label=f"{region} capacity"
                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                        ax.plot(times, values, label=series_label)

                app_data = attr_data.get(app, dict())
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                    max_minutes = max(max_minutes, max(times))

                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[region]))
                    ax.plot(times, values, label=region)
                    
                    if stack_xs is None:
                        stack_xs = times
                    stack_ys.append(values)
                    stack_labels.append(region)


            ax.set_xlim(left=0, right=max_minutes)
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}-{app}.csv", index_label="relative minutes")

            # create stacked plot
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")
            ax.set_xlim(left=0, right=max_minutes)
            ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}_stacked.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
    except:
        get_logger().exception("Unexpected error")
        

def output_capacity_graph(first_timestamp, output, capacity):
    """
    Create a capacity graph per node attribute.
    Creates files with names capacity-<attribute>.png
    
    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    max_minutes = 0
    try:
        label='capacity'
        for attr, attr_data in capacity.items():
            frames = list()
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label}")
            ax.set_xlabel("Time (minutes)")

            stack_xs = None
            stack_ys = list()
            stack_labels = list()
            
            if 'QueueLength' == attr:
                # graphing queue length capacity doesn't work well because we've hardcoded it to be a big number
                continue

            for app, app_data in sorted(attr_data.items()):
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                    max_minutes = max(max_minutes, max(times))

                    series_label=f"{app} capacity"
                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                    ax.plot(times, values, label=series_label)

                    if stack_xs is None:
                        stack_xs = times
                    stack_ys.append(values)
                    stack_labels.append(series_label)
                    
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            ax.set_xlim(left=0, right=max_minutes)
            output_name = output / f"{label}-{attr}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)

            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}.csv", index_label="relative minutes")

            # create stacked plot
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label}")
            ax.set_xlabel("Time (minutes)")
            ax.set_xlim(left=0, right=max_minutes)
            ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}_stacked.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
    except:
        get_logger().exception("Unexpected error")


def output_capacity_graph_per_region(first_timestamp, output, capacity):
    """
    Create a capacity graph per node attribute per region.
    Creates files with names capacity-<attribute>-<region>.png

    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    max_minutes = 0
    try:
        label='capacity'
        for region, region_data in capacity.items():
            for attr, attr_data in region_data.items():
                frames = list()
                fig, ax = map_utils.subplots()
                ax.set_title(f"{attr} {label} in {region}")
                ax.set_xlabel("Time (minutes)")

                stack_xs = None
                stack_ys = list()
                stack_labels = list()
                
                if 'QueueLength' == attr:
                    # graphing queue length capacity doesn't work well because we've hardcoded it to be a big number
                    continue

                for app, app_data in sorted(attr_data.items()):
                    if len(app_data) > 0:
                        pairs = sorted(app_data.items())
                        timestamps, values = zip(*pairs)
                        times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                        max_minutes = max(max_minutes, max(times))

                        series_label=f"{app} capacity"
                        frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                        ax.plot(times, values, label=series_label)

                        if stack_xs is None:
                            stack_xs = times
                        stack_ys.append(values)
                        stack_labels.append(series_label)
                        
                ax.set_xlim(left=0, right=max_minutes)
                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}-{region}.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
                
                # write out CSV of the plot data
                df = pd.concat(frames, axis=1)
                df.to_csv(output / f"{label}-{attr}-{region}.csv", index_label="relative minutes")

                # create stacked plot
                fig, ax = map_utils.subplots()
                ax.set_title(f"{attr} {label} in {region}")
                ax.set_xlabel("Time (minutes)")
                ax.set_xlim(left=0, right=max_minutes)
                ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

                handles, labels = ax.get_legend_handles_labels()
                lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

                output_name = output / f"{label}-{attr}-{region}_stacked.png"
                fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                plt.close(fig)
                
    except:
        get_logger().exception("Unexpected error")


def output_region_capacity_graph_for_app(first_timestamp, output, capacity, app):
    """
    Create a graph per node attribute for the specified service with a series for each region.
    Creates files with names capacity-<attribute>-<app>.png
    
    Args:
        first_timestamp(datetime.datetime): when the simulation started 
        output (Path): output directory
        capacity (boolean): if true output a graph of capacity only
        app (str): name of the service to generate the graph for
    """

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    max_minutes = 0
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
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")
            
            stack_xs = None
            stack_ys = list()
            stack_labels = list()

            for region in sorted(all_regions):
                region_data = capacity.get(region, dict())
                attr_data = region_data.get(attr, dict())

                app_data = attr_data.get(app, dict())
                if len(app_data) > 0:
                    pairs = sorted(app_data.items())
                    timestamps, values = zip(*pairs)
                    times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp_ms) for t in timestamps]
                    max_minutes = max(max_minutes, max(times))

                    series_label=f"{region} capacity"
                    frames.append(pd.DataFrame(list(values), index=list(times), columns=[series_label]))
                    ax.plot(times, values, label=series_label)

                    if stack_xs is None:
                        stack_xs = times
                    stack_ys.append(values)
                    stack_labels.append(series_label)
                    
            ax.set_xlim(left=0, right=max_minutes)
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
            # write out CSV of the plot data
            df = pd.concat(frames, axis=1)
            df.to_csv(output / f"{label}-{attr}-{app}.csv", index_label="relative minutes")

            # create stacked plot
            fig, ax = map_utils.subplots()
            ax.set_title(f"{attr} {label} for {app}")
            ax.set_xlabel("Time (minutes)")
            ax.set_xlim(left=0, right=max_minutes)
            ax.stackplot(stack_xs, stack_ys, labels=stack_labels)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{label}-{attr}-{app}_stacked.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
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

                
def process_node(first_timestamp, all_apps, data_load, data_demand_short, data_demand_long, data_capacity, node_dir, container_node):
    """
    Args:
        first_timestamp(datetime.datetime): when the simulation started
    """
    
    node_all_times = set()
    node_containers = set()
    node_name_dir = map_utils.find_ncp_folder(node_dir)
    if node_name_dir is None:
        get_logger().debug("No NCP folder found in %s", node_name_dir)
        return

    ncp = map_utils.node_name_from_dir(node_name_dir)

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    
    get_logger().debug("Processing ncp folder %s from %s. NCP name: %s", node_name_dir, node_dir, ncp)
    for time_dir in node_name_dir.iterdir():
        if not time_dir.is_dir():
            continue

        time = int(time_dir.stem)
        if time < first_timestamp_ms:
            # ignore data before the start of the simulation
            continue
        
        node_all_times.add(time)

        resource_report_file = time_dir / 'resourceReport-SHORT.json'
        if resource_report_file.exists():
            try:
                with open(resource_report_file, 'r') as f:
                    resource_report = json.load(f)
                process_resource_report(ncp, data_load, data_demand_short, data_capacity, node_containers, time, resource_report)
            except json.decoder.JSONDecodeError:
                get_logger().warning("Problem reading %s, skipping", resource_report_file)

        resource_report_file = time_dir / 'resourceReport-LONG.json'
        if resource_report_file.exists():
            try:
                with open(resource_report_file, 'r') as f:
                    resource_report = json.load(f)
                process_resource_report(ncp, None, data_demand_long, None, node_containers, time, resource_report)
            except json.decoder.JSONDecodeError:
                get_logger().warning("Problem reading %s, skipping", resource_report_file)
                
    fill_in_missing_apps(all_apps, data_load, ncp, node_all_times)
    fill_in_missing_apps(all_apps, data_demand_short, ncp, node_all_times)
    fill_in_missing_apps(all_apps, data_demand_long, ncp, node_all_times)
            
    fill_in_missing_containers(all_apps, data_capacity, node_all_times, node_containers)

    for container in node_containers:
        container_node[container] = ncp


def main_method(args):
    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)
    
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    scenario_dir = Path(args.scenario)
    if not scenario_dir.exists():
        get_logger().error("%s does not exist", scenario_dir)
        return 1
    
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    all_apps = map_utils.gather_all_services(scenario_dir)

    # container_name -> ncp_name
    container_node = dict()
    
    # node_attr -> app -> ncp_name -> timestamp -> value
    data_load = dict() 
    data_demand_short = dict()
    data_demand_long = dict()
    # node_attr -> app -> container_name -> timestamp -> value
    data_capacity = dict()
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        process_node(first_timestamp, all_apps, data_load, data_demand_short, data_demand_long, data_capacity, node_dir, container_node)

    results = list()

    ## process raw data
    get_logger().info("Processing raw data")

    # ncp_name -> region_name
    node_region = map_utils.gather_region_info(scenario_dir)
    get_logger().debug("node_region: %s", node_region)

    get_logger().info("Summing values by region")
    data_load_per_region = sum_values_by_region(data_load, node_region)
    data_demand_short_per_region = sum_values_by_region(data_demand_short, node_region)

    data_demand_long_per_region = sum_values_by_region(data_demand_long, node_region)
    capacity_per_region = sum_capacity_values_by_region(data_capacity, node_region, container_node)
    del container_node
    del node_region

    get_logger().info("Summing load")
    #get_logger().debug("Data load: %s", data_load)
    # node_attr -> app -> timestamp -> value
    data_load_summed = sum_values(data_load)
    #get_logger().debug("Data load summed: %s", data_load_summed)
    del data_load

    get_logger().info("Summing demand")
    # node_attr -> app -> timestamp -> value
    data_demand_short_summed = sum_values(data_demand_short)
    data_demand_long_summed = sum_values(data_demand_long)
    del data_demand_short
    del data_demand_long

    #get_logger().debug("Data capacity: %s", data_capacity)
    # node_attr -> app -> timestamp -> value
    data_capacity_summed = sum_values(data_capacity)
    #get_logger().debug("Data capacity summed: %s", data_capacity_summed)
    del data_capacity

    get_logger().info("Starting graph creation")

    output_graphs_per_region(first_timestamp, output, capacity_per_region, data_load_per_region, 'load')
    output_graphs_per_region(first_timestamp, output, capacity_per_region, data_demand_short_per_region, 'demand-short')
    output_graphs_per_region(first_timestamp, output, capacity_per_region, data_demand_long_per_region, 'demand-long')
    output_capacity_graph_per_region(first_timestamp, output, capacity_per_region)

    for app in all_apps:
        output_region_graphs_for_app(first_timestamp, output, capacity_per_region, data_load_per_region, 'load', app)
        output_region_graphs_for_app(first_timestamp, output, capacity_per_region, data_demand_short_per_region, 'demand-short', app)
        output_region_graphs_for_app(first_timestamp, output, capacity_per_region, data_demand_long_per_region, 'demand-long', app)
        output_region_capacity_graph_for_app(first_timestamp, output, capacity_per_region, app)


    output_graphs(first_timestamp, output, data_capacity_summed, data_load_summed, "load")
    output_graphs(first_timestamp, output, data_capacity_summed, data_demand_short_summed, "demand-short")
    output_graphs(first_timestamp, output, data_capacity_summed, data_demand_long_summed, "demand-long")
    output_capacity_graph(first_timestamp, output, data_capacity_summed)

    get_logger().info("Finished")
        
    
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
    parser.add_argument("--scenario", dest="scenario", help="Scenario directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)

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
