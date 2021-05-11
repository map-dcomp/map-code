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
    import topology_fs

script_dir=os.path.abspath(os.path.dirname(__file__))

import importlib.util
map_topology_infra_spec = importlib.util.spec_from_file_location("map_topology_infra", os.path.join(script_dir, "../map_topology_infra.py"))
map_topology_infra = importlib.util.module_from_spec(map_topology_infra_spec)
map_topology_infra_spec.loader.exec_module(map_topology_infra)

topology2_spec = importlib.util.spec_from_file_location("topology2", os.path.join(script_dir, "../topology2.py"))
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

    # create the NCPs
    routers = dict()
    for region in list(string.ascii_uppercase)[:20]:
        routers[region] = topology_fs.create_region(topology, region, 35, ncps_per_lane=7)

    for dc in ['Xlow', 'Xmed', 'Xhigh']:
        routers[dc] = topology_fs.create_region(topology, dc, 140, ncps_per_lane=11)

    # link up the regions
    topology.add_link("NC", routers['N'], routers['C'], 100)
    topology.add_link("CP", routers['C'], routers['P'], 100, 100)
    topology.add_link("CH", routers['C'], routers['H'], 100)
    topology.add_link("CE", routers['C'], routers['E'], 100)
    topology.add_link("PG", routers['P'], routers['G'], 200)
    topology.add_link("GO", routers['G'], routers['O'], 100, 100)
    topology.add_link("OB", routers['O'], routers['B'], 50)
    topology.add_link("PI", routers['P'], routers['I'], 200)
    topology.add_link("IA", routers['I'], routers['A'], 200)
    topology.add_link("AF", routers['A'], routers['F'], 200)
    topology.add_link("FQ", routers['F'], routers['Q'], 100)
    topology.add_link("QS", routers['Q'], routers['S'], 50)
    topology.add_link("QJ", routers['Q'], routers['J'], 100, 100)
    topology.add_link("IJ", routers['I'], routers['J'], 200)
    topology.add_link("JK", routers['J'], routers['K'], 200)
    topology.add_link("IL", routers['I'], routers['L'], 200, 200)
    topology.add_link("LM", routers['L'], routers['M'], 200)
    topology.add_link("DXlow", routers['D'], routers['Xlow'], 200)
    topology.add_link("MD", routers['M'], routers['D'], 200)
    topology.add_link("LD", routers['L'], routers['D'], 200)
    topology.add_link("DR", routers['D'], routers['R'], 200)
    topology.add_link("RXmed", routers['R'], routers['Xmed'], 200)
    topology.add_link("DT", routers['D'], routers['T'], 200)
    topology.add_link("TXhigh", routers['T'], routers['Xhigh'], 200)

    for region in ['B', 'N', 'S']: 
        topology2.add_clients_to_region(topology, region, routers[region], 9)
        
    for name, node in topology.nodes.items():
        if node.RLG or node.DCOP or node.dns:
            node.hardware = "no_containers_large"
        elif node.router:
            node.hardware = "no_containers_large"
        elif node.client:
            node.hardware = "no_containers_large"
        else:
            node.hardware = "small"
    
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
