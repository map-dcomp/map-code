#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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
#!/usr/bin/env python3.6

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
    import pathlib

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

def upgrade_demand(filename):
    with open(filename, 'r') as f:
        data = json.load(f)

    for clientLoad in data:
        if 'networkLoad' in clientLoad:
            netLoad = clientLoad['networkLoad']
            if 'DATARATE' in netLoad:
                datarate = netLoad['DATARATE']
                if 'DATARATE_TX' not in netLoad:
                    netLoad['DATARATE_TX'] = datarate
                if 'DATARATE_RX' not in netLoad:
                    netLoad['DATARATE_RX'] = datarate
                del netLoad['DATARATE']
        
    with open(filename, 'w') as f:
        json.dump(data, f, indent=2)
        
def upgrade_service_configuration(filename):
    with open(filename, 'r') as f:
        data = json.load(f)

    for service in data:
        if 'networkCapacity' in service:
            netCapacity = service['networkCapacity']
            if 'DATARATE' in netCapacity:
                datarate = netCapacity['DATARATE']
                if 'DATARATE_TX' not in netCapacity:
                    netCapacity['DATARATE_TX'] = datarate
                if 'DATARATE_RX' not in netCapacity:
                    netCapacity['DATARATE_RX'] = datarate
                del netCapacity['DATARATE']

    with open(filename, 'w') as f:
        json.dump(data, f, indent=2)
    
def upgrade_scenario(directory):
    get_logger().info("Upgrading scenario {}".format(directory))
    service_config = directory / 'service-configurations.json'
    if service_config.exists():
        get_logger().info("\tUpgrading service config {}".format(service_config))
        upgrade_service_configuration(service_config)


    for e in directory.glob('*'):
        if e.is_dir():
            get_logger().info("\tUpgrading demand directory {}".format(e))
            for demand_file in e.glob('*.json'):
                get_logger().info("\t\tUpgrading demand {}".format(demand_file))
                upgrade_demand(demand_file)
    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument('scenarios', metavar='SCENARIO', nargs='+', help='Scenario directory to convert')

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    for scenario in args.scenarios:
        s = pathlib.Path(scenario)
        upgrade_scenario(s)
        
if __name__ == "__main__":
    sys.exit(main())
    
