#!/bin/sh
JAVA="`which java`"
CONFIGFILE="DATA/SETTINGS/yacy.conf"
LOGFILE="yacy.log"
OS="`uname`"

#check if OS is Sun Solaris or one of the OpenSolaris distributions and use different version of id if necessary
if [ $OS = "SunOS" ]
then
    # only this version of id supports the parameter -u
    ID="/usr/xpg4/bin/id"
else
    # regular id for any other case (especially Linux and OSX)
    ID="id"
fi

if [ "`$ID -u`" -eq 0 ]
then
	echo
	echo "For security reasons you should not run this script as root!"
	echo
	exit 1
elif [ ! -x "$JAVA" ]
then
	echo "The java command is not executable."
	echo "Either you have not installed java or it is not in your PATH"
	#Cron supports setting the path in 
	#echo "Has this script been invoked by CRON?"
	#echo "if so, please set PATH in the crontab, or set the correct path in the variable in this script."
	exit 1
fi

#startup YaCy
cd "`dirname $0`"

options="`getopt -n YaCy -o d,l,p,t -- $@`"
if [ $? -ne 0 ];then
	exit 1;
fi

isparameter=0; #options or parameter part of getopts?
parameter="" #parameters will be collected here

LOGGING=0
DEBUG=0
PRINTONLY=0
TAILLOG=0
for option in $options;do
	if [ $isparameter -ne 1 ];then #option
		if [ "$option" = "-l" ];then
			LOGGING=1
			if [ $DEBUG -eq 1 ];then
				echo "can not combine -l and -d"
				exit 1;
			fi
		elif [ "$option" = "-d" ];then
			DEBUG=1
			if [ $LOGGING -eq 1 ];then
				echo "can not combine -l and -d"
				exit 1;
			fi
		elif [ "$option" = "-p" ];then
			PRINTONLY=1
		elif [ "$option" = "-t" ];then
			TAILLOG=1
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

#echo $options;exit 0 #DEBUG for getopts

#get javastart args
JAVA_ARGS="-ea"
if [ ! -f $CONFIGFILE -a -f DATA/SETTINGS/httpProxy.conf ]
then
	# old config if new does not exist
	CONFIGFILE="DATA/SETTINGS/httpProxy.conf"
fi
if [ -f $CONFIGFILE ]
then
	# startup memory
	for i in Xmx Xms; do
		j="`grep javastart_$i $CONFIGFILE | sed 's/^[^=]*=//'`";
		if [ -n $j ]; then JAVA_ARGS="-$j $JAVA_ARGS"; fi;
	done
	
	# Priority
	j="`grep javastart_priority $CONFIGFILE | sed 's/^[^=]*=//'`";

	if [ ! -z "$j" ];then
		if [ -n $j ]; then JAVA="nice -n $j $JAVA"; fi;
	fi

        PORT="`grep ^port= $CONFIGFILE | sed 's/^[^=]*=//'`";
	
#	for i in `grep javastart $CONFIGFILE`;do
#		i="${i#javastart_*=}";
#		JAVA_ARGS="-$i $JAVA_ARGS";
#	done
else
    JAVA_ARGS="-Xmx120m -Xms120m $JAVA_ARGS";
    PORT="8080"
fi

#echo "JAVA_ARGS: $JAVA_ARGS"
#echo "JAVA: $JAVA"

# generating the proper classpath
CLASSPATH=""
for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
CLASSPATH="classes:.:htroot:$CLASSPATH"

cmdline="";
if [ $DEBUG -eq 1 ] #debug
then
	cmdline="$JAVA $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy"
elif [ $LOGGING -eq 1 ];then #logging
	cmdline="$JAVA $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy >> yacy.log &"
else
	cmdline="$JAVA $JAVA_ARGS -Djava.awt.headless=true -classpath $CLASSPATH yacy >> /dev/null &"
fi
if [ $PRINTONLY -eq 1 ];then
	echo $cmdline
else
	echo "****************** YaCy Web Crawler/Indexer & Search Engine *******************"
	echo "**** (C) by Michael Peter Christen, usage granted under the GPL Version 2  ****"
	echo "****   USE AT YOUR OWN RISK! Project home and releases: http://yacy.net/   ****"
	echo "**  LOG of       YaCy: DATA/LOG/yacy00.log (and yacy<xx>.log)                **"
	echo "**  STOP         YaCy: execute stopYACY.sh and wait some seconds             **"
	echo "**  GET HELP for YaCy: see www.yacy-websearch.net/wiki and forum.yacy.de     **"
	echo "*******************************************************************************"
	echo " >> YaCy started as daemon process. Administration at http://localhost:$PORT << "
	eval $cmdline
	if [ "$TAILLOG" -eq "1" -a ! "$DEBUG" -eq "1" ];then
		tail -f DATA/LOG/yacy00.log
	fi
fi
