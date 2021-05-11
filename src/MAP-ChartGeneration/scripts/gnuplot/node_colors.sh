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
node_name=$1
color_component=$2	# h,s,v

node=$(echo ${node_name} | sed -e 's/node\|server\|_c.*//g')
region=$(echo ${node} | sed -e 's/[0-9]*//g')
number=$(echo ${node} | sed -e 's/[^0-9]*//g' | sed 's/^0*//g')

#echo [${region}]
#echo [${number}]


hue=""

case "${region}" in
	A)
		hue="10"
		;;
	B)
		hue="20"
		;;
	C)
		hue="30"
		;;
	D)
		hue="30"
		;;
	E)
		hue="40"
		;;
	F)
		hue="50"
		;;
	X)
		hue="0"
		;;
	*)
		hue="0"
		;;
esac


case "${region}" in
	X)
		sat="$((0))"
		;;
	*)
		sat="$((100))"
		;;
esac


if [ -z "${number}" ] ; then
	var=70
else
	case "${region}" in
		X)
			var="$((number*(80)/10+0))"
			;;
		*)
			var="$((number*(100-70)/10+70-35))"
			;;
	esac
fi





result=""

case "${color_component}" in
	"h")
		result=${hue}
		;;
	"s")
		result=${sat}
		;;
	"v")
		result=${var}
		;;

	*)
		result="0"
		;;
esac


echo ${result}












