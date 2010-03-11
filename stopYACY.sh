#!/bin/sh
cd `dirname $0`

if [ -x `which wget` ]
then
	bin/apicall.sh "Steering.html?shutdown=true"

echo "Please wait until the YaCy daemon process terminates [wget]"
echo "You can monitor this with 'tail -f DATA/LOG/yacy00.log' and 'fuser log/yacy00.log'"

elif [ -x `which curl` ]
then
	bin/apicall.sh "Steering.html?shutdown=true"

echo "Please wait until the YaCy daemon process terminates [curl]"
echo "You can monitor this with 'tail -f DATA/LOG/yacy00.log' and 'fuser log/yacy00.log'"

elif [ -x `which java` ]
then
	# generating the proper classpath
	CLASSPATH=""
	for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done	
	for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done

	java -classpath classes:htroot:$CLASSPATH net.yacy.yacy -shutdown

	echo "Please wait until the YaCy daemon process terminates [java]"
	echo "You can monitor this with 'tail -f DATA/LOG/yacy00.log' and 'fuser log/yacy00.log'"

else
	port=`cat DATA/SETTINGS/yacy.conf |grep "^port="|sed "s/.*=//"`
	echo "Neither wget nor java could be found or are not executable."
	echo "Visit http://localhost:$port/Steering.html?shutdown=true to stop YaCy or (in emergency case) use ./killYACY.sh"
fi
