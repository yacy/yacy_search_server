#!/usr/bin/env sh
JAVA="`which java`"
CONFIGFILE="DATA/SETTINGS/yacy.conf"
LOGFILE="yacy.log"
PIDFILE="yacy.pid"
OS="`uname`"

#get javastart args
JAVA_ARGS="-server -Djava.awt.headless=true -Dfile.encoding=UTF-8";

#JAVA_ARGS="-verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails $JAVA_ARGS";

#check if OS is Sun Solaris or one of the OpenSolaris distributions and use different version of id if necessary
if [ $OS = "SunOS" ]
then
    # only this version of id supports the parameter -u
    ID="/usr/xpg4/bin/id"
else
    # regular id for any other case (especially Linux and OSX)
    ID="id"
fi

if [ ! -x "$JAVA" ]
then
    echo "The java command is not executable."
    echo "Either you have not installed java or it is not in your PATH"
    #Cron supports setting the path in 
    #echo "Has this script been invoked by CRON?"
    #echo "if so, please set PATH in the crontab, or set the correct path in the variable in this script."
    exit 1
fi

usage() {
    cat - <<USAGE
startscript for YaCy on UNIX-like systems
Options
  -h, --help                show this help
  -t, --tail-log            show the output of "tail -f DATA/LOG/yacy00.log" after starting YaCy
  -l, --logging             save the output of YaCy to yacy.log
  -d, --debug               show the output of YaCy on the console and enable remote monitoring with JMX
  -f, --foreground          run as a foreground process, showing the output of YaCy on the console
  -p, --print-out           only print the command, which would be executed to start YaCy
  -s, --startup [data-path] start YaCy using the specified data folder path, relative to the current user home
  -g, --gui                 start a gui for YaCy
USAGE
}

#startup YaCy
YACY_PARENT_DATA_PATH="`dirname $0`"
cd "$YACY_PARENT_DATA_PATH"

case "$OS" in
    *"BSD"|"Darwin")
        if [ $(echo $@ | grep -o "\-\-" | wc -l) -ne 0  ]
        then
            echo "WARNING: Unfortunately this script does not support long options in $OS."
        fi
        
        options="`getopt hdlptsg: $*`"
;;
  *)
        options="`getopt -u -n YaCy -o h,d,f,l,p,t,s,g -l help,debug,foreground,logging,print-out,tail-log,startup,gui -- $@`"
;;
esac

if [ $? -ne 0 ];then

    exit 1;
fi

isparameter=0; #options or parameter part of getopts?
parameter="" #parameters will be collected here

LOGGING=0
DEBUG=0
FOREGROUND=0
PRINTONLY=0
TAILLOG=0
STARTUP=0
GUI=0
for option in $options;do
    if [ $isparameter -ne 1 ];then #option
        case $option in
            -h|--help) 
                usage
                exit 3
                ;;
            -l|--logging) 
                LOGGING=1
                if [ $DEBUG -eq 1 ];then
                    echo "can not combine -l and -d"
                    exit 1;
                fi
                if [ $FOREGROUND -eq 1 ];then
                    echo "can not combine -l and -f"
                    exit 1;
                fi
                ;;
            -d|--debug)
                DEBUG=1
                # enable asserts
                JAVA_ARGS="$JAVA_ARGS -ea -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
                if [ $LOGGING -eq 1 ];then
                    echo "can not combine -l and -d"
                    exit 1;
                fi
                ;;
            -f|--foreground)
                FOREGROUND=1
                if [ $LOGGING -eq 1 ];then
                    echo "can not combine -l and -f"
                    exit 1;
                fi
                ;;
            -p|--print-out)
                PRINTONLY=1
                ;;
            -t|--tail-log)
                TAILLOG=1
                ;;
            -s|--startup)
                STARTUP=1
                isparameter=1
                ;;
            -g|--gui)
                GUI=1
                isparameter=1
                ;;
        esac #case option 
    else #parameter
        if [ $option = "--" ];then #option / parameter separator
            isparameter=1;
            continue
        else
            if [ $parameter ];then
                parameter="$parameter $option"
            else
                parameter="$option"
            fi
        fi
    fi #parameter or option?
done

if [ ! -z "$parameter" ] && [ "$STARTUP" -eq 1 -o "$GUI" -eq 1 ]; then
    # The data path is explicitely provided with startup or gui option
    YACY_PARENT_DATA_PATH=$parameter
    if [ ! "`echo $YACY_PARENT_DATA_PATH | cut -c1`" = "/" ]; then
        # Parent DATA path is relative to the user home
        YACY_PARENT_DATA_PATH="$HOME/$YACY_PARENT_DATA_PATH"
    fi
    CONFIGFILE="$YACY_PARENT_DATA_PATH/DATA/SETTINGS/yacy.conf"
fi

#echo $options;exit 0 #DEBUG for getopts

#check if Linux system supports large memory pages or if OS is Solaris which 
#supports large memory pages since version 9 
#(according to http://java.sun.com/javase/technologies/hotspot/largememory.jsp)
ENABLEHUGEPAGES=0;

