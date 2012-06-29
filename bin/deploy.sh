#!/bin/bash
cd "`dirname $0`"
cd ..
./stopYACY.sh
#./killYACY.sh
cd DATA/RELEASE/
rm ../../lib/*
rm -Rf yacy
tar xfz `basename $1`
cp -Rf yacy/* ../../
rm -Rf yacy
cd ../../
chmod 755 *.sh
chmod 755 bin/*.sh
nohup ./startYACY.sh -l
