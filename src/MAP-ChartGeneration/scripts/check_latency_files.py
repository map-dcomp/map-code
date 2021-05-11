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
Verify the number of columns are consistent throughout the processing latency files for both server and client.

Also count the total number of requests for clients and servers.
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
    import csv
    import tarfile
    from io import StringIO
    import map_utils

script_dir = os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def get_container_service(time_dir, image):
    service_file = time_dir / 'service.json'
    if service_file.exists():
        with open(service_file, 'r') as f:
            data = json.load(f)
            if 'artifact' in data:
                return data['artifact']
            else:
                get_logger().warn("Could not find service artifact in %s, using image name for service name", service_file)
    else:
        get_logger().warn("Could not find %s, using image name for service name", service_file)
        return image


def process_server(metrics_dir):
    """
    :param metrics_dir: the directory to parse for all server processing latency files
    :return: number of records
    """
    record_count = dict()

    for image_dir in metrics_dir.iterdir():
        image = image_dir.name
        for container_dir in image_dir.iterdir():
            for time_dir in container_dir.iterdir():
                latency_file = time_dir / 'app_metrics_data/processing_latency.csv'
                if latency_file.exists() and latency_file.stat().st_size > 0:
                    get_logger().debug("Reading server file %s", latency_file)
                    with open(latency_file) as f:
                        file_record_count = process_latency_file(latency_file, f)
                        record_count[image] = record_count.get(image, 0) + file_record_count
    return record_count


def process_latency_file(filename, f):
    """
    :param filename: name of file for error reporting
    :param f: file object
    :return: count of records
    """
    try:
        reader = csv.reader(f)
        headers = next(reader)
        record_index = 0
        for row in reader:
            record_index = record_index + 1
            if len(row) != len(headers):
                get_logger().warn("File '%s' line %d has %d columns and the header has %d columns",
                                  filename, record_index, len(row), len(headers))
    except:
        get_logger().error("Error processing '%s'", filename)
        raise
    return record_index


def process_compressed_file(compressed_filename):
    """
    Process a comparessed file.

    :param compressed_filename: the file name to read
    :return: (client_record_count, server_record_count)
    """
    client_record_count = dict()
    server_record_count = dict()

    client_re = re.compile(
        r'(?P<basedir>[^/]+)/(?P<node>[^/]+)/client/container_data/(?P<service>[^/]+)/[^/]+/app_metrics_data/processing_latency.csv')
    server_re = re.compile(
        r'(?P<basedir>[^/]+)/(?P<node>[^/]+)/agent/container_data/(?P<image>[^/]+)/[^/]+/[^/]+/app_metrics_data/processing_latency.csv')
    with tarfile.open(compressed_filename, 'r:xz') as compressed_file:
        for member in compressed_file.getmembers():
            client_match = client_re.match(member.name)
            server_match = server_re.match(member.name)
            # if member.name.endswith('processing_latency.csv') and re.search(r'agent', member.name):
            #     print(member.name)
            if client_match and member.size > 0:
                get_logger().debug("Found client node: %s service: %s -> %s",
                                   client_match.group('node'), client_match.group('service'), member.name)
                service = client_match.group('service')
                with compressed_file.extractfile(member) as f:
                    csv_file = StringIO(f.read().decode('ascii'))
                    file_record_count = process_latency_file(member.name, csv_file)
                    client_record_count[service] = client_record_count.get(service, 0) + file_record_count
            if server_match and member.size > 0:
                get_logger().debug("Found server node: %s image: %s -> %s",
                                   server_match.group('node'), server_match.group('image'), member.name)
                image = server_match.group('image')
                with compressed_file.extractfile(member) as f:
                    csv_file = StringIO(f.read().decode('ascii'))
                    file_record_count = process_latency_file(member.name, csv_file)
                    server_record_count[image] = server_record_count.get(image, 0) + file_record_count
    return client_record_count, server_record_count


def process_client(client_metrics_dir):
    """
    Process a client directory.

    :param client_metrics_dir: the directory to process for client latency files
    :return: the number of records per service
    """
    record_count = dict()

    for service_dir in client_metrics_dir.iterdir():
        service = service_dir.name
        for client_container_dir in service_dir.iterdir():
            latency_file = client_container_dir / 'app_metrics_data/processing_latency.csv'
            if latency_file.exists() and latency_file.stat().st_size > 0:
                get_logger().debug("Reading client file %s", latency_file)

                with open(latency_file) as f:
                    file_record_count = process_latency_file(latency_file, f)
                    record_count[service] = record_count.get(service, 0) + file_record_count
    return record_count


def sum_dictionaries(x, y):
    """

    :param x: first dictionary
    :param y: second dictionary
    :return: new dictionary with values summed
    """
    return {k: x.get(k, 0) + y.get(k, 0) for k in set(x) | set(y)}


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Hifi sim output directory or compressed file (Required)",
                        required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    if sim_output.is_file():
        if sim_output.name.endswith('.tar.xz'):
            get_logger().info("Processing '%s' as a compressed file", sim_output)
            (client_record_count, server_record_count) = process_compressed_file(sim_output)
        else:
            get_logger().error("Don't know how to process file %s", sim_output.name)
            return 1
    else:
        server_record_count = dict()
        client_record_count = dict()

        for node_dir in sim_output.iterdir():
            if not node_dir.is_dir():
                continue

            client_metrics_dir = node_dir / 'client/container_data'
            if client_metrics_dir.exists() and client_metrics_dir.is_dir():
                client_record_count = sum_dictionaries(client_record_count, process_client(client_metrics_dir))

            server_metrics_dir = node_dir / 'agent/container_data'
            if server_metrics_dir.exists() and server_metrics_dir.is_dir():
                server_record_count = sum_dictionaries(server_record_count, process_server(server_metrics_dir))

    get_logger().info("Number of requests received by servers: %s", server_record_count)
    get_logger().info("Number of requests successful at clients: %s", client_record_count)


if __name__ == "__main__":
    sys.exit(main())

