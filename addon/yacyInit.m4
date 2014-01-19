dnl automatically generate initscripts for different distros
dnl by Florian Richter <Florian_Richter@gmx.de>
dnl
define(helptext, `Usage:
    m4 -DopenSUSE yacyInit.m4 > yacy.init.openSUSE
or: m4 -DDebian yacyInit.m4 > yacy.init.Debian
or: m4 -DFedora yacyInit.m4 > yacy.init.Fedora
or: m4 -DArchLinux yacyInit.m4 > yacy.init.ArchLinux
')dnl
ifdef(`openSUSE',, ifdef(`ArchLinux',, ifdef(`Fedora',, ifdef(`Debian',,````errprint(helptext())m4exit(0)''''))))dnl
#!/bin/bash
#
# init script for YaCy
#
# Provided by Matthias Kempka, 26.12.2004
# Updated by Florian Richter, 17.7.2008
#
ifdef(`Fedora', `
# chkconfig:   - 20 80
# description: Distributed web search engine
')dnl
### BEGIN INIT INFO
# Provides:          yacy
# Required-Start:    $network
# Required-Stop:     $network
ifdef(`openSUSE', `
# Default-Start:     3 5
')dnl
ifdef(`Debian', `
# Default-Start:     2 3 4 5
')dnl
ifdef(`Fedora', `
# Default-Start:     3 5
')dnl
ifdef(`Mandriva', `
# Default-Start:     3 5
')dnl
# Default-Stop:      0 1 6
# Short-Description: Distributed web search engine
# Description:       yacy is a distributed search engine
#                    config-file is /etc/yacy/yacy.conf
### END INIT INFO

NAME="yacy"
DESC="YaCy P2P Web Search"
YACY_HOME="/usr/share/yacy"
DATA_HOME="/var/lib/yacy"
PID_FILE="/var/run/yacy.pid"
USER=yacy


# Set this to the maximum number of seconds the script should try to shutdown
# yacy. You might want to increase this on slower peers or for bigger
# databases.
SHUTDOWN_TIMEOUT=50

# Default niceness if not set in config file
NICE_VAL=0

JAVA_ARGS="-server -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dsolr.directoryFactory=solr.MMapDirectoryFactory"

ifdef(`openSUSE', `dnl
. /etc/rc.status
rc_reset

')dnl
ifdef(`ArchLinux', `dnl
[ -f /etc/profile.d/jre.sh ] && . /etc/profile.d/jre.sh

[ -f /etc/conf.d/yacy ] && . /etc/conf.d/yacy

. /etc/rc.conf
. /etc/rc.d/functions

')dnl
ifdef(`Fedora', `dnl
# Source function library.
. /etc/rc.d/init.d/functions

[ -e /etc/sysconfig/$NAME ] && . /etc/sysconfig/$NAME
')dnl
if [ "$(id -u)" != "0" -a "$(whoami)" != "$USER" ] ; then
	echo "please run this script as root!"
	exit 4
fi

JAVA=$(which java 2> /dev/null)
if [ ! -x "$JAVA" ]; then
	echo "The 'java' command is not executable."
	echo "Either you have not installed java or it is not in your PATH"
	if [ $1 == "stop" -a $2 == "--force" ]; then exit 0; else exit 1; fi
fi

cd $YACY_HOME

#get javastart args
if [ -s DATA/SETTINGS/yacy.conf ]
then
	# startup memory
	for i in Xmx Xms; do
		j=$(grep javastart_$i DATA/SETTINGS/yacy.conf | sed 's/^[^=]*=//');
		if [ -n $j ]; then JAVA_ARGS="-$j $JAVA_ARGS"; fi;
	done
	
	# Priority
	j=$(grep javastart_priority DATA/SETTINGS/yacy.conf | sed 's/^[^=]*=//');

	if [ ! -z "$j" ];then
		if [ -n $j ]; then NICE_VAL=$j; fi;
	fi
	
else
	JAVA_ARGS="-Xmx120m -Xms120m $JAVA_ARGS"
fi

# generating the proper classpath
CP=/usr/share/java/yacy.jar:$YACY_HOME/htroot
for name in /usr/share/java/yacy/*.jar; do
	CP=$CP:$name
done
ifdef(`Debian', `
CP="$CP:/usr/share/java/javatar.jar"
CP="$CP:/usr/share/java/commons-httpclient.jar"
CP="$CP:/usr/share/java/commons-fileupload.jar"
CP="$CP:/usr/share/java/commons-logging.jar"
CP="$CP:/usr/share/java/commons-codec.jar"
CP="$CP:/usr/share/java/commons-discovery.jar"
CP="$CP:/usr/share/java/commons-io.jar"
CP="$CP:/usr/share/java/pdfbox.jar"
CP="$CP:/usr/share/java/bcprov.jar"
CP="$CP:/usr/share/java/bcmail.jar"
CP="$CP:/usr/share/java/jakarta-poi.jar"
CP="$CP:/usr/share/java/jakarta-poi-scratchpad.jar"
CP="$CP:/usr/share/java/oro.jar"
CP="$CP:/usr/share/java/xerces.jar"
CP="$CP:/usr/share/java/jsch.jar"
CP="$CP:/usr/share/java/ant.jar"    # bzip-stuff
CP="$CP:/usr/share/java/jmimemagic.jar"
CP="$CP:/usr/share/java/log4j-1.2.jar"
CP="$CP:/usr/share/java/odfutils.jar"
CP="$CP:/usr/share/java/jrpm.jar"
CP="$CP:/usr/share/java/tmextractors.jar"
CP="$CP:/usr/share/java/servlet-api.jar"
CP="$CP:/usr/share/java/j7zip.jar"
')dnl
CLASSPATH=$CP

if [ -f $PID_FILE ]; then
	pid=$(cat "$PID_FILE")
	pidno=$( ps ax | grep "$pid" | awk '{ print $1 }' | grep "$pid" )
fi

RETVAL=0
case "$1" in
  start)
	if [ -n "$pidno" ]; then
		echo "already running"
		exit 0
	fi
ifdef(`openSUSE', `
	echo -n "Starting $DESC. "
')dnl
ifdef(`Debian', `
	echo -n "Starting $DESC: "
')dnl
ifdef(`Fedora', `
	echo -n "Starting $DESC: "
')dnl
ifdef(`ArchLinux', `
		stat_busy "Starting YaCy Daemon"
')dnl
	ARGS="$JAVA_ARGS -classpath $CLASSPATH net.yacy.yacy"
define(`START_YACY_WITH_START_STOP_DAEMON',`
	/sbin/start-stop-daemon --start --background --make-pidfile --chuid $USER\
		--pidfile $PID_FILE --chdir $YACY_HOME --startas $JAVA\
		--nicelevel $NICE_VAL\
		-- $ARGS
')dnl
define(`START_YACY_WITH_SUDO', `
	cmdline="$JAVA $ARGS"
	if [ "$(whoami)" != "$USER" ]; then
		nice -$NICE_VAL sudo -u yacy $cmdline &>/dev/null &
	else
		nice -$NICE_VAL $cmdline &>/dev/null &
	fi
	echo $! >$PID_FILE
')dnl
ifdef(`ArchLinux', `START_YACY_WITH_SUDO()')dnl
ifdef(`openSUSE', `START_YACY_WITH_SUDO()')dnl
ifdef(`Fedora', `START_YACY_WITH_SUDO()')dnl
ifdef(`Debian', `START_YACY_WITH_START_STOP_DAEMON()')dnl
	sleep 1
	ps ax|grep "^ *$(cat $PID_FILE)" > /dev/null
	if [ "$?" == "0" ]; then
ifdef(`Debian', `
		echo "$NAME."
')dnl
ifdef(`Fedora', `
		echo
')dnl
ifdef(`openSUSE', `
		rc_status -v
')dnl
ifdef(`ArchLinux', `
		add_daemon yacy
		chown yacy:root /var/run/daemons/yacy
		stat_done
')dnl
		RETVAL=0
		chown yacy:root $PID_FILE
	else
ifdef(`Debian', `
		echo "failed."
')dnl
ifdef(`Fedora', `
		echo
')dnl
ifdef(`openSUSE', `
		rc_failed
		rc_status -v
')dnl
ifdef(`ArchLinux', `
		stat_fail
')dnl
		RETVAL=1
	fi
	;;
	
  stop)
	if [ -n "$pidno" ]; then
ifdef(`openSUSE', `
		echo -n "Shutting down $DESC: "
')dnl
ifdef(`Debian', `
		echo -n "Stopping $DESC: "
')dnl
ifdef(`Fedora', `
		echo -n "Stopping $DESC: "
')dnl
ifdef(`ArchLinux', `
		stat_busy "Stopping YaCy Daemon"
')dnl
		cd $YACY_HOME
		cmdline="$JAVA $JAVA_ARGS -cp $CLASSPATH net.yacy.yacy -shutdown"
		if [ "$(whoami)" != "$USER" ]; then
			sudo -u yacy $cmdline &>/dev/null & 
		else
			$cmdline &>/dev/null & 
		fi
		shutdown_pid=$!

		timeout=$SHUTDOWN_TIMEOUT
		while [ -n "$pidno" ]; do
			let timeout=$timeout-1
			if [ $timeout -eq 0 ]; then
				kill -9 $pid &>/dev/null
				break
			fi
			echo -n  "."
			sleep 1
			pidno=$( ps ax | grep $pid | awk '{ print $1 }' | grep $pid )
		done

		# dont forget to kill shutdown process if necessary
		shutdown_pid=$( ps ax | grep $shutdown_pid | awk '{ print $1 }' | grep $shutdown_pid )
		if [ -n "$shutdown_pid" ] ; then
			kill -9 $shutdown_pid &>/dev/null
		fi

		if [ "$2" != "--leave-pidfile" ]; then
			rm $PID_FILE
ifdef(`ArchLinux', `
			rm_daemon yacy
')dnl
		fi
		cd - >/dev/null
ifdef(`Debian', `
		echo "$NAME."
')dnl
ifdef(`Fedora', `
		echo
')dnl
ifdef(`openSUSE', `
		rc_status -v
')dnl
ifdef(`ArchLinux', `
		stat_done
')dnl
		exit 0
	fi
	echo "not running."
	;;

  restart)
	$0 stop --leave-pidfile
	sleep 3
	$0 start
	;;
  reload)
	$0 restart
	;;
  force-reload)
	$0 restart
	;;
  status)
	# needed by Fedora
	if [ -n "$pidno" ]; then
		echo "is running."
		exit 0
	else
		if [ -f $PID_FILE ]; then
			echo "is dead, but pid file exists."
			exit 1
		else
			echo "is not running."
			exit 3
		fi
	fi
	;;
  *)
	echo "Usage: $0 {start|stop|restart}" >&2
	exit 1
	;;
esac

exit $RETVAL
