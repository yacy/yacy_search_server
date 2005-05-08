#!/bin/sh
#
# THIS IS THE YACY MAKE-RELEASE SCRIPT
# YOU CAN USE IT TO COMPILE YOUR OWN RELEASE
# THE TARGET OF THE COMPILATION CAN BE FOUND
# IN THE 'RELEASE' DIRECTORY AFTERWARDS
# -----------------------------------------
# This Software is Copyrighted
# (C) by Michael Peter Christen; mc@anomic.de
# first published on http://www.anomic.de
# Frankfurt, Germany, 2005
# last major change: 08.05.2005
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
# Using this software in any meaning (reading, learning, copying, compiling,
# running) means that you agree that the Author(s) is (are) not responsible
# for cost, loss of data or any harm that may be caused directly or indirectly
# by usage of this softare or this documentation. The usage of this software
# is on your own risk. The installation and usage (starting/running) of this
# software may allow other people or application to access your computer and
# any attached devices and is highly dependent on the configuration of the
# software which must be done by the user of the software; the author(s) is
# (are) also not responsible for proper configuration and usage of the
# software, even if provoked by documentation provided together with
# the software.
#
# Any changes to this file according to the GPL as documented in the file
# gpl.txt aside this file in the shipment you received can be done to the
# lines that follows this copyright notice here, but changes must not be
# done inside the copyright notive above. A re-distribution must contain
# the intact and unchanged copyright notice.
# Contributions and changes to the program code must be marked as such.

# define variables
version='0.372'
datestr=`date +%Y%m%d`
#release='yacy_v'$version'_'$datestr
release='yacy_dev_v'$version'_'$datestr
extralibs='yacy_libx'
target='RELEASE'
classes='classes'
lib='lib'
libx='libx'
source='source'
doc='doc'
data='DATA'
mainclass='yacy.java'

echo "[`date +%Y/%m/%d\ %H:%M:%S`] Building yacy version $version - $datestr ..."

classpath="$classes"
for N in `ls -1 lib/*.jar`; do classpath="$classpath:$N"; done
for N in `ls -1 libx/*.jar`; do classpath="$classpath:$N"; done
echo "[`date +%Y/%m/%d\ %H:%M:%S`] Using classpath: $classpath"

#classpath='$classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar:libx/PDFBox-0.7.1.jar:libx/log4j-1.2.9.jar:libx/tm-extractors-0.4.jar'

mkdir -p $release
mkdir -p $extralibs

# clean up
echo "[`date +%Y/%m/%d\ %H:%M:%S`] Clean up ..."
rm -Rf $target &> /dev/null
rm -Rf $classes &> /dev/null
rm $doc/release.txt &> /dev/null

