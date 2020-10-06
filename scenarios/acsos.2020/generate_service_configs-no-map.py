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
    import generate_service_configs
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

        
def main_method(args):
    params_file = Path(args.params)
    if not params_file.exists():
        raise RuntimeError(f"{params_file} doesn't exist")

    with open(params_file) as f:
        params = json.load(f)
        
    services_per_ncp = params['services_per_ncp']

    scenario_dir = Path(args.scenario_dir)

    # ncp -> containers
    node_containers = generate_service_configs.gather_max_containers_info(scenario_dir, services_per_ncp)

    get_logger().debug("Containers per node: %s", node_containers)

    # ncp -> region
    ncp_to_region = generate_service_configs.gather_region_information(scenario_dir)

    # service -> ncp -> container_count
    service_assignments = dict()

    # all services that we need to allocate
    services = list()
    # service -> priority
    service_priority = dict()
    for priority in range(1, args.num_priorities+1):
        for num in range(1, args.num_apps_per_priority+1):
            service_name = map_topology_infra.app_name(priority, num)
            services.append(service_name)
            service_priority[service_name] = priority
    
    ncps = list(node_containers.keys())
    next_ncp_index = -1
    current_ncp = None
    done = False
    while not done:
        get_logger().debug("Top of while next ncp %d out of %d", next_ncp_index, len(ncps))
        
        for service in services:
            get_logger().debug("Service %s", service)
            service_assigned = False
            
            while not done and not service_assigned:
                next_ncp_index = next_ncp_index + 1

                if next_ncp_index >= len(ncps):
                    get_logger().debug("Done")
                    # done allocating containers
                    done = True
                    break

                current_ncp = ncps[next_ncp_index]
                containers_remaining = node_containers[current_ncp]
                if containers_remaining < 1:
                    get_logger().debug("No more containers in %s", current_ncp)
                    del node_containers[current_ncp]
                    
                    # goto the next NCP
                    current_ncp = None
                else:
                    # assign to service
                    service_ncps = service_assignments.get(service, dict())
                    service_containers = service_ncps.get(current_ncp, 0)
                    service_containers = service_containers + 1
                    service_ncps[current_ncp] = service_containers
                    service_assignments[service] = service_ncps
                    get_logger().debug("Assigned %s to %s count is %d", service, current_ncp, service_containers)
                    service_assigned = True
                    
                    # reduce the number of available containers
                    node_containers[current_ncp] = containers_remaining - 1


    service_definitions = list()
    for service, assignments in service_assignments.items():
        first_ncp = next(iter(assignments.keys()))
        first_ncp_region = ncp_to_region[first_ncp]
        priority = service_priority[service]
        definition = generate_service_configs.create_service(service, priority, assignments, first_ncp_region)
        service_definitions.append(definition)

    with open(args.output, "w") as f:
        json.dump(service_definitions, f, indent=4, sort_keys=True)
    


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
