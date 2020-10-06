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


def gen_lead(topologyFile, region):
    nodeName='{0}node1'.format(region)
    with open('{0}.json'.format(nodeName), 'w') as f:
        f.write('{\n')
        f.write('  "region": "{0}",\n'.format(region))
        f.write('  "DCOP": true,\n')
        f.write('  "RLG": true,\n')
        f.write('  "dns": true\n')
        f.write('}\n')
    topology_node(nodeName, topologyFile)
    return nodeName


def gen_node(topologyFile, region, index):
    nodeName='{0}node{1}'.format(region, index)
    with open('{0}.json'.format(nodeName), 'w') as f:
        f.write('{\n')
        f.write('  "region": "{0}"\n'.format(region))
        f.write('}\n')
    topology_node(nodeName, topologyFile)
    return nodeName


def topology_node(name, topologyFile):
    topologyFile.write('set {0} [$ns node]\n'.format(name))
    topologyFile.write('tb-set-node-os ${0} XEN46-64-STD\n'.format(name))
    topologyFile.write('tb-set-hardware ${0} pc3000\n'.format(name))
    topologyFile.write('\n')


def createLan(topologyFile, region, regionNodes):
    if len(regionNodes) > 1:
        lanName='lan{0}'.format(region)
        lanNodes=['${0}'.format(n) for n in regionNodes]
        topologyFile.write('set {0} [$ns make-lan "{1}" 100Mb 0ms]\n'.format(lanName, ' '.join(lanNodes)))

def gen_clients(topologyFile, region, parent, numClients):
    # create lan with all nodes
    nodes=list()
    nodes.append(parent)

    for i in range(1, numClients+1):
        clientName="{0}client{1}".format(region, i)
        nodes.append(clientName)
        
        topologyFile.write('set {0} [$ns node]\n'.format(clientName))
        topologyFile.write('tb-set-node-os ${0} XEN46-64-STD\n'.format(clientName))
        topologyFile.write('\n')

        with open('{0}.json'.format(clientName), 'w') as f:
            f.write('{\n')
            f.write('  "region": "{0}",\n'.format(region))
            f.write('  "client": true\n')
            f.write('}\n')


    lanName='lan{0}Client'.format(region)
    lanNodes=['${0}'.format(n) for n in nodes]
    topologyFile.write('set {0} [$ns make-lan "{1}" 100Mb 0ms]\n'.format(lanName, ' '.join(lanNodes)))

    
def gen_arm1(topologyFile):
    region='arm1region1'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm1region2'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    regionNodes.append(gen_node(topologyFile, region, 3))
    createLan(topologyFile, region, regionNodes)

    region='arm1region3'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm1region4'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)
    
def gen_arm2(topologyFile):
    region='arm2region1'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm2region2'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    regionNodes.append(gen_node(topologyFile, region, 3))
    createLan(topologyFile, region, regionNodes)

    region='arm2region3'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)
    
    region='arm2region4'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)
    
def gen_arm3(topologyFile):
    region='arm3region1'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm3region2'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    regionNodes.append(gen_node(topologyFile, region, 3))
    createLan(topologyFile, region, regionNodes)

    region='arm3region3'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)
    
    region='arm3region4'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)

    region='arm3region5'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)

    region='arm3region6'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)

def gen_arm4(topologyFile):
    region='arm4region1'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm4region2'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    gen_clients(topologyFile, region, leadNode, 2)

def gen_arm5(topologyFile):
    region='arm5region1'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    
    region='arm5region2'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)

    region='arm5region3'
    regionNodes = list()
    regionNodes.append(gen_lead(topologyFile, region))
    regionNodes.append(gen_node(topologyFile, region, 2))
    regionNodes.append(gen_node(topologyFile, region, 3))
    createLan(topologyFile, region, regionNodes)
    
    region='arm5region4'
    regionNodes = list()
    leadNode = gen_lead(topologyFile, region)
    regionNodes.append(leadNode)
    regionNodes.append(gen_node(topologyFile, region, 2))
    createLan(topologyFile, region, regionNodes)
    gen_clients(topologyFile, region, leadNode, 2)
    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    with open('topology.include.ns', 'w') as topologyFile:
        gen_arm1(topologyFile)
        gen_arm2(topologyFile)
        gen_arm3(topologyFile)
        gen_arm4(topologyFile)
        gen_arm5(topologyFile)
    
if __name__ == "__main__":
    sys.exit(main())
    
