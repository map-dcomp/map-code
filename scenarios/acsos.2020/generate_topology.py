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

    import map_topology_infra
    import map_acsos

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


def cloud(topology):
    leader = True

    sw_counter = 0
    sw_name = "swnc"
    sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
    router = topology.add_node("rtrnc")
    #router.underlay = True
    router.hardware = 'empty'
    sw.add_node(router)
    
    lan_ncp_count = 0
    for num in range(1, map_acsos.num_cloud_ncps()+1):
        lan_ncp_count = lan_ncp_count + 1

        if lan_ncp_count > topology.ncps_per_lan_limit():
            sw_counter = sw_counter + 1
            sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
            sw.add_node(router)
            lan_ncp_count = 0
        
        name = f"NC{num:02d}"
        node = topology.add_node(name)
        sw.add_node(node)
        
        if leader:
            node.DCOP = True
            node.RLG = True
            node.dns = True
            
            # first node is the leader, nothing else is
            leader = False

    return router


def central_office(topology, cloud_router):
    router = topology.add_node("rtrNML")
    #router.underlay = True
    router.hardware = 'empty'
    topology.add_link("cloudCO", cloud_router, router, 100)

    sw_counter = 0
    sw_name = "swNML"
    sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
    sw.add_node(router)

    lan_ncp_count = 0
    for num in range(1, map_acsos.num_central_office_ncps()+1):
        lan_ncp_count = lan_ncp_count + 1

        if lan_ncp_count > topology.ncps_per_lan_limit():
            sw_counter = sw_counter + 1
            sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
            sw.add_node(router)
            lan_ncp_count = 0
            
        name = f"NML{num:02d}"
        node = topology.add_node(name)
        sw.add_node(node)
        
            
    return router


def mesh_group(topology, group_num):
    router = topology.add_node(f"g{group_num}rtr")
    #router.underlay = True
    router.hardware = 'empty'

    sw_counter = 0
    sw_name = f"g{group_num}sw"
    sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
    sw.add_node(router)
    
    lan_ncp_count = 0
    for num in range(1, map_acsos.num_ncps_per_group()+1):
        lan_ncp_count = lan_ncp_count + 1
        
        if lan_ncp_count > topology.ncps_per_lan_limit():
            sw_counter = sw_counter + 1
            sw = topology.add_switch(f"{sw_name}{sw_counter}", 100)
            sw.add_node(router)
            lan_ncp_count = 0
            
        name = f"g{group_num}NEC{num:02d}"
        node = topology.add_node(name)
        sw.add_node(node)

    for num in range(1, map_acsos.num_clients_per_group()+1):
        name = f"g{group_num}client{num:02d}"
        node = topology.add_node(name)
        node.client = True
        topology.add_link(f"g{group_num}client{num:02d}link", router, node, 100)
        
        
    return router
    
    
def mesh(topology, central_office_router):
    mesh_switch = topology.add_switch(f"meshSw", 100)
    
    g1_router = mesh_group(topology, 1)
    mesh_switch.add_node(g1_router)
    topology.add_link("COg1", central_office_router, g1_router, 100)

    g2_router = mesh_group(topology, 2)
    mesh_switch.add_node(g2_router)
    topology.add_link("COg2", central_office_router, g2_router, 100)

    # g3_router = mesh_group(topology, 3)
    # mesh_switch.add_node(g3_router)
    # 
    # g4_router = mesh_group(topology, 4)
    # mesh_switch.add_node(g4_router)


def main_method(args):
    scenario_path = Path(args.output)
    scenario_path.mkdir(parents=True, exist_ok=True)

    topology = map_topology_infra.Topology()
    topology.containers_per_ncp = args.ncp_containers
    
    cloud_router = cloud(topology)
    central_office_router = central_office(topology, cloud_router)
    mesh(topology, central_office_router)

    # make everything pc3000 hardware that isn't explicitly set
    hardware_name = 'pc3000'
    for name, node in topology.nodes.items():
        if node.hardware is None:
            node.hardware = hardware_name
    
    topology.write(scenario_path)


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
    parser.add_argument("--output", dest="output", help="Output directory", default="scenario")
    parser.add_argument("--containers", dest="ncp_containers", help="Number of containers for each NCP", default=1, type=int)

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
