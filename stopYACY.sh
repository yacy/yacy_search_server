#!/bin/sh
cd `dirname $0`

if [ -x `which wget` ];
then
port=`cat DATA/SETTINGS/httpProxy.conf |grep "^port="|sed "s/.*=//"`
pw=`cat DATA/SETTINGS/httpProxy.conf |grep "^adminAccountBase64MD5="|sed "s/.*=//"`
wget -q --header "Authorization: realm=$pw" http://localhost:$port/Steering.html?shutdown=true -O /dev/null
else

# generating the proper classpath
CLASSPATH=""
for N in `ls -1 lib/*.jar`; do CLASSPATH="$CLASSPATH$N:"; done	
for N in `ls -1 libx/*.jar`; do CLASSPATH="$CLASSPATH$N:"; done

java -classpath classes:htroot:$CLASSPATH yacy -shutdown
fi

echo "please wait until the YaCy daemon process terminates"
echo "you can monitor this with 'tail -f DATA/LOG/yacy00.log' and 'fuser log/yacy00.log'"
