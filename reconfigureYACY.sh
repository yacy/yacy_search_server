#!/bin/bash

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
DATADIR='DATA'
SETTINGSDIR='DATA/SETTINGS'
CONFIGFILE='DATA/SETTINGS/yacy.conf'
CONFIGTEMPLATE='defaults/yacy.init'
LOCKFILE='DATA/yacy.running'
JAVA="`which java`"
# THIS REGEX WILL ALSO RETURN EVERYTHING AFTER THE NUMBER WHICH IS OK HERE BECAUSE
# WE EITHER HAVE NUMBERS (port) OR A NUMBER PLUS A UNIT (memory settings)
NUMBERFILTER='s/^[^0-9]*\([0-9]*.*\)/\1/'
NOCHANGESTEXT='No changes have been performed.'

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
    if read_parameter 'adminAccountForLocalhost'
    then
        if [ $REPLY == 'true' ]
        then
            CURRENTLOCALACCESS='allowed'
        else
            CURRENTLOCALACCESS='not allowed'
        fi
    else
        CURRENTLOCALACCESS = 'unknown'
    fi


    echo
    echo -n "Allow unrestricted admin access for all local users (y/n, currently $CURRENTLOCALACCESS)? "
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
        '')
            STATUS="$NOCHANGESTEXT"
            ;;
        *)
            echo
            echo 'Please enter y or n or just hit enter to abort.'
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

    if [ "$INPUT1" == "" ]
    then
        echo
        echo 'Empty password is not allowed, please try again.'
        change_admin_password
    elif [ $INPUT1 != $INPUT2 ]
    then
        echo
        echo 'Entries did not match, please try again.'
        change_admin_password
    else
        replace_parameter 'adminAccount' "$USERNAME:$INPUT1"
    fi
    STATUS='Admin password has been changed.'

}

# CHANGES THE MEMORY SETTINGS
change_mem_settings()
{
    if read_parameter 'javastart_Xmx' $NUMBERFILTER
    then
        CURRENTMEM="$REPLY"
    else
        CURRENTMEM='unknown'
    fi

    echo
    echo -n "How much memory (in MB) do you want YaCy to be able to use (currently $CURRENTMEM)? "
    read INPUT

    case $INPUT in
      *[^0-9]*)
            echo
            echo 'Please enter a number or just hit enter to abort.'
            change_mem_settings
            ;;
      [0-9]*)
            replace_parameter 'javastart_Xmx' "Xmx$INPUT"'m'
            replace_parameter 'javastart_Xms' "Xms$INPUT"'m'
            STATUS="Memory settings have been changed. YaCy will be able to use $INPUT MB of RAM now."
            ;;
      '')
            STATUS="$NOCHANGESTEXT"
            ;;
    esac
}

# CHANGES THE PORT SETTINGS
change_port_settings()
{
    if read_parameter 'port'
    then
        CURRENTPORT="$REPLY"
    else
        CURRENTPORT='unknown'
    fi

    echo
    echo -n "Which port do you want YaCy to use (currently $CURRENTPORT)? "
    read INPUT

    case $INPUT in
      [0-9]*)
            if [ "$INPUT" -ge 1024 ] && [ "$INPUT" -le 65535 ];
            then
                replace_parameter 'port' $INPUT
                STATUS="Port settings have been changed. YaCy listens to port $INPUT now."
            else
                echo
                echo 'Please use choose a number between 1024 and 65535.'
                change_port_settings
            fi
            ;;
      '')
            STATUS="$NOCHANGESTEXT"
            ;;
      *)
            echo
            echo 'Please enter a number or just hit enter to abort.'
            change_port_settings
            ;;
    esac
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

# READS THE VALUE OF A PARAMETER (FIRST ARGUMENT) FROM CONFIG FILE. PARAMETER CAN BE FILTERED
# IF A SECOND PARAMETER, WHICH IS A VALID sed OPERATION LIKE "s/xxx/yyy/", EXISTS.
read_parameter()
{
    REPLY="`cat "$CONFIGFILE" | grep "^$1" | sed "s/\(^$1 *= *\)\(.*\)$/\2/"`"
    if [ "$2" != '' ]
    then
        REPLY="`echo "$REPLY" | sed $2`"
    fi
}

# REPLACES THE VALUE OF A PARAMETER (FIRST ARGUMENT) WITH A NEW ONE (SECOND ARGUMENT)
replace_parameter()
{
    $JAVA -classpath lib/yacycore.jar net.yacy.yacy -config "$1=$2" 2>/dev/null
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
