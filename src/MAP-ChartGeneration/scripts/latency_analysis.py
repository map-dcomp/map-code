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
Analyaze the processing latency files for both client and server.

Creates images:
  - client_duration_with_server_{service}.png
  - client_duration_with_server_per_service.png
  - server_processing_duration_per_service.png
  - {client}_request_duration.png
  - client_request_duration_per_service.png
  - client_request_duration.png
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
    
    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    
    import matplotlib.pyplot as plt
    import pandas as pd
    import numpy as np
    import multiprocessing
    from pathlib import Path
    import map_utils

script_dir = os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def get_container_service(time_dir, image):
    service_file = time_dir / 'service.json'
    if service_file.exists():
        with open(service_file, 'r') as f:
            data = json.load(f)
            if 'artifact' in data:
                return data['artifact']
            else:
                get_logger().warn("Could not find service artifact in %s, using image name for service name", service_file)
    else:
        get_logger().warn("Could not find %s, using image name for service name", service_file)
        return image


def process_server(name, metrics_dir):
    frames = list()
    for image_dir in metrics_dir.iterdir():
        image = image_dir.name
        for container_dir in image_dir.iterdir():
            container_name = map_utils.node_name_from_dir(container_dir)
            for time_dir in container_dir.iterdir():
                service = get_container_service(time_dir, image)
                latency_file = time_dir / 'app_metrics_data/processing_latency.csv'
                if latency_file.exists() and latency_file.stat().st_size > 0:
                    try:
                        df = pd.read_csv(latency_file)
                        df['server'] = name
                        df['service'] = service
                        df['container'] = container_name
                        frames.append(df)
                    except pd.errors.ParserError as e:
                        get_logger().error("Error reading %s", latency_file, exc_info=e)
                        raise e

    return frames


def process_client(name, client_metrics_dir):
    get_logger().debug("Processing client {}".format(name))

    frames = list()
    for service_dir in client_metrics_dir.iterdir():
        service = service_dir.name
        for client_container_dir in service_dir.iterdir():
            latency_file = client_container_dir / 'app_metrics_data/processing_latency.csv'
            if latency_file.exists() and latency_file.stat().st_size > 0:
                df = pd.read_csv(latency_file)
                df['service'] = service
                df['client'] = name
                frames.append(df)

    return frames


