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
#!/bin/sh

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"
map_srcdir=${mydir}/../../../src
chart_jar=$(ls -rt "${map_srcdir}"/MAP-ChartGeneration/build/libs/MAP-ChartGeneration-*-executable.jar | tail -1)

cleanup() {
    debug "In cleanup"
}

experiment=chain-priority
date_folder_name=$(date +%m%d_%H%M)



# parse arguments
output_dir="${experiment}_results-${date_folder_name}"
autochart_input_folder="."
run_sim=0
gen_charts=0
log_analysis=0
runs=1
scenario_name=$(basename ${mydir})
while [ $# -gt 0 ]; do
    case $1 in
        --output)
            if [ -z "$2" ]; then
                fatal "--output is missing argument"
            fi
            output_dir="${2}"
            shift
            ;;
        --autochart-input)
            if [ -z "$2" ]; then
                fatal "--autochart-input is missing argument"
            fi
            autochart_input_folder="${2}"
            shift
            ;;
        --run-sim)
            run_sim=1
            ;;
        --generate-charts)
            gen_charts=1
            ;;
        --log-analysis)
            log_analysis=1
            ;;
        --runs)
            if [ -z "$2" ]; then
                fatal "--runs is missing argument"
            fi
            runs="${2}"
            shift
            ;;
        *)
            error "Unknown argument $1"
            help
            ;;
    esac
    shift
done

chart_chart_output="${output_dir}/${date_folder_name}"



log "Runs: ${runs}"


for i in $(seq 1 ${runs}) ; do

	results_folder=${output_dir}/${experiment}_${i}
	try mkdir -p ${results_folder}

	for demand in \
		demand.flat_40TC \
		; do

		for configuration in \
			STUB-GdyGr-RR \
			; do

			scenario_name=$(echo "${experiment}_${demand}_${configuration}" | tr '/' '_')
			sim_output="${results_folder}/${scenario_name}"
			chart_output="${results_folder}/${scenario_name}/chart-output"
			log_analysis_output="${results_folder}/${scenario_name}/log-analysis"
			chart_chart_output_i="${chart_chart_output}/tables/${configuration}/${demand}/${i}"
			

			log "========== Scenario configuration \"${configuration}\", demand \"${demand}\", output \"${sim_output}\" =========="

			if [ ${run_sim} -eq 1 ] ; then
				log "Executing ${demand} using ${configuration}"
				try "${mydir}"/run-lofi.sh \
					--agent-configuration "../agent-config_${configuration}.json" \
					--demand "${demand}" \
					--output "${sim_output}" \
					2>&1 | tee ${results_folder}/${scenario_name}.execute.log

				try mv $(ls -rt map*.log | tail -n 1) "${sim_output}/sim-output"
				try mkdir -p "${sim_output}/sim-output/inputs/scenario"
				try mkdir -p "${sim_output}/sim-output/inputs/demand"
				try cp -r "scenario"/* "${sim_output}/sim-output/inputs/scenario"
				try cp -r "${demand}"/* "${sim_output}/sim-output/inputs/demand"
			fi


			if [ ${gen_charts} -eq 1 ] ; then
				log "Generating charts to \"${chart_output}\""

				rm -fr "${chart_output}"
				try mkdir -p "${chart_output}"

				log "Generating chart outputs"
				try java -jar "${chart_jar}" \
     					all \
     					"${sim_output}/sim-output/inputs/scenario" \
     					"${sim_output}/sim-output/inputs/demand" \
     					"${sim_output}/sim-output" \
     					"${chart_output}" \
    					10000 \
					2>&1 | tee ${results_folder}/${scenario_name}.charts.log

				try mkdir -p "${chart_chart_output_i}"
				cp -r "${chart_output}" "${chart_chart_output_i}"
				cp -r "${sim_output}/sim-output/inputs"/* "${chart_chart_output_i}"
			fi

			if [ ${log_analysis} -eq 1 ] ; then
				log "Generating log analysis to \"${log_analysis_output}\""

				rm -fr "${log_analysis_output}"
				try mkdir -p "${log_analysis_output}"

				log "Generating log analysis"
				try java -jar "${chart_jar}" \
     					log_analysis \
     					"category_matchers.txt" \
     					"${sim_output}/sim-output" \
     					"${log_analysis_output}" \
    					60000 \
					2>&1 | tee ${results_folder}/${scenario_name}.log_analysis.log
			fi

	    done

	done

done

mv "${chart_chart_output}" "${autochart_input_folder}"