rm $source/*.class &> /dev/null
rm $source/de/anomic/kelondro/*.class &> /dev/null
rm $source/de/anomic/tools/*.class &> /dev/null
rm $source/de/anomic/data/*.class &> /dev/null
rm $source/de/anomic/htmlFilter/*.class &> /dev/null
rm $source/de/anomic/http/*.class &> /dev/null
rm $source/de/anomic/net/*.class &> /dev/null
rm $source/de/anomic/plasma/*.class &> /dev/null
rm $source/de/anomic/server/*.class &> /dev/null
rm $source/de/anomic/yacy/*.class &> /dev/null

rm $source/*~ &> /dev/null
rm $source/de/anomic/kelondro/*~ &> /dev/null
rm $source/de/anomic/tools/*~ &> /dev/null
rm $source/de/anomic/data/*~ &> /dev/null
rm $source/de/anomic/htmlFilter/*~ &> /dev/null
rm $source/de/anomic/http/*~ &> /dev/null
rm $source/de/anomic/net/*~ &> /dev/null
rm $source/de/anomic/plasma/*~ &> /dev/null
rm $source/de/anomic/server/*~ &> /dev/null
rm $source/de/anomic/yacy/*~ &> /dev/null
rm doc/*~ &> /dev/null
rm addon/*~ &> /dev/null
rm htroot/*~ &> /dev/null
rm htroot/yacy/*~ &> /dev/null
rm htroot/htdocsdefault/*~ &> /dev/null
rm htroot/env/*~ &> /dev/null
rm htroot/env/grafics/*~ &> /dev/null
rm htroot/env/templates/*~ &> /dev/null

# make classes directory
echo "[`date +%Y/%m/%d\ %H:%M:%S`] make classes directory ..."
mkdir -p $classes

# make release directory
echo "[`date +%Y/%m/%d\ %H:%M:%S`] make release directory ..."
mkdir -p $target

# compile core
echo "[`date +%Y/%m/%d\ %H:%M:%S`] compile core ..."
mv -f $source/$mainclass $source/$mainclass.orig
sed `echo 's/@REPL_DATE@/'$datestr'/'` $source/$mainclass.orig > $source/$mainclass.sed1
sed `echo 's/@REPL_VERSION@/'$version'/'` $source/$mainclass.sed1 > $source/$mainclass
rm $source/$mainclass.sed1
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/tools/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/net/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/htmlFilter/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/server/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/http/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/kelondro/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/data/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/plasma/parser/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/plasma/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/de/anomic/yacy/*.java
javac -classpath $classpath -sourcepath $source -d $classes -g $source/$mainclass
mv -f $source/$mainclass.orig $source/$mainclass

# compile server pages
echo "[`date +%Y/%m/%d\ %H:%M:%S`] compile server pages ..."
javac -classpath $classpath -sourcepath htroot -d htroot -g htroot/*.java
javac -classpath $classpath -sourcepath htroot/yacy -d htroot/yacy -g htroot/yacy/*.java
javac -classpath $classpath -sourcepath htroot/htdocsdefault -d htroot/htdocsdefault -g htroot/htdocsdefault/*.java

# copy classes
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy classes ..."
mkdir -p $release/$classes
cp -R $classes/* $release/$classes/

# copy libs
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy libs ..."
mkdir -p $release/$lib
cp -R $lib/* $release/$lib/
rm -fR `find $release/$lib/ | grep svn`
mkdir -p $extralibs/$libx
cp -R $libx/* $extralibs/$libx/
rm -fR `find $extralibs/$libx/ | grep svn`

# copy configuration files
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy configuration files ..."
cp yacy.init $release
cp yacy.yellow $release
#cp yacy.black $release
cp yacy.blue $release
cp yacy.stopwords $release
cp httpd.mime $release
cp superseed.txt $release

# copy wrappers
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy wrappers ..."
cp startYACY.command $release
cp startYACY.bat $release
cp startYACY_noconsole.bat $release
cp startYACY.sh $release
cp stopYACY.command $release
cp stopYACY.bat $release
cp stopYACY.sh $release
cp killYACY.sh $release
cp makerelease.sh $release

# copy documentation
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy documentation ..."
cp readme.txt $release
cp gpl.txt $release
mkdir -p $release/$doc
mkdir -p $release/$doc/grafics
cp $doc/*.css $release/$doc/
cp $doc/*.js $release/$doc/
cp $doc/*.html $release/$doc/
cp $doc/*.txt $release/$doc/
cp $doc/grafics/*.gif $release/$doc/grafics/
#cp $doc/grafics/*.ico $release/$doc/grafics/
#cp $doc/grafics/*.jpg $release/$doc/grafics/
rm -fR `find $release/$doc/ | grep svn`

# copy source code
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy source code ..."
mkdir -p $release/$source
cp -R $source/* $release/$source/
rm -fR `find $release/$source/ | grep svn`

# copy server pages
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy server pages ..."
mkdir -p $release/htroot
mkdir -p $release/htroot/yacy
mkdir -p $release/htroot/htdocsdefault
mkdir -p $release/htroot/env
mkdir -p $release/htroot/env/grafics
mkdir -p $release/htroot/env/templates
mkdir -p $release/htroot/proxymsg
cp htroot/*.rss $release/htroot/
cp htroot/*.xml $release/htroot/
cp htroot/*.html $release/htroot/
cp htroot/*.java $release/htroot/
cp htroot/*.class $release/htroot/
cp htroot/*.ico $release/htroot/
cp htroot/yacy/*.html $release/htroot/yacy/
cp htroot/yacy/*.java $release/htroot/yacy/
cp htroot/yacy/*.class $release/htroot/yacy/
cp htroot/htdocsdefault/*.html $release/htroot/htdocsdefault/
cp htroot/htdocsdefault/*.java $release/htroot/htdocsdefault/
cp htroot/htdocsdefault/*.class $release/htroot/htdocsdefault/
cp htroot/env/*.css $release/htroot/env/
cp htroot/env/grafics/* $release/htroot/env/grafics/
cp htroot/env/templates/*.template $release/htroot/env/templates/
cp htroot/proxymsg/*.html $release/htroot/proxymsg/
rm -fR `find $release/htroot/ | grep svn`

# copy add-on's
echo "[`date +%Y/%m/%d\ %H:%M:%S`] copy add-on's ..."
mkdir -p $release/addon
cp addon/* $release/addon/

# set access rights
echo "[`date +%Y/%m/%d\ %H:%M:%S`] set access rights ..."
chmod 644 $release/*
chmod 755 $release/htroot
chmod 644 $release/htroot/*
chmod 755 $release/htroot/env
chmod 644 $release/htroot/env/*
chmod 755 $release/htroot/env/grafics
chmod 644 $release/htroot/env/grafics/*
chmod 755 $release/htroot/env/templates
chmod 644 $release/htroot/env/templates/*
chmod 755 $release/htroot/yacy
chmod 644 $release/htroot/yacy/*
chmod 755 $release/htroot/htdocsdefault
chmod 644 $release/htroot/htdocsdefault/*
chmod 755 $release/htroot/proxymsg
chmod 644 $release/htroot/proxymsg/*
chmod 755 $release/$source
chmod 644 $release/$source/*.java
chmod 755 $release/$source/de
chmod 755 $release/$source/de/anomic
chmod 755 $release/$source/de/anomic/*
chmod 755 $release/$source/de/anomic/plasma/parser
chmod 644 $release/$source/de/anomic/kelondro/*.java
chmod 644 $release/$source/de/anomic/tools/*.java
chmod 644 $release/$source/de/anomic/data/*.java
chmod 644 $release/$source/de/anomic/htmlFilter/*.java
chmod 644 $release/$source/de/anomic/http/*.java
chmod 644 $release/$source/de/anomic/net/*.java
chmod 644 $release/$source/de/anomic/plasma/*.java
chmod 644 $release/$source/de/anomic/plasma/parser/*.java
chmod 644 $release/$source/de/anomic/server/*.java
chmod 644 $release/$source/de/anomic/yacy/*.java
chmod 755 $release/$classes
chmod 644 $release/$classes/*
chmod 755 $release/$classes/de
chmod 755 $release/$classes/de/anomic
chmod 755 $release/$classes/de/anomic/*
chmod 644 $release/$classes/de/anomic/kelondro/*.class
chmod 644 $release/$classes/de/anomic/tools/*.class
chmod 644 $release/$classes/de/anomic/data/*.class
chmod 644 $release/$classes/de/anomic/htmlFilter/*.class
chmod 644 $release/$classes/de/anomic/http/*.class
chmod 644 $release/$classes/de/anomic/net/*.class
chmod 644 $release/$classes/de/anomic/plasma/*.class
chmod 644 $release/$classes/de/anomic/server/*.class
chmod 644 $release/$classes/de/anomic/yacy/*.class
chmod 755 $release/$lib
chmod 644 $release/$lib/*
chmod 755 $release/$doc
chmod 644 $release/$doc/*
chmod 755 $release/$doc/grafics
chmod 644 $release/$doc/grafics/*
chmod 755 $release/*.command
chmod 755 $release/*.sh
chmod 755 $release/addon
chmod 755 $extralibs/$libx
chmod 644 $extralibs/$libx/*

# compress files
echo "[`date +%Y/%m/%d\ %H:%M:%S`] compress files ..."
tar -cf $release.tar $release
rm -Rf $release
gzip -9 $release.tar
mv $release.tar.gz $target
tar -cf $extralibs.tar $extralibs
rm -Rf $extralibs
gzip -9 $extralibs.tar
mv $extralibs.tar.gz $target

# make release test file:
# this file must be copied later on to
# www.yacy.net/yacy/
echo "[`date +%Y/%m/%d\ %H:%M:%S`] make release file ..."
echo $version > $doc/release.txt

# finished
echo "[`date +%Y/%m/%d\ %H:%M:%S`] finished."
echo "[`date +%Y/%m/%d\ %H:%M:%S`] created $target/$release.tar.gz"
echo "[`date +%Y/%m/%d\ %H:%M:%S`] created $target/$extralibs.tar.gz"
