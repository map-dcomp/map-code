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
file_base="service_ncp_load_cap_req"
file_base_dns="service_dns_response"
title="Load, Allocated Capacity, and Requests Results with overlayed Client Demand for a Service"
title_dns="DNS Response for a Service"

load_unit="TASK_CONTAINERS"

gnu_file="${folder}/${file_base}.gnu"
eps_file="${folder}/${file_base}"
eps_file_dns="${folder}/${file_base_dns}"

echo "Making NCP load plot for ${file_path}"




# create GNU plot file
cat > ${gnu_file} <<- EOM
set terminal eps enhanced truecolor font 'Arial,11' dashed size 10,6
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
service_name(long_name)=system("name='" . long_name . "' && name=\${name#*ncp*-} && name=\${name#*requests*-} && name=\${name#*dns_req_count*_} && name=\${name#*map}  && name=\${name%.csv} && name=\${name%-${load_unit}} && printf \${name}")
client_name(long_name)=system("name='" . long_name . "' && name=\${name##*/} && echo \${name%-*.csv}")
get_service_node_color(s, n, services, ncps)=(s*ncps + (n-1) + 1)
convert_time_unit(time)=(time / 1000)   # convert time from milliseconds to seconds
convert_time(time)=convert_time_unit(time - time_offset)   # offset and convert time from milliseconds to seconds

n_demand_files_by_client=system("ls ${folder}/client_demand/client_*-${load_unit}.csv 2> /dev/null | wc -l")
n_demand_files_by_service=system("ls ${folder}/client_demand/service_*-${load_unit}.csv 2> /dev/null | wc -l")
n_load_files=system("ls ${folder}/load/ncp_load-*-${load_unit}.csv 2> /dev/null | wc -l")
n_demand_reported_files=system("ls ${folder}/load/ncp_demand-*-${load_unit}.csv 2> /dev/null | wc -l")
n_allocated_capacity_files=system("ls ${folder}/load/ncp_allocated_capacity-*-${load_unit}.csv 2> /dev/null | wc -l")
n_requests_results_count_files=system("ls ${folder}/requests_results/binned_request_count-*.csv 2> /dev/null | wc -l")
n_requests_results_load_files=system("ls ${folder}/requests_results/binned_request_load-*-${load_unit}.csv 2> /dev/null | wc -l")

