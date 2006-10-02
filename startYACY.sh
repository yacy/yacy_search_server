#!/bin/sh
if [ `id -u` -eq 0 ]
then
	echo
	echo "For security reasons you should not run this script as root!"
	echo
	exit 1
elif [ ! -x "`which java`" ]
then
	echo "The java command is not executable."
	echo "Either you have not installed java or it is not in your PATH"
	#echo "Has this script been invoked by CRON? Then use the -c option."
	exit 1
fi
	
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

#startup YaCy
cd `dirname $0`

options=$(getopt -n YaCy -o d,l,p -- $@)
if [ $? -ne 0 ];then
	exit 1;
fi

isparameter=0; #options or parameter part of getopts?
parameter="" #parameters will be collected here

LOGGING=0
DEBUG=0
PRINTONLY=0
for option in $options;do
	if [ $isparameter -ne 1 ];then #option
		if [ x$option = "x-l" ];then
			LOGGING=1
			if [ $DEBUG -eq 1 ];then
				echo "can not combine -l and -d"
				exit 1;
			fi
		elif [ x$option = "x-d" ];then
			DEBUG=1
			if [ $LOGGING -eq 1 ];then
				echo "can not combine -l and -d"
				exit 1;
			fi
		elif [ x$option = "x-p" ];then
			PRINTONLY=1
		fi #which option 
	else #parameter
		if [ x$option = "--" ];then #option / parameter seperator
			isparameter=1;
			continue
		else
			parameter="$parameter $option"
		fi
	fi #parameter or option?
done

#echo $options;exit 0 #debug for getopts

#get javastart args
java_args=""
if [ -f DATA/SETTINGS/httpProxy.conf ]
then
	for i in $(grep javastart DATA/SETTINGS/httpProxy.conf);do 
		i="${i#javastart_*=}";
		JAVA_ARGS="-$i $JAVA_ARGS";
	done
fi

# generating the proper classpath
CLASSPATH=""
#prefix=$(dirname $0);
#if [ x$prefix = "x." ];then
#	prefix="";
#else
#	prefix="$prefix/"
#fi
for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done	
for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
CLASSPATH="classes:.:$CLASSPATH"


cmdline="";
if [ $DEBUG -eq 1 ] #debug
then
	if [ $PRINTONLY -eq 1 ];then
		echo java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy
	else
		java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy
	fi
elif [ $LOGGING -eq 1 ];then #logging
	if [ $PRINTONLY -eq 1 ];then
		echo "java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy >> yacy.log"
	else
		nohup java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy >> yacy.log &
	fi
else
	if [ $PRINTONLY -eq 1 ];then
		echo "java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy > /dev/null"
	else
		nohup java $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy > /dev/null &
		#nohup java -Xms160m -Xmx160m -classpath $CLASSPATH yacy > /dev/null &
		echo "****************** YaCy Web Crawler/Indexer & Search Engine *******************"
		echo "**** (C) by Michael Peter Christen, usage granted under the GPL Version 2  ****"
		echo "**** USE AT YOUR OWN RISK! Project home and releases: http://yacy.net/yacy ****"
		echo "**  LOG of       YaCy: DATA/LOG/yacy00.log (and yacy<xx>.log)                **"
		echo "**  STOP         YaCy: execute stopYACY.sh and wait some seconds             **"
		echo "**  GET HELP for YaCy: see www.yacy-websearch.net/wiki and www.yacy-forum.de **"
		echo "*******************************************************************************"
		echo " >> YaCy started as daemon process. Administration at http://localhost:8080 <<"
	fi
fi
