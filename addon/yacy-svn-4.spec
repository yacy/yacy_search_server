# Author: Franz Brauße <mike-nought@gmx.de>
# Date: 14.10.2006
# based on the spec-file of 0.48-3
# Distributed under the terms of the GNU General Public Licens v2

Summary:		P2P search engine, web-crawler and proxy
Name:			yacy
Version:		@REPL_VERSION@_@REPL_REVISION_NR@
Release:		3
License:		GPL
Group:			Application/Internet
Source0:		git@gitorious.org:yacy/rc1.git
URL:			http://yacy.net
Requires:		bash
Requires:		sudo
Requires:		coreutils
Requires:		util-linux
Requires:		grep
Requires:		sed
Requires:		wget
Requires:		jre >= 1.4.2
BuildArch:		noarch
BuildRoot:		@REPL_YACY_ROOT_DIR@/RELEASE/BUILD
Packager:		Franz Brauße <mike-nought@gmx.de>

%description
YaCy is a Java-based peer-2-peer search engine.

It contains a proxy which gathers all the web-pages
you retrieve with it. All private data stays private and
is not indexed or processed in any way. Furthermore
you'll get a individual .yacy-domain which makes you
independent of the traditional DNS system.

Also included in YaCy is a Wiki, a P2P-message-system, a
Blog and a bookmark management system. YaCy can be configured
to set special limits for proxy-users i.e. a maximum quota or
online-time.

%package libx
Summary:		Addon package containing parsers, etc.
License:		GPL
Group:			Application/Internet
Requires:		yacy

%description libx
This package contains the following parsers:
OpenDocument V2, MimeType, Rich Text Format, Word Document,
vCard, rpm, Bzip2, Acrobat Portable Document, RSS/Atom Feed,
Zip, tar, Power Point, gzip

Additionally it allows port forwarding via secure channel,
seed uploading via SCP and provides a SOAP API.

# %prep	# nothing to be done here, ant already prepared everything nicely for us

# %build	# ant did this for us as well... such a nice tool

%pre
# check whether group 'yacy' already exists, if not it will be created
#if ! getent group yacy >> /dev/null; then
#	echo "adding group yacy"
#	groupadd -r yacy
#fi
# check whether user 'yacy' already exists, if not it will be created
if ! getent passwd yacy >> /dev/null; then
	echo "adding user yacy"
	useradd yacy -p `dd count=1 if=/dev/urandom status=noxfer 2> /dev/null | md5sum | cut -c0-15` -r
fi

%install
cd ../..
rm -rf $RPM_BUILD_ROOT

# define directories yacy will use
YACYCDIR="/usr/share/yacy"				# all the other shit
YACYDDIR="/usr/share/doc/yacy"			# documentation
YACYLDIR="/usr/lib/yacy"				# classes / jars
DATADIR="/var/lib/yacy"					# DB, SETTINGS, WORK, etc. - all in DATA
LOGDIR="/var/log/yacy"					# logs of yacy, basically DATA/LOG

install -d ${RPM_BUILD_ROOT}{$YACYCDIR,$YACYDDIR,$YACYLDIR/libx,$DATADIR,$LOGDIR}

# copy all other files
cp -r htroot locales ranking skins ${RPM_BUILD_ROOT}$YACYCDIR/
cp -r defaults classes lib libx ${RPM_BUILD_ROOT}$YACYLDIR/
cp -r doc ${RPM_BUILD_ROOT}$YACYDDIR/
cp *.sh build.properties superseed.txt httpd.mime yacy.badwords.example yacy.logging yacy.stopwords* yacy.yellow ${RPM_BUILD_ROOT}$YACYCDIR/
cp AUTHORS COPYRIGHT ChangeLog gpl.txt readme.txt ${RPM_BUILD_ROOT}$YACYDDIR/

install -m 744 *.sh ${RPM_BUILD_ROOT}$YACYCDIR/				# start/stop/kill scripts
rm -r `find ${RPM_BUILD_ROOT}/ -type d -name '.svn'`		# delete unwanted .svn-folders
rm -r `find ${RPM_BUILD_ROOT}/ -type d -name '.git'`		# delete unwanted .git-folders

# location for init-script
install -d ${RPM_BUILD_ROOT}/etc/init.d/

