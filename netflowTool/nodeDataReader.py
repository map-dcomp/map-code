#!/usr/bin/env python3.6

import json
import os
import datetime
import csv

with open("config.json", "r") as fin:
                config = json.load(fin)
path = config["nodeCpuCSVPath"]
with open(path, newline='') as csvfile:
	csvData = csv.reader(csvfile, delimiter=',', quotechar='|')
	data = []
	nodes = []
	output = []
	for row in csvData:
		data.append(row)

	i = 0
	while(i < len(data[0])):
		nodes.append(data[0][i].lower())
		i = i+1
	i = 1
	while(i < len(data)):
		j = 1
		dic = {"time": int(int(data[i][0]))}
		while(j < len(data[i])):
			if(data[i][j] == '?'):
				dic[nodes[j]] = 0
			else:
				dic[nodes[j]] = data[i][j]
			j = j+1
		i = i+1
		output.append(dic)
		
	fout = open("nodeData.json", "w")
	json.dump(output,fout)
	fout.close()	
	
