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


def create_service(name, priority, default_node, default_node_region):
    """
    Parameters:
    name (string): name of the service
    priority (int): priority of the service
    default_node (dict): node to number of instances on that node
    default_node_region (string): default region for the service
    
    Returns:
    dict: service definition to be written to JSON
    """
    service = dict()

    computeCapacity = dict()
    computeCapacity['TASK_CONTAINERS'] = 1
    computeCapacity['CPU'] = 1
    service['computeCapacity'] = computeCapacity

    networkCapacity = dict()
    networkCapacity['DATARATE_TX'] = 100
    networkCapacity['DATARATE_RX'] = 100
    service['networkCapacity'] = networkCapacity

    service['serverPort'] = 7000
    service['imageName'] = "/v2/fake_load_server"
    service['priority'] = priority

    service['hostname'] = name

    appCoordinate = dict()
    appCoordinate['group'] = 'com.bbn'
    appCoordinate['version'] = 1
    appCoordinate['artifact'] = name
    service['service'] = appCoordinate

    defaultNodes = dict()
    for node, count in default_node.items():
        defaultNodes[node] = count
    service['defaultNodes'] = defaultNodes
    service['defaultNodeRegion'] = default_node_region

    service['initialInstances'] = 1
    
    return service


def gather_max_containers_info(scenario_dir, services_per_ncp):
    """
    Parameters:
    scenario_dir (Path): where the scenario is located

    Returns:
    dict: node to number of containers
    """
    node_max_containers = dict()
    hw_containers = dict()
    with open(scenario_dir / 'hardware-configurations.json') as f:
        hw_config = json.load(f)
        for hw in hw_config:
            hw_containers[hw['name']] = min(services_per_ncp, int(hw.get("maximumServiceContainers", 0)))

    get_logger().debug("Found hw containers info: %s", hw_containers)
    
    with open(scenario_dir / 'topology.ns') as f:
        for line in f:
            match = re.match(r'^tb-set-hardware\s+\$(?P<node>\S+)\s+(?P<hw>\S+)', line)
            if match:
                node = match.group('node')
                with open(scenario_dir / f"{node}.json", 'r') as f:
                    node_info = json.load(f)
                if not node_info.get('client', False) and not node_info.get('underlay', False):
                    hw_name = match.group('hw')
                    node_max_containers[node] = hw_containers[hw_name]
                    get_logger().debug("Found hw line in topology for node %s with hw %s", node, hw_name)

    return node_max_containers


def gather_region_information(scenario_dir):
    """
    Arguments:
    scenario_dir (Path): where to read the scenario
    
    Returns:
    dict: ncp to region
    """

    ncp_to_region = dict()
    for node_file in scenario_dir.glob('*.json'):
        node_name = os.path.splitext(os.path.basename(node_file))[0]
        with open(node_file, 'r') as f:
            node_info = json.load(f)
            get_logger().debug("node_info: %s", node_info)
            
            if 'region' in node_info:
                if not node_info.get('client', False) and not node_info.get('underlay', False):
                    ncp_to_region[node_name] = node_info['region']
    return ncp_to_region


def main_method(args):
    params_file = Path(args.params)
    if not params_file.exists():
        raise RuntimeError(f"{params_file} doesn't exist")
    
    with open(params_file) as f:
        params = json.load(f)
        
    services_per_ncp = params['services_per_ncp']

    scenario_dir = Path(args.scenario_dir)

    node_max_containers = gather_max_containers_info(scenario_dir, services_per_ncp)
    get_logger().debug("Max containers per node: %s", node_max_containers)

    ncp_to_region = gather_region_information(scenario_dir)
    
    ncps = list(filter(lambda n: ncp_to_region[n] == 'X', node_max_containers.keys()))
    next_ncp_index = -1
    next_container = 0
    services_on_current_ncp = -1
    current_ncp = None
    services = list()
    for priority in range(1, args.num_priorities+1):
        for num in range(1, args.num_apps_per_priority+1):
            service_name = map_topology_infra.app_name(priority, num)
            service_assigned = False
            
            while not service_assigned:
                if current_ncp is None or next_container >= services_on_current_ncp:
                    # move to the next NCP
                    next_ncp_index = next_ncp_index + 1
                    next_container = 0

                    if next_ncp_index >= len(ncps):
                        raise RuntimeError(f"Ran out of NCPs for services: {ncps}")

                    current_ncp = ncps[next_ncp_index]
                    services_on_current_ncp = node_max_containers[current_ncp]
                    get_logger().debug("NCP %s has %d possible services", current_ncp, services_on_current_ncp)
                    if services_on_current_ncp < 1:
                        # goto the next NCP
                        current_ncp = None
                        continue

                get_logger().debug("Assigned %s to %s", service_name, current_ncp)
                node_count = {current_ncp: 1}
                service = create_service(service_name, priority, node_count, ncp_to_region[current_ncp])
                services.append(service)
                service_assigned = True

                next_container = next_container + 1

    with open(args.output, "w") as f:
        json.dump(services, f, indent=4, sort_keys=True)

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
    parser.add_argument("--num-priorities", dest="num_priorities", required=True, type=int)
    parser.add_argument("--num-apps-per-priority", dest="num_apps_per_priority", required=True, type=int)
    parser.add_argument("--output", dest="output", required=True)
    parser.add_argument("--params", dest="params", required=True)

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
