#!/bin/sh
cd `dirname $0`
java -classpath classes yacy -shutdown
echo "please wait until the YaCy daemon process terminates"
echo "you can monitor this with 'tail -f yacy.log' and 'fuser yacy.log'"