if [ $OS = "Linux" ]
then
    HUGEPAGESTOTAL="`cat /proc/meminfo | grep HugePages_Total | sed s/[^0-9]//g`"
    if [ -n "$HUGEPAGESTOTAL" ] && [ $HUGEPAGESTOTAL -ne 0 ]
    then 
        ENABLEHUGEPAGES=1
    fi
    # the G1 GC is on by default in Java7, so we try that here as well
    # JAVA_ARGS="$JAVA_ARGS -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC"
elif [ $OS = "SunOS" ]
then
    # the UseConcMarkSweepGC option caused a full CPU usage - bug on Darwin.
    # It was reported that the same option causes good performance on solaris.
    JAVA_ARGS="$JAVA_ARGS -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode"
    ENABLEHUGEPAGES=1
fi 

#turn on support for large memory pages if supported by OS
if [ $ENABLEHUGEPAGES -eq 1 ]
then
    JAVA_ARGS="$JAVA_ARGS -XX:MinHeapFreeRatio=60 -XX:MaxHeapFreeRatio=90"
fi

#turn on MMap for Solr if OS is a 64bit OS
if [ -n "`uname -m | grep 64`" ]; then JAVA_ARGS="$JAVA_ARGS -Dsolr.directoryFactory=solr.MMapDirectoryFactory"; fi

if [ -f "$CONFIGFILE" ]
then
    # startup memory

    if [ -z "$YACY_JAVASTART_XMX" ]
    then
        # When YACY_JAVASTART_XMX is not set or empty:
        # Read from $CONFIGFILE
        j="`grep javastart_Xmx "$CONFIGFILE" | sed 's/^[^=]*=//'`";
        if [ -n "$j" ]; then JAVA_ARGS="-$j $JAVA_ARGS"; fi;
    else
        # use the YACY_JAVASTART_XMX variable
        JAVA_ARGS="-$YACY_JAVASTART_XMX $JAVA_ARGS"
    fi

    # Priority
    j="`grep javastart_priority "$CONFIGFILE" | sed 's/^[^=]*=//'`";

    if [ -n "$j" ]; then JAVA="nice -n $j $JAVA"; fi;

    PORT="`grep ^port= "$CONFIGFILE" | sed 's/^[^=]*=//'`";
    if [ -z "$PORT" ]; then PORT="8090"; fi;
    
#    for i in `grep javastart "$CONFIGFILE"`;do
#        i="${i#javastart_*=}";
#        JAVA_ARGS="-$i $JAVA_ARGS";
#    done
else
    JAVA_ARGS="-Xmx600m $JAVA_ARGS";
    PORT="8090"
fi

#echo "JAVA_ARGS: $JAVA_ARGS"
#echo "JAVA: $JAVA"

# generating the proper classpath
CLASSPATH=""
for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
CLASSPATH=".:$CLASSPATH"

cmdline="$JAVA $JAVA_ARGS -classpath $CLASSPATH net.yacy.yacy";

if [ $STARTUP -eq 1 ] #startup
then
    cmdline="$cmdline -startup \"$parameter\""
elif [ $GUI -eq 1 ];then #gui
    cmdline="$cmdline -gui $parameter"
fi
if [ $DEBUG -eq 1 ] #debug
then
    cmdline=$cmdline
elif [ $FOREGROUND -eq 1 ];then # foreground process without remote JMX monitoring
    cmdline=$cmdline
elif [ $LOGGING -eq 1 ];then #logging
    cmdline="$cmdline >> yacy.log & echo \$! > $PIDFILE"
else
    cmdline="$cmdline >/dev/null 2>/dev/null &"
fi
if [ $PRINTONLY -eq 1 ];then
    echo $cmdline
else
    echo "****************** YaCy Web Crawler/Indexer & Search Engine *******************"
    echo "**** (C) by Michael Peter Christen, usage granted under the GPL Version 2  ****"
    echo "****   USE AT YOUR OWN RISK! Project home and releases: https://yacy.net/  ****"
    echo "**  LOG of       YaCy: DATA/LOG/yacy00.log (and yacy<xx>.log)                **"
    echo "**  STOP         YaCy: execute stopYACY.sh and wait some seconds             **"
    echo "**  GET HELP for YaCy: join our community at https://community.searchlab.eu  **"
    echo "*******************************************************************************"
    if [ $DEBUG -eq 1 ] #debug
    then
        # with exec the java process become the main process and will receive signals such as SIGTERM
        exec $cmdline
    elif [ $FOREGROUND -eq 1 ];then # foreground process without remote JMX monitoring
        # with exec the java process become the main process and will receive signals such as SIGTERM
        exec $cmdline
    else
        echo " >> YaCy started as daemon process. Administration at http://localhost:$PORT << "
        eval $cmdline
        if [ "$TAILLOG" -eq "1" -a ! "$DEBUG" -eq "1" ];then
            sleep 1
            tail -f DATA/LOG/yacy00.log
        fi
    fi
fi
