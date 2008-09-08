port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
if which curl &>/dev/null; then
  httpfetcher="curl -s"
elif which wget &>/dev/null; then
  httpfetcher="wget -q -O - "
else
  echo "Neither curl nor wget installed!"
  exit 1
fi
eval $httpfetcher "http://localhost:$port/yacysearch.rss?query=$1&resource=local&verify=false" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