load_file(f)=system("ls ${folder}/load/ncp_load-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
demand_reported_file(f)=system("ls ${folder}/load/ncp_demand-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
allocated_capacity_file(f)=system("ls ${folder}/load/ncp_allocated_capacity-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
demand_file_by_client(f)=system("ls ${folder}/client_demand/client_*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
demand_file_by_service(f)=system("ls ${folder}/client_demand/service_*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
requests_results_count_file(f)=system("ls ${folder}/requests_results/binned_request_count-*.csv 2> /dev/null | head -n " . f . " | tail -n 1")
requests_results_load_file(f)=system("ls ${folder}/requests_results/binned_request_load-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")

n_dns_req_files=system("ls ${folder}/dns/dns_req_count_*.csv 2> /dev/null | grep -v -E '.*--.*' | wc -l")
dns_req_file(f)=system("ls ${folder}/dns/dns_req_count_*.csv 2> /dev/null | grep -v -E '.*--.*' | head -n " . f . " | tail -n 1")
dns_req_file_get_n_regions(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -v -E '.-.*_' | wc -l")
dns_req_file_get_n_containers(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -E '.-.*_' | wc -l")



col_sum(a, b)=(sum [c=a:b] (valid(c) ? column(c) : 0))



ncps = 0
services = 0
clients = 0

do for [f=1:n_load_files] {
	stats load_file(f) skip 1 nooutput
	cols = floor(STATS_columns)

	ncps = (cols-1 > ncps ? cols-1 : ncps)
}

services = (n_load_files > services ? n_load_files : services)
services = (n_allocated_capacity_files > services ? n_allocated_capacity_files : services)

clients = n_demand_files_by_client

print "Services: " . services
print "NCPS: " . ncps
print "Clients: " . clients





# preprocess data to establish scales

x_offset = 0

x_scale_min = -1
x_scale_max = 0
y_scale_min = 0
y_scale_max = 0
y_scale_max_demand = 0
y_scale_max_load = 0
y_scale_max_allocated_capacity = 0
y_scale_max_requests_results = 0


# demand files
do for [f=1:n_demand_files_by_service] {
	stats demand_file_by_service(f) using 1 nooutput
	min_time = STATS_min
	max_time = STATS_max

	do for [s=1:services] {

		stats demand_file_by_service(f) skip 1 nooutput
		cols = floor(STATS_columns)

		stats demand_file_by_service(f) using 2 nooutput
		min_value = STATS_min
		max_value = STATS_max

		y_scale_max_demand = (max_value > y_scale_max_demand ? max_value : y_scale_max_demand)
	}
}


# load and capacity files
do for [service=1:services] {
	# load
	f=service
	stats load_file(f) using 1 nooutput
	min_time = STATS_min
	max_time = STATS_max

	stats load_file(f) using 2 nooutput
	min_value = STATS_min
	max_value = STATS_max

	x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
	x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)
	y_scale_max_load = (max_value > y_scale_max_load ? max_value : y_scale_max_load)


	# capacity
	f=service
	stats allocated_capacity_file(f) using 1 nooutput
	min_time = STATS_min
	max_time = STATS_max

	stats allocated_capacity_file(f) using 2 nooutput
	min_value = STATS_min
	max_value = STATS_max

	x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
	x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)
	y_scale_max_allocated_capacity = (max_value > y_scale_max_allocated_capacity ? max_value : y_scale_max_allocated_capacity)

	if (n_requests_results_count_files > 0 && n_requests_results_load_files > 0) {
		# requests results
		f=service
		stats requests_results_count_file(f) using 1 nooutput
		min_time = STATS_min
		max_time = STATS_max

		stats requests_results_count_file(f) using 2 nooutput
		min_value = STATS_min
		max_value = STATS_max

		x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
		x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)
		y_scale_max_requests_results = (max_value > y_scale_max_requests_results ? max_value : y_scale_max_requests_results)

		stats requests_results_load_file(f) using 1 nooutput
		min_time = STATS_min
		max_time = STATS_max

		stats requests_results_load_file(f) using 2 nooutput
		min_value = STATS_min
		max_value = STATS_max

		x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
		x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)
		y_scale_max_requests_results = (max_value > y_scale_max_requests_results ? max_value : y_scale_max_requests_results)
	}	
}

x_scale_min = (x_scale_min >= 0 ? x_scale_min : 0)
x_offset = x_scale_min
x_scale_min = x_scale_min - x_offset
x_scale_max = ceil((x_scale_max - x_offset) * 1.02)

y_scale_max = (y_scale_max_demand > y_scale_max ? y_scale_max_demand : y_scale_max)
y_scale_max = (y_scale_max_load > y_scale_max ? y_scale_max_demand : y_scale_max)
y_scale_max = (y_scale_max_allocated_capacity > y_scale_max ? y_scale_max_demand : y_scale_max)
#y_scale_max = (y_scale_max_requests_results > y_scale_max ? y_scale_max_demand : y_scale_max)
y_scale_max = y_scale_max * 1.1


print "\n"
print sprintf("Max demand: %.2f", y_scale_max_demand)
print sprintf("Max load: %.2f", y_scale_max_load)
print sprintf("Max allocated capacity: %.2f", y_scale_max_allocated_capacity)
print sprintf("X scale: [%.0f, %.0f]", x_scale_min, x_scale_max)
print sprintf("Y scale: [%.2f, %.2f]", y_scale_min, y_scale_max)
print sprintf("X offset: %.0f", x_offset)

time_offset = x_offset

print("\n\n")





do for [service=1:services] {
	print("Service " . service)

	#set xrange [x_scale_min:x_scale_max]
	#set yrange[y_scale_min:]
	set xrange [*:*]
	set yrange [*:*]

	# construct string for plot command
	plot_string=""


	# add demand lines
	print("   Demand")
	demand_line_string=""
	service_demand_plot_string=""
	service_all_demand_plot_string=""
	service_all_demand_title_plot_string=""


	f=service
	do for [s=1:services] {
		stats demand_file_by_service(f) skip 1 nooutput
		cols = floor(STATS_columns)

		if (s == service) {
			demand_line_string = "demand_file_by_service(".f.") using (convert_time_unit(column(1))):(col_sum(2, ".cols.")) lc rgb 'black' lw 3 dt 1 t ('demand') with lines, "

			service_demand_plot_string=service_demand_plot_string . demand_line_string
		} else {
			demand_line_string = "demand_file_by_service(".f.") using (convert_time_unit(column(1))):(col_sum(2, ".cols.")) lc rgb 'black' lw 3 dt 1 notitle with lines, "
		}

		service_all_demand_plot_string=service_all_demand_plot_string . demand_line_string
	}



	# add load stacked areas
	print("   Load")
	service_load_plot_string=""
	load_service_names=""

	f=service
	stats load_file(f) skip 1 nooutput
	cols = floor(STATS_columns)


	do for [col=2:cols] {
		service_load_plot_string=service_load_plot_string . \
			"load_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lc get_service_node_color(".f.", ".(col-1).", services, ncps) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"load_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with points lc get_service_node_color(".f.", ".(col-1).", services, ncps) pt (7+".f."-1) ps 0.3 notitle, "
	}

	load_service_names=load_service_names . (f > 1 ? "\n" : "") . service_name(load_file(f))




	# add Demand Reported stacked areas
	print("   Demand Reported")
	service_demand_reported_plot_string=""
	demand_reported_service_names=""

	f=service

	stats demand_reported_file(f) skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {
		service_demand_reported_plot_string=service_demand_reported_plot_string . \
			"demand_reported_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lc get_service_node_color(".f.", ".(col-1).", services, ncps) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"demand_reported_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with points lc get_service_node_color(".f.", ".(col-1).", services, ncps) pt (7+".f."-1) ps 0.3 notitle, "
	}

	demand_reported_service_names=demand_reported_service_names . (f > 1 ? "\n" : "") . service_name(demand_reported_file(f))




	# add allocated capacity stacked areas
	print("   Allocated Capacity")
	service_allocated_capacity_plot_string=""
	allocated_capacity_service_names=""

	f=service
	stats allocated_capacity_file(f) skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {
		service_allocated_capacity_plot_string=service_allocated_capacity_plot_string . \
			"allocated_capacity_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lc get_service_node_color(".f.", ".(col-1).", services, ncps) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"allocated_capacity_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with points lc get_service_node_color(".f.", ".(col-1).", services, ncps) pt (7+".f."-1) ps 0.3 notitle, "
	}

	allocated_capacity_service_names=allocated_capacity_service_names . (f > 1 ? "\n" : "") . service_name(allocated_capacity_file(f))



	# add requests results lines
	print("   Request Results")
	requests_results_count_plot_string=""
	requests_results_load_plot_string=""
	requests_results_count_service_names=""
	requests_results_load_service_names=""

	if (n_requests_results_count_files > 0 && n_requests_results_load_files > 0) {
		f=service

		requests_results_count_plot_string=requests_results_count_plot_string . \
				"requests_results_count_file(".f.") using (convert_time(column(1))):(column('succeeded')) axes x1y2 lw 1 lt 1 lc rgb 'blue' title 'Succeeded', " . \
				"requests_results_count_file(".f.") using (convert_time(column(1))):(column('failed_for_server')) axes x1y2 lw 1 lt 1 lc rgb 'red' title 'Failed (Server)', " . \
				"requests_results_count_file(".f.") using (convert_time(column(1))):(column('failed_for_network')) axes x1y2 lw 1 lt 1 lc rgb 'black' title 'Failed (Network)', "
		requests_results_count_service_names=requests_results_count_service_names . (f > 1 ? "\n" : "") . service_name(requests_results_count_file(f))

		requests_results_load_plot_string=requests_results_load_plot_string . \
				"requests_results_load_file(".f.") using (convert_time(column(1))):(column('succeeded')) axes x1y2 lw 1 lt 1 lc rgb 'blue' title 'Demand Succeeded', " . \
				"requests_results_load_file(".f.") using (convert_time(column(1))):(column('failed_for_server')) axes x1y2 lw 1 lt 1 lc rgb 'red' title 'Demand Failed (Server)', " . \
				"requests_results_load_file(".f.") using (convert_time(column(1))):(column('failed_for_network')) axes x1y2 lw 1 lt 1 lc rgb 'black' title 'Demand Failed (Network)', "
		requests_results_load_service_names=requests_results_load_service_names . (f > 1 ? "\n" : "") . service_name(requests_results_load_file(f))
	}






	# add DNS response counts
	print("   DNS Response Counts")
	dns_req_count_regions_plot_string=""
	dns_req_count_containers_plot_string=""
	dns_req_count_plot_service_names=""

	f=service
	file=dns_req_file(f)
	n_regions=dns_req_file_get_n_regions(file)
	n_containers=dns_req_file_get_n_containers(file)

	print "DNS Response: " . file . " (" . n_regions . " regions, " . n_containers . " containers)"

	do for [r=1:n_regions] {
		col=r+1
		dns_req_count_regions_plot_string=dns_req_count_regions_plot_string . \
			"dns_req_file(".f.") using ((convert_time(column(1)))):(column(".col.")) with lines dt 1 lc ".col." t column(".col."), \n"
	}

	do for [c=1:n_containers] {
		col=1+n_regions+c
		dns_req_count_containers_plot_string=dns_req_count_containers_plot_string . \
			"dns_req_file(".f.") using ((convert_time(column(1)))):(column(".col.")) with lines dt 1 lc ".col." t column(".col."), \n"
	}

	dns_req_count_plot_service_names=dns_req_count_plot_service_names . (f > 1 ? "\n" : "") . service_name(file)



	print("\n\n")


	### Output chart for Load, Demand, Allocated Containers, and Requests Results
	set output "${eps_file}-" . service . ".eps"
	set multiplot layout 2,2 title "${title//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,14'

		set xrange [convert_time_unit(x_scale_min):convert_time_unit(x_scale_max)]
		set yrange [y_scale_min:*]

		set key samplen 5 spacing 1.75font ",6" box opaque top right reverse
		set key box width 1

		set tmargin 5
		set title load_service_names
		set ylabel "Load (${load_unit})"
		set key
		eval "plot " . service_load_plot_string . service_demand_plot_string

		set title demand_reported_service_names
		set ylabel "Predicted Demand (${load_unit})"
		set key
		eval "plot " . service_demand_reported_plot_string . service_demand_plot_string

		#if (strlen(requests_results_load_plot_string) > 0) {
			set title requests_results_load_service_names
			set ylabel "Request Demand (${load_unit})"
			set key
			eval "plot " . requests_results_load_plot_string . service_demand_plot_string
		#}

		set title allocated_capacity_service_names
		set ylabel "Allocated Capacity (${load_unit})"
		set key
		eval "plot " . service_allocated_capacity_plot_string . service_all_demand_plot_string

	unset multiplot




	### Output chart for DNS response analysis
	set output "${eps_file_dns}-" . service . ".eps"
	set multiplot layout 3,1 title "${title_dns//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,14'

		set xrange [convert_time_unit(x_scale_min):convert_time_unit(x_scale_max)]
		set yrange [y_scale_min:]

		set key samplen 5 spacing 1.75font ",6" box opaque top right reverse
		set key box width 1
		set key inside

		set tmargin 5


		set title allocated_capacity_service_names
		set ylabel "Allocated Capacity\n(${load_unit})"
		eval "plot " . service_allocated_capacity_plot_string . service_all_demand_plot_string

		set title dns_req_count_plot_service_names
		set ylabel "Number of DNS Responses\nto Region"
		eval "plot " . dns_req_count_regions_plot_string

		set title dns_req_count_plot_service_names
		set ylabel "Number of DNS Responses\nto Container"
		eval "plot " . dns_req_count_containers_plot_string

	unset multiplot

	### Output chart for DNS response analysis for regions
	set output "${eps_file_dns}-reg-" . service . ".eps"
	set multiplot layout 2,1 title "${title_dns//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,14'

		set xrange [convert_time_unit(x_scale_min):convert_time_unit(x_scale_max)]
		set yrange [y_scale_min:]

		set key samplen 5 spacing 1.75font ",6" box opaque top right reverse
		set key box width 1
		set key inside

		set tmargin 5


		set title allocated_capacity_service_names
		set ylabel "Allocated Capacity\n(${load_unit})"
		eval "plot " . service_allocated_capacity_plot_string . service_all_demand_plot_string

		set title dns_req_count_plot_service_names
		set ylabel "Number of DNS Responses\nto Region"
		eval "plot " . dns_req_count_regions_plot_string

	unset multiplot

	### Output chart for DNS response analysis for containers
	set output "${eps_file_dns}-con-" . service . ".eps"
	set multiplot layout 2,1 title "${title_dns//_/\\\\_}\n${scenario_run_name//_/\\\\_}" font 'Garamond,14'

		set xrange [convert_time_unit(x_scale_min):convert_time_unit(x_scale_max)]
		set yrange [y_scale_min:]

		set key samplen 5 spacing 1.75font ",6" box opaque top right reverse
		set key box width 1
		set key inside

		set tmargin 5


		set title allocated_capacity_service_names
		set ylabel "Allocated Capacity\n(${load_unit})"
		eval "plot " . service_allocated_capacity_plot_string . service_all_demand_plot_string

		set title dns_req_count_plot_service_names
		set ylabel "Number of DNS Responses\nto Container"
		eval "plot " . dns_req_count_containers_plot_string

	unset multiplot
}

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

