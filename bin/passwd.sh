#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh
. ./checkConfFile.sh

echo "Setting new YaCy administrator password..."

# take the administrator user name from the config file
YACY_ADMIN_USER_NAME=$(grep ^adminAccountUserName= "$YACY_DATA_PATH/SETTINGS/yacy.conf" | cut -d= -f2 | tr -d '\r\n')

if [ -z "$YACY_ADMIN_USER_NAME" ]; then 
	# administrator user name should not be empty : by the way, in that case use the same default value as in YaCy application
	YACY_ADMIN_USER_NAME="admin"
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

(./protectedPostApiCall.sh "ConfigAccounts_p.html" "setAdmin=&adminuser=$YACY_ADMIN_USER_NAME&adminpw1=$YACY_ADMIN_PASSWORD&adminpw2=$YACY_ADMIN_PASSWORD&access=" && \
echo "Password successfully changed for User Name '$YACY_ADMIN_USER_NAME'.") || \
(echo "Password setting failed." && \
exit 1)