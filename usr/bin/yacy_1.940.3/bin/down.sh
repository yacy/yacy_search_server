#!/usr/bin/env sh
# List the passive senior peers known by the local peer or by the peer at the given address
# $1 : (optional) the peer address as host:port

cd "`dirname $0`"

if [ -z "$1" ]; then
	# Request the local peer
	./apicall.sh "/Network.xml?page=2" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}'
else
	# Request the given peer
	if which curl > /dev/null; then
  		curl -sSf "http://$1/Network.xml?page=2" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}'
	elif which wget > /dev/null; then
  		wget -nv -t 1 --timeout=120 "http://$1/Network.xml?page=2" -O - | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}'
	else
  		echo "Please install curl or wget" > /dev/stderr
  		exit 1
	fi
fi