def write_client_data(output_dir, all_clients, all_services, client_data):
    fig, ax = plt.subplots()
    ax.set_title('Client request duration over time per service and client')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    with open(output_dir / 'client_request_duration.csv', 'w') as f:
        f.write('client,service,requests,avg_duration\n')
        successful_requests = dict()
        for node_dir in all_clients:
            for service in all_services:
                plot_data = client_data.loc[ (client_data['client'] == node_dir) & (client_data['service'] == service) ]

                label = "{0}-{1}".format(node_dir, service)
                ax.scatter(plot_data['time_minutes'], plot_data['latency_seconds'], label=label, s=1)

                request_count = client_data.loc[(client_data['client'] == node_dir) & (client_data['service'] == service)].count()['timestamp']
                get_logger().info("Number of requests executed by %s for %s = %d", node_dir, service, request_count)
                successful_requests[service] = successful_requests.get(service, 0) + request_count

                avg_duration = plot_data['latency_seconds'].mean()
                count = plot_data['latency_seconds'].size
                f.write('{0},{1},{2},{3}\n'.format(node_dir, service, count, avg_duration))

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / 'client_request_duration.png'
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    get_logger().info("Total client requests by service %s", successful_requests)

    # get latency per service across all clients
    fig, ax = plt.subplots()
    ax.set_title('Client request duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    for service in all_services:
        plot_data = client_data.loc[ (client_data['service'] == service) ]

        label = "{0}".format(service)
        ax.scatter(plot_data['time_minutes'], plot_data['latency_seconds'], label=label, s=1)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / 'client_request_duration_per_service.png'
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

    plt.close(fig)

    with open(output_dir / 'client-service-counts.csv', 'w') as f:
        f.write('service,requests\n')
        for (service, count) in successful_requests.items():
            f.write('{0},{1}\n'.format(service, count))


def write_per_client(output_dir, all_services, all_clients, client_data):
    for client in all_clients:
        fig, ax = plt.subplots()
        ax.set_title('{0} request duration over time per service'.format(client))
        ax.set_ylabel('duration (s)')
        ax.set_xlabel('time (minutes)')
        ax.grid(alpha=0.5, axis='y')

        for service in all_services:
            plot_data = client_data.loc[ (client_data['client'] == client) & (client_data['service'] == service) ]

            label = "{0}".format(service)
            ax.scatter(plot_data['time_minutes'], plot_data['latency_seconds'], label=label, s=1)

        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

        output_name = output_dir / '{0}_request_duration.png'.format(client)
        fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

        plt.close(fig)


def write_server_data(output_dir, all_services, server_data):
    fig, ax = plt.subplots()
    ax.set_title('Server processing duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    for service in all_services:
        plot_data = server_data.loc[ (server_data['service'] == service) ]

        label = "{0}".format(service)
        ax.scatter(plot_data['time_minutes'], plot_data['latency_seconds'], label=label, s=1)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / 'server_processing_duration_per_service.png'
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

    plt.close(fig)


def write_combined(output_dir, all_services, server_data, client_data):
    fig, ax = plt.subplots()
    ax.set_title('Client request and server processing duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    for service in all_services:
        server_plot_data = server_data.loc[(server_data['service'] == service)]
        server_plot_data = server_plot_data.sort_values('time_minutes')

        server_label = "server processing {0}".format(service)
        ax.fill_between(server_plot_data['time_minutes'], server_plot_data['latency_seconds'], label=server_label,
                        interpolate=True, alpha=0.33)

        client_plot_data = client_data.loc[(client_data['service'] == service)]

        client_label = "client request {0}".format(service)
        ax.scatter(client_plot_data['time_minutes'], client_plot_data['latency_seconds'], label=client_label, s=1)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / 'client_duration_with_server_per_service.png'
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

    plt.close(fig)


def write_combined_per_service(output_dir, all_services, server_data, client_data):

    for service in all_services:
        fig, ax = plt.subplots()
        ax.set_title('Client request and server processing duration over time for service {}'.format(service))
        ax.set_ylabel('duration (s)')
        ax.set_xlabel('time (minutes)')
        ax.grid(alpha=0.5, axis='y')

        server_plot_data = server_data.loc[(server_data['service'] == service)]
        server_plot_data = server_plot_data.sort_values('time_minutes')

        server_label = "server processing {0}".format(service)
        ax.fill_between(server_plot_data['time_minutes'], server_plot_data['latency_seconds'], label=server_label,
                        interpolate=True, alpha=0.33)

        client_plot_data = client_data.loc[(client_data['service'] == service)]

        client_label = "client request {0}".format(service)
        ax.scatter(client_plot_data['time_minutes'], client_plot_data['latency_seconds'], label=client_label, s=1)

        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

        output_name = output_dir / 'client_duration_with_server_{}.png'.format(service)
        fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

        plt.close(fig)


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Hifi sim output directory (Required)",
                        required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # read all of the data in
    client_frames = list()
    server_frames = list()
    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        client_results = list()
        server_results = list()
        for node_dir in sim_output.iterdir():
            if not node_dir.is_dir():
                continue

            client_metrics_dir = node_dir / 'client/container_data'
            if client_metrics_dir.exists() and client_metrics_dir.is_dir():
                result = pool.apply_async(func=process_client, args=[node_dir.name, client_metrics_dir])
                client_results.append(result)

            server_metrics_dir = node_dir / 'agent/container_data'
            if server_metrics_dir.exists() and server_metrics_dir.is_dir():
                result = pool.apply_async(func=process_server, args=[node_dir.name, server_metrics_dir])
                server_results.append(result)

        for result in client_results:
            frames = result.get()
            client_frames.extend(frames)

        for result in server_results:
            frames = result.get()
            server_frames.extend(frames)
            
    if len(client_frames) < 1:
        get_logger().error("No client data")
        return

    if len(server_frames) < 1:
        get_logger().error("No server data")
        return

    client_data = pd.concat(client_frames, ignore_index=True)
    server_data = pd.concat(server_frames, ignore_index=True)

    earliest_time = min(client_data['time_ack_received'].min(),
                        server_data['timestamp'].min())

    client_data['time_minutes'] = (client_data['time_ack_received'] - earliest_time) / 1000 / 60
    client_data['latency_seconds'] = client_data['latency'] / 1000

    server_data['time_minutes'] = (server_data['timestamp'] - earliest_time) / 1000 / 60
    server_data['latency_seconds'] = server_data['latency'] / 1000

    get_logger().debug(server_data.head())

    all_clients = client_data['client'].unique()

    all_services = np.unique(np.concatenate([client_data['service'].unique(), server_data['service'].unique()], axis=0))

    get_logger().debug("Client services {}".format(client_data['service'].unique()))
    get_logger().debug("Server services {}".format(server_data['service'].unique()))
    get_logger().debug("all services {}".format(all_services))

    write_client_data(output_dir, all_clients, all_services, client_data)
    write_per_client(output_dir, all_services, all_clients, client_data)

    with open(output_dir / 'all_client_requests.csv', 'w') as f:
        client_data.to_csv(f, index=False)

    write_server_data(output_dir, all_services, server_data)

    write_combined(output_dir, all_services, server_data, client_data)
    write_combined_per_service(output_dir, all_services, server_data, client_data)


if __name__ == "__main__":
    sys.exit(main())

