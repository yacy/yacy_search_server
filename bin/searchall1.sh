#!/usr/bin/env sh
# Perform a search on the Solr index of each active principal and senior peers known by the given peer
# $1 a peer address as host:port
# $2 search term

TMPFILE=`mktemp -t searchXXX` || exit 1
for address in `./up.sh $1`; 
do 
	sleep 0.01
	./search1.sh -s `printf "$address" | tr -d '\r\n'` "$2" >> "$TMPFILE" &
done
sleep 2
cat $TMPFILE
