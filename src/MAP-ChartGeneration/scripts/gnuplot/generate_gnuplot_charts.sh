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

help() {
    log "Usage: $0 --sim <run output dir> --chart_output <chart table output dir> --output <output dir> --load_unit <load unit> --run_title <run titles>"
    exit
}

#configuration
sim_output=""
chart_output_dir=""
output_dir=""
load_unit=""
window_suffix="SHORT"
scenario_run_name="[scenario name]"
while [ $# -gt 0 ]; do
    debug "Checking arg '${1}' with second '${2}'"
    case ${1} in
        --help|-h)
            help
            ;;
        --sim)
            if [ -z "${2}" ]; then
                fatal "--sim is missing an argument"
            fi
            sim_output=${2}
            shift
            ;;
        --chart_output)
            if [ -z "${2}" ]; then
                fatal "--chart_output is missing an argument"
            fi
            chart_output_dir=${2}
            shift
            ;;
        --output)
            if [ -z "${2}" ]; then
                fatal "--output is missing an argument"
            fi
            output_dir=${2}
            shift
            ;;
        --load_unit)
            if [ -z "${2}" ]; then
                fatal "--load_unit is missing an argument"
            fi
            load_unit=${2}
            shift
            ;;
        --window_suffix)
            if [ -z "${2}" ]; then
                fatal "--window_suffix is missing an argument"
            fi
            window_suffix=${2}
            shift
            ;;
        --run_title)
            if [ -z "${2}" ]; then
                fatal "--run_title is missing an argument"
            fi
            scenario_run_name=${2}
            shift
            ;;
        *)
            error "Unknown argument ${1}"
            help
            ;;
    esac
    shift
done

mkdir -p "${output_dir}"



gnuplot_file="${output_dir}/gnuplot_charts.gnu"
output_file_load_latency="${output_dir}/service_load_latency"

echo "Making gnuplot charts for run ${scenario_run_name} with chart table files at ${chart_output}. Outputting to ${output}"

if [ -d "${sim_output}/simulation" ]; then
    # lo-fi
    agent_config_file="$(ls ${sim_output}/*/agent-configuration.json | head -n 1)"
else
    # hi-fi
    agent_config_file="$(ls ${sim_output}/*/agent/*.map.dcomp/agent-configuration.json | head -n 1)"
fi
log "Found agent config file: ${agent_config_file}"



# create GNU plot file
cat > ${gnuplot_file} <<- EOM
set terminal eps enhanced truecolor font 'Arial,11' dashed size 10,8
#set terminal png enhanced truecolor font 'Arial,11' size 1000,800
#set terminal pdf enhanced truecolor font 'Arial,11' dashed size 5in,4in
#set terminal svg size 1000,800 dynamic enhanced font 'Arial,11' dashed
set datafile separator ","


# macros
set macro

str2num(a)=(a+0)
num2str(a)=sprintf("%f", a+0)


# service, client, region, node
service_name(long_name)=system("echo '" . long_name . "' | sed -E s/'(.*ncp_allocated_capacity-|.*ncp_demand-|.*ncp_load-|.*requests.*-|.*dns_req_count.*_|.*processing_latency-.*-|.*processing_latency-|.*binned_server_processing_latency_counts-|.*map|.*service_|-container_weights)'//g | sed -E s/'${window_suffix}-'//g |  sed -E s/'(-${load_unit})?.csv'//g")
client_name(long_name)=system("name='" . long_name . "' && name=\${name##*/} && echo \${name%-*.csv}")
region_name(file)=system(" echo '".file."' | sed -E 's/.*processing_latency-|-.*.csv//g'")    #system(" echo '".file."' | sed -e \"s/.*processing_latency-\|-.*\.csv//g\"")

# colors
get_node_color_component(node, component)=(system("${mydir}/node_colors.sh " . node . " " . component) + 0.0)
get_node_color(node)=hsv2rgb(get_node_color_component(node,'h')/100.0,get_node_color_component(node,'s')/100.0,get_node_color_component(node,'v')/100.0)
get_node_color_v(node,variance)=hsv2rgb(get_node_color_component(node,'h')/100.0,get_node_color_component(node,'s')/100.0,variance/100.0)

