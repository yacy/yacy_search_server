#!/bin/sh
cd "`dirname $0`"
./searchtest.sh searchtest.words.aa &
sleep 1
./searchtest.sh searchtest.words.ab &
sleep 1
./searchtest.sh searchtest.words.ac &
sleep 1
./searchtest.sh searchtest.words.ad &
sleep 1
./searchtest.sh searchtest.words.ae &
sleep 1
./searchtest.sh searchtest.words.af &
sleep 1
./searchtest.sh searchtest.words.ag &
sleep 1
./searchtest.sh searchtest.words.ah &
sleep 1
./searchtest.sh searchtest.words.ai &
sleep 1
./searchtest.sh searchtest.words.aj &

