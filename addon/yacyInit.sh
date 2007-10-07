#! /bin/sh
#
# init script for the HTTP Proxy: YaCy
#
# Provided by Matthias Kempka, 26.12.2004

PATH=/sbin:/bin:/usr/sbin:/usr/local/bin:/usr/bin #ensure java is in the path
DAEMON_DIR=/opt/yacy #installation directory
USER=yacy #set to the user whose rights the proxy will gain


# generating the proper classpath
CLASSPATH=""
for N in $DAEMON_DIR/lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
for N in $DAEMON_DIR/libx/*.jar; do CLASSPATH="$CLASSPATH$N:"; done
CLASSPATH="classes:.:htroot:$CLASSPATH"
#CLASSPATH=$DAEMON_DIR/classes
DAEMON=$DAEMON_DIR/startYACY.sh
NAME="yacy"
DESC="YaCy HTTP Proxy"
PID_FILE=/var/run/$NAME.pid

# Don't run if not installed
test -f $DAEMON || exit 0

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
		--pidfile $PID_FILE --startas $JAVA\
		-- -classpath $CLASSPATH yacy $DAEMON_DIR
	echo "$NAME."
	;;
	
  stop)
	if [ -n "$pidno" ]; then
		echo -n "Stopping $DESC: "
		cd $DAEMON_DIR
		./stopYACY.sh
		timeout=20
		while [ -n "$pidno" ]
		  do
		  let timeout=$timeout-1
		  if [ $timeout -eq 0 ]; then
		      start-stop-daemon --stop --pidfile $PID_FILE --oknodo
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
