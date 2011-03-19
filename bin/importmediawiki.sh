#!/bin/bash
cd "`dirname $0`"
./apicall.sh /IndexImportWikimedia_p.html?file=$1 > /dev/null
