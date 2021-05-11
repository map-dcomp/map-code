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
Analyze the processing latency files for both client and server.

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
                get_logger().warning("Could not find service artifact in %s, using image name for service name", service_file)
    else:
        get_logger().warning("Could not find %s, using image name for service name", service_file)
        return image

    
def process_server_latency_file(server_name, service, container_name, latency_file):
    if latency_file.exists() and latency_file.stat().st_size > 0:
        try:
            df = pd.read_csv(latency_file)
            df['server'] = server_name
            df['service'] = service
            df['container'] = container_name
            return df
        except pd.errors.ParserError as e:
            get_logger().error("Error reading %s", latency_file, exc_info=e)
            raise e
    else:
        return None
    

def process_server(name, server_container_data):
    s_frames = list()
    c_frames = list()
    for image_dir in server_container_data.iterdir():
        image = image_dir.name
        for container_dir in image_dir.iterdir():
            container_name = map_utils.node_name_from_dir(container_dir)
            for time_dir in container_dir.iterdir():
                service = get_container_service(time_dir, image)
                latency_file = time_dir / 'app_metrics_data/processing_latency.csv'
                df = process_server_latency_file(latency_file, service, container_name, latency_file)
                if df is not None:
                    s_frames.append(df)

                dependent_services_dir = time_dir / 'dependent-services'
                if dependent_services_dir.exists():
                    for d_service_dir in dependent_services_dir.iterdir():
                        d_latency_file = d_service_dir / 'processing_latency.csv'
                        d_df = process_client_latency_file(d_latency_file)
                        if d_df is not None:
                            c_frames.append(d_df)

    return s_frames, c_frames


def process_client_latency_file(client_name, service, latency_file):
    if latency_file.exists() and latency_file.stat().st_size > 0:
        df = pd.read_csv(latency_file)
        df['service'] = service
        df['client'] = client_name
        return df
    else:
        return None


def process_client(name, client_metrics_dir):
    get_logger().debug("Processing client {}".format(name))

    frames = list()
    for service_dir in client_metrics_dir.iterdir():
        service = service_dir.name
        for client_container_dir in service_dir.iterdir():
            latency_file = client_container_dir / 'app_metrics_data/processing_latency.csv'
            df = process_client_latency_file(name, service, latency_file)
            if df is not None:
                frames.append(df)

    return frames


def write_client_data(output_dir, all_clients, all_services, client_data):
    fig, ax = map_utils.subplots()
    ax.set_title('Client request duration over time per service and client')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')

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
    fig, ax = map_utils.subplots()
    ax.set_title('Client request duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')

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
        fig, ax = map_utils.subplots()
        ax.set_title('{0} request duration over time per service'.format(client))
        ax.set_ylabel('duration (s)')
        ax.set_xlabel('time (minutes)')

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
    fig, ax = map_utils.subplots()
    ax.set_title('Server processing duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')

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
    fig, ax = map_utils.subplots()
    ax.set_title('Client request and server processing duration over time per service')
    ax.set_ylabel('duration (s)')
    ax.set_xlabel('time (minutes)')

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
        fig, ax = map_utils.subplots()
        ax.set_title('Client request and server processing duration over time for service {}'.format(service))
        ax.set_ylabel('duration (s)')
        ax.set_xlabel('time (minutes)')

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


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)

    earliest_time = first_timestamp.timestamp() * 1000
    
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

            server_container_data = node_dir / 'agent/container_data'
            if server_container_data.exists() and server_container_data.is_dir():
                result = pool.apply_async(func=process_server, args=[node_dir.name, server_container_data])
                server_results.append(result)

        for result in client_results:
            frames = result.get()
            client_frames.extend(frames)

        for result in server_results:
            (s_frames, c_frames) = result.get()
            server_frames.extend(s_frames)
            client_frames.extend(c_frames)
            
    if len(client_frames) > 0:
        client_data = pd.concat(client_frames, ignore_index=True)
        client_data['time_minutes'] = (client_data['time_ack_received'] - earliest_time) / 1000 / 60
        client_data['latency_seconds'] = client_data['latency'] / 1000
        all_clients = client_data['client'].unique()

        get_logger().debug("Client services {}".format(client_data['service'].unique()))

        with open(output_dir / 'all_client_requests.csv', 'w') as f:
            client_data.to_csv(f, index=False)
    else:
        get_logger().warning("No client data")

    if len(server_frames) > 0:
        server_data = pd.concat(server_frames, ignore_index=True)

        server_data['time_minutes'] = (server_data['timestamp'] - earliest_time) / 1000 / 60
        server_data['latency_seconds'] = server_data['latency'] / 1000

        get_logger().debug(server_data.head())
        get_logger().debug("Server services {}".format(server_data['service'].unique()))
    else:
        get_logger().warning("No server data")


    if len(server_frames) < 1 or len(client_frames) < 1:
        get_logger().error("Need both client and server data to write out remaining files, skipping remaining outputs")
        return 0
        
    all_services = np.unique(np.concatenate([client_data['service'].unique(), server_data['service'].unique()], axis=0))

    get_logger().debug("all services {}".format(all_services))

    write_client_data(output_dir, all_clients, all_services, client_data)
    write_per_client(output_dir, all_services, all_clients, client_data)

    write_server_data(output_dir, all_services, server_data)

    write_combined(output_dir, all_services, server_data, client_data)
    write_combined_per_service(output_dir, all_services, server_data, client_data)


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
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Hifi sim output directory (Required)",
                        required=True)
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

