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
#!/bin/bash 

# execute a single run.
# run the map sim and chartgen tools
# this script is part of a framework for executing many scenarios
# where an individual scenario can be executed many times under many configurations

source ./config.sh

# set up exit code for this run
exit_code=0

# get the configruation/inputs
scenario_dir=$1
demand_dir="${scenario_dir}"/demand
rlg_algorithm=$2
rlg_stub_choose_algorithm=$3
scenario_input="${4}/scenario/"
sim_output="${4}/simulation/"
chart_output="${4}/charting/"

if [ ! -d "${demand_dir}" ]; then
	error "could not locate demand dir for scenario: ${scenario_dir}. soft stop."
	exit 252
fi

# copy scenario to output directory for later reference
try cp -r "${scenario_dir}" "${scenario_input}"

# create directories for this run
try mkdir -p "${sim_output}"
try mkdir -p "${chart_output}"

# output configuration
log "Test input: ${scenario_input}"
log "Simulation output: ${sim_output}"
log "Chart output: ${chart_output}"

rm -f map*.log
log "Start simulation"

#dcopx="DISTRIBUTED_ROUTING_DIFFUSION"
dcopx="DISTRIBUTED_CONSTRAINT_DIFFUSION"
	     #--rlgNullOverflowPlan \

if [ "$rlg_algorithm" != "STUB" ] || [ "$rlg_stub_choose_algorithm" = "" ] ; then
	try java -jar ${agent_jar} \
	     --dcopAlgorithm "${dcopx}" \
	     --dcopCapacityThreshold .5 \
	     --rlgAlgorithm "${rlg_algorithm}" \
	     --scenario "${scenario_dir}" \
	     --demand "${demand_dir}" \
	     --output "${sim_output}" > /dev/null 2>&1
else
	try java -jar ${agent_jar} \
	     --dcopAlgorithm "${dcopx}" \
	     --dcopCapacityThreshold .5 \
	     --rlgAlgorithm "${rlg_algorithm}" \
	     --rlgStubChooseAlgorithm "${rlg_stub_choose_algorithm}" \
	     --scenario "${scenario_dir}" \
	     --demand "${demand_dir}" \
	     --output "${sim_output}" > /dev/null 2>&1
fi

try mv map.log "${4}/map-sim.log"
rm -f map.log

log "Start chart gen"

# generate chart tables
try java -jar ${chart_generation_jar} \
     all \
     "${scenario_dir}" \
     "${demand_dir}" \
     "${sim_output}" \
     "${chart_output}" \
    10000

try mv map.log "${4}/map-chart-gen.log"

# rename the directory to indicate that the simulation and chart generation finished without interruption
mv "${4}" "${4}-finished"

log "Finished"

exit ${exit_code}
