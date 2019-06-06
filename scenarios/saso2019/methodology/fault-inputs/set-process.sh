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

dir_out="./output/"

# loop iterators
pt="-30 0 30 60 90 120 150 180 210 240"
fct="3 6 9"

rm -rf ${dir_out}
mkdir ${dir_out}

for i in $pt
do
for j in $fct
do

fb="t${i}-${j}"
f="./inputs/${fb}.txt"
fout="node-failures.json"
num="^[0-9]+$"

if [ -f ${f} ]; then
	echo "Input: $f Output: $fout"
	mkdir -p "${dir_out}${fb}"
	t="${dir_out}${fb}/node-failures.json"
	echo "[" > ${t}

	first=0
	fcount=0

while IFS='' read -r line || [[ -n "$line" ]]; do
	if [[ $line =~ $num ]]; then
		if [ $first == 1 ];
		then
			echo -e "," >> ${t}
		else
			first=1
		fi

    		#echo "Time: '$line'"
		
		fcount=$(($fcount+1))

		echo -e "\t{" >> ${t}
		echo -e "\t\t\"time\": ${line}," >> ${t}
		echo -e "\t\t\"node\": \"${set}\"," >> ${t}
		echo -e "\t\t\"chooseAlgorithm\": \"MOST_LOADED\"" >> ${t}
		echo -e "\t\t\"nodeMetric\": \"TASK_CONTAINERS\"" >> ${t}
		echo -n -e "\t}" >> ${t}
	fi
done < "$f"
	
	echo -e "\n]" >> ${t}

else
	echo "ERROR: could not locate $f"
	exit 1
fi

done
done
