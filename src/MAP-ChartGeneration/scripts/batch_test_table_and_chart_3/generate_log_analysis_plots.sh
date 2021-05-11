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

echo "Making Log Analysis Plot for ${file_path}: ${gnu_file} -> ${eps_file}"




# create GNU plot file
cat > ${gnu_file} <<- EOM
#set terminal eps enhanced truecolor font 'Arial,11' dashed size 10,6
set terminal png enhanced truecolor font 'Arial,11' size 2000,1200
set datafile separator ","

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y
set xrange  [0:] 
set yrange  [0:]
#set offsets 0,0,1,0

set title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}"
set xlabel "Time (s)"
set key center bottom box outside
set style fill transparent solid 0.5 noborder
set style data lines


time_offset = 0



set macro

# macros
convert_time_unit(time)=(time / 1000)   # convert time from milliseconds to seconds
convert_time(time)=convert_time_unit(time - time_offset)   # offset and convert time from milliseconds to seconds
col_sum(a, b)=(sum [c=a:b] (valid(c) ? column(c) : 0))
get_line(str, l)=system("echo '" . str . "' | head -n " . l . " | tail -n 1")
get_column_header(file, c)=system("head " . file . " -n 1 | tr ',' '\n' | head -n " . c . " | tail -n 1")

value_or_default(a, b)=(valid(a) ? column(a) : b)

max(a, b)=(a > b ? a : b)




print get_line("abc\ndef\nxyz", 2)

	# add log message timeline
	print("   Log Message Occurrences")
	log_message_occurrences_plot_string=""

	stats 'category_match_occurrences.csv' skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {

	col2 = cols + 2 - col
	
		log_message_occurrences_plot_string=log_message_occurrences_plot_string . \
			"'category_match_occurrences.csv' using (convert_time(column(1))):(column(".col2.")):(column(".col2.")-0.5):(column(".col2.")+0.5) with yerr ps 0.5 lc ".col2." title (columnhead(".col2.")), " #. \
			#"'category_match_occurrences.csv' using (convert_time(column(1))):(column(".col2.")) with lines  lw 3 lc ".col2." notitle, "
	}


	print("\n\n")



	# add log message activity bins lines
	print("   Log Message Acitivity Levels")
	log_message_activity_levels_plot_string=""

	stats 'category_match_occurrences.csv' skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {

	col2 = cols + 2 - col
	
		log_message_activity_levels_plot_string=log_message_activity_levels_plot_string . \
			"'binned_category_match_counts.csv' using (convert_time(column(1))):(column(".col2.")) with linespoints pt ".col2." ps 1 lc ".col2." title (columnhead(".col2.")), "
	}


	print("\n\n")



	# add log message time interval bars
	print("   Log Message Interval Bins")
	log_message_time_interval_plot_string=""
	log_message_time_interval_titles=""

	stats 'binned_match_time_interval_counts.csv' skip 1 nooutput
	cols = floor(STATS_columns)

	log_message_interval_categories = cols - 1

	do for [col=2:cols] {

	col2 = col #cols + 2 - col
	
		log_message_time_interval_plot_string=log_message_time_interval_plot_string . \
			"\"binned_match_time_interval_counts.csv\" using ((convert_time(column(1)))):(((value_or_default(".col2.", 0.1/column(1)) * max(column(1), 1)))) with boxes lc ".col2." notitle, \n"

		
	}


	print("\n\n")



	set output "${png_file}_timeline" . ".png"
	set multiplot layout 1,1 title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,18'

	set key samplen 5 spacing 1.75 font ",9" box opaque top right reverse
	set key box width 1
	set key spacing 1

	set tmargin 5

	set title "Log Message Category Match Timeline"
	set xlabel "Time (s)"
	set ylabel ""
	set key
	eval "plot " . log_message_occurrences_plot_string

	unset multiplot



	set output "${png_file}_activity_levels" . ".png"
	set multiplot layout 1,1 title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,18'

	set key samplen 5 spacing 1.75 font ",9" box opaque top right reverse
	set key box width 1
	set key spacing 1

	set tmargin 5

	set title "Log Message Category Activity Levels over Time"
	set xlabel "Time (s)"
	set ylabel "Number of Messages in Time Bin"
	set key
	eval "plot " . log_message_activity_levels_plot_string

	unset multiplot




	multiplot_rows = ceil(sqrt(log_message_interval_categories))
	multiplot_cols = ceil(sqrt(log_message_interval_categories))

	set output "${png_file}_time_interval_distribution" . ".png"
	set multiplot layout multiplot_rows,multiplot_cols title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,18'

	set key samplen 5 spacing 1.75 font ",9" box opaque top right reverse
	set key box width 1
	set key spacing 1

	set tmargin 5


	set xrange [0.1:]
	set yrange [0.1:]
	set logscale x 10
	set logscale y 10
	unset key
	set boxwidth 0.05 absolute
	set style fill solid 1.0 noborder

	set xlabel "Time Interval Bin (s)"
	set ylabel "Total time during Interval (s)"
	set title "--- KEY ---"
	plot x

	set xlabel ""
	set ylabel ""

	do for [c=1:log_message_interval_categories] {
		set title get_column_header("binned_match_time_interval_counts.csv", c + 1)
		plot_str = get_line(log_message_time_interval_plot_string, c)
print(plot_str)
		eval "plot " . plot_str
	}	

	unset multiplot

print("\n\n\n")

EOM




# create eps plot file from gnu file and data
gnuplot "${gnu_file}"
if [ $? -ne 0 ]; then
	warn "Plot failed"
else
	log "Plot Succeeded"
fi

exit 0

