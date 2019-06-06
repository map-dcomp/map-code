set title "Region C DCOP Plan(s) with Regional Load"
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
set datafile separator ","
set output "3c-dcop-timeline-C.png"
set ylabel "Load (# containers)"
set xlabel "Time (ms)"
set yrange  [0:25] 
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
set label "Region Plan:\n\\{\\}" at 685000,22 center font 'Verdana,7'

plot 20 dashtype '.-_' lw 3 lc rgb 'black' title "Region C Capacity", \
'regions-TASK_CONTAINERS.csv' using 1:10 smooth bezier with points pt 2 pointsize .5 title 'Regional Load'


