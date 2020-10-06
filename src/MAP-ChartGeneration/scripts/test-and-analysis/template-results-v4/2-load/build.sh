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

doopen=0

rm -f *.png *.eps

for filename in ./*.gnu; do
	gnuplot "${filename}"
	if [ $? -ne 0 ]; then
		echo "failed to process: ${filename}"
	fi
done


files="2c-load-nodes-TASK_CONTAINERS 2c-load-nodes-a-TASK_CONTAINERS 2c-load-nodes-b-TASK_CONTAINERS 2c-load-nodes-c-TASK_CONTAINERS 2c-load-nodes-x-TASK_CONTAINERS"
files="2c-load-nodes-a-TASK_CONTAINERS 2c-load-nodes-b-TASK_CONTAINERS 2c-load-nodes-c-TASK_CONTAINERS 2c-load-nodes-x-TASK_CONTAINERS"

for f in ${files}
do
if [ -f "./${f}.eps" ]; then
echo "found: ./$f"
convert -colorspace sRGB -density 600 ./${f}.eps -background white -flatten -resize 800x600 -units pixelsperinch -density 224.993 ./${f}.png
fi
done


cp *.png ..

if [ ${doopen} -eq 1 ]; then
	eog *.png &
fi

