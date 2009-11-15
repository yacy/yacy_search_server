#!/bin/sh

# THIS SCRIPT CAN BE USED TO EDIT SOME BASIC SETTINGS OF YACY
#
# Copyright 2009 by Marc Nause; marc.nause@gmx.de
#
# This is a part of YaCy, a peer-to-peer based web search engine.
# http://www.yacy.net
#
# $LastChangedDate$
# $LastChangedRevision$
# $LastChangedBy$
#
# LICENSE
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

#
# DEFINING SOME CONSTANTS
#
DATADIR="DATA"
SETTINGSDIR="DATA/SETTINGS"
CONFIGFILE="DATA/SETTINGS/yacy.conf"
CONFIGTEMPLATE="defaults/yacy.init"
LOCKFILE="DATA/yacy.running"
JAVA="`which java`"

#
# INITIALIZING VARIABLES
#
STATUS='No actions have been performed yet.'

#
# DEFINING SOME FUNCTIONS WHICH WILL BE USED LATER
#

# CHANGES THE ADMIN SETTINGS
change_admin_settings()
{
    change_admin_localhost
}

change_admin_localhost()
{
    echo
    echo -n 'Allow admin access for all local users (y/n)? '
    read INPUT
    case $INPUT in
        y)
            replace_parameter 'adminAccountForLocalhost' 'true'
            STATUS = 'Admin access granted for all local users.'
            ;;
        n)
            replace_parameter 'adminAccountForLocalhost' 'false'
            change_admin_password
            ;;
        *)
            change_admin_localhost
            ;;
    esac
}

# CHANGES THE ADMIN PASSWORD
change_admin_password()
{
    echo -n 'Enter username (leave empty for standard username "admin"): '
    read USERNAME
    if [ "$USERNAME" == '' ]
    then
        USERNAME='admin';
    fi
    echo -n 'Enter new password (will not be displayed): '
    read -s INPUT1
    echo
    echo -n 'Enter new password again (will not be displayed): '
    read -s INPUT2
    echo

    if [ $INPUT1 == $INPUT2 ]
    then
        BASE64=`$JAVA -cp classes net/yacy/kelondro/order/Base64Order -es "$USERNAME:$INPUT1"`
        B64MD5=`$JAVA -cp classes net/yacy/kelondro/order/Digest -strfhex "$BASE64"`
        B64MD5=`echo $B64MD5 | sed "s/\(\S\) .*/\1/"`
        replace_parameter 'adminAccountBase64MD5' "$B64MD5"
    else
        echo 'Entries did not match, please try again.'
        change_admin_password
    fi
    STATUS='Admin password has been changed.'

}

# CHANGES THE MEMORY SETTINGS
change_mem_settings()
{
    echo
    echo -n 'How much memory (in MB) do you want YaCy to be able to use? '
    read INPUT

    case $INPUT in
      *[0-9]*)
            replace_parameter 'javastart_Xmx' "Xmx$INPUT"'m'
            replace_parameter 'javastart_Xms' "Xms$INPUT"'m'
            ;;
      *)
            echo 'Please enter a number.'
            change_mem_settings
            ;;
    esac
    STATUS="Memory settings have been changed. YaCy will be able to use $INPUT MB of RAM now."
}

# CHANGES THE PORT SETTINGS
change_port_settings()
{
    echo
    echo -n 'Which port do you want YaCy to use (standard is 8080)? '
    read INPUT


    if [ "$INPUT" == '' ]
    then
        INPUT='8080'
    fi

    case $INPUT in
      *[0-9]*)
            replace_parameter 'port' $INPUT
            ;;
      *)
            echo 'Please enter a number.'
            change_port_settings
            ;;
    esac
    STATUS="Port settings have been changed. YaCy listens to port $INPUT now."
}

# CHECKS IF CONFIG FILE EXISTS, EXISTS IF IT DOESN'T
check_conf()
{
    if [ ! -e "$CONFIGFILE" ]
    then
        echo 'ERROR:'
        echo 'Config file does not exist. Please start YaCy at least once to create config file.'
        exit 1
    fi
}

# CHECKS IF A STANDARD JVM HAS BEEN SET, EXITS IF NOT
check_java()
{
    if [ "$JAVA" == '' ]
    then
        echo 'ERROR:'
        echo 'Java has not been detected. Please add a JRE to your classpath.'
        exit 1
    fi
}

# EXITS WITH WARNING IF LOCKFILE EXISTS
check_lock()
{
    if [ -f "$LOCKFILE" ]
    then
        echo "WARNING:"
        echo "$LOCKFILE exists which indicates that YaCy is still running."
        echo "Please stop YaCy before running this script."
        echo "If you are sure that YaCy is not running anymore,"
        echo "delete $LOCKFILE and start this script again."
        exit 1
    fi
}

# EXITS SRIPT AND PRINTS GOODBYE MESSAGE
goodbye()
{
    echo
    echo "Goodbye!"
    echo
    exit 0
}

# PRINTS THE MENU
print_menu()
{
    clear
    echo "*** YaCy commandline configuration tool ***"
    echo
    echo "Make your choice:"
    echo "[1] change memory settings"
    echo "[2] change admin password"
    echo "[3] change port"
    echo "[0] quit"
    echo
    echo "Status: $STATUS"
}

# REPLACES THE VALUE OF A PARAMETER (FIRST ARGUMENT) WITH A NEW ONE (SECOND ARGUMENT)
replace_parameter()
{
    sed "s/^\($1 *=\)\(.*\)/\1$2/" "$CONFIGFILE" >"$SETTINGSDIR/yacy.tmp"
    mv "$SETTINGSDIR/yacy.tmp" "$CONFIGFILE"
}

#
# MAIN PROGRAM
#

check_lock

check_conf

print_menu

while read -s -n1 INPUT
do
   case $INPUT in
    0)
        goodbye
        ;;
    1)
        change_mem_settings
        ;;
    2)
        change_admin_settings
        ;;
    3)
        change_port_settings
        ;;
   esac
   print_menu
done

#EOF