%post
# link DATA- and LOG-directories to YaCy-folder
ln -s /usr/lib/yacy/classes /usr/share/yacy/classes			# classes linken
ln -s /usr/lib/yacy/lib /usr/share/yacy/lib					# lib linken
ln -s /usr/lib/yacy/libx /usr/share/yacy/libx				# libx linken

ln -s /var/lib/yacy /usr/share/yacy/DATA					# DATA linken
echo "created link /usr/share/yacy/DATA -> /var/lib/yacy"	# user-feedback

if [ ! -L /var/lib/yacy/LOG ]; then
	ln -s /var/log/yacy /var/lib/yacy/LOG					# LOG linken
	echo "created link /var/lib/yacy/LOG -> /var/log/yacy"	# user-feedback
fi

if [ ! -e /var/lib/yacy/SETTINGS ]; then
	mkdir /var/lib/yacy/SETTINGS;
	chown yacy /var/lib/yacy/SETTINGS;
fi
if [ ! -e /etc/yacy ]; then ln -s /var/lib/yacy/SETTINGS /etc/yacy; fi	# SETTINGS linken

chown yacy -R /var/lib/yacy
chown yacy -R /var/log/yacy
chmod +x /usr/share/yacy/startYACY.sh
chmod +x /usr/share/yacy/stopYACY.sh
chmod +x /usr/share/yacy/killYACY.sh

## language check - not wanted
#LNG=""
#LNT=""
#if [ ! -z $LC_TYPE ]; then LNT=${LC_TYPE%_*}; fi
#if [ ! -z $LANG ]; then LNT=${LANG%_*}; fi
#if [ $LNT == de -o $LNT == it -o $LNT == sk ]; then 							# translation exists
#	# test whether settings-directory exists
#	if [ ! -d /var/lib/yacy/SETTINGS ]; then mkdir /var/lib/yacy/SETTINGS; chown yacy:root /var/lib/yacy/SETTINGS; fi
#	echo "htLocaleSelection=$LNT.lng" >> /var/lib/yacy/SETTINGS/yacy.conf
#	chown yacy:root /var/lib/yacy/SETTINGS/yacy.conf
#fi


# we need an init-script
cat > /etc/init.d/yacy <<EOF
#!/bin/sh
# YaCy init script
# Author: Franz Brauße <mike-nought@gmx.de>
# Date: 14.10.2006
# License: Distributed under the terms of the GNU General Public Licens v2
# This file belongs to the YaCy RPM package

# TODO
# - save PID when started in debug-mode

### BEGIN INIT INFO
# Provides:       yacy
# Required-Start: $network
# Required-Stop:  $network
# Default-Start:  3 5
# Default-Stop:
# Description:    yacy is a distributed search engine
#                 config-file is /etc/yacy/yacy.conf
### END INIT INFO

YACY_HOME="/usr/share/yacy"
DATA_HOME="/var/lib/yacy"
PID_FILE="/var/run/yacy.pid"

