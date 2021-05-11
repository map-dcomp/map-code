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
#!/bin/bash

#configuration
input_folder=$1
output_folder=$2


mkdir "${output_folder}"


for algorithm_runs_folder in $(ls "${input_folder}") ; do

	for scenario_folder in $(ls "${input_folder}/${algorithm_runs_folder}") ; do
		scenario_run_output_path="${output_folder}/${scenario_folder}"
		scenario_run_demand_output_path="${scenario_run_output_path}/demand"
		scenario_run_service_ncp_load_output_path="${scenario_run_output_path}/load"
		scenario_run_service_ncp_allocated_capacity_output_path="${scenario_run_output_path}/ncp_allocated_capacity"
		scenario_run_rlg_service_plan_output_path="${scenario_run_output_path}/rlg_service_plan"

		scenario_input_path="${input_folder}/${algorithm_runs_folder}/${scenario_folder}"

		if [ -d  "${scenario_input_path}" ] ; then
			mkdir "${scenario_run_output_path}"
			mkdir "${scenario_run_demand_output_path}"
			mkdir "${scenario_run_service_ncp_load_output_path}"
			mkdir "${scenario_run_service_ncp_allocated_capacity_output_path}"
			mkdir "${scenario_run_rlg_service_plan_output_path}"


			for scenario_run_folder in $(ls "${scenario_input_path}") ; do
				scenario_run_input_path="${scenario_input_path}/${scenario_run_folder}"
				scenario_run_title="${scenario_folder} [${algorithm_runs_folder}]\n(run ${scenario_run_folder})"



				if [ -d "${scenario_run_input_path}" ] ; then
					echo "================= Generating charts for ${scenario_run_input_path} -> ${scenario_run_output_path} ================="
					sleep 1

					# specific chart folders
					chart_table_input_folder="${scenario_run_input_path}/test_chart_tables"
					demand_input_folder="${scenario_run_input_path}/$(ls ${scenario_input_path}/${scenario_run_folder} | grep scenario)/demand"
					service_ncp_load_input_folder="${scenario_run_input_path}/test_chart_tables/load"
					rlg_service_plan_input_folder="${scenario_run_input_path}/test_chart_tables/rlg_plan_updates"
	: '
					# Demand charts
					echo "    Demand: ${demand_input_folder}"
					./generate_demand_plot.sh "${demand_input_folder}" "${scenario_run_title}"
					cp "${demand_input_folder}"/*.png "${scenario_run_demand_output_path}/${scenario_folder}-demand-${scenario_run_folder}.png"

					# Service NCP Load charts
					echo "    Service NCP Load: ${service_ncp_load_input_folder}"
					./generate_service_ncp_load_plot.sh "${service_ncp_load_input_folder}" "${scenario_run_title}" "ncp_load" "NCP Load Plot"
					cp "${service_ncp_load_input_folder}"/ncp_load*.png "${scenario_run_service_ncp_load_output_path}/${scenario_folder}-ncp_load-${scenario_run_folder}.png"

					# Service NCP Allocated Capacity charts
					echo "    Service Allocated Capacity: ${service_ncp_load_input_folder}"
					./generate_service_ncp_load_plot.sh "${service_ncp_load_input_folder}" "${scenario_run_title}" "ncp_allocated_capacity" "NCP Allocated Capacity Plot"
					cp "${service_ncp_load_input_folder}"/ncp_allocated_capacity*.png "${scenario_run_service_ncp_allocated_capacity_output_path}/${scenario_folder}-ncp_allocated_capacity-${scenario_run_folder}.png"

					# Service Plan chart
					echo "    Service plan: ${rlg_service_plan_input_folder}"
					./generate_service_plan_plot.sh "${rlg_service_plan_input_folder}" "${scenario_run_title}"
					cp "${rlg_service_plan_input_folder}/service_plan.png" "${scenario_run_rlg_service_plan_output_path}/${scenario_folder}-rlg_service_plan-${scenario_run_folder}.png"

	'

					# Service Plan chart
					echo "    NCP Load area and demand: ${chart_table_input_folder}"
					./generate_service_ncp_load_area_demand_plot.sh "${chart_table_input_folder}" "${scenario_run_title}" "service_demand_and_ncp_load" "Client demand and Load stacked by NCP for each service"
					cp "${chart_table_input_folder}/service_demand_and_ncp_load.png" "${scenario_run_output_path}/${scenario_folder}-${algorithm_runs_folder}-demand_ncp_load-${scenario_run_folder}.png"

					echo -e "\n\n\n\n"

				fi
			done

	: '
			# create animated GIFs

			# Service NCP Load
			convert -delay 25 -loop 0 "${scenario_run_service_ncp_load_output_path}/${scenario_folder}-ncp_load-*.png" "${scenario_run_service_ncp_load_output_path}/${scenario_folder}-ncp_load.gif"

			# Service NCP Allocated Capacity
			convert -delay 25 -loop 0 "${scenario_run_service_ncp_allocated_capacity_output_path}/${scenario_folder}-ncp_allocated_capacity-*.png" "${scenario_run_service_ncp_allocated_capacity_output_path}/${scenario_folder}-ncp_allocated_capacity.gif"
		
			# RLG service plans		
			convert -delay 25 -loop 0 "${scenario_run_rlg_service_plan_output_path}/${scenario_folder}-rlg_service_plan-*.png" "${scenario_run_rlg_service_plan_output_path}/${scenario_folder}-rlg_service_plan.gif"
	'
		fi
	done
done


