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

# runct should/could be configured.
runct=5

# algo list could be configured.
algo_list="STUBMOST_AVAILABLE_CONTAINERS STUBCURRENTLY_RUNNING_SERVICE"

# base saso configs
topo_list="star chain single"
mag_list="-pm55 -pm83 -pm111 -f3 -f6 -f9"
switch_list="-30 0 30 60 90 120 150 180 210 240"


doheader=1
debug=0
currname=

# loop saso configs for algo, mag/fault config, and topologies
for algo in ${algo_list}; do
for topo in ${topo_list}; do
for mag in ${mag_list}; do

#echo "${algo}: ${topo}<Pt>${mag}"


# loop Pt configuration
for switch in ${switch_list}; do

# loop run count
locct=$runct
while [ $locct -gt 0 ]; do

	# build path for fast search
	currname=${topo}${switch}${mag}
	currbase="${algo}/${currname}/run-${locct}-finished/simulation/simulation/"

	# don't search unless there is a completed run	
	if [ -d $currbase ]; then
		pushd $currbase

		# variables for counting data
		num=0
        	ct=0

		# query files
        	#sl=$(find . -path "*${algo}*${topo}${switch}${mag}*final-state.json")
		sl=$(find . -name "client-clientPool[A-Z]-final-state.json")
        	for j in ${sl}; do
                	if [[ ! $j =~ .*final.* ]]; then
				# not sure why this is here. shouldn't happen.
                        	echo "error path: $j"
                	fi

                	v=$(grep "numRequestsSucceeded" "${j}" | cut -d ':' -f 2- | cut -d ',' -f 1)
                	ct=$(($ct+$v))
			num=$(($num+1))
			if [ $num -gt 1 ]; then
				if [[ ! $topo =~ single ]]; then
					echo "warn. to many files. Pt=${switch}"
				fi
			fi
        	done

        	if [ $ct -gt 0 ]; then
                	if [ $doheader -eq 1 ]; then
                        	echo "$switch,$ct"
                	else
                        	echo $ct
                	fi
        	else
			if [ ${debug} -eq 1 ]; then
                		echo "error"
			fi
        	fi

		popd
	fi
locct=$(($locct-1))
done
done

done
done
done
