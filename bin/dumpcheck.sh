#!/bin/bash
cd "`dirname $0`"
java -ea -cp ../classes:../lib/yacycore.jar net.yacy.kelondro.logging.ThreadDump -f $1
