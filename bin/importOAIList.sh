#!/bin/sh
cd "`dirname $0`"
for N in `cat $1`; do 
  echo import of $N:
  ./apicall.sh /IndexImportOAIPMH_p.html?urlstart=$N > /dev/null
  C=$(($C+1))
done