# time
convert_time_unit(time)=(time / 1000)   # convert time from milliseconds to seconds
convert_time(time)=convert_time_unit(time - time_offset)   # offset and convert time from milliseconds to seconds

# data columns
column_head(file, col)=system("cat ".file." 2> /dev/null | head -n 1 | sed -e 's/\r//g' | tr ',' '\n' | head -n ".col." | tail -n 1")
is_column_head_name(file, col)=system("cat ".file." 2> /dev/null | head -n 1 | sed -e 's/\r//g' | tr ',' '\n' | grep " . col . " | wc -l")
n_columns(file)=(system("cat ".file." 2> /dev/null | head -n 1 | sed -e 's/\r//g' | tr ',' '\n' | wc -l") + 0)
n_rows(file)=(system("cat ".file." 2> /dev/null | wc -l") + 0)
col_sum(a, b)=(sum [c=a:b] (valid(c) ? column(c) : 0))
col_sum_all(file)=col_sum(2, n_columns(file))



# agent configuration
found_agent_config=(system("ls ${agent_config_file} | grep agent-configuration.json | wc -l") + 0)
get_agent_config_value_a(property)=(system("cat ${agent_config_file} 2> /dev/null | grep '\"".property."\" : ' | sed -E s/'.* : |,.*'//g"))
get_agent_config_value(property, default)=(val="".system("cat ${agent_config_file} 2> /dev/null | grep '\"".property."\" : ' | sed -E s/'.* : |,.*'//g"), (val != "" ? val : default))
get_agent_config_value_as_string(property, default)=system("echo '" . (get_agent_config_value(property, default)) . "' | sed -E s/'^\"|\"$'//g")
get_agent_config_value_as_num(property, default)=(get_agent_config_value_as_string(property, default) + 0)

# macros for accessing particular types of data files
n_demand_files_by_client=system("ls ${chart_output_dir}/client_demand/client_*-${load_unit}.csv 2> /dev/null | wc -l")
n_demand_files_by_service=system("ls ${chart_output_dir}/client_demand/service_*-${load_unit}.csv 2> /dev/null | wc -l")
n_demand_reported_files=system("ls ${chart_output_dir}/load/ncp_demand-${window_suffix}-*-${load_unit}.csv 2> /dev/null | wc -l")
n_load_files=system("ls ${chart_output_dir}/load/ncp_load-${window_suffix}-*-${load_unit}.csv 2> /dev/null | wc -l")
n_allocated_capacity_files=system("ls ${chart_output_dir}/load/ncp_allocated_capacity-*-${load_unit}.csv 2> /dev/null | wc -l")
n_requests_results_count_files=system("ls ${chart_output_dir}/requests_results/binned_request_count-*.csv 2> /dev/null | wc -l")
n_requests_results_load_files=system("ls ${chart_output_dir}/requests_results/binned_request_load-*-${load_unit}.csv 2> /dev/null | wc -l")

