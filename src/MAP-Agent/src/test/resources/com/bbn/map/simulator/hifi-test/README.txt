This scenario has been tested in Emulab using the following commands. This is without the MAP agent. 
Instead iftop is run directly using the script start-iftop.sh. This script is put into 
the directory flow-test in the user's home directory.

The commands to gather the data follow. Note that each ssh connection needs to be in a separate terminal.

## setup
ssh e.jps-test.a3.emulab.net
nc -v -l 4200 > /dev/null

ssh client.jps-test.a3.emulab.net
dd if=/dev/urandom | nc e 4200

## A
ssh a.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.10\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee a.input

   1 10.3.0.5:4200                            =>      990Kb     1.02Mb     0.99Mb     17.5MB
     10.10.0.10:41206                         <=     62.7Mb     67.5Mb     67.5Mb     1.14GB


ssh a.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.0\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee a.output

   1 10.10.0.10:41206                         =>     69.1Mb     67.2Mb     67.6Mb     1.01GB
     10.3.0.5:4200                            <=     1.03Mb     1.00Mb     1.00Mb     15.4MB

### report
[
  {
    neighbor: client,
    flow: E -> client server: E,
    TX=1,
    RX=65
  },
  {
    neighbor: B,
    flow: client -> E server: E,
    TX=65,
    RX=1
  }
]

## B
ssh b.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.0\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee b.input

   1 10.3.0.5:4200                            =>     0.99Mb     1.01Mb     1.00Mb     14.6MB
     10.10.0.10:41206                         <=     64.7Mb     66.9Mb     67.7Mb      977MB

ssh b.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.1\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee b.output

   1 10.10.0.10:41206                         =>     65.2Mb     67.5Mb     67.6Mb      844MB
     10.3.0.5:4200                            <=     1.01Mb     1.00Mb     1.00Mb     12.6MB

### report
[
  {
    neighbor: A
    flow: E -> client server: E,
    TX=1,
    RX=65
  },
  {
    neighbor: C,
    flow: client -> E server: E,
    TX=65,
    RX=1
  }
]


## C
Neighbor B
ssh c.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.1\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee c.input
   1 10.3.0.5:4200                            =>      988Kb     1.01Mb     1.00Mb     10.5MB
     10.10.0.10:41206                         <=     69.1Mb     68.2Mb     67.7Mb      705MB

Neighbor D
ssh c.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.2\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee c.output
   1 10.10.0.10:41206                         =>     66.3Mb     67.7Mb     67.5Mb      588MB
     10.3.0.5:4200                            <=     1.00Mb      990Kb     1.00Mb     8.73MB

### report
[
  {
    neighbor: B
    flow: E -> client server: E,
    TX=1,
    RX=65
  },
  {
    neighbor: D,
    flow: client -> E server: E,
    TX=65,
    RX=1
  }
]

## D
ssh d.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.2\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee d.input
   1 10.3.0.5:4200                            =>      986Kb      989Kb     1.00Mb     6.70MB
     10.10.0.10:41206                         <=     67.0Mb     67.6Mb     67.4Mb      451MB

ssh d.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.3\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee d.output
   1 10.10.0.10:41206                         =>     70.4Mb     67.8Mb     67.6Mb      383MB
     10.3.0.5:4200                            <=     1.06Mb      992Kb     1.00Mb     5.69MB

### report
[
  {
    neighbor: C
    flow: E -> client server: E,
    TX=1,
    RX=65
  },
  {
    neighbor: E,
    flow: client -> E server: E,
    TX=65,
    RX=1
  }
]

## E
ssh e.jps-test.a3.emulab.net
cd flow-test
sudo ./start-iftop.sh $(ip addr show | grep -B 2 '10\.3\.0' | head -1 | awk '{print $2}' | awk -F: '{print $1}') 2>&1 | tee e.input

   1 10.3.0.5:4200                            =>     1.00Mb      993Kb     0.99Mb     3.70MB
     10.10.0.10:41206                         <=     69.2Mb     67.8Mb     67.1Mb      252MB

### report
[
  {
    neighbor: D,
    flow: E -> client server: E,
    TX=1,
    RX=65
  }
]



#TODO once ticket:232 is fixed, check the container values and adjust the test to connect to the container.
