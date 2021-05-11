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
    import string

script_dir=os.path.abspath(os.path.dirname(__file__))

import importlib.util
map_topology_infra_spec = importlib.util.spec_from_file_location("map_topology_infra", os.path.join(script_dir, "../../../map_topology_infra.py"))
map_topology_infra = importlib.util.module_from_spec(map_topology_infra_spec)
map_topology_infra_spec.loader.exec_module(map_topology_infra)

topology2_spec = importlib.util.spec_from_file_location("topology2", os.path.join(script_dir, "../../../topology2.py"))
topology2 = importlib.util.module_from_spec(topology2_spec)
topology2_spec.loader.exec_module(topology2)


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
    scenario_path = Path(args.output)
    scenario_path.mkdir(parents=True, exist_ok=True)

    topology = map_topology_infra.Topology()
    topology.containers_per_ncp = args.ncp_containers

    compute_ncps_per_region = 15

    # create the NCPs
    routers = dict()
    for region in list(string.ascii_uppercase)[:20]:
        routers[region] = topology2.create_region(topology, region, compute_ncps_per_region)

    # link up the regions
    topology.add_link("BA", routers['B'], routers['A'], 200)
    topology.add_link("CA", routers['C'], routers['A'], 200)
    topology.add_link("GC", routers['G'], routers['C'], 200)
    topology.add_link("HC", routers['H'], routers['C'], 200)
    topology.add_link("IF", routers['I'], routers['F'], 200)
    topology.add_link("JF", routers['J'], routers['F'], 200)
    topology.add_link("LJ", routers['L'], routers['J'], 200)
    topology.add_link("MG", routers['M'], routers['G'], 200)
    topology.add_link("NE", routers['N'], routers['E'], 200)
    topology.add_link("NI", routers['N'], routers['I'], 200)
    topology.add_link("OM", routers['O'], routers['M'], 200)
    topology.add_link("PA", routers['P'], routers['A'], 200)
    topology.add_link("QG", routers['Q'], routers['G'], 200)
    topology.add_link("QK", routers['Q'], routers['K'], 200)
    topology.add_link("RD", routers['R'], routers['D'], 200)
    topology.add_link("RF", routers['R'], routers['F'], 200)
    topology.add_link("RG", routers['R'], routers['G'], 200)
    topology.add_link("SC", routers['S'], routers['C'], 200)
    topology.add_link("TJ", routers['T'], routers['J'], 200)


    for client_region in ['A', 'F', 'P']: 
        topology2.add_clients_to_region(topology, client_region, routers[client_region], 1)
        
    for name, node in topology.nodes.items():
        if node.hardware is None:
            if not node.client:
                node.hardware = "small"
            else:
                node.hardware = "no_containers_large"
    
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