demand_file_by_client(f)=system("ls ${chart_output_dir}/client_demand/client_*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
demand_file_by_service(f)=system("ls ${chart_output_dir}/client_demand/service_*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
demand_reported_file(f)=system("ls ${chart_output_dir}/load/ncp_demand-${window_suffix}-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
load_file(f)=system("ls ${chart_output_dir}/load/ncp_load-${window_suffix}-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
allocated_capacity_file(f)=system("ls ${chart_output_dir}/load/ncp_allocated_capacity-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")
requests_results_count_file(f)=system("ls ${chart_output_dir}/requests_results/binned_request_count-*.csv 2> /dev/null | head -n " . f . " | tail -n 1")
requests_results_load_file(f)=system("ls ${chart_output_dir}/requests_results/binned_request_load-*-${load_unit}.csv 2> /dev/null | head -n " . f . " | tail -n 1")

n_dns_req_files=system("ls ${chart_output_dir}/dns/dns_req_count_*.csv 2> /dev/null | grep -v -E '.*--.*' | wc -l")
dns_req_file(f)=system("ls ${chart_output_dir}/dns/dns_req_count_*.csv 2> /dev/null | grep -v -E '.*--.*' | head -n " . f . " | tail -n 1")
dns_req_file_get_n_regions(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -v -E '.-.*_' | wc -l")
dns_req_file_get_n_containers(file)=system("head -n 1 ".file." | tr ',' '\n' | tail -n +2 | grep -E '.-.*_' | wc -l")

n_server_latency_files=system("ls ${chart_output_dir}/latency_dns/server_processing_latency-*.csv 2> /dev/null | wc -l")
n_region_server_latency_files(service)=system("ls ${chart_output_dir}/latency_dns/server_processing_latency-*-".service.".csv 2> /dev/null | wc -l")
n_binned_server_latency_count_files=system("ls ${chart_output_dir}/latency_dns/binned_server_processing_latency_counts-*.csv 2> /dev/null | wc -l")
server_latency_file(f)=system("ls ${chart_output_dir}/latency_dns/server_processing_latency-*.csv 2> /dev/null | head -n " . f . " | tail -n 1")
region_server_latency_file(f,service)=system("ls ${chart_output_dir}/latency_dns/server_processing_latency-*-".service.".csv 2> /dev/null | head -n " . f . " | tail -n 1")
binned_region_server_latency_count_file(f)=system("ls ${chart_output_dir}/latency_dns/binned_server_processing_latency_counts-*.csv 2> /dev/null | head -n " . f . " | tail -n 1")
n_client_latency_files=system("ls ${chart_output_dir}/latency_dns/client_processing_latency-*.csv 2> /dev/null | wc -l")
client_latency_file(f)=system("ls ${chart_output_dir}/latency_dns/client_processing_latency-*.csv 2> /dev/null | head -n " . f . " | tail -n 1")

n_rlg_service_container_weights_files=system("ls ${chart_output_dir}/rlg_plan_updates/service_*-container_weights.csv 2> /dev/null | wc -l")
rlg_service_container_weights_file(f)=system("ls ${chart_output_dir}/rlg_plan_updates/service_*-container_weights.csv 2> /dev/null | head -n " . f . " | tail -n 1")



demand_scale_factor=1.0

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
print "\n"




# preprocess data to establish scales
set xrange  [*:*] 
set yrange  [*:*]

x_offset = 0

x_scale_min = -1
x_scale_max = 0
y_scale_min = 0
y_scale_max = 0
y_scale_max_demand = 0
y_scale_max_load = 0
y_scale_max_allocated_capacity = 0
y_scale_max_processing_latency = 0
y_scale_max_mean_processing_latency = 0
y_scale_max_processing_latency_bin_count = 0
y_scale_max_requests_results = 0


# demand files
do for [f=1:n_demand_files_by_service] {
	stats demand_file_by_service(f) using 1 nooutput
	min_time = STATS_min
	max_time = STATS_max

	do for [s=1:services] {

		stats demand_file_by_service(f) skip 1 nooutput
		cols = floor(STATS_columns)

		cols=n_columns(demand_file_by_service(f))
		stats demand_file_by_service(f) using (col_sum(2,cols)) nooutput

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

	cols=n_columns(load_file(f))
	stats load_file(f) using (col_sum(2,cols)) nooutput
	min_value = STATS_min
	max_value = STATS_max

	y_scale_max_load = (max_value > y_scale_max_load ? max_value : y_scale_max_load)
	x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
	x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)

	# capacity
	f=service
	stats allocated_capacity_file(f) using 1 nooutput
	min_time = STATS_min
	max_time = STATS_max

	x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
	x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)

	cols=n_columns(allocated_capacity_file(f))
	stats allocated_capacity_file(f) using (col_sum(2,cols)) nooutput
	min_value = STATS_min
	max_value = STATS_max
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

	# server procesing latency
	f=service
	if (n_server_latency_files > 0) {
		if (n_rows(server_latency_file(f)) > 1) {
			stats server_latency_file(f) using 1 nooutput
			min_time = STATS_min
			max_time = STATS_max

			x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
			x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)

			stats server_latency_file(f) using (column('latency')) nooutput
			min_value = STATS_min
			max_value = STATS_max
			mean_value = STATS_mean
	
			y_scale_max_mean_processing_latency = (mean_value > y_scale_max_mean_processing_latency ? mean_value : y_scale_max_mean_processing_latency)
			y_scale_max_processing_latency = (max_value > y_scale_max_processing_latency ? max_value : y_scale_max_processing_latency)
		}
	}


	# server procesing latency count
	f=service

	if (n_binned_server_latency_count_files > 0) {
		stats binned_region_server_latency_count_file(f) using 1 nooutput
		min_time = STATS_min
		max_time = STATS_max
		x_scale_min = (min_time < x_scale_min | x_scale_min == -1 ? min_time : x_scale_min)
		x_scale_max = (max_time > x_scale_max ? max_time : x_scale_max)

		cols=n_columns(binned_region_server_latency_count_file(f))
		do for [col=2:cols] {
			stats binned_region_server_latency_count_file(f) using (column(col)) nooutput
			min_value = STATS_min
			max_value = STATS_max

			y_scale_max_processing_latency_bin_count = (max_value > y_scale_max_processing_latency_bin_count ? max_value : y_scale_max_processing_latency_bin_count)
		}
	}
}

x_scale_min = (x_scale_min >= 0 ? x_scale_min : 0)
x_offset = x_scale_min
x_scale_min = x_scale_min - x_offset
x_scale_max = ceil((x_scale_max - x_offset) * 1.02)

y_scale_max = (y_scale_max_demand > y_scale_max ? y_scale_max_demand : y_scale_max)
y_scale_max = (y_scale_max_load > y_scale_max ? y_scale_max_load : y_scale_max)
y_scale_max = (y_scale_max_allocated_capacity > y_scale_max ? y_scale_max_allocated_capacity : y_scale_max)
#y_scale_max = (y_scale_max_requests_results > y_scale_max ? y_scale_max_demand : y_scale_max)
y_scale_max = y_scale_max * 1.1

#rlg_overload_threshold=get_agent_config_value("rlgLoadThreshold", "0.75")
#rlg_underload_ended_threshold=get_agent_config_value("rlgUnderloadEndedThreshold", "0.35")
#rlg_underload_threshold=get_agent_config_value("rlgUnderloadThreshold", "0.25")

rlg_overload_threshold=0
rlg_underload_ended_threshold=0
rlg_underload_threshold=0

if (found_agent_config > 0) {
	rlg_overload_threshold=get_agent_config_value_a("rlgLoadThreshold")
	rlg_underload_ended_threshold=get_agent_config_value_a("rlgUnderloadEndedThreshold")
	rlg_underload_threshold=get_agent_config_value_a("rlgUnderloadThreshold")
}

print("rlgLoadThreshold: " . rlg_overload_threshold . "  rlgUnderloadEndedThreshold: " . rlg_underload_ended_threshold . "  rlgUnderloadThreshold: " . rlg_underload_threshold)

print "\n"
print sprintf("RLG Overload Threshold: %f", str2num(rlg_overload_threshold))
#print sprintf("RLG Underload Ended Threshold: %f", str2num(rlg_underload_ended_threshold))
#print sprintf("RLG Underload Threshold: %f", str2num(rlg_underload_threshold))
print sprintf("Max demand: %.2f", y_scale_max_demand)
print sprintf("Max load: %.2f", y_scale_max_load)
print sprintf("Max allocated capacity: %.2f", y_scale_max_allocated_capacity)
print sprintf("Max processing latency: %d", y_scale_max_processing_latency)
print sprintf("Max processing latency count: %d", y_scale_max_processing_latency_bin_count)
print sprintf("X scale: [%.0f, %.0f]", x_scale_min, x_scale_max)
print sprintf("Y scale: [%.2f, %.2f]", y_scale_min, y_scale_max)
print sprintf("X offset: %.0f", x_offset)

time_offset = x_offset

print("\n\n")














service_load_thresholds_plot_string=""
service_load_thresholds_plot_string=service_load_thresholds_plot_string . \
	rlg_overload_threshold . "lw 3 lc rgb 'blue' t 'Overload (P+)', " . \
	rlg_underload_threshold . "lw 3 lc rgb 'red' t 'Underload (P-)', " . \
	rlg_underload_ended_threshold . "lw 2 lc rgb 'purple' t 'Underload Ended'"

service_load_thresholds_plot_string=""



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
			demand_line_string = "demand_file_by_service(".f.") using (convert_time_unit(column(1))):(col_sum(2, ".cols.")*demand_scale_factor) lc rgb 'black' lw 3 dt 1 t ('demand') with lines, "

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
			"load_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lt rgb get_node_color(column_head(load_file(".f."),".col.")) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"load_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with linespoints lt rgb 'black' pt (7+".f."-1) ps 0.3 notitle, "
	}


	load_service_names=load_service_names . (f > 1 ? "\n" : "") . service_name(load_file(f))




	# add Demand Reported stacked areas
	print("   Demand ${window_suffix} Reported")
	service_demand_reported_plot_string=""
	demand_reported_service_names=""

	f=service

	stats demand_reported_file(f) skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {
		service_demand_reported_plot_string=service_demand_reported_plot_string . \
			"demand_reported_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lt rgb get_node_color(column_head(load_file(".f."),".col.")) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"demand_reported_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with linespoints lt rgb 'black' pt (7+".f."-1) ps 0.3 notitle, "
	}

	demand_reported_service_names=demand_reported_service_names . (f > 1 ? "\n" : "") . service_name(demand_reported_file(f))





	# add allocated capacity stacked areas
	print("   Allocated Capacity")
	service_allocated_capacity_plot_string=""
	allocated_capacity_service_names=""
	service_load_thresholds_plot_string=""

	f=service
	stats allocated_capacity_file(f) skip 1 nooutput
	cols = floor(STATS_columns)

	do for [col=2:cols] {
		service_allocated_capacity_plot_string=service_allocated_capacity_plot_string . \
			"allocated_capacity_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")):(column(1)*0) lt rgb get_node_color(column_head(load_file(".f."),".col.")) lw 2 t (columnhead(".col.")) with filledcurves, " . \
			"allocated_capacity_file(".f.") using (convert_time(column(1))):(col_sum(".col.",".cols.")) with linespoints lt rgb 'black' pt (7+".f."-1) ps 0.3 notitle, "
	}

	
	service_load_thresholds_plot_string=service_load_thresholds_plot_string . \
		"allocated_capacity_file(".f.") using (convert_time(column(1))):(rlg_overload_threshold * col_sum_all(allocated_capacity_file(".f."))) lw 3 lc rgb 'blue' t 'Overload (P+)', " . \
		"allocated_capacity_file(".f.") using (convert_time(column(1))):(rlg_underload_threshold * col_sum_all(allocated_capacity_file(".f."))) lw 3 lc rgb 'red' t 'Overload (P-)', " . \
		"allocated_capacity_file(".f.") using (convert_time(column(1))):(rlg_underload_ended_threshold * col_sum_all(allocated_capacity_file(".f."))) lw 2 lc rgb 'purple' t 'Underload Ended'"

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
	dns_req_count_regions_plot_string=""
	dns_req_count_containers_plot_string=""
	dns_req_count_plot_service_names=""
	if (n_dns_req_files > 0) {
		print("   DNS Response Counts")

		f=service
		file=dns_req_file(f)
		n_regions=dns_req_file_get_n_regions(file)
		n_containers=dns_req_file_get_n_containers(file)

		#print "DNS Response: " . file . " (" . n_regions . " regions, " . n_containers . " containers)"

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
	} else {
		print("   > Skipping DNS Response Counts")
	}



	# add container weights data
	container_weights_plot_string=""
	container_weights_service_names=""
	if (n_rlg_service_container_weights_files > 0) {
		print("   RLG Container Weights")

		file=rlg_service_container_weights_file(f)
		service_name=service_name(file)
		container_weights_service_names=container_weights_service_names . (f > 1 ? "\n" : "") . service_name
	
		f=service
		stats rlg_service_container_weights_file(f) skip 1 nooutput
		cols = floor(STATS_columns)

		do for [col=2:cols] {
			#node_color = get_node_color(column_head(file, col))		

			container_weights_plot_string=container_weights_plot_string . \
				"rlg_service_container_weights_file(".f.") using (convert_time(column(1))):(column(".col.")) with linespoints lc ".col." ps 0.3 lw 2 dt ".(col+1)." title columnhead(".col."), "
				#"rlg_service_container_weights_file(".f.") using (convert_time(column(1))):(column(".col.")) with linespoints lt rgb get_node_color(column_head(rlg_service_container_weights_file(".f."), ".col.")) ps 0.3 lw 0.5 title columnhead(".col."), "
		}
	} else {
		print("   > Skipping RLG Container Weights")
	}




	# add client processing latency data
	print("   Client Processing Latency Data")
	client_request_latency_plot_string=""
	client_request_latency_smooth_plot_string=""
	client_request_latency_plot_service_names=""

	f=service
	file=client_latency_file(f)
	client_request_latency_plot_string=client_request_latency_plot_string . \
		"client_latency_file(".f.") using ((convert_time(column(1)))):(convert_time_unit(column('latency'))) lc 'gray' t 'Latency (raw)', \n"

	client_request_latency_smooth_plot_string=client_request_latency_smooth_plot_string . \
		"client_latency_file(".f.") using ((convert_time(column(1)))):(convert_time_unit(column('latency'))) lw 5 lc 'red' smooth bezier t 'Latency (smooth)', \n"

	client_request_latency_plot_service_names=client_request_latency_plot_service_names . (f > 1 ? "\n" : "") . service_name(file)



	# add server processing latency data
	print("   Server Processing Latency Data")
	server_processing_latency_plot_string=""
	server_processing_latency_smooth_plot_string=""
	binned_processing_latency_count_plot_string=""
	server_processing_latency_plot_service_names=""

	f=service
	file=server_latency_file(f)
	service_name = service_name(file)
	server_processing_latency_plot_service_names=server_processing_latency_plot_service_names . (f > 1 ? "\n" : "") . service_name


	do for [r=1:n_region_server_latency_files(service_name)] {
		col=r+1
		file=region_server_latency_file(r,service_name)
		file_rows = n_rows(file)
		#print file . " rows: " . file_rows

		if (file_rows > 1) {
			region=region_name(file)
			region_color=get_node_color(region)
			region_color_smooth=get_node_color_v(region, 50)

			server_processing_latency_plot_string=server_processing_latency_plot_string . \
				"'" . file . "' using ((convert_time(column(1)))):(convert_time_unit(column('latency'))) lt rgb ".region_color." t 'Latency (raw) - Region ".region."', \n"

			server_processing_latency_smooth_plot_string=server_processing_latency_smooth_plot_string . \
				"'" . file . "' using ((convert_time(column(1)))):(convert_time_unit(column('latency'))) lw 5 lt rgb ".region_color_smooth." smooth bezier t 'Latency (smooth) - Region ".region."', \n"	

			if (is_column_head_name(binned_region_server_latency_count_file(f),region) > 0) {
				binned_processing_latency_count_plot_string=binned_processing_latency_count_plot_string . \
					"binned_region_server_latency_count_file(".f.") using (convert_time(column(1))):(column('".region."')) lc rgb get_node_color(column_head(binned_region_server_latency_count_file(".f."),".col.")) lw 2 t 'Region ".region."' with linespoints, "
			}

			#print "Service: " . service_name . "   Region: " . region . "\n    (" . file . ")\n"
			stats file using (column(1)):(column('latency')) nooutput

		} else {
			print "Only header and no data found in " . file
		}


		#print "\n\n\n"
	}









	print("\n")



	# configure chart parameters

	unset xtics
	set xtics rotate by -45 offset -1,-.5
	set xtics 300
	set mxtics 5
	set ytics auto
	set boxwidth 1
	set grid y
	set xrange  [0:] 
	set yrange  [0:]
	#set offsets 0,0,1,0

	set title "Allocated Capacity, Load, and Demand for a Service\n${scenario_run_name//_/\\\\_}"
	set xlabel "Time (s)"
	set key center bottom box outside
	set style fill solid 0.5 noborder
	set style data lines

	include_demand=0	# determines if input demand is displayed on the charts


	### Output chart for Load and Processing Latency
	output_file="${output_file_load_latency}-" . service . ".eps"
	set output output_file
	set multiplot layout 4,1 title "Allocated Capacity, Load, and Demand for a Service\n${scenario_run_name//_/\\\\_}" font 'Garamond,14'

		set xrange [convert_time_unit(x_scale_min):convert_time_unit(x_scale_max)]

		set key samplen 5 spacing 1.75font ",6" box opaque top right noreverse
		set key box width 1
		set key top inside maxrows 1

		set tmargin 5

		set title allocated_capacity_service_names
		set xlabel
		set ylabel "Allocated Capacity\n(${load_unit})"
		set yrange [y_scale_min:y_scale_max_allocated_capacity*1.2+0.1]		
		eval "plot " . service_allocated_capacity_plot_string . (include_demand == 1 ? service_demand_plot_string : "")

		set title load_service_names
		set xlabel
		set ylabel "Load\n(${load_unit})"
		set yrange [y_scale_min:y_scale_max_load*1.2+0.1]
		eval "plot " . service_load_plot_string . (include_demand == 1 ? service_demand_plot_string : "") . service_load_thresholds_plot_string

		set title demand_reported_service_names
		set xlabel
		set ylabel "Demand ${window_suffix}\n(${load_unit})"
		set yrange [y_scale_min:y_scale_max_load*1.2+0.1]
		eval "plot " . service_demand_reported_plot_string . (include_demand == 1 ? service_demand_plot_string : "") . service_load_thresholds_plot_string

		if (strlen(server_processing_latency_plot_string . server_processing_latency_smooth_plot_string) > 0) {
			set title server_processing_latency_plot_service_names
			set xlabel
			set ylabel "Server Processing Latency (s)\n "
			set yrange [convert_time_unit(y_scale_min):convert_time_unit(y_scale_max_mean_processing_latency*2)*1.2+0.1]
			#eval "plot " . server_processing_latency_plot_string . server_processing_latency_smooth_plot_string
		}

		if (strlen(binned_processing_latency_count_plot_string) > 0) {
			set title server_processing_latency_plot_service_names
			set xlabel "Time (s)"
			set ylabel "Server Request Rate (requests/30s) \n "
			set yrange [0:y_scale_max_processing_latency_bin_count*1.2+0.1]
			#eval "plot " . binned_processing_latency_count_plot_string
		}

		set title client_request_latency_plot_service_names
		set xlabel ""
		set ylabel "Client Request Latency (s)\n "
		set yrange [convert_time_unit(y_scale_min):convert_time_unit(y_scale_max_processing_latency)*1.2]
		#eval "plot " . client_request_latency_plot_string . client_request_latency_smooth_plot_string

		if (strlen(container_weights_plot_string) > 0) {
			set title container_weights_service_names
			set xlabel
			set ylabel "Container Weight\n()"
			set yrange [0:1]
			#eval "plot " . container_weights_plot_string
		}

	unset multiplot

	print("Output " . output_file)
	print("\n\n")
}

print("\n\n\n")

EOM




# create eps plot file from gnu file and data
gnuplot "${gnuplot_file}"
if [ $? -ne 0 ]; then
	warn "Plot failed"
	exit 1
else
	log "Plot Succeeded"
	exit 0
fi
