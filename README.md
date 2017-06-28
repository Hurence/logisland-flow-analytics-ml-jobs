# logisland-flow-analytics-ml-jobs




# THE MCFP
The Malware Capture Facility Project is an effort from the Czech Technical University ATG Group for capturing, analyzing and publishing real and long-lived malware traffic.

The goals of the project are:

- To execute real malware for long periods of time.
- To analyze the malware traffic manually and automatically.
- To assign ground-truth labels to the traffic, including several botnet phases, attacks, normal and background.
- To publish these dataset to the community to help develop better detection methods.

## TOPOLOGY
The topology used in the project was designed to be as simple as 
possible. It uses VirtualBox to execute Windows virtual machines on 
Linux Hosts. The only two restrictions applied to the traffic are a 
bandwidth control to prevent DDoS and a redirection of all the SMTP 
traffic to prevent SPAM sending. 
More details can be found on the Topology page.

## PUBLISHING
The complete dataset is published and can be downloaded from the Dataset menu. The published files include:

- The pcap files of the malware traffic.
- The argus binary flow file.
- The text argus flow file.
- The text web logs
- A text file with the explanation of the experiment
- Several related files, such as the histogram of labels.

## COLLABORATION
If you find this project or the dataset useful please consider 
collaborating with it. Among the things that need more attention are 
a better labeling, more screenshots of the traffic and information about
 which malware it really is. Feel free to send an email to
  (sebastian.garcia@agents.fel.cvut.cz)[mailto:sebastian.garcia@agents.fel.cvut.cz].
  
## BOTNET ANALYSIS
This dataset is directly feeding the CTU efforts for modelling and 
detecting botnets behavior on the network. As such, the Botnet Analysis
 blog page includes some analysis of their behaviors. 




## THE CTU-13 DATASET. A LABELED DATASET WITH BOTNET, NORMAL AND BACKGROUND TRAFFIC.

The CTU-13 is a dataset of botnet traffic that was captured in the CTU 
University, Czech Republic, in 2011. The goal of the dataset was to 
have a large capture of real botnet traffic mixed with normal traffic 
and background traffic. The CTU-13 dataset consists in thirteen 
captures (called scenarios) of different botnet samples. 
On each scenario we executed a specific malware, which used several 
protocols and performed different actions. 

Table 2 shows the characteristics of the botnet scenarios.

!(Table 2. Characteristics of botnet scenarios)[docs/145022_orig.jpg]

Each scenario was captured in a pcap file that contains all the packets 
of the three types of traffic. These pcap files were processed to 
obtain other type of information, such as NetFlows, WebLogs, etc. 
The first analysis of the CTU-13 dataset, that was described and 
published in the paper "An empirical comparison of botnet detection 
methods" (see Citation below) used unidirectional NetFlows to represent 
the traffic and to assign the labels. These unidirectional NetFlows 
should not be used because they were outperformed by our second 
analysis of the dataset, which used bidirectional NetFlows. 
The bidirectional NetFlows have several advantages over the directional 
ones. First, they solve the issue of differentiating between the client 
and the server, second they include more information and third they 
include much more detailed labels. The second analysis of the dataset 
with the bidirectional NetFlows is the one published here. 

The relationship between the duration of the scenario, the number of 
packets, the number of NetFlows and the size of the pcap file is shown 
in Table 3. This Table also shows the malware used to create the 
capture, and the number of infected computers on each scenario.

!(Table 3. Amount of data on each botnet scenario
)[docs/6977136.jpg]

The distinctive characteristic of the CTU-13 dataset is that we 
manually analyzed and label each scenario. The labeling process was 
done inside the NetFlows files. Table 4 shows the relationship between 
the number of labels for the Background, Botnet, C&C Channels and 
Normal on each scenario. 


!(Table 4. Distribution of labels in the NetFlows for each scenario in the dataset.
)[docs/7883961.jpg]


## CTU-Malware-Capture-Botnet-42 or Scenario 1 in the CTU-13 dataset.

### Description

- Probable Name: Neris
- MD5: bf08e6b02e00d2bc6dd493e93e69872f
- SHA1: 5c2ba68d78471ff02adcdab12b2f82db8efe2104
- SHA256: 527da5fd4e501765cdd1bccb2f7c5ac76c0b22dfaf7c24e914df4e1cb8029d71
- Password of zip file: infected
- Duration: 6.15 hours
- Complete Pcap size: 52GB
- Botnet Pcap size: 56MB
- NetFlow size: 1GB

- VirusTotal
- HybridAnalysis

    
    
### Get the files

   
    wget https://mcfp.felk.cvut.cz/publicDatasets/CTU-Malware-Capture-Botnet-42/detailed-bidirectional-flow-labels/capture20110810.binetflow



### IP Addresses

- Infected hosts
    - 147.32.84.165: Windows XP (English version) Name: SARUMAN (Label: Botnet) (amount of bidirectional flows: 40961)
- Normal hosts:
    - 147.32.84.170 (amount of bidirectional flows: 18438, Label: Normal-V42-Stribrek)
    - 147.32.84.164 (amount of bidirectional flows: 7654, Label: Normal-V42-Grill)
    - 147.32.84.134 (amount of bidirectional flows: 3808, Label: Normal-V42-Jist)
    - 147.32.87.36 (amount of bidirectional flows: 269, Label: CVUT-WebServer. This normal host is not so reliable since is a webserver)
    - 147.32.80.9 (amount of bidirectional flows: 83, Label: CVUT-DNS-Server. This normal host is not so reliable since is a dns server)
    - 147.32.87.11 (amount of bidirectional flows: 6, Label: MatLab-Server. This normal host is not so reliable since is a matlab server)

### Important Label note

Please note that the labels of the flows generated by the malware start with "From-Botnet". The labels "To-Botnet" are flows sent to the botnet by unknown computers, so they should not be considered malicious perse. Also for the normal computers, the counts are for the labels "From-Normal". The labels "To-Normal" are flows sent to the botnet by unknown computers, so they should not be considered malicious perse.

### Timeline

Wed ago 10 15:58:00 CEST 2011

Today we capture the neris bot along with the packets of the whole CTU department. We used an XP virtualbox machine with the 147.32.84.165 public ip address. The first hour of capture was only background and latter we run the malware until 5 minutes before ending. We limited the bandwith of the experiment to 20kbps in the output of the bot.

### Traffic Analysis

The bot sent spam, connected to an HTTP CC, and use HTTP to do some ClickFraud.


send some records to

    cat /data/tmp/CTU-13-Dataset/11/capture20110818-2.binetflow | kafkacat -b sd-84190:6667,sd-84191:6667,sd-84192:6667,sd-84186:6667 -t binetflow_rawkafkacat -b sd-84190:6667,sd-84191:6667,sd-84192:6667,sd-84186:6667 -t binetflow_raw



create the index into Elasticsearch

    curl -XPUT http://sd-84186.dedibox.fr:9200/ctu-13 -d @logisland-framework/logisland-resources/src/main/resources/conf/ctu-13-mapping.json 
