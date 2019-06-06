set title "Region X DCOP Plan(s) with Regional Load"
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
set datafile separator ","
set output "3-dcop-timeline-X.png"
set ylabel "Load (# containers)"
set xlabel "Time (ms)"
set yrange  [0:15] 
set xrange  [0:1500000]
set key autotitle columnhead

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y

set key right top

set obj rect fc rgb 'red' fs solid 0.1 from 0, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{\\}" at 55000,10 center font 'Verdana,8'

set obj rect fc rgb 'blue' fs solid 0.1 from 110001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.000,X:1.000\\}" at 185001,12 center font 'Verdana,8'

set obj rect fc rgb 'red' fs solid 0.1 from 260001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 335001,10 center font 'Verdana,8'

set obj rect fc rgb 'blue' fs solid 0.1 from 410001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 485001,12 center font 'Verdana,8'

set obj rect fc rgb 'red' fs solid 0.1 from 560001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 635001,10 center font 'Verdana,8'

set obj rect fc rgb 'blue' fs solid 0.1 from 710001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 785001,12 center font 'Verdana,8'

set obj rect fc rgb 'red' fs solid 0.1 from 860001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.172,X:0.828\\}" at 935001,10 center font 'Verdana,8'

set obj rect fc rgb 'blue' fs solid 0.1 from 1010001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 1085001,12 center font 'Verdana,8'

set obj rect fc rgb 'red' fs solid 0.1 from 1160001, graph 0 to 1370001, graph 1
set label "Region Plan:\n\\{B:0.193,X:0.807\\}" at 1265001,10 center font 'Verdana,8'

plot 8 dashtype '.-_' lw 3 lc rgb 'black' title "Data Center Capacity", \
'regions-TASK_CONTAINERS.csv' using 1:13 smooth bezier with points pt 2 pointsize .5 title 'Regional Load'


