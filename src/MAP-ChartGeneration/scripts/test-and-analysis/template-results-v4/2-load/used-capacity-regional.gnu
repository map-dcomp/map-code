set title "Regional Utilization Plot - Percent Load across Active Containers"
set terminal png size 800,600 enhanced truecolor font 'Verdana,12'
set output "2b-load-regional-used-capacity.png"
set datafile separator ","
set ylabel "% Load against allocated containers"
set xlabel "Time (ms)"
set yrange  [0:1] 
set xrange  [0:1500000]

unset xtics
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
set boxwidth 1
set grid y

set key right top

#1   ,2,        ,3,     ,4,5         ,6      ,7,8         ,9      ,10,11       ,12     ,13
#time,A capacity,A count,A,B capacity,B count,B,C capacity,C count,C,X capacity,X count,X


f(x,y)=(y/x)

plot 'regions-TASK_CONTAINERS.csv' using 1:(f($11,$13)) smooth bezier with points pt 2 pointsize .5 title "X", \
'' using 1:(f($8,$10)) smooth bezier with points pt 3 pointsize .5 title "C", \
'' using 1:(f($5,$7)) smooth bezier with points pt 4 pointsize .5 title "B", \
'' using 1:(f($2,$4)) smooth bezier with points pt 5 pointsize .5 title "A"


#'' using 1:7 smooth bezier with points pt 3 pointsize .5, \
#'' using 1:10 smooth bezier with points pt 4 pointsize .5, \
#'' using 1:13 smooth bezier with points pt 5 pointsize .5, \
#'' using 1:17 smooth bezier with points pt 6 pointsize .5

