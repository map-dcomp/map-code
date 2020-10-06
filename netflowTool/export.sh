#!/bin/bash
mkdir netflowTool
mkdir netflowTool/js
mkdir netflowTool/css
mkdir netflowTool/webdata
cp css/* netflowTool/css/
cp js/* netflowTool/js/
cp webdata/* netflowTool/webdata/
cp server.py netflowTool/server.py
cp index.html netflowTool/index.html
zip -r netflowTool.zip netflowTool
tar -cvzf netflowTool.tar.gz netflowTool
rm -R netflowTool
