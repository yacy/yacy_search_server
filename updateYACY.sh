#!/bin/sh
cd `dirname $0`

if [ -x `which wget` ]
then
	bin/apicall.sh "ConfigUpdate_p.html?autoUpdate="

elif [ -x `which java` ]
then
	# generating the proper classpath
	CLASSPATH=""
	for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done	
	for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done

	java -classpath classes:htroot:$CLASSPATH yacy -update

else
	port=`cat DATA/SETTINGS/yacy.conf |grep "^port="|sed "s/.*=//"`
	echo "Neither wget nor java could be found or are not executable."
fi
