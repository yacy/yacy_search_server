#!/bin/bash
cd "`dirname $0`"
./apicall.sh /IndexImportMediawiki_p.html?file=$1 > /dev/null
