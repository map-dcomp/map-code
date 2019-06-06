set title "Client Dispatch Table Region A: Lo-Fi Sim Outputs"
set datafile separator ","
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
#set terminal png size 533,400 enhanced truecolor font 'Arial,11'
set output "5-1-tablea-dispatch-all.png"
set ylabel "Dispatch Count (running count)"
set xlabel "Time (ms)"
set xrange [0:1500000]
set yrange [0:1000]
#set pointsize 0.8
set border 11
set xtics rotate by -45 offset -1,-.5
set xtics out
set tics front
set key left top box opaque
set grid y
set logscale y

plot for [i=2:5] "clientPoolA-requests_to_regions.csv" using 1:i title "From A to ".columnhead(i) lt (i-2) lc 'red'