JAVA=\`which java\ 2> /dev/null\`
if [ ! -x "\$JAVA" ]; then
	echo "The 'java' command is not executable."
	echo "Either you have not installed java or it is not in your PATH"
	#Cron supports setting the path in 
	#echo "Has this script been invoked by CRON?"
	#echo "if so, please set PATH in the crontab, or set the correct path in the variable in this script."
	if [ \$1 == "stop" -a \$2 == "--force" ]; then exit 0; else exit 1; fi
fi

# get arguments for java
if [ -f \$DATA_HOME/SETTINGS/yacy.conf ]; then
	i=\`grep javastart_Xmx \$DATA_HOME/SETTINGS/yacy.conf\`;
	JAVA_MAX="-\${i#javastart_Xmx=}";
	i=\`grep javastart_Xms \$DATA_HOME/SETTINGS/yacy.conf\`;
	JAVA_MIN="-\${i#javastart_Xms=}";
fi

CLASSPATH="\$YACY_HOME/classes:."
for N in \$YACY_HOME/lib/*.jar; do CLASSPATH="\$CLASSPATH:\$N"; done	
if [ -d \$YACY_HOME/libx ]; then
	for N in \$YACY_HOME/libx/*.jar; do CLASSPATH="\$CLASSPATH:\$N"; done
fi

ME=\$0
if [ "\$1" = "restart" ]; then
	shift
	\$ME stop
	\$ME start \$*
	exit 0
fi

WTF=\$1; shift
if [ "\$1" == "--max" ]; then JAVA_MAX="-Xmx\$2"; shift; shift; fi
if [ "\$1" == "--min" ]; then JAVA_MIN="-Xms\$2"; shift; shift; fi
if [ "\$1" == "--nice" ]; then NICE="nice -n \$2"; shift; shift; fi
if [ "\$1" == "--debug" ]; then DEBUG="-d"; shift; fi
shift
cd \$YACY_HOME

if [ "\$JAVA_MAX" == "-" ]; then JAVA_MAX=""; fi
if [ "\$JAVA_MIN" == "-" ]; then JAVA_MIN=""; fi

case "\$WTF" in
	start)
		if [ -e \$PID_FILE ]; then
			echo "YaCy seems to be running. If not, delete the file \$PID_FILE."
			exit 1
		fi
		echo -n "Starting YaCy... "
		CMD="sudo -u yacy \$NICE \$JAVA -Djava.awt.headless=true \$JAVA_MAX \$JAVA_MIN -classpath \$CLASSPATH yacy \$* & pid=\\\$!; echo \\\$pid > \$PID_FILE"
		if [ \$DEBUG ]; then
			\$CMD
		else
			eval \$CMD &> /dev/null
		fi
		echo "done"
		exit 0
		;;
	stop)
		echo "Shutting down YaCy, please be patient. Waiting maximal 60 seconds before killing the process... "
		\$YACY_HOME/stopYACY.sh
		if [ ! -e \$PID_FILE ]; then
			echo "PID-file not found: YaCy doesn't appear to be running. You eventually have to kill the process yourself."
			exit 1
		else
			pid=\`cat \$PID_FILE\`
		fi
		i=0
		while test -d /proc/\$pid; do
			sleep 2;
			i=\$[i+2];
			if [ \$i -ge 60 ]; then
				echo -n "Now killing: ";
				kill -9 \$pid;
				echo "done"
				break;
			fi
		done
		rm \$PID_FILE
		exit 0
		;;
	status)
		echo -n "YaCy is "
		if [ ! -e \$PID_FILE -o ! -d /proc/`cat \$PID_FILE` ]; then
			echo -n "not "
		fi
		echo "running."
		exit 0
		;;
	kill)
		echo -n "Killing YaCy: "
		kill -9 \`cat \$PID_FILE\`
		rm \$PID_FILE
		echo "done"
		exit 0
		;;
	*)
		echo "Usage: /etc/init.d/yacy {start|restart} [--max RAM] [--min RAM] [--nice LEVEL] [--debug] [YACY_ARGUMENTS]"
		echo "                        {stop|kill|status}"
		echo ""
		echo "  --max RAM[{k|M|G}]    Maximum RAM YaCy may use"
		echo "  --min RAM[{k|M|G}]    Initial RAM YaCY shall use"
		echo "  --nice LEVEL          Enter desired nice-level of YaCy-process"
		echo "  --debug               Active mode, process will not be backgrounded"
		exit 1
		;;
esac
EOF
#chmod +x /etc/init.d/yacy
chmod 744 /etc/init.d/yacy

%preun
if [ -x /etc/init.d/yacy ]; then /etc/init.d/yacy stop --force; fi

%postun
rm -r /usr/share/yacy
rm -r /usr/lib/yacy
rm -r /usr/share/doc/yacy
if [ -e /etc/init.d/yacy ]; then rm -f /etc/init.d/yacy; fi
if [ `getent passwd yacy` ]; then userdel yacy &> /dev/null; fi

%clean
rm -rf $RPM_BUILD_ROOT

%files
%config %dir /var/lib/yacy/
%dir /var/log/yacy/
%attr (755,root,root) /usr/share/yacy/startYACY.sh
%attr (755,root,root) /usr/share/yacy/stopYACY.sh
%attr (755,root,root) /usr/share/yacy/killYACY.sh
%defattr(644,root,root,755)
/usr/share/yacy/*
/usr/lib/yacy/lib/*
/usr/lib/yacy/classes/*
%doc /usr/share/doc/yacy/*

%changelog
* Fri Oct 20 2006 Franz Brauße <mike-nought@gmx.de>
- added Packager-Tag
- marked documentation-files as such
- fixed permissions

* Sat Oct 14 2006 Franz Brauße <mike-nought@gmx.de>
- initial spec file based on yacy-0.48-3.spec
- some adaptions for build with ant
