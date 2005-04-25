#!/bin/sh
if [ $UID -eq 0 ]
then
	echo
	echo "For security reasons, you should not run this as root!"
	echo
else
	cd `dirname $0`
	if [ x$1 != x-d ]
	then
		nohup java -classpath classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar:libx/PDFBox-0.7.1.jar:libx/log4j-1.2.9.jar:libx/tm-extractors-0.4.jar yacy >> yacy.log &
		echo "YaCy started as daemon process. View it's activity in yacy.log"
		echo "To stop YaCy, please execute stopYACY.sh and wait some seconds"
		echo "To administrate YaCy, start your web browser and open http://localhost:8080"
	else
		java -classpath classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar yacy
	fi
fi
