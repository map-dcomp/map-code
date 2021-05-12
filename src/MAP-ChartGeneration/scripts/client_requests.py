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

"""Create graphs per node and link attribute showing the amount of
demand put on the network by the clients.  These graphs are useful for
lo-fi and when using the fake load server in hi-fi. 

"""

import warnings
with warnings.catch_warnings():
    import re
    import sys
    import argparse
    import os
    import logging
    import logging.config
    import json
    from pathlib import Path
    import map_utils
    import csv
    
    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import pandas as pd


script_dir=Path(__file__).parent.absolute()

def get_logger():
    return logging.getLogger(__name__)


def write_csv_data(csv_server, csv_network, demand_dir):
    with open(csv_server, 'w') as f_server:
        writer_server = csv.writer(f_server)
        writer_server.writerow(['client', 'service', 'attr', 'value', 'start', 'end', 'num clients'])

        with open(csv_network, 'w') as f_network:
            writer_network = csv.writer(f_network)
            writer_network.writerow(['client', 'service', 'attr', 'value', 'start', 'end', 'num clients'])

            for demand_file in demand_dir.glob("*.json"):
                try:
                    with open(demand_file) as f:
                        requests = json.load(f)
                except json.decoder.JSONDecodeError:
                    get_logger().warning("Problem reading %s, skipping", demand_file)
                    continue

                client = demand_file.stem

                for request in requests:
                    if 'service' not in request:
                        get_logger().debug("%s doesn't have 'service' propery, must not be a client demand file, skipping", demand_file)
                        continue

                    service = request['service']['artifact']
                    start = int(request['startTime'])
                    end_server = start + int(request['serverDuration'])
                    end_network = start + int(request['networkDuration'])
                    num_clients = int(request['numClients'])

                    for attr, value in request['nodeLoad'].items():
                        writer_server.writerow([client, service, attr, value, start, end_server, num_clients])
                    for attr, value in request['networkLoad'].items():
                        writer_network.writerow([client, service, attr, value, start, end_network, num_clients])

                        
def graph_num_clients(step_size, output_name, title, data):
    """
    Create a stacked graph per client pool.
    Arguments:
        output_name(Path): where to write the graph
        title(str): title for the graph
        step_size(int): number of milliseconds between samples
        data(DataFrame): data to graph
    """

    max_time = data['end'].max()
    clients = data['client'].unique()
    xs = list()
    ydata = dict() # client -> data

    time_step = 0
    while time_step < max_time:
        xs.append(map_utils.timestamp_to_minutes(time_step))

        for client in sorted(clients):
            ys = ydata.get(client, list())

            y_value = data.loc[(data['start'] <= time_step) & (time_step < data['end']) & (data['client'] == client)]['num clients'].sum()
            ys.append(y_value)
            
            ydata[client] = ys

        time_step = time_step + step_size
    
    fig, ax = map_utils.subplots()
    ax.set_title(title)
    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Number of clients")

    ax.set_xlim(left=0, right=map_utils.timestamp_to_minutes(max_time))
    stack_labels, stack_ys = zip(*(sorted(ydata.items())))
    ax.stackplot(xs, stack_ys, labels=stack_labels)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
    
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    

def graph_data(step_size, output_name, title, data):
    """
    Create a stacked graph per client.
    Arguments:
        output_name(Path): where to write the graph
        title(str): title for the graph
        step_size(int): number of milliseconds between samples
        data(DataFrame): data to graph
    """

    max_time = data['end'].max()
    clients = data['client'].unique()
    xs = list()
    ydata = dict() # client -> data

    time_step = 0
    while time_step < max_time:
        xs.append(map_utils.timestamp_to_minutes(time_step))

        for client in sorted(clients):
            ys = ydata.get(client, list())

            y_value = data.loc[(data['start'] <= time_step) & (time_step < data['end']) & (data['client'] == client)]['client value'].sum()
            ys.append(y_value)
            
            ydata[client] = ys

        time_step = time_step + step_size
    
    fig, ax = map_utils.subplots()
    ax.set_title(title)
    ax.set_xlabel("Time (minutes)")

    ax.set_xlim(left=0, right=map_utils.timestamp_to_minutes(max_time))
    stack_labels, stack_ys = zip(*(sorted(ydata.items())))
    ax.stackplot(xs, stack_ys, labels=stack_labels)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
    
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    
def main_method(args):
    demand_dir = Path(args.demand)
    if not demand_dir.exists():
        get_logger().error("%s does not exist", demand_dir)
        return 1

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    step_size = args.step_size

    csv_server = output_dir / 'requests_server.csv'
    csv_network = output_dir / 'requests_network.csv'

    write_csv_data(csv_server, csv_network, demand_dir)

    df_server = pd.read_csv(csv_server)
    df_server['client value'] = df_server['value'] * df_server['num clients']
    services_server = df_server['service'].unique()
    attrs_server = df_server['attr'].unique()
    for service in services_server:
        for attr in attrs_server:
            data = df_server.loc[(df_server['service'] == service) & (df_server['attr'] == attr)]
            title = f'Client demand for server attribute {attr} of {service}'
            output_name = output_dir / f'client_demand-server-{service}-{attr}.png'
            graph_data(step_size, output_name, title, data)
    
    for attr in attrs_server:
        data = df_server.loc[df_server['attr'] == attr]
        title = f'Client demand for server attribute {attr} for all services'
        output_name = output_dir / f'client_demand-server-all-{attr}.png'
        graph_data(step_size, output_name, title, data)

    df_network = pd.read_csv(csv_network)
    df_network['client value'] = df_network['value'] * df_network['num clients']
    services_network = df_network['service'].unique()
    attrs_network = df_network['attr'].unique()
    for service in services_network:
        for attr in attrs_network:
            data = df_network.loc[(df_network['service'] == service) & (df_network['attr'] == attr)]
            title = f'Client demand for network attribute {attr} of {service}'
            output_name = output_dir / f'client_demand-network-{service}-{attr}.png'
            graph_data(step_size, output_name, title, data)

    for attr in attrs_network:
        data = df_network.loc[df_network['attr'] == attr]
        title = f'Client demand for network attribute {attr} for all services'
        output_name = output_dir / f'client_demand-network-all-{attr}.png'
        graph_data(step_size, output_name, title, data)

    title = f'Number of active clients for all services'
    output_name = output_dir / f'num-clients-all.png'
    graph_num_clients(step_size, output_name, title, df_server)


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
    parser.add_argument("--demand", dest="demand", help="Directory to read the demand information from", required=True)
    parser.add_argument("--output", dest="output", help="Directory to write the demand information to", required=True)
    parser.add_argument("--step-size", dest="step_size", help="Number of milliseconds between samples", type=int, default=10000)

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
