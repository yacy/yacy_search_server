#!/bin/sh
if [ $UID -eq 0 ]
then
	echo
	echo "For security reasons, you should not run this as root!"
	echo
else
	cd `dirname $0`
	
	# generating the proper classpath
	CLASSPATH=""
	for N in `ls -1 lib/*.jar`; do CLASSPATH="$CLASSPATH$N:"; done	
	for N in `ls -1 libx/*.jar`; do CLASSPATH="$CLASSPATH$N:"; done
	
	if [ x$1 == x-d ] #debug
	then
		java -classpath classes:$CLASSPATH yacy
		exit 0
	elif [ x$1 == x-l ] #logging
	then
		nohup java -classpath classes:htroot:$CLASSPATH yacy >> yacy.log &
	else
		nohup java -classpath classes:htroot:$CLASSPATH yacy > /dev/null &
	fi
	echo "YaCy started as daemon process. View it's activity in log/yacy00.log"
	echo "To stop YaCy, please execute stopYACY.sh and wait some seconds"
	echo "To administrate YaCy, start your web browser and open http://localhost:8080"
fi
