#!/bin/sh
cd "`dirname $0`"
S=`date "+%s"`
C=0
for N in `cat $1`; do 
  echo search for $N:
  ./localsearch.sh $N > /dev/null
  C=$(($C+1))
done
T=`date "+%s"`
echo runtime = $(($T-$S)) seconds, count = $C, time per query = $((1000*($T-$S)/$C)) milliseconds
