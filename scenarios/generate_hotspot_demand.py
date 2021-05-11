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

"""Generates additional demand for an existing demand profile.

This is sparsely documented as it's mostly the same as
generate_demand.py with some modifications to allow the user to
specify the nodes to execute on.
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
    import random
    import map_topology_infra

script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)

def setup_logging(
    default_path='logging.json',
    default_level=logging.INFO,
    env_key='LOG_CFG'
):
    """
    Setup logging configuration
    """
    path = default_path
    value = os.getenv(env_key, None)
    if value:
        path = value
    if os.path.exists(path):
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)


def minutes_to_ms(minutes):
    return minutes * 60 * 1000

def add_impulse(params, client_demand, client_pool, offset, service, impulse_start, impulse_duration, num_clients):

    demand = dict()
    demand['startTime'] = offset + int(minutes_to_ms(impulse_start))
    demand['serverDuration'] = int(minutes_to_ms(impulse_duration))
    demand['networkDuration'] = demand['serverDuration']
    demand['numClients'] = num_clients
    demand['service'] = dict()
    demand['service']['group'] = 'com.bbn'
    demand['service']['artifact'] = service
    demand['service']['version'] = '1'
    demand['nodeLoad'] = dict()
    demand['nodeLoad']['CPU'] = float(params['client_request_cpu_load'])
    demand['networkLoad'] = dict()
    demand['networkLoad']['DATARATE_TX'] = float(params['client_request_network_tx'])
    demand['networkLoad']['DATARATE_RX'] = float(params['client_request_network_rx'])

    c_demand = client_demand.get(client_pool, list())
    c_demand.append(demand)
    client_demand[client_pool] = c_demand

    
def add_feathered_imulses(params, client_demand, client_pool, offset, service, start_time, impulse_duration, num_clients, feathering_factor, feathering_range):
    feathered_request_params = [{} for i in range(feathering_factor)]
    feathered_num_clients_per_impulse = int(num_clients / feathering_factor)
    remainder = num_clients

    # assign request start times and uniformly assign largest number of clients to feathered_num_clients that will not exceed num_clients
    for i in range(len(feathered_request_params)):
        if feathering_factor > 1:
            feathered_request_params[i]['start_minutes'] = start_time + (feathering_range * 1.0 / (feathering_factor - 1) * i)
        else:
            feathered_request_params[i]['start_minutes'] = start_time

        feathered_request_params[i]['num_clients'] = feathered_num_clients_per_impulse
        remainder -= feathered_request_params[i]['num_clients']

    # assign remaining clients prioritizing earlier requests
    i = 0
    while (remainder > 0):
        feathered_request_params[i]['num_clients'] += 1
        remainder -= 1
        i = (i + 1) % len(feathered_request_params)
             

    # add each feathered requests in the form of feathering_factor impulses
    for i in range(len(feathered_request_params)):
        start_minutes = feathered_request_params[i]['start_minutes']
        num_clients = feathered_request_params[i]['num_clients']
        get_logger().debug("Feathered start_minutes: %f, num_clients: %d", start_minutes, num_clients)
        add_impulse(params, client_demand, client_pool, offset, service, start_minutes, impulse_duration, num_clients)



def create_requests(params, client_demand, client_pool, offset, app_name, start_minutes, duration_minutes, num_clients, request_duration_minutes, feathering_factor, feathering_range):
    end_time = start_minutes + duration_minutes
    
    current_start = start_minutes
    while current_start < end_time:
        add_feathered_imulses(params, client_demand, client_pool, offset, app_name, current_start, request_duration_minutes, num_clients, feathering_factor, feathering_range)
        
        current_start = current_start + request_duration_minutes


def main_method(args):
    params_file = Path(args.params)
    if not params_file.exists():
        raise RuntimeError(f"{params_file} doesn't exist")
    
    with open(params_file) as f:
        params = json.load(f)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    start_minutes = int(params.get('start_minutes'))
    end_minutes = int(params.get('end_minutes'))
    
    scenario_start_minutes = start_minutes
        
    num_clients = int(params['num_clients_per_service'])
    request_duration_minutes = 1.0
    feathering_factor = int(params['feathering_factor'])
    feathering_range = float(params['feathering_range_minutes'])
    max_random_pool_offset_ms = 0

    # pool -> client requests
    client_demand = dict()

    if 'max_random_pool_offset' in params.keys():
        max_random_pool_offset_ms = int(float(params['max_random_pool_offset']) * 60 * 1000)

    client_pools = dict()
    for pool in params['clients']:
        random_offset_ms = random.randrange(0, max_random_pool_offset_ms, 1) if max_random_pool_offset_ms > 0 else 0
        client_pools[pool] = random_offset_ms

        # read in existing data if it exists
        client_file = output_dir / '{0}.json'.format(pool)
        if client_file.exists():
            with open(client_file, 'r') as f:
                client_demand[pool] = json.load(f)
        else:
            client_demand[pool] = list()
                

    get_logger().info("client pools, pool offsets: %s", str(client_pools))

    # service -> start_minutes
    service_starts = dict()
    for service in params['services']:
        service_starts[service] = start_minutes

    get_logger().debug("Pools %s demand keys: %s", client_pools.keys(), client_demand.keys())
    for app_name, start_minutes in service_starts.items():
        duration_minutes = end_minutes - start_minutes
        for client_pool, offset in client_pools.items():
            get_logger().debug("Creating demand for service %s in pool %s", app_name, client_pool)
            create_requests(params, client_demand, client_pool, offset, app_name, start_minutes, duration_minutes, num_clients, request_duration_minutes, feathering_factor, feathering_range)

        
    for pool, offset in client_pools.items():
        output_file = output_dir / '{0}.json'.format(pool)
        with open(output_file, 'w') as output:
            json.dump(client_demand[pool], output, indent=4, sort_keys=True)



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
    parser.add_argument("--scenario_dir", dest="scenario_dir", required=True, help="Scenario directory")
    parser.add_argument("--output_dir", dest="output_dir", required=True, help="Output directory, also used to read current demand in")
    parser.add_argument("--params", dest="params", required=True, help="Specifies the JSON file that contains demand profile parameters")

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

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
