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

if [ $# -ne 2 ]; then
	echo "$0 <analysis> <results>"
	exit 1
fi

if [[ $1 =~ \..* ]]; then
	echo "input file: $1 name with dot"
	exit 1
fi

if [[ $2 =~ \..* ]]; then
	echo "output file: $2 name with dot"
	exit 1
fi

if [[ $1 =~ .*/ ]]; then
	echo "input file: $1 name with /"
	exit 1
fi

if [[ $2 =~ .*/ ]]; then
	echo "output file: $2 name with /"
	exit 1
fi

out="./$2/"
demand="${out}1-demand/"
load="${out}2-load/"
dcop="${out}3-dcop/"
rlg="${out}4-rlg/"
score_dns="${out}5-score_dns/"

in="./$1/"
inplans="${in}dcop_plan_updates/"
inrlgplans="${in}rlg_plan_updates/"

if [ ! -d $demand -o ! -d $load -o ! -d $dcop -o ! -d $score_dns ]; then
	echo "could not locate results folders, e.g., $load"
	exit 1
fi

if [ ! -d $inplans ]; then
	echo "could not locate plot input folder: $inplans"
	exit 1
fi 

if [ ! -d $inrlgplans ]; then
	echo "could not locate rlg plot input folder: $inrlgplans"
	exit 1
fi 

cp ${in}clientPool[A-Z]-TASK_CONTAINERS* ${demand}
if [ $? -ne 0 ]; then
	echo "failed to copy clientPools to demand"
fi

cp ${in}nodes-* ${load}
if [ $? -ne 0 ]; then
	echo "failed to copy node load"
fi

cp ${in}regions-* ${load}
if [ $? -ne 0 ]; then
	echo "failed to copy region load"
fi

cp ${in}regions-* ${dcop}
if [ $? -ne 0 ]; then
	echo "failed to copy region load"
fi

cp ${inplans}region_[A-Z]-dcop_plan_change_times.csv ${dcop}
if [ $? -ne 0 ]; then
	echo "failed to copy plan change times"
fi

cp ${inplans}dcop-*-gnu.txt ${dcop}
if [ $? -ne 0 ]; then
	echo "failed to copy plan windows "
fi

cp ${inrlgplans}service_AppCoordinates* ${rlg}
if [ $? -ne 0 ]; then
	echo "failed to copy rlg plans"
fi

cp ${in}clientPool* ${score_dns}
if [ $? -ne 0 ]; then
	echo "failed to copy clientPools"
fi

echo "fin."

exit 0
