set title "Client Success/Failure: Lo-Fi Load Scoring for Region B"
set datafile separator ","
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
#set terminal png size 533,400 enhanced truecolor font 'Arial,11'
set output "5-b-score-clientsB.png"
set ylabel "Request Result (running count)"
set xlabel "Time (ms)"
set xrange [0:1400000]
set yrange [0:800]
set pointsize 0.8
set border 11
set xtics rotate by -45 offset -1,-.5
set xtics out
set tics front
set key right top
set grid y

plot "clientPoolB-1.csv" using 1:2 title 'B Success' with lines lt 1 lc rgb 'blue', \
     "clientPoolB-1.csv" using 1:3 title 'B Failed' with lines lt 1 lc rgb 'green'
