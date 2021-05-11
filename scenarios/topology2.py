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

script_dir=os.path.abspath(os.path.dirname(__file__))


def create_region(topology, region_name, num_compute_ncps, ncps_per_lane = 5):
    """
    Create a region of NCPs.
    
    Argument:
        topology (Topology): network topology object
        region_name (str): name of the region
        num_compute_ncps (int): number of compute NCPs to allocate
        ncps_per_lane (int): number of compute NCPs to connect to a switch/swim lane

    Returns:
        node: routing node for the region. This node is connected to the neighboring regions
    """

    nodes = list()
    rtr_index = 1
    sw_index = 1
    
    main_router = topology.add_node(f"{region_name}rtr{rtr_index}")
    main_router.router = True
    rtr_index = rtr_index + 1
    main_router.region = region_name
    main_router.hardware = "no_containers"

    lvl1_switch = topology.add_switch(f"{region_name}swcore", 100)
    lvl1_switch.add_node(main_router)

    # setup leaders
    control_router = topology.add_node(f"{region_name}rtr{rtr_index}")
    rtr_index = rtr_index + 1
    control_router.router = True
    control_router.region = region_name
    control_router.hardware = "no_containers"
    lvl1_switch.add_node(control_router)

    control_switch = topology.add_switch(f"{region_name}sw{sw_index}", 100)
    sw_index = sw_index + 1
    control_switch.add_node(control_router)
    
    rlg = topology.add_node(f"{region_name}rlg")
    rlg.region = region_name
    rlg.hardware = "no_containers"
    rlg.RLG = True
    control_switch.add_node(rlg)
    
    dcop = topology.add_node(f"{region_name}dcop")
    dcop.region = region_name
    dcop.hardware = "no_containers"
    dcop.DCOP = True
    control_switch.add_node(dcop)
    
    dns = topology.add_node(f"{region_name}dns")
    dns.region = region_name
    dns.hardware = "no_containers"
    dns.dns = True
    control_switch.add_node(dns)

    # compute nodes
    
    compute_index = 1
    nodes_on_sw = 0
    compute_router = None
    compute_sw = None

    compute_name_precision = len(f"{num_compute_ncps}")
    
    for i in range(num_compute_ncps):
        if compute_sw is None:
            compute_sw = topology.add_switch(f"{region_name}sw{sw_index}", 100)
            sw_index = sw_index + 1
        
        if compute_router is None:
            compute_router = topology.add_node(f"{region_name}rtr{rtr_index}")
            compute_router.router = True
            
            rtr_index = rtr_index + 1
            compute_router.region = region_name
            compute_router.hardware = "no_containers"
            lvl1_switch.add_node(compute_router)
            compute_sw.add_node(compute_router)

        node = topology.add_node(f"{region_name}server{compute_index:0{compute_name_precision}d}")
        compute_index = compute_index + 1
        node.region = region_name
        compute_sw.add_node(node)
        nodes_on_sw = nodes_on_sw + 1

        if nodes_on_sw >= ncps_per_lane:
            compute_sw = None
            compute_router = None
            nodes_on_sw = 0

    return main_router


def add_clients_to_region(topology, region_name, region_rtr, num_clients):
    """
    Arguments:
        topology (Topology): network topology object
        region_name (str): name of the region
        region_rtr (node): connect the clients to this node
        num_clients (int): number of compute client pools
    """

    csw = topology.add_switch(f"{region_name}csw", 200)
    csw.add_node(region_rtr)

    for i in range(num_clients):
        client = topology.add_node(f"{region_name}client{i+1:02d}")
        client.region = region_name
        client.client = True
        csw.add_node(client)
            
