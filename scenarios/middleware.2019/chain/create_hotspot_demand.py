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
Create demand where each application ramps up quickly and then down with varying gaps betweeen applications.
"""

import warnings

with warnings.catch_warnings():
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    from pathlib import Path

script_dir = os.path.abspath(os.path.dirname(__file__))


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


def add_impulse(client_demand, client_pools, service, impulse_start, impulse_duration):
    demand = dict()
    demand['startTime'] = minutes_to_ms(impulse_start)
    demand['serverDuration'] = minutes_to_ms(impulse_duration)
    demand['networkDuration'] = demand['serverDuration']
    demand['numClients'] = 1
    demand['service'] = dict()
    demand['service']['group'] = 'com.bbn'
    demand['service']['artifact'] = service
    demand['service']['version'] = '1'
    # mostly fake numbers just to keep parsers happy, these should be modified based on experiments for lo-fi
    demand['nodeLoad'] = dict()
    demand['nodeLoad']['TASK_CONTAINERS'] = 0.3
    demand['networkLoad'] = dict()
    demand['networkLoad']['DATARATE_TX'] = 0.2
    demand['networkLoad']['DATARATE_RX'] = 0.01

    for pool in client_pools:
        c_demand = client_demand.get(pool, list())
        c_demand.append(demand)
        client_demand[pool] = c_demand


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--app-gap", dest="demand_gap", help="Number of minutes between the end of one service demand and the next, may be negative (Required)", required=True, type=int)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    services = ['simple-webserver_large-response', 'simple-webserver_small-response', 'database-query']
    client_pools = list()
    for group in [2, 3, 4]:
        for pool in [1,2,3, 4]:
            name = 'client{0}Pool{1}'.format(group, pool)
            client_pools.append(name)

    client_demand = dict()

    impulse_interval = 2
    max_clients_per_pool = 5
    demand_length = impulse_interval * max_clients_per_pool
    # how long the demand exists at the peak
    peak_duration = 5

    # ramp up and down plus peak
    demand_length = max_clients_per_pool * impulse_interval * 2 + peak_duration
    prev_demand_end = None

    for service in services:
        if prev_demand_end is None:
            current_demand_start = 0
        else:
            current_demand_start = prev_demand_end + args.demand_gap
        current_demand_end = current_demand_start + demand_length
        get_logger().debug("Demand end: %s service: %s", current_demand_end, service)

        for client_idx in range(0, max_clients_per_pool):
            impulse_start = current_demand_start + client_idx * impulse_interval
            impulse_end = current_demand_end - client_idx * impulse_interval
            impulse_duration = impulse_end - impulse_start
            get_logger().debug("Impulse start: %s end: %s index: %s impulse_interval: %s", impulse_start, impulse_end, client_idx, impulse_interval)
            add_impulse(client_demand, client_pools, service, impulse_start, impulse_duration)

        prev_demand_end = current_demand_end

    for client in client_pools:
        output_file = output_dir / '{0}.json'.format(client)
        with open(output_file, 'w') as output:
            json.dump(client_demand[client], output, indent=4)


if __name__ == "__main__":
    sys.exit(main())

