#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/ConfigAccounts_p.html?setAdmin=&adminuser=admin&adminpw1=$1&adminpw2=$1&access=" > /dev/null
echo "Password for User Name 'admin' set to '$1'"