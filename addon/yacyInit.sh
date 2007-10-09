#! /bin/sh
#
# init script for the HTTP Proxy: YaCy
#
# Provided by Matthias Kempka, 26.12.2004

# ensure java is in the path
PATH=/sbin:/bin:/usr/sbin:/usr/local/bin:/usr/bin
# installation directory
DAEMON_DIR=/opt/yacy
# set to the user whose rights the proxy will gain
USER=yacy
# Set this to the maximum number of seconds the script should try to shutdown
# yacy. You might want to increase this on slower peers or for bigger
# databases.
SHUTDOWN_TIMEOUT=20

# Don't run if not installed
test -f $DAEMON_DIR/startYACY.sh || exit 0

# generating the proper classpath
cd $DAEMON_DIR
CLASSPATH=""
for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
for N in libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
CLASSPATH="classes:.:htroot:$CLASSPATH"
NAME="yacy"
DESC="YaCy HTTP Proxy"
PID_FILE=/var/run/$NAME.pid

JAVA=$(which java)

if [ -f $PID_FILE ]; then
	pid=$(cat "$PID_FILE")
	pidno=$( ps ax | grep "$pid" | awk '{ print $1 }' | grep "$pid" )
fi

case "$1" in
  start)
	if [ -n "$pidno" ]; then
		echo "already running"
		exit 0
	fi
	echo -n "Starting $DESC: "
	start-stop-daemon --start --background --make-pidfile --chuid $USER\
		--pidfile $PID_FILE --chdir $DAEMON_DIR --startas $JAVA\
		-- -classpath $CLASSPATH yacy $DAEMON_DIR > yacy.log
	echo "$NAME."
	;;
	
  stop)
	if [ -n "$pidno" ]; then
		echo -n "Stopping $DESC: "
		cd $DAEMON_DIR
		./stopYACY.sh
		timeout=$SHUTDOWN_TIMEOUT
		while [ -n "$pidno" ]; do
			let timeout=$timeout-1
			if [ $timeout -eq 0 ]; then
				start-stop-daemon --stop --pidfile $PID_FILE --oknodo --verbose
				break
			fi
			echo -n  "."
			sleep 1
			pidno=$( ps ax | grep $pid | awk '{ print $1 }' | grep $pid )
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
