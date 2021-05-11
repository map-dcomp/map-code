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
folder=$1
file_base="service_plan"
title="Service Plan Instances Plot"
scenario_run_name=$2



gnu_file="${folder}/${file_base}.gnu"
eps_file="${folder}/${file_base}.eps"
png_file="${folder}/${file_base}.png"


cat > ${gnu_file} <<- EOM
set title "${title}\n${scenario_run_name}"
set terminal eps enhanced truecolor font 'Arial,11' dashed
set output "${eps_file}"
set datafile separator ","
set ylabel "# of Service Containers"
set xlabel "Time (ms)"
#set yrange  [0:35] 
set xrange  [0:800000]
set key autotitle columnhead

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y

set key right top box opaque

set style data lines

set macro
file="${folder}/service_image-recognition-low_1.0-rlg_service_plans.csv"
file2="${folder}/service_image-recognition-high_1.0-rlg_service_plans.csv"

N=\`awk 'BEGIN { FS=","} NR==1 {print NF}' @file\`
N2=\`awk 'BEGIN { FS=","} NR==1 {print NF}' @file2\`

plot for [i=2:N-1] file using 1:i dt i lt i lw 3 ti 'low '.(i-2), \
	for [i=2:N2-1] file2 using 1:i lt i lw 3 ti 'high '.(i-2)

EOM


# create eps plot file from gnu file and data
gnuplot "${gnu_file}"

if [ $? -ne 0 ]; then
	echo "Plot failed"
else
	# create png file from eps file
	convert -colorspace sRGB -density 600 "${eps_file}" -background white -flatten -resize 800x600 -units pixelsperinch -density 224.993 "${png_file}"

	if [ $? -eq 0 ]; then
		echo "Output image: ${png_file}"
	else 
		echo "Convert failed"
	fi
fi

exit 0

