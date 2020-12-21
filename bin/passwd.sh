#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh
. ./checkConfFile.sh

echo "Setting new YaCy administrator password..."

YACY_CONF_FILE="$YACY_DATA_PATH/SETTINGS/yacy.conf"

# take the administrator user name and realm from the config file
YACY_ADMIN_USER_NAME=$(grep ^adminAccountUserName= "$YACY_CONF_FILE" | cut -d= -f2 | tr -d '\r\n')
YACY_ADMIN_REALM=$(grep "^adminRealm=" "$YACY_CONF_FILE" | cut -d= -f2 | tr -d '\r\n')

# admin user name and realm should not be empty : by the way, in that case use the same default values as in YaCy application
if [ -z "$YACY_ADMIN_USER_NAME" ]; then
    YACY_ADMIN_USER_NAME="admin"
fi
if [ -z "$YACY_ADMIN_REALM" ]; then
    YACY_ADMIN_REALM="YaCy"
fi

if [ -z "$1" ]; then
	printf "Please enter the new password for user '$YACY_ADMIN_USER_NAME' :"
	stty -echo # Do not echo typed characters when typing password
	read YACY_ADMIN_PASSWORD
	stty echo # Restore echo
	printf "\n"
else
	YACY_ADMIN_PASSWORD="$1"
fi

if [ ${#YACY_ADMIN_PASSWORD} -le 2 ]; then
	echo "Please enter a password with more than 2 characters."
	exit 1
fi

if [ -f "$YACY_DATA_PATH/yacy.running" ]; then
	echo "YaCy server appears to be running. Calling the ConfigAccounts_p API..." 
	# When the server is running we can not directly modify the yacy.conf file so we use the ConfigAccounts_p API.
	# Otherwise the new password provided here could be overwritten by the server when it saves its in-memory configuration to the yacy.conf file 
	(./apicall.sh "ConfigAccounts_p.html?setAdmin=&adminuser=$YACY_ADMIN_USER_NAME&adminpw1=$YACY_ADMIN_PASSWORD&adminpw2=$YACY_ADMIN_PASSWORD&access=" > /dev/null && \
	echo "Password successfully changed for User Name '$YACY_ADMIN_USER_NAME'.") || \
	(echo "Password setting failed." && exit 1)
else
	echo "YaCy server appears to be shutdown. Modifying the configuration file at $YACY_CONF_FILE..."
	B64MD5=$(java -cp "$YACY_APP_PATH/lib/yacycore.jar" net.yacy.cora.order.Digest -strfhex "$YACY_ADMIN_USER_NAME:$YACY_ADMIN_REALM:$YACY_ADMIN_PASSWORD" | head -n 1)
	if [ -z "$B64MD5" ]; then
		echo "Password setting failed."
		exit 1
	fi	
	PASSWORD_HASH="MD5:$B64MD5"

	(sed "/adminAccountBase64MD5=/c\adminAccountBase64MD5=$PASSWORD_HASH" "$YACY_CONF_FILE" > "$YACY_CONF_FILE".tmp && \
	 mv "$YACY_CONF_FILE".tmp "$YACY_CONF_FILE" && \
	echo "Password successfully changed for User Name '$YACY_ADMIN_USER_NAME'.") || \
	(echo "Password setting failed." && exit 1)
fi
