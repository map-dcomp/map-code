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

#configuration
folder=$1
scenario_run_name=$2
file_base=$3
title=$4



#title="NCP Load Plot"

#for file_path in $(ls "${folder}"/ncp_load-*-*.csv) ; do

#	file=${file_path##*/}
#	file_base=${file%.csv}
#	file_base=ncp_load

gnu_file="${folder}/${file_base}.gnu"
eps_file="${folder}/${file_base}.eps"
png_file="${folder}/${file_base}.png"

echo "Making NCP load plot for ${file_path}: ${gnu_file} -> ${eps_file} -> ${png_file}"




# create GNU plot file
cat > ${gnu_file} <<- EOM
set terminal eps enhanced truecolor font 'Arial,11' dashed
set datafile separator ","

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y
set yrange  [0:] 
set xrange  [0:]
set offsets 0,0,1,0

set title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}"
set ylabel "Load"
set xlabel "Time (s)"
#set key right top box opaque
set key center bottom box outside
#set key autotitle columnhead
set style fill transparent solid 0.5 noborder
set style data lines


set output "${eps_file}"



set macro

# macros
service_name(long_name)=system("name='" . long_name . "' && name=\${name#*ncp*-} && name=\${name#*map} && echo \${name%-TASK_CONTAINERS.csv}")
get_service_node_color(s, n, services, ncps)=(s*ncps + (n-1) + 1)
convert_time_unit(time)=(time/1000)   # convert time from milliseconds to seconds

n_demand_files=system("ls ${folder}/client_demand/*-*.csv | wc -l")
n_load_files=system("ls ${folder}/load/ncp_load-*-*.csv | wc -l")
load_file(f)=system("ls ${folder}/load/ncp_load-*-*.csv | head -n " . f . " | tail -n 1")
demand_file(f)=system("ls ${folder}/client_demand/*-*.csv | head -n " . f . " | tail -n 1")
col_sum(a, b)=(sum [c=a:b] (valid(c) ? column(c) : 0))




# construct string for plot command
plot_string=""

# add demand data

print n_demand_files

ncps = 0

do for [f=1:n_load_files] {
	stats load_file(f) skip 1
	cols = floor(STATS_columns)

	ncps = (cols-1 > ncps ? cols-1 : ncps)
}

do for [f=1:n_demand_files] {
	print demand_file(f)

	stats demand_file(f) skip 1
	cols = floor(STATS_columns)

	plot_string=plot_string . \
		"for [col=2:".cols."] demand_file(".f.") using (convert_time_unit(column(1))):(column(col)) lc get_service_node_color(col-1, 1, n_load_files, ncps) lw 1 dt 9 t (service_name(columnhead(col))) with lines, "	
}


# add load stacked area data


do for [f=1:n_load_files] {
	print load_file(f)

	stats load_file(f) skip 1
	cols = floor(STATS_columns)

	plot_string=plot_string . \
		"for [col=2:".cols."] load_file(".f.") using (convert_time_unit(column(1))):(col_sum(col,".cols.")):(column(1)*0) lc get_service_node_color(".f.", col-1, n_load_files, ncps) lw 2 t (service_name(load_file(".f.")) . ' : ' . columnhead(col)) with filledcurves, " . \
	        "for [col=2:".cols."] load_file(".f.") using (convert_time_unit(column(1))):(col_sum(col,".cols.")) with points lc get_service_node_color(".f.", col-1, n_load_files, ncps) pt (7+".f."-1) ps 0.3 notitle, "



	print get_service_node_color(f, 1, n_load_files, ncps)
}

print plot_string
eval "plot " . plot_string

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
#done

exit 0

