#!/bin/sh
cd "`dirname $0`"
S=`date "+%s"`
C=0
for N in `cat $1`; do 
  echo search for $N:
  ./search.sh $N | head -1
  C=$(($C+1))
  T=`date "+%s"`
  echo runtime = $(($T-$S)) seconds, count = $C, time per query = $((1000*($T-$S)/$C)) milliseconds
  #sleep 1
done

