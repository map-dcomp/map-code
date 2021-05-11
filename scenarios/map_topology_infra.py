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
    import math

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"


class Node(Base):
    def __init__(self, name):
        self.name = name
        self.region = "region"
        self.RLG = False
        self.DCOP = False
        self.dns = False
        self.hardware = None
        self.client = False
        self.underlay = False
        self.router = False
        
        
class Link(Base):
    def __init__(self, name, node1, node2, mbps, delay=None):
        self.name = name
        self.node1 = node1
        self.node2 = node2
        self.mbps = mbps
        self.delay = delay
        

class Switch(Base):
    def __init__(self, name, mbps):
        self.name = name
        self.mbps = mbps
        self.nodes = set()

    def add_node(self, node):
        self.nodes.add(node)

        
class Topology(Base):
    def __init__(self):
        self.nodes = {}
        self.links = {}
        self.switches = {}
        self.ips_per_lan = 163
        self.containers_per_ncp = 2

    def ncps_per_lan_limit(self):
        return math.floor(self.ips_per_lan / (self.containers_per_ncp + 1))

    def add_switch(self, name, mbps):
        sw = Switch(name, mbps)
        self.switches[name] = sw
        return sw
    
        
    def add_node(self, name):
        node = Node(name)
        self.nodes[node.name] = node
        return node

    
    def add_link(self, name, node1, node2, mbps, delay=None):
        link = Link(name, node1, node2, mbps, delay)
        self.links[name] = link
        return link

    
    def write(self, scenario_path):
        
        with open(scenario_path / 'topology.ns', 'w') as f:
            f.write("set ns [new Simulator]\n")
            f.write("source tb_compat.tcl\n")
            f.write("\n")
            
            for name, node in sorted(self.nodes.items()):
                f.write(f"set {node.name} [$ns node]\n")
                f.write(f"tb-set-node-os ${node.name} UBUNTU16-64-MAP\n")
                if node.hardware is not None:
                    f.write(f"tb-set-hardware ${node.name} {node.hardware}\n")
                    
                f.write("\n")
                with open(scenario_path / f"{node.name}.json", "w") as nf:
                    json.dump(node.__dict__, nf, sort_keys=True, indent=4)
            f.write("\n")
            
            for name, link in self.links.items():
                if link.delay is not None:
                    delay = float(link.delay)
                else:
                    delay = 0
                    
                f.write(f"set {link.name} [$ns duplex-link ${link.node1.name} ${link.node2.name} {link.mbps}Mb {delay:0.1f}ms DropTail]\n")
            f.write("\n")
            
            for name, sw in sorted(self.switches.items()):
                members = " ".join([ f"${node.name}" for node in sorted(sw.nodes, key=lambda x: x.name) ])
                f.write(f"set {sw.name} [$ns make-lan \"{members}\" {sw.mbps}Mb 0.0ms]\n")
            f.write("\n")
            
            f.write("$ns rtproto Manual\n")
            f.write("$ns run\n")

            
def app_name(priority, num):
    app_name = f"appP{priority}N{num}"
    return app_name


def create_region(topology, region_name, ncps_in_region, max_ncps_per_lan, speed):
    """
    Create a region of NCPs.
    
    Argument:
    topology (Topology): network topology object
    region_name (str): name of the region
    ncps_in_region (int): number of NCPs to allocate
    max_ncps_per_lan (int): number of NCPs to put in a single subnet
    speed (int): speed of the links in the region in Mbps

    Returns:
    node: routing node for the region. This node is connected to the neighboring regions
    list: all nodes in the region
    """

    sw_counter = 0

    switches = list()
    lan_ncp_count = 0    
    inter_region_router = None
    intra_region_router = None
    rlg_leader = None
    dcop_leader = None
    dns_leader = None
    nodes = list()
    sw = topology.add_switch(f"sw{region_name}{sw_counter}", speed)
    switches.append(sw)
    for i in range(1, ncps_in_region+1):
        lan_ncp_count = lan_ncp_count + 1

        if lan_ncp_count > max_ncps_per_lan:
            sw_counter = sw_counter + 1
            sw = topology.add_switch(f"sw{region_name}{sw_counter}", speed)
            switches.append(sw)
            lan_ncp_count = 0
        
        name = f"{region_name}server{i:02d}"
        node = topology.add_node(name)
        node.region = region_name
        sw.add_node(node)
        nodes.append(node)

        # distribute the leaders among the non-routing nodes
        if i == 1:
            inter_region_router = node
        elif i == 2:
            rlg_leader = node
        elif i == 3:
            dcop_leader = node
        elif i == 4:
            dns_leader = node
        elif i == 5:
            intra_region_router = node

    # handle case where there aren't enough nodes in the region to get the leaders that we wanted
    if rlg_leader is None:
        nodes[0].RLG = True 
        nodes[0].hardware = "no_containers"
    if dcop_leader is None:
        nodes[0].DCOP = True 
        nodes[0].hardware = "no_containers"
    if dns_leader is None:
        nodes[0].dns = True 
        nodes[0].hardware = "no_containers"
    if inter_region_router is None:
        inter_region_router = nodes[0]
    if intra_region_router is None:
        intra_region_router = nodes[0]

    rlg_leader.RLG = True
    rlg_leader.hardware = "no_containers"
    dcop_leader.DCOP = True
    dcop_leader.hardware = "no_containers"
    dns_leader.dns = True
    dns_leader.hardware = "no_containers"
    inter_region_router.hardware = "no_containers"
    intra_region_router.hardware = "no_containers"

    # connect the pieces of the region to the intra region router
    for sw in switches:
        sw.add_node(intra_region_router)
    
    return inter_region_router, nodes


def create_clients(topology, region_name, index, num_clients, ncp_neighbor, speed):
    """
    Create a group of clients that are connected to a single NCP.
    
    Arguments:
    topology (Topology): the topology object
    region_name (str): region name
    num_clients (int): how many clients to create
    ncp_neighbor (Node): NCP to connect the clients to
    index (int): unique to each call to this method for a region. Used to make the names unique.
    speed (int): speed of the links between the clients

    Returns:
    list: clients created
    """

    sw = topology.add_switch(f"swClient{region_name}{index:02d}", speed)
    sw.add_node(ncp_neighbor)

    clients = list()
    for i in range(1, num_clients+1):
        name = f"{region_name}g{index:02d}client{i:02d}"
        client = topology.add_node(name)
        client.region = region_name
        client.client = True
        sw.add_node(client)
        clients.append(client)

    return clients


