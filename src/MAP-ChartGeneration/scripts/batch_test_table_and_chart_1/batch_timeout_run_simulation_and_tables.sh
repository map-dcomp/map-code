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

scenarios_folder=$1
runs_per_scenario=$2
output_folder=$3
time_limit=$4
time_limit_kill=2m


echo "Scenarios folder: ${scenarios_folder}"
echo "Runs per scenario: ${runs_per_scenario}"
echo "Output folder: ${output_folder}"
echo "Time limit per run: ${time_limit}"
echo -e "\n"

mkdir ${output_folder}

for ((i=1; i<=runs_per_scenario; i++)) ; do
	for scenario in $(ls "${scenarios_folder}") ; do
		echo -e "\n\n\n"
		echo ======== Scenario at "${scenario}", iteration "${i}" ========
		log_file=${output_folder}/${scenario}-${i}.log
		touch ${log_file}
		start_time=$(date +%s)
		timeout -k $time_limit_kill $time_limit ./run_simulation_and_tables.sh "${scenario}" "${scenarios_folder}/${scenario}" "${output_folder}" > "${log_file}" 2>&1
		exit_code=$?
		end_time=$(date +%s)

		if [ $exit_code -eq 124 ] ; then
			echo Timed out after a duration of $((end_time - start_time)) seconds.
		else
			echo Finished in $((end_time - start_time)) seconds with exit code "${exit_code}".
		fi

		sleep 5
	done
done
