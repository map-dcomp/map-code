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

results_folder=$1


for scenario_algorithm_results_folder in $(ls "${results_folder}") ; do
	for scenario_results_folder in $(ls "${results_folder}/${scenario_algorithm_results_folder}") ; do
		for scenario_run_results_folder in $(ls "${results_folder}/${scenario_algorithm_results_folder}/${scenario_results_folder}") ; do

			output_dir="${results_folder}/${scenario_algorithm_results_folder}/${scenario_results_folder}/${scenario_run_results_folder}"

			if [ -d "${output_dir}" ] ; then

				echo "======== Scenario at ${scenario_results_folder}, algorithm ${scenario_algorithm_results_folder}, run ${scenario_run_results_folder} ========"

				echo Output to "${output_dir}"

				scenario_dir=${output_dir}/$(ls ${output_dir} | grep -m 1 "scenario")

				echo Scenario dir "${scenario_dir}"
				demand_dir="${scenario_dir}"/demand
				sim_output="${output_dir}"/test_output
				chart_output="${output_dir}"/test_chart_tables

				# clear contents of chart_output directory
				rm -r ${chart_output}/*

				sleep 3

				printf "\n\n"

				# generate chart tables
				java -jar MAP-code/src/MAP-ChartGeneration/build/libs/MAP-ChartGeneration-0.1.0-executable.jar \
				     all \
				     "${scenario_dir}" \
				     "${demand_dir}" \
				     "${sim_output}" \
				     "${chart_output}" \
				    10000

				mv map*.log "${output_dir}" 

				echo "===================================================================="

			fi
		done
	done
done
