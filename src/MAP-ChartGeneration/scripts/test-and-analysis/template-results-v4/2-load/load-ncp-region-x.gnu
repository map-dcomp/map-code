set title "NCP Load Plot Region X - Container Count"
#set terminal png size 800,600 enhanced truecolor font 'Verdana,12'
set terminal eps enhanced truecolor font 'Arial,11' dashed
set datafile separator ","
set ylabel "Load (# containers)"
set xlabel "Time (ms)"
set yrange  [0:10] 
set xrange  [0:1500000]
set key autotitle columnhead
#set xrange [1463519639000:1463519862000]
#set style line 1 lt 1 lw 3 pt 2 linecolor rgb "red"
#set style point 1 lt 1 lw 3 pt 2 linecolor rgb "red"
#set style line 2 lt 1 lw 3 pt 3 linecolor rgb "blue"

unset xtics
#set format x "%.0f"
set xtics rotate by -45 offset -1,-.5
set xtics auto
set ytics auto
#set xrange [0:2]
#set yrange[0:150000]
#set log y
#set yrange[0.1:100]
#set ytics ("0" 0.1, "1" 1, "10" 10, "100" 100, "1000" 1000)
#set yrange [200:400]
set boxwidth 1
set grid y

#set logscale y

#set key left bottom
set key right top

#set autoscale

set key autotitle columnheader
set style data lines


#plot for [i=2:6] 'nodes-TASK_CONTAINERS.csv' using 1:i dt i lt i

if (system ("awk -F ',' '{sum += $17 + $18 + $19 + $20 + $21} END {print sum}' nodes-TASK_CONTAINERS.csv") > 0) { \
set output "2c-load-nodes-x-TASK_CONTAINERS.eps"; \
plot for [i=17:21] 'nodes-TASK_CONTAINERS.csv' using 1:i dt i lt i \
} else { \
system ("echo no") \
}



