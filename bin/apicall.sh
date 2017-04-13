#!/usr/bin/env sh
# Call an HTTP API on the local YaCy peer, authenticated as administrator
#
# Authentication options :
# - enable unauthenticated local access as administrator : set adminAccountForLocalhost=true in the DATA/SETTINGS/yacy.conf file
# - OR use the legacy Basic HTTP authentication mode (unsecured for remote access): set the "auth-method" to BASIC in the defaults/web.xml file
# - OR use the Digest HTTP authentication mode : set the "auth-method" to DIGEST in the defaults/web.xml file. 
#   With that last option, the script will run in interactive mode as default, prompting for the administrator password.
#   To run in batch mode, you must first export an environment variable filled with the clear-text administrator password before using this script :
#    For example with > export YACY_ADMIN_PASSWORD=your_admin_password
# 

cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
admin=$(grep ^adminAccountUserName= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
adminAccountForLocalhost=$(grep ^adminAccountForLocalhost= ../DATA/SETTINGS/yacy.conf | cut -d= -f2)

if grep "<auth-method>BASIC</auth-method>" ../defaults/web.xml > /dev/null; then
	# When authentication method is in basic mode, use directly the password hash from the configuration file 
	YACY_ADMIN_PASSWORD=$(grep ^adminAccountBase64MD5= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
fi

if which curl > /dev/null; then
	if [ "$adminAccountForLocalhost" = "true" ]; then
		# localhost access as administrator without authentication is enabled
		curl -sSf "http://127.0.0.1:$port/$1"
	elif [ -n "$YACY_ADMIN_PASSWORD" ]; then
		# admin password is provided as environment variable : let's use it
		curl -sSf --anyauth -u "$admin:$YACY_ADMIN_PASSWORD" "http://127.0.0.1:$port/$1"
	else
		# no password environment variable : it will be asked interactively
		curl -sSf --anyauth -u "$admin" "http://127.0.0.1:$port/$1"
	fi
elif which wget > /dev/null; then
	if [ "$adminAccountForLocalhost" = "true" ]; then
		# localhost access as administrator without authentication is enabled
		wget -nv -t 1 --timeout=120 "http://127.0.0.1:$port/$1" -O -
	elif [ -n "$YACY_ADMIN_PASSWORD" ]; then
		# admin password is provided as environment variable : let's use it
		wget -nv -t 1 --timeout=120 --http-user "$admin" --http-password "$YACY_ADMIN_PASSWORD" "http://127.0.0.1:$port/$1" -O -
	else
		# no password environment variable : it will be asked interactively
		wget -nv -t 1 --timeout=120 --http-user "$admin" --ask-password "http://127.0.0.1:$port/$1" -O -
	fi
else
	echo "Please install curl or wget" > /dev/stderr
	exit 1
fi
