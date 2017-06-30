#!/bin/bash

:

rm /tmp/*.pid

/etc/init.d/nginx start
service sshd start

echo "starting elasticsearch"
runuser -l  elastic -c '/usr/local/elasticsearch/bin/elasticsearch -d'

echo "starting kafka"
cd $KAFKA_HOME
nohup bin/zookeeper-server-start.sh config/zookeeper.properties > zookeeper.log 2>&1 &
JMX_PORT=10101 nohup bin/kafka-server-start.sh config/server.properties > kafka.log 2>&1 &

echo "starting kibana"
cd $KIBANA_HOME
nohup bin/kibana > kibana.log 2>&1 &


echo "logisland is installed in /usr/local/logisland  enjoy!"
cd $LOGISLAND_HOME


CMD=${1:-"exit 0"}
if [[ "$CMD" == "-d" ]];
then
	service sshd stop
	/usr/sbin/sshd -D -d
else
	/bin/bash -c "$*"
fi