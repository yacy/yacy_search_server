#!/bin/bash
cd "`dirname $0`"
if which curl &>/dev/null; then
  while getopts "ys" opt; do
    case $opt in
    y)
      shift;
      curl -s "http://$1/yacysearch.rss?query=$2" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
      ;;
    s)
      shift;
      curl -s "http://$1/solr/select?q=text_t:$2&start=0&rows=100&fl=sku&wt=rss" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
      ;;
    esac
  done
elif which wget &>/dev/null; then
  while getopts "ys" opt; do
    case $opt in
    y)
      shift;
      wget -q -O - "http://$1/yacysearch.rss?query=$2" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
      ;;
    s)
      shift;
      wget -q -O - "http://$1/solr/select?q=text_t:$2&start=0&rows=100&fl=sku&wt=rss" | awk '/^<link>/{ gsub("<link>","" );gsub("<\/link>","" ); print $0 }'
      ;;
    esac
  done
else
  echo "Neither curl nor wget installed!"
  exit 1
fi

