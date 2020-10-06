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
#!/bin/bash

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

var=${1:-default}

# check for ${word} in ${string}
test "${string#*$word}" != "$string" && echo "$word found in $string"

#configuration
folder=$1
scenario_run_name=$2
file_base=$3
title=$4

load_unit="TASK_CONTAINERS"

gnu_file="${folder}/${file_base}.gnu"
eps_file="${folder}/${file_base}"
png_file="${folder}/${file_base}.png"

echo "Making NCP load plot for ${file_path}: ${gnu_file} -> ${eps_file}"




# create GNU plot file
cat > ${gnu_file} <<- EOM



set terminal png enhanced truecolor font 'Arial,11' size 2000,1200
set datafile separator ","

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y
#set xrange  [0:] 
set yrange  [0:]
#set offsets 0,0,1,0


set macro

# macros
time_offset=0
convert_time_unit(time)=(time / 1000)   # convert time from milliseconds to seconds
convert_time(time)=convert_time_unit(time - time_offset)   # offset and convert time from milliseconds to seconds

n_dns_req_files=system("ls ${folder}/dns/dns_req_count_*.csv | wc -l")
dns_req_file(f)=system("ls ${folder}/dns/dns_req_count_*.csv | head -n " . f . " | tail -n 1")
dns_req_file_get_n_regions(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -v -E '.-.*_' | wc -l")
dns_req_file_get_n_containers(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -E '.-.*_' | wc -l")

#file=dns_req_file(1)
#print system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -E '.-.*_'")



do for [f=1:n_dns_req_files] {

	file=dns_req_file(f)
	n_regions=dns_req_file_get_n_regions(file)
	n_containers=dns_req_file_get_n_containers(file)   #cols-1-n_regions

	print "DNS Response: " . file . " (" . n_regions . " regions, " . n_containers . " containers)"

	stats file skip 1 nooutput
	cols = floor(STATS_columns)



	dns_req_count_plot_string=""

	do for [r=1:n_regions] {
		col=r+1
		dns_req_count_plot_string=dns_req_count_plot_string . \
			"dns_req_file(".f.") using ((convert_time(column(1)))):(column(".col.")) with linespoints dt 1 lc ".col." t column(".col."), \n"
	}

	do for [c=1:n_containers] {
		col=1+n_regions+c
		dns_req_count_plot_string=dns_req_count_plot_string . \
			"dns_req_file(".f.") using ((convert_time(column(1)))):(column(".col.")) with linespoints dt 1 lc ".col." t column(".col."), \n"
	}

	set output dns_req_file(f) . ".png"
	set multiplot layout 1,1 title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,20'

	set key samplen 5 spacing 1.75 font ",14" box opaque top right outside reverse
	set key box width 1
	set key spacing 1

	set tmargin 5

	set title "DNS Requests over Time for a service" font "Garamond,16"
	set xlabel "Time (s)" font "Garamond,16"
	set ylabel "Number of DNS Requests in Time Bin to Region or Container" font "Garamond,16"
	set key
	eval "plot " . dns_req_count_plot_string

	unset multiplot
}


	




EOM




# create eps plot file from gnu file and data
gnuplot "${gnu_file}"
if [ $? -ne 0 ]; then
	warn "Plot failed"
else
	log "Plot Succeeded"
fi

exit 0

