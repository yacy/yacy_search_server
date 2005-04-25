#!/bin/sh
cd `dirname $0`
java -classpath classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar:libx/PDFBox-0.7.1.jar:libx/log4j-1.2.9.jar:libx/tm-extractors-0.4.jar yacy -shutdown
echo "please wait until the YaCy daemon process terminates"
echo "you can monitor this with 'tail -f yacy.log' and 'fuser yacy.log'"