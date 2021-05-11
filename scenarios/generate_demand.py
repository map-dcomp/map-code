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
Generates a demand profile for a scenario using a set of demand parameters defined in a given demand params JSON file.

The demand profiles can consist of a number of services split into groups by priority. The demand profile of each service consists of a sequence of back to back batches of 1 minute long client requests.
The priority groups' demand profiles start in ascending order of priority separated in time by a given time interval. The demand for each service continues until the end of the run.
Additional features such as starting the services together all at the beginning of the run and spreading out requests more evenly in time by splitting large batches into many smaller batches each of fewer clients (feathering) are provided.



Description of demand parameters to include in the input demand params JSON file (typically called demand-params.json):

client_request_cpu_load: the amount of CPU load to put in each individual client request. This represents the amount of work to do on the server to process the request.
client_request_network_rx: the amount of RX network load to put in each client request in Mbps. RX load is the rate of data sent from the client to the server and represents the data being sent to the server for processing.
client_request_network_tx: the amount of TX network load to put in each client request in Mbps. TX load is the rate of data sent from the server back to the client and represents the server's response to the request.

num_priorities: the number of service priorities to use in the demand profile (defaults to 1 if omited)
num_apps_per_priority: the number of services per priority (defaults to 1 if omitted)
minutes_per_priority_group: the time interval in minutes to start the demand for each priority group. The duration of the demand profile will be [minutes_per_priority_group] * ([num_priorities] + 2) or [minutes_per_priority_group] * 2 if all services are starting together.
minutes_per_service_num: the time interval in minutes within each priority group to start the demand for each service in the group.
num_clients_per_service: the number of clients making requests for each service per client pool


Feathering:
  feathering_factor: the factor by which to divide each impulse of client requests to produce smaller, evenly spread out requests. Each of [feathering_factor] request groups in the result will have number of clients = [num_clients_per_service] / [feathering_factor]. This value should be set to 1 to disable feathering.
  feathering_range_minutes: the range within the request duration about which to evenly spread the [feathering_factor] feathered requests in time. This value should be < the request duration (1 minute). The range starts at the same time as the original unfeathered request and continues forward in time towards the end of the minute.


Additional features:
  start_together: causes all services to start together. Note that this will shorten duration to [minutes_per_priority_group] * 2 (defaults to false if omit).
  single_service_per_client_pool: Normally, load for all services is split evenly among all client pools. Setting this to true will cause each client pool demand profile to contain demand for only 1 service (defaults to false if omitted).
  max_random_pool_offset: allows the start time of client pool demand profiles to differ by a random offset. This value sets the maximum random offset in minutes (defaults to 0 if omitted).

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
        #add_impulse(params, client_demand, client_pool, offset, app_name, current_start, request_duration_minutes, num_clients)
        add_feathered_imulses(params, client_demand, client_pool, offset, app_name, current_start, request_duration_minutes, num_clients, feathering_factor, feathering_range)
        
        current_start = current_start + request_duration_minutes


def main_method(args):
    params_file = Path(args.params)
    if not params_file.exists():
        raise RuntimeError(f"{params_file} doesn't exist")
    
    with open(params_file) as f:
        params = json.load(f)

    start_together = params.get('start_together', False)
    num_priorities = int(params.get('num_priorities', 1))
    num_apps_per_priority = int(params.get('num_apps_per_priority', 1))
        
    # how long to give RLG to respond to the demand
    minutes_per_priority_group = int(params['minutes_per_priority_group'])
    minutes_per_service_num = int(params['minutes_per_service_num'])
    scenario_start_minutes = 0
    if not start_together:
        scenario_duration_minutes = scenario_start_minutes + minutes_per_priority_group * num_priorities + 2 * minutes_per_priority_group
    else:
        scenario_duration_minutes = scenario_start_minutes + 2 * minutes_per_priority_group
        
    num_clients = int(params['num_clients_per_service'])
    request_duration_minutes = 1.0
    feathering_factor = int(params['feathering_factor'])
    feathering_range = float(params['feathering_range_minutes'])
    max_random_pool_offset_ms = 0

    if 'max_random_pool_offset' in params.keys():
        max_random_pool_offset_ms = int(float(params['max_random_pool_offset']) * 60 * 1000)



    client_pools = dict() # pool -> offset
    for node_file in os.listdir(args.scenario_dir):
        if (node_file.endswith(".json")):
            node_name = os.path.splitext(os.path.basename(node_file))[0]
            with open(os.path.join(args.scenario_dir,node_file), 'r') as f:
                node_info = json.load(f)
                if 'client' in node_info:
                    if (node_info['client']):
                        random_offset_ms = random.randrange(0, max_random_pool_offset_ms, 1) if max_random_pool_offset_ms > 0 else 0
                        client_pools[node_name] = random_offset_ms

    get_logger().info("client pools, pool offsets: %s", str(client_pools))

    # service -> start_minutes
    service_starts = dict()
    for priority in range(1, num_priorities+1):
        for num in range(1, num_apps_per_priority+1):
            app_name = map_topology_infra.app_name(priority, num)

            if not start_together:
                priority_offset = minutes_per_priority_group * (priority - 1)
                num_offset = minutes_per_service_num * (num - 1)
                start_minutes = scenario_start_minutes + priority_offset + num_offset
            else:
                start_minutes = scenario_start_minutes
                
            service_starts[app_name] = start_minutes

    # pool -> client requests
    client_demand = dict()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    get_logger().debug("Pools %s demand keys: %s", client_pools.keys(), client_demand.keys())
    if params.get("single_service_per_client_pool", False):
        all_services = list(sorted(service_starts.keys()))
        service_index = 0
        
        for client_pool, offset in sorted(client_pools.items()):
            if service_index >= len(all_services):
                service_index = 0
            app_name = all_services[service_index]
            start_minutes = service_starts[app_name]
            duration_minutes = scenario_duration_minutes - start_minutes

            get_logger().debug("Creating demand for service %s in pool %s", app_name, client_pool)
            create_requests(params, client_demand, client_pool, offset, app_name, start_minutes, duration_minutes, num_clients, request_duration_minutes, feathering_factor, feathering_range)
            
            service_index = service_index + 1
    else:
        for app_name, start_minutes in service_starts.items():
            duration_minutes = scenario_duration_minutes - start_minutes
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
    parser.add_argument("--output_dir", dest="output_dir", required=True, help="Output directory")
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
