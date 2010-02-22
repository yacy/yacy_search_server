#!/bin/sh
cd "`dirname $0`"
./searchtest.sh ../test/words/searchtest.words.aa &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ab &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ac &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ad &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ae &
sleep 1
./searchtest.sh ../test/words/searchtest.words.af &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ag &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ah &
sleep 1
./searchtest.sh ../test/words/searchtest.words.ai &
sleep 1
./searchtest.sh ../test/words/searchtest.words.aj &

