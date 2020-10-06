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
#!/bin/sh

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

# find latest jars
agent_jar=$(ls -rt ${mydir}/MAP-code/src/MAP-Agent/build/libs/map-sim-runner-*-executable.jar | tail -1) || fatal "Could not find Agent Jar"
chart_generation_jar=$(ls -rt ${mydir}/MAP-code/src/MAP-ChartGeneration/build/libs/MAP-ChartGeneration-*-executable.jar | tail -1) || fatal "Could not find Chart Generation Jar"

# define scenario and configruation details for the simulation
scenario=$1
scenario_dir=$2
demand_dir="${scenario_dir}"/demand
rlg_algorithm=$3
rlg_stub_choose_algorithm=$4

# create directories for this run
time_output_dir=$(date +%Y%m%d_%H%M%S)
output_dir=$5/"${scenario}"/"${time_output_dir}"
sim_output="${output_dir}"/test_output
chart_output="${output_dir}"/test_chart_tables

try mkdir -p "${sim_output}"
try mkdir -p "${chart_output}"

# output configuration
log "Scenario: ${scenario}"
log "Scenario directory: ${output_dir}"
log "RLG Algorithm: ${rlg_algorithm} ${rlg_stub_choose_algorithm}"

# copy scenario to output directory for later reference
cp -r "${scenario_dir}" "${output_dir}/scenario"


if [ "$rlg_algorithm" != "STUB" ] || [ "$rlg_stub_choose_algorithm" = "" ] ; then
	try java -jar ${agent_jar} \
	     --rlgAlgorithm "${rlg_algorithm}" \
	     --scenario "${scenario_dir}" \
	     --demand "${demand_dir}" \
	     --output "${sim_output}"
else
	try java -jar ${agent_jar} \
	     --rlgAlgorithm "${rlg_algorithm}" \
	     --rlgStubChooseAlgorithm "${rlg_stub_choose_algorithm}" \
	     --scenario "${scenario_dir}" \
	     --demand "${demand_dir}" \
	     --output "${sim_output}"
fi


# generate chart tables
try java -jar ${chart_generation_jar} \
     all \
     "${scenario_dir}" \
     "${demand_dir}" \
     "${sim_output}" \
     "${chart_output}" \
    10000

# move logs to output directory
mv map*.log "${output_dir}"

# rename the directory to indicate that the simulation and chart generation finished without interruption
mv "${output_dir}" "${output_dir}-finished"

log "Finished"


