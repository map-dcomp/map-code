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
    log "Usage: $0 --scenario <scenario dir> --demand <demand dir> --sim <sim output dir> --output <output dir>"
    exit
}

scenario_dir=""
demand_dir=""
chart_output=""
sim_output=""
while [ $# -gt 0 ]; do
    debug "Checking arg '${1}' with second '${2}'"
    case ${1} in
        --help|-h)
            help
            ;;
        --scenario)
            if [ -z "${2}" ]; then
                fatal "--scenario is missing an argument"
            fi
            scenario_dir=${2}
            shift
            ;;
        --demand)
            if [ -z "${2}" ]; then
                fatal "--demand is missing an argument"
            fi
            demand_dir=${2}
            shift
            ;;
        --sim)
            if [ -z "${2}" ]; then
                fatal "--sim is missing an argument"
            fi
            sim_output=${2}
            shift
            ;;
        --output)
            if [ -z "${2}" ]; then
                fatal "--output is missing an argument"
            fi
            chart_output=${2}
            shift
            ;;
        *)
            error "Unknown argument ${1}"
            help
            ;;
    esac
    shift
done

if [ -z "${scenario_dir}" ]; then
   help
fi
if [ -z "${demand_dir}" ]; then
   help
fi
if [ -z "${chart_output}" ]; then
   help
fi

map_base=${mydir}/../../..
map_srcdir=${map_base}/src

graph_output="${chart_output}"/graphs
gnuplot_graph_output="${chart_output}"/graphs-gnuplot

if [ ! -d "${demand_dir}" ]; then
    fatal "Cannot find ${demand_dir}"
fi
if [ ! -d "${scenario_dir}" ]; then
    fatal "Cannot find ${scenario_dir}"
fi

try mkdir -p "${chart_output}"
try mkdir -p "${graph_output}"
try mkdir -p "${gnuplot_graph_output}"

log "Running chartgen see ${chart_output}/chartgen.log for output"
try "${mydir}"/run-chartgen.sh \
    --sim "${sim_output}" \
    --scenario "${scenario_dir}" \
    --demand "${demand_dir}" \
    --output "${chart_output}" \
    > "${chart_output}"/chartgen.log 2>&1

log "Running latency_analysis see ${graph_output}/client-requests.txt for output"
"${mydir}"/latency_analysis.py \
    --sim-output "${sim_output}" \
    --output "${graph_output}" \
    > "${graph_output}"/client-requests.txt 2>&1 &

log "Running graph-request-status see ${graph_output}/graph-request-status.log for output"
"${mydir}"/graph-request-status.py \
    --sim-output "${sim_output}" \
    --output "${graph_output}" \
    > "${graph_output}"/graph-request-status.log 2>&1 &

log "Running overflow-plan-analysis see ${graph_output}/overflow-plan-analysis.log for output"
"${mydir}"/overflow-plan-analysis.py \
    --chart_output "${chart_output}" \
    --output "${graph_output}" \
    > "${graph_output}"/overflow-plan-analysis.log 2>&1 &
    

log "Running graph-network-traffic see ${graph_output}/graph-network-traffic.log for output"
"${mydir}"/network-data-analysis/graph-network-traffic.py \
    -c "${chart_output}" \
    -o "${graph_output}" 
    > "${graph_output}"/graph-network-traffic.log 2>&1 &

log "Running load-cpu-plot see ${graph_output}/load-cpu-plot.log for output"
"${mydir}"/load-cpu-plot.py \
    -c "${chart_output}" \
    -o "${graph_output}" \
    > "${graph_output}"/load-cpu-plot.log 2>&1 &

log "Running container-node-load-plot see ${graph_output}/container-node-load-plot.log for output"
"${mydir}"/container-node-load-plot.py \
    --sim-output "${sim_output}" \
    -o "${graph_output}" \
    > "${graph_output}"/container-node-load-plot.log 2>&1 &

log "Running graph-num-clients see ${graph_output}/graph-num-clients.log for output"
"${mydir}"/graph-num-clients.py \
    -c "${chart_output}" \
    -o "${graph_output}" \
    > "${graph_output}"/graph-num-clients.log 2>&1 &

log "Running compare-app-load see ${graph_output}/compare-app-load.log for output"
"${mydir}"/compare-app-load.py \
    -s "${sim_output}" \
    -o "${graph_output}" \
    > "${graph_output}"/compare-app-load.log 2>&1 &

log "Running system-stats see ${graph_output}/system-stats.log for output"
"${mydir}"/system-stats.py \
    --sim-output "${sim_output}" \
    --output "${graph_output}" \
    > "${graph_output}"/system-stats.log 2>&1 &

log "Running graph-report-lag see ${chart_output}/max-report-lag.txt for output"
"${mydir}"/graph-report-lag.py \
    -s "${sim_output}" \
    -o "${graph_output}" \
    > "${chart_output}"/max-report-lag.txt 2>&1 &

# check for pauses in all agent AP threads
log "Checking for pauses in AP threads see ${chart_output}/map-agent_*_ap-thread.log for output"
for dir in "${sim_output}"/*; do
    if [ -e "${dir}"/agent/map-agent.log ]; then
        node=$(basename ${dir})
        ${mydir}/map_log_run_time_inspection.py \
            -L "${dir}"/agent/map-agent.log \
            -d 30 \
            -t \
            -f "Node-${node}.map.dcomp" \
            -o "${chart_output}"/map-agent_${node}_ap-thread.log \
            > "${chart_output}"/map-agent_${node}_ap-thread.script.log 2>&1 &
    fi
done

log "Generating gnuplot charts see ${gnuplot_graph_output}/gnuplot_graphs.log for output"
"${mydir}"/gnuplot/generate_gnuplot_charts.sh \
    --sim "${sim_output}" \
	--chart_output "${chart_output}" \
	--load_unit "CPU" \
	--run_title "$(basename $(dirname ${sim_output}))" \
	--output "${gnuplot_graph_output}" \
	> "${gnuplot_graph_output}"/gnuplot_graphs.log 2>&1 &

log "See ${chart_output}/hifi-summary.txt for summary of the run"
"${mydir}"/summarize-hifi.py \
    --dir "${sim_output}" \
    > "${chart_output}"/hifi-summary.txt 2>&1 &

wait