port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
if which curl &>/dev/null; then
  curl -s "http://localhost:$port/yacysearch.rss?resource=local&verify=false&query=$1" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
elif which wget &>/dev/null; then
  wget -q -O - "http://localhost:$port/yacysearch.rss?resource=local&verify=false&query=$1" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
else
  echo "Neither curl nor wget installed!"
  exit 1
fi

