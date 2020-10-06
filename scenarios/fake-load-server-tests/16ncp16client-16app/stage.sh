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

cleanup() {
    debug "In cleanup"
}
trap 'cleanup' INT TERM EXIT

help() {
    log "Usage $0 --experiment <emulab experiment name> --demand <demand directory> [--agent-configuration <agent config file> --scenario-name <name>]"
    exit
}

# parse arguments
emulab_experiment=""
agent_configuration=""
demand_dir=""
scenario_name=$(basename ${mydir})
while [ $# -gt 0 ]; do
    case $1 in
        --help|-h)
            help
            ;;
        --experiment)
            if [ -z "$2" ]; then
                fatal "--experiment is missing argument"
            fi
            emulab_experiment=$2
            shift
            ;;
        --agent-configuration)
            if [ -z "$2" ]; then
                fatal "--agent-configuration is missing argument"
            fi
            agent_configuration="--agentConfiguration ${2}"
            shift
            ;;
        --scenario-name)
            if [ -z "$2" ]; then
                fatal "--scenario-name is missing argument"
            fi
            scenario_name="${2}"
            shift
            ;;
        --demand)
            if [ -z "$2" ]; then
                fatal "--demand is missing argument"
            fi
            demand_dir="${2}"
            shift
            ;;
        *)
            error "Unknown argument $1"
            help
            ;;
    esac
    shift
done

if [ -z "${emulab_experiment}" ]; then
   help
fi

if [ -z "${demand_dir}" ]; then
   help
fi

if [ ! -d "${demand_dir}" ]; then
    fatal "Cannot find demand directory: ${demand_dir}"
fi

scenario_dir=${mydir}/scenario
if [ ! -d "${scenario_dir}" ]; then
    fatal "Cannot find scenario directory ${scenario_dir}"
fi

hifi_base=${mydir}/../../../..

if [ ! -d "${hifi_base}"/stage-experiment ]; then
    fatal "This script needs to be run from inside a checkout of the hi-fi repository"
fi

client_service_config=${mydir}/client-service-configuration.json
if [ ! -e "${client_service_config}" ]; then
    fatal "Cannot find ${client_service_config}"
fi

generated_dir=generated_${scenario_name}

agent_jar=$(ls -rt "${hifi_base}"/hifi-resmgr/build/libs/hifi-resmgr-*-executable.jar | tail -1)
dns_jar=$(ls -rt "${hifi_base}"/DnsServer/build/libs/DnsServer-*-executable.jar | tail -1)
client_jar=$(ls -rt "${hifi_base}"/ClientDriver/build/libs/ClientDriver-*-executable.jar | tail -1)
client_pre_start_jar=$(ls -rt "${hifi_base}"/ClientPreStart/build/libs/ClientPreStart-*-executable.jar | tail -1)
stage_experiment_jar=$(ls -rt "${hifi_base}"/stage-experiment/build/libs/stage-experiment-*-executable.jar | tail -1)
sim_driver_jar=$(ls -rt "${hifi_base}"/SimulationDriver/build/libs/SimulationDriver-*-executable.jar | tail -1)
background_driver_jar=$(ls -rt "${hifi_base}"/BackgroundTraffic/build/libs/BackgroundTraffic-*-executable.jar | tail -1)

log "Outputting to ${generated_dir}"

try java -jar ${stage_experiment_jar} ${agent_configuration} \
     --agent-jar ${agent_jar} \
     --dns-jar ${dns_jar} \
     --client-jar ${client_jar} \
     --client-pre-start-jar ${client_pre_start_jar} \
     --sim-driver-jar ${sim_driver_jar} \
     --background-driver-jar ${background_driver_jar} \
     --emulab-experiment ${emulab_experiment} \
     --docker-registry-hostname serverX1 \
     --dumpEnabled \
     --dumpDirectory /var/lib/map/agent \
     --client-service-config "${client_service_config}" \
     --scenario "${scenario_dir}" \
     --demand "${demand_dir}" \
     --output "${generated_dir}" \
     --scenario-name ${scenario_name}
     
