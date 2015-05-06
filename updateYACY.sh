#!/usr/bin/env sh
PROGRAMDIR="`dirname $0`/"
DATADIR="${PROGRAMDIR}DATA/"

if [ -x `which wget` ]
then
	${PROGRAMDIR}bin/apicall.sh "ConfigUpdate_p.html?autoUpdate="

elif [ -x `which java` ]
then
	# generating the proper classpath
	CLASSPATH=""
	for N in ${PROGRAMDIR}lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done	
	for N in ${PROGRAMDIR}libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done

	java -Duser.dir=$PROGRAMDIR -classpath classes:htroot:$CLASSPATH yacy -update

else
	port=`cat ${DATADIR}SETTINGS/yacy.conf |grep "^port="|sed "s/.*=//"`
	echo "Neither wget nor java could be found or are not executable."
fi
