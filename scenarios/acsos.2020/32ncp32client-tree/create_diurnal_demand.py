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
Create demand that simulates a daily cycle.

TODO:
* add variation for weekends
* handle ramp too short for the number of clients - need to ramp multiple clients per ms - not sure this seems reasonable
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


def hours_to_ms(hours):
    return minutes_to_ms(hours * 60)


def create_impulse(service, impulse_start, impulse_duration):
    demand = dict()
    demand['startTime'] = int(impulse_start)
    demand['serverDuration'] = int(impulse_duration)
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

    return demand


def create_demand_for_day(base_tz_offset, pool_tz_offset, day_index, service, max_clients):
    demand_impulses = list()

    day_offset_hours = pool_tz_offset - base_tz_offset + day_index * 24
    get_logger().debug("Base tz offset %d pool tz offset %d day offset %d hours day_index %d", base_tz_offset, pool_tz_offset, day_offset_hours, day_index)

    # start at 7:00 local time
    day_start_hours = 7 + day_offset_hours
    day_start = hours_to_ms(day_start_hours)
    get_logger().debug("Day start %d ms", day_start)

    # do linear ramp from 1 client at 7:00 to max clients at 11:00
    # keep constant from 11:00 until 22:00
    # do linear ramp from max clients at 22:00 to zero clients at 2:00
    # This makes the ramp up and the ramp down both 4 hours and therefore can be easily mirrored

    # 7:00 to 2:00 is 19 hours
    max_demand_duration = hours_to_ms(19)

    # amount of time between starting/ending a client
    # 4 hours is the ramp up time
    impulse_gap = hours_to_ms(4) / max_clients
    if impulse_gap < 1:
        raise Exception("There are more clients ({}) than there are milliseconds in the ramp up time ({})".format(max_clients, hours_to_ms(4)))
    get_logger().debug("Impulse gap is %d ms with %d clients", impulse_gap, max_clients)

    for client_index in range(max_clients):
        start = client_index * impulse_gap + day_start
        # need gap on both start and end
        duration = max_demand_duration - (client_index * impulse_gap * 2)
        get_logger().debug("Start %d duration %d", start, duration)
        demand = create_impulse(service, start, duration)
        demand_impulses.append(demand)

    return demand_impulses


def assign_services_to_clients(client_tz_offsets, services):
    client_service = dict()
    service_index = 0

    for (client, _) in client_tz_offsets.items():
        if service_index >= len(services):
            service_index = 0
        service = services[service_index]

        client_service[client] = service

        service_index = service_index + 1

    return client_service


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--num-days", dest="numDays", help="Number of days of data to output (Default is 7)", default=7, type=int)
    parser.add_argument("--max-clients", dest="maxClients", help="Number of clients in each pool (Default is 10)", default=10, type=int)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # client -> tz offset
    client_tz_offsets = dict()

    # GMT
    client_tz_offsets["arm1region4client1"] = 0
    client_tz_offsets["arm1region4client2"] = 0

    # BST
    client_tz_offsets["arm2region3client1"] = 1
    client_tz_offsets["arm2region3client2"] = 1

    # EEST
    client_tz_offsets["arm2region4client1"] = 3
    client_tz_offsets["arm2region4client2"] = 3

    # Easter US
    client_tz_offsets["arm3region3client1"] = -4
    client_tz_offsets["arm3region3client2"] = -4

    # Central US
    client_tz_offsets["arm3region5client1"] = -5
    client_tz_offsets["arm3region5client2"] = -5

    # Pacific US
    client_tz_offsets["arm3region6client1"] = -7
    client_tz_offsets["arm3region6client2"] = -7

    # China
    client_tz_offsets["arm4region2client1"] = 8
    client_tz_offsets["arm4region2client2"] = 8

    # India
    client_tz_offsets["arm5region4client1"] = 5.5
    client_tz_offsets["arm5region4client2"] = 5.5

    # client -> service to use
    services = ['simple-webserver_large-response', 'simple-webserver_small-response', 'database-query']
    client_service = assign_services_to_clients(client_tz_offsets, services)

    # End setup

    base_tz_offset = min(client_tz_offsets.values())

    get_logger().debug('Client TZ offsets %s', client_tz_offsets)
    get_logger().debug('Client services %s', client_service)

    demand = dict()
    for day_index in range(args.numDays):
        for client in client_tz_offsets.keys():
            client_impulses = create_demand_for_day(base_tz_offset, client_tz_offsets[client], day_index, client_service[client], args.maxClients)
            all_client_impulses = demand.get(client, list())
            all_client_impulses.extend(client_impulses)
            demand[client] = all_client_impulses

    output_dir.mkdir(parents=True, exist_ok=True)
    for (client, impulses) in demand.items():
        output_file = output_dir / '{0}.json'.format(client)
        with open(output_file, 'w') as output:
            json.dump(impulses, output, indent=4)


if __name__ == "__main__":
    sys.exit(main())

