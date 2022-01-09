#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh


for i in "$YACY_DATA_PATH/INDEX"/* ; do
  if [ -d "$i" ]; then
  	  echo "Checking Solr index at $i..."
      java -cp "$YACY_APP_PATH/lib/*" org.apache.lucene.index.CheckIndex  "$i/SEGMENTS/solr_6_6/collection1/data/index/" -exorcise
  fi
done
cd ..
