set title "Client Success/Failure: Lo-Fi Load Scoring - Log Scale"
set datafile separator ","
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
#set terminal png size 533,400 enhanced truecolor font 'Arial,11'
set output "5-2-score-clients-all.png"
set ylabel "Request Event (log scale, running count)"
set xlabel "Time (ms)"
set xrange [0:1500000]
set yrange [0:1000]
#set pointsize 0.8
set border 11
set xtics rotate by -45 offset -1,-.5
set xtics out
set tics front
set key right bottom box opaque
set grid y

set logscale y

plot "clientPoolA-1.csv" using 1:2 title 'A Success' lt 2 lc rgb 'blue', \
     "clientPoolA-1.csv" using 1:3 title 'A Failed'  lt 5 lc rgb 'blue', \
     "clientPoolB-1.csv" using 1:2 title 'B Success' lt 2 lc rgb 'red', \
     "clientPoolB-1.csv" using 1:3 title 'B Failed'  lt 5 lc rgb 'red', \
     "clientPoolC-1.csv" using 1:2 title 'C Success' lt 2 lc rgb 'green', \
     "clientPoolC-1.csv" using 1:3 title 'C Failed'  lt 5 lc rgb 'green'
