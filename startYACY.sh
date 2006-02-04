#!/bin/sh
if [ $UID -eq 0 ]
then
	echo
	echo "For security reasons, you should not run this script as root!"
	echo
	exit 2
elif ! [-x "`which java`"]
	echo "The java command is not executable."
	echo "Either you have not installed java or it is not in your $PATH"
	echo "Has this script been invoked by CRON? Then use the -c option."
	exit 2
	
	#-c to be imlemented.
	#Possible locations for setting of PATH
	
	#sh, ksh, bash, zsh
	#. ~/.profile
	#bash
	#. ~/.bash_profile
	#csh, tcsh
	#. ~/.login
	#sh, ksh, bash, zsh
	#. /etc/profile
	#csh, tcsh
	#. /etc/csh.login
	
elif [-x "`which java`"]
	#startup YaCy
	cd `dirname $0`
	
	#get javastart args
	java_args=""
	if [ -f DATA/SETTINGS/httpProxy.conf ]
	then
		for i in $(grep javastart DATA/SETTINGS/httpProxy.conf);
		do  i="${i#javastart_*=}";java_args=-$i" "$java_args;
		done
	fi
	
	# generating the proper classpath
	CLASSPATH=""
	for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done	
	for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
	
	if [ x$1 == x-d ] #debug
	then
		java $java_args -classpath classes:$CLASSPATH yacy
		exit 0
	elif [ x$1 == x-l ] #logging
	then
		nohup java $java_args -classpath classes:htroot:$CLASSPATH yacy >> yacy.log &
	else
		nohup java $java_args -classpath classes:htroot:$CLASSPATH yacy > /dev/null &
#		nohup java -Xms160m -Xmx160m -classpath classes:htroot:$CLASSPATH yacy > /dev/null &
	fi
	echo "YaCy started as daemon process. View it's activity in DATA/LOG/yacy00.log"
	echo "To stop YaCy, please execute stopYACY.sh and wait some seconds"
	echo "To administrate YaCy, start your web browser and open http://localhost:8080"
fi
