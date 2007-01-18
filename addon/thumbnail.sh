#!/bin/sh
cd /tmp
FILE=`tempfile`.html
wget "$1" -O $FILE -k
convert $FILE -resize 168x128 "$2"
if [ -e "${2%.png}"-0.png ];then
	mv "${2%.png}"-0.png "${2}"
	rm "${2%.png}"-[0-9]*.png
fi
rm $FILE
