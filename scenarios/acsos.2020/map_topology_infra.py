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
        
        
class Link(Base):
    def __init__(self, name, node1, node2, mbps):
        self.name = name
        self.node1 = node1
        self.node2 = node2
        self.mbps = mbps
        

class Switch(Base):
    def __init__(self, name, mbps):
        self.name = name
        self.mbps = mbps
        self.nodes = []

    def add_node(self, node):
        self.nodes.append(node)

        
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

    
    def add_link(self, name, node1, node2, mbps):
        link = Link(name, node1, node2, mbps)
        self.links[name] = link
        return link

    
    def write(self, scenario_path):
        
        with open(scenario_path / 'topology.ns', 'w') as f:
            f.write("set ns [new Simulator]\n")
            f.write("source tb_compat.tcl\n")
            f.write("\n")
            
            for name, node in self.nodes.items():
                f.write(f"set {node.name} [$ns node]\n")
                f.write(f"tb-set-node-os ${node.name} UBUNTU16-64-MAP\n")
                if node.hardware is not None:
                    f.write(f"tb-set-hardware ${node.name} {node.hardware}\n")
                    
                f.write("\n")
                with open(scenario_path / f"{node.name}.json", "w") as nf:
                    json.dump(node.__dict__, nf, sort_keys=True, indent=4)
            f.write("\n")
            
            for name, link in self.links.items():
                f.write(f"set {link.name} [$ns duplex-link ${link.node1.name} ${link.node2.name} {link.mbps}Mb 0.0ms DropTail]\n")
            f.write("\n")
            
            for name, sw in self.switches.items():
                members = " ".join([ node.name for node in sw.nodes ])
                f.write(f"set {sw.name} [$ns make-lan \"{members}\" {sw.mbps}Mb 0.0ms]\n")
            f.write("\n")
            
            f.write("$ns rtproto Manual\n")
            f.write("$ns run\n")

            
def app_name(priority, num):
    app_name = f"appP{priority}N{num}"
    return app_name

