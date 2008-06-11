#!/bin/sh
S=`date "+%s"`
C=0
for N in `cat searchtest.words`; do 
curl -s "http://localhost:8080/yacysearch.rss?query=$N&resource=local&verify=false" | grep link
C=$(($C+1))
done
T=`date "+%s"`
echo runtime = $(($T-$S)) seconds, count = $C, time per query = $((1000*($T-$S)/$C)) milliseconds
