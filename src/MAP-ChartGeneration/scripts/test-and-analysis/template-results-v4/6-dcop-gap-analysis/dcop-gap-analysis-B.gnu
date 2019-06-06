set terminal png size 800,600 enhanced truecolor font 'Verdana,11'
#set terminal pngcairo dashed size 480,360 enhanced truecolor font 'Verdana,8'
#set datafile separator ","
# so for some reason this will not work with CSV files. space files will work fine.
set output '4b-dcop-gap-analysis-B.png'
set style fill   pattern 2 border
set style data lines
set title "Region B Event Timeline:\nDemand, Load, and Regional Plans"
set xrange [ 0 : 1500000 ]
set yrange [ 0 : 25 ]

set xlabel "Time (ms)"
set ylabel "# Containers"

set key right top box opaque
set key width -2
#set key outside
set key font "Verdana,9"

set obj rect fc rgb 'red' fs solid 0.1 from 0, graph 0 to 260000, graph 1
set label "Region Plan:\n\\{Empty Set\\}" at 130000,22 center font 'Verdana,9'

set obj rect fc rgb 'blue' fs solid 0.1 from 260000, graph 0 to 1500000, graph 1
set label "Region Plan:\n\\{WS:\nB:1.0,X:0.0,C:0.0,A:0.0\\}" at 800000,15 center font 'Verdana,9'

# to create merge.dat
# awk -F, '{ print $1,$7 }' regions-TASK_CONTAINERS.csv > merge.dat
# awk -F, '{ print $1,$2 }' clientPoolB-TASK_CONTAINERS.csv > mergeb.dat
# join merge.dat mergeb.dat > out.dat

plot 20 dashtype '.-_' lw 3 lc rgb 'black' title "Regional Capacity", 'out.dat' u 1:2:3 "%lf %lf %lf" w filledcurves title "Demand-Load Gap", '' u 1:3 lt -1 lc rgb 'red' title 'Demand Inputs within Region', '' u 1:2 lt -1 lc rgb 'blue' title 'Load Processed in Region'

