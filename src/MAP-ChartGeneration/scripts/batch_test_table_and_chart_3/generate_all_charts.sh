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

#configuration
input_folder=$1
output_folder=$2

printf "mydir = ${mydir}"


mkdir "${output_folder}"

# Visit each algorithm folder
for algorithm_runs_folder in "${input_folder}"/*; do
    algorithm_name=$(basename "${algorithm_runs_folder}")

	# visit each scenario folder
        for scenario_folder in "${algorithm_runs_folder}"/*; do
                scenario_name=$(basename "${scenario_folder}")
		scenario_run_output_path="${output_folder}/${scenario_name}"

		scenario_input_path="${scenario_folder}"

		# check if the input path for the scenario is a valid directory
		if [ -d  "${scenario_input_path}" ] ; then
                        # use -p so that an existing directory is ok and all parents are created
			try mkdir -p "${scenario_run_output_path}"

			# visit each scenario iteration (run) folder
			for scenario_run_folder in "${scenario_input_path}"/*; do
                                scenario_run_name=$(basename "${scenario_run_folder}")
				scenario_run_input_path="${scenario_run_folder}"
				scenario_run_title="${scenario_name} [${algorithm_name}]\n(run ${scenario_run_name})"


				# check if the input path for the scenario iteration (run) is a valid directory
				if [ -d "${scenario_run_input_path}" ] ; then
					log "================= Generating charts for ${scenario_run_input_path} -> ${scenario_run_output_path} ================="
					sleep 1
					chart_table_input_folder="${scenario_run_input_path}/test_chart_tables"



					# Load, Allocated Capacity, and Requests with overlayed client demand per service charts
					log "    Load, Allocated Capacity, and Requests Results with overlayed Client Demand for a Service: ${chart_table_input_folder}"
					try "${mydir}"/generate_service_ncp_demand_load_cap_req_plot.sh "${chart_table_input_folder}" "${scenario_run_title}" "service_ncp_load_cap_req" "Load, Allocated Capacity, and Requests Results with overlayed Client Demand for a Service"

					cp_result=0
					s=1

					while [ $cp_result -eq 0 ] ; do
						source_file="${chart_table_input_folder}/service_ncp_load_cap_req-${s}.eps"
						destination_file="${scenario_run_output_path}/${scenario_name}-${algorithm_name}-${scenario_run_name}-service_${s}_ncp_load_cap_req"
						
						if [ -f ${source_file} ] ; then
							try cp "${source_file}" "${destination_file}.eps"
							cp_result=$?

								# create png file from eps file
								convert -colorspace sRGB -density 600 "${destination_file}.eps" -background white -flatten -resize 1600x1200 -units pixelsperinch -density 224.993 "${destination_file}.png"

								if [ $? -eq 0 ]; then
									log "Output image: ${destination_file}.png"
								else 
									warn "Convert failed"
								fi
						else
							cp_result=1
						fi

						s=$((s+1))
					done

					log
                                        log
                                        log
                                        log
				fi
			done
		fi
	done
done


