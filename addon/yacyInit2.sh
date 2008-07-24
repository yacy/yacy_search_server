#!/bin/sh
#
# init script for the HTTP Proxy: YaCy
#
# Provided by Matthias Kempka, 26.12.2004
# edited by DanielR, 2008-07-21

# installation directory
DAEMON_DIR=/path/to/yacy
# set to the user whose rights the proxy will gain
USER=yacyUser
# Default niceness if not set in config file
NICE_VAL=0

# Set this to the maximum number of minutes the script should try to shutdown
# yacy. You might want to increase this on slower peers or for bigger
# databases.
SHUTDOWN_TIMEOUT=10

# Don't run if not installed
test -f $DAEMON_DIR/startYACY.sh || exit 0

NAME="yacy"
DESC="YaCy HTTP Proxy"
PID_FILE="yacy.pid"


cd $DAEMON_DIR

if [ -f $PID_FILE ]; then
	pid=$(cat "$PID_FILE")
fi

check_process()
{
	pidno=""
	if [ $pid -gt 0 ]; then
		pidno=$( ps ax | grep "$pid" | awk '{ print $1 }' | grep "$pid" )
	fi
}

# checks if yacy.running exists every 2 seconds for a minute (exits after 60 seconds)
# returns true (0) if exists
check_runningFile()
{
	iteration=0
	while [ $iteration -lt 30 ]; do
		echo -n "."
		iteration=$(($iteration + 1))
		sleep 2
		if [ ! -f DATA/yacy.running ]; then
			return 1
		fi
	done
	return 0
}

check_process
case "$1" in
  start)
	if [ -n "$pidno" ]; then
		echo "already running"
		exit 0
	fi
	echo -n "Starting $DESC: "
	#./startYACY.sh 2>error.log >/dev/null
	start-stop-daemon --start --verbose --pidfile $PID_FILE --nicelevel $NICE_VAL\
		 --chuid $USER --chdir $DAEMON_DIR --startas ./startYACY.sh\
		 -- 2>error.log
	echo "$NAME."
	;;
	
  stop)
	if [ -n "$pidno" ]; then
		echo -n "Stopping $DESC: "
		cd $DAEMON_DIR
		./stopYACY.sh > /dev/null
		# yacy has per default a delayed shutdown by 5 seconds
		sleep 6
		timeout=$SHUTDOWN_TIMEOUT
		echo "waiting that YaCy has finished (killing YaCy after $timeout minutes)"
		while [ -n "$pidno" ]; do
			check_runningFile
			timeout=$(($timeout-1))
			if [ $timeout -eq 0 ]; then
				start-stop-daemon --stop --pidfile $PID_FILE --oknodo --verbose
				break
			fi
			echo -n  ":"
			check_process
			#pidno=$( ps ax | grep $pid | awk '{ print $1 }' | grep $pid )
		done
		echo "$NAME."
		cd -
		exit 0
	fi
	echo "not running."
	;;

  restart)
	$0 stop
	sleep 1
	$0 start 
	;;
  *)
	N=/etc/init.d/yacyInit.sh
	# echo "Usage: $N {start|stop|restart|reload|force-reload}" >&2
	echo "Usage: $N {start|stop|restart}" >&2
	exit 1
	;;
esac

exit 0
