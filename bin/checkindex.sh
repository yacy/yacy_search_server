#!/bin/bash
cd "`dirname $0`/.."
for i in DATA/INDEX/* ; do
  if [ -d "$i" ]; then
      java -cp 'lib/*' org.apache.lucene.index.CheckIndex  $i/SEGMENTS/solr_46/collection1/data/index/ -fix
  fi
done
cd ..
