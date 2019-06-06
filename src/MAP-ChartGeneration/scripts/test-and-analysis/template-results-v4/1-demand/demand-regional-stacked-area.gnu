set title "Per-Region Client Demand for 20 minutes"
set datafile separator ","
set terminal png size 800,600 enhanced truecolor font 'Arial,11'
#set terminal png size 533,400 enhanced truecolor font 'Verdana,11'
set output "1-demand-regional-stacked-area.png"
set ylabel "Container Load (count)"
set xlabel "Time (ms)"
set xrange [0:1400000]
set yrange [0:10]
set pointsize 0.8
set border 11
set xtics rotate by -45 offset -1,-.5
set xtics out
set tics front
set key right top box opaque
#unset xtics
#set xtics auto
#set ytics auto
set boxwidth 1
set grid y

colorSet(name, rgb) = sprintf("color_%s = %d", name, rgb)
colorGet(name) = value(sprintf("color_%s", name))

eval colorSet("magenta", 0xff00ff)
eval colorSet("red",     0xff0000)
eval colorSet("green",   0x00ff00)
eval colorSet("blue",   0x0000ff)
eval colorSet("black",   0x000000)
eval colorSet("dark_yellow",   0xc8c800)

colors = "red blue green black magenta"

plot \
  for [i=2:2:1] \
    "clientPoolA-TASK_CONTAINERS.csv" using 1:(sum [col=i:2] column(col)) \
      title columnheader(i) \
      with filledcurves x1 fillcolor rgb colorGet(word(colors,i)) fs solid 0.3, \
  for [i=2:2:1] \
    "clientPoolA-TASK_CONTAINERS.csv" using 1:(sum [col=i:2] column(col)) \
      notitle \
      with lines lc rgb "#000000" lt -1 lw .5
