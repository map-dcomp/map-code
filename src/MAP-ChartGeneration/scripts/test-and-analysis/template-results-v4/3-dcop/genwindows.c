/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/*
 *
 * timestamp
29
110029
260029
410029

*/

void processrow(int index, int curr, int last, int max, int regioncap, FILE * outfile) {
	char * color = NULL;
	int mod = 0;
	int mid = last+((curr-last)/2);
	
	printf("process%d: [%d, %d, %d] cap: %d\n", index, last, curr, max, regioncap);
	
	if (index % 2 == 0) {
		color = "red";
		mod = 2;
	} else {
		color = "blue";
		mod = 4;
	}

	/*
	set obj rect fc rgb 'red' fs solid 0.1 from 0, graph 0 to 110000, graph 1
	set label "Region Plan:\n\\{Empty Set\\}" at 55000,9 center font 'Verdana,7'
	*/
	
	fprintf(outfile, "set obj rect fc rgb '%s' fs solid 0.1 from %d, graph %d to %d, graph 1\n",
			color, 
			last,
			0,
			max
	       );
	
	fprintf(outfile, "set label \"Region Plan:\\n\\\\{X:,A:,B:,C:\\\\}\" at %d,%d center font 'Verdana,7'\n\n", 
			mid,
			(regioncap+mod)
	       );

}

int main (int argc, char ** argv) { 
	int maxbound = 1500000;
	int regioncap = 20;
	FILE * in = NULL;
	
	int buflen = 256;
	char buf[buflen];
	int linect = 0;
	int lastbound = 0;
	int currbound = 0;

	FILE * out = NULL;
	char outfile[256];
	int ret = 1;

	if (argc < 3) {
		printf("%s <in-file> <experiment-time> [region-capacity]\n", argv[0]);
		goto cleanup;
	}
	
	in = fopen(argv[1], "r");
	if (in == NULL) {
		printf("could not open input file: %s\n", argv[1]);
		goto cleanup;
	}
	
	maxbound = atoi(argv[2]);
	snprintf(outfile, 256, "%s-win.dat", argv[1]);

	out = fopen(outfile, "w+");
	if (out == NULL) {
		printf("could not open output file: %s\n", outfile);
		goto cleanup;
	}

	if (argc > 3) {
		regioncap = atoi(argv[3]);
	}

	while (fgets(buf, buflen, in) != NULL) {
		if (linect > 0) {
			currbound = atoi(strtok(buf, "\n"));
		}
		if (linect > 1) {
			processrow(linect, currbound, lastbound, maxbound, regioncap, out);	
		}

		lastbound = currbound;	

		linect++;
	}

	if (linect > 2) {
		processrow(linect, maxbound, lastbound, maxbound, regioncap, out);	
	}

	if (linect <= 2) {
		processrow(0,maxbound,0,maxbound,regioncap,out);
	}

	ret = 0;
cleanup:
	if (in != NULL) 
		fclose(in);
	if (out != NULL) 
		fclose(out);
	return ret;
}
