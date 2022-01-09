#!/usr/bin/env sh
# Call a YaCy HTTP POST API URL protected by HTTP authentication and transaction token validation
# $1 : API path
# $2 : POST parameters (example : "param1=value1&param2=value2")
#
# $YACY_DATA_PATH : path to the YaCy DATA folder to use. When not set, the relative ../DATA path is used as a default.
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
. ./checkDataFolder.sh
. ./checkConfFile.sh

port=$(grep ^port= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
admin=$(grep ^adminAccountUserName= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
adminAccountForLocalhost=$(grep ^adminAccountForLocalhost= "$YACY_DATA_PATH/SETTINGS/yacy.conf" | cut -d= -f2)

if grep "<auth-method>BASIC</auth-method>" "$YACY_APP_PATH/defaults/web.xml" > /dev/null; then
	# When authentication method is in basic mode, use directly the password hash from the configuration file 
	YACY_ADMIN_PASSWORD=$(grep ^adminAccountBase64MD5= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
fi

if which curl > /dev/null; then
	if [ "$adminAccountForLocalhost" = "true" ]; then
		# localhost access as administrator without authentication is enabled
		
		# retrieve the transaction token from the HTTP GET flavor of the URL 
		transactionToken=$(curl -sSfI "http://127.0.0.1:$port/$1" | grep X-YaCy-Transaction-Token: | awk {'printf $2'} | tr -d '[:space:]')
		# send POST data including the transaction token
		curl -sSf -d "$2&transactionToken=$transactionToken" "http://127.0.0.1:$port/$1" > /dev/null
		
	else
		if [ -z "$YACY_ADMIN_PASSWORD" ]; then
			# no password environment variable : we ask interactively for it only once (not using read -s to be POSIX compliant)
			stty -echo
			read -p "Enter host password for user '$admin':" YACY_ADMIN_PASSWORD
			stty echo
			printf "\n"
		fi
		
		# retrieve the transaction token from the HTTP GET flavor of the URL 
		transactionToken=$(curl -sSfI --anyauth -u "$admin:$YACY_ADMIN_PASSWORD" "http://127.0.0.1:$port/$1" | grep X-YaCy-Transaction-Token: | awk {'printf $2'} | tr -d '[:space:]')
		# send POST data including the transaction token
		curl -sSf --anyauth -u "$admin:$YACY_ADMIN_PASSWORD" -d "$2&transactionToken=$transactionToken" "http://127.0.0.1:$port/$1" > /dev/null
		
	fi
elif which wget > /dev/null; then
	
	if [ "$adminAccountForLocalhost" = "true" ]; then
		# localhost access as administrator without authentication is enabled
		
		# retrieve the transaction token from the HTTP GET flavor of the URL
		transactionToken=$(wget -q -t 1 -O - --save-headers --timeout=120 "http://127.0.0.1:$port/$1" | grep X-YaCy-Transaction-Token: | awk {'printf $2'} | tr -d '[:space:]')
		# send POST data including the transaction token
		wget -nv -t 1 -O /dev/null --timeout=120 --post-data "$2&transactionToken=$transactionToken" "http://127.0.0.1:$port/$1"
		
	else
		if [ -z "$YACY_ADMIN_PASSWORD" ]; then
			# no password environment variable : we ask interactively for it only once (not using read -s to be POSIX compliant)
			stty -echo
			read -p "Enter host password for user '$admin':" YACY_ADMIN_PASSWORD
			stty echo
			printf "\n"
		fi

		# retrieve the transaction token from the HTTP GET flavor of the URL
		transactionToken=$(wget -q -t 1 -O - --http-user "$admin" --http-password "$YACY_ADMIN_PASSWORD" --save-headers --timeout=120 "http://127.0.0.1:$port/$1" | grep X-YaCy-Transaction-Token: | awk {'printf $2'} | tr -d '[:space:]')
		# send POST data including the transaction token
		wget -nv -t 1 -O /dev/null --timeout=120 --http-user "$admin" --http-password "$YACY_ADMIN_PASSWORD" --post-data "$2&transactionToken=$transactionToken" "http://127.0.0.1:$port/$1"
		
	fi
else
	printf "Please install curl or wget\n" > /dev/stderr
	exit 1
fi