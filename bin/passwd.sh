#!/usr/bin/env sh
cd "`dirname $0`"

if [ -z "$1" ]; then
	echo "Usage : ./passwd.sh NEW_PASSWORD"
	exit 2
fi

(./protectedPostApiCall.sh "ConfigAccounts_p.html" "setAdmin=&adminuser=admin&adminpw1=$1&adminpw2=$1&access=" && \
echo "Password for User Name 'admin' set to '$1'") || \
(echo "Password setting failed" && \
exit 1)