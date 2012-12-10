#!/bin/bash
cd "`dirname $0`"
if which curl &>/dev/null; then
  curl -s "http://$1/Network.xml?page=1&ip=" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}';
elif which wget &>/dev/null; then
  wget -q -O - "http://$1/Network.xml?page=1&ip=" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}';
else
  exit 1
fi

