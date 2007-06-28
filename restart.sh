#!/bin/sh

# this is the restart process script that is started from YaCy
# in case that YaCy is terminated with the restart option

# navigate into the own directory path
cd `dirname $0`

# waiting for shutdown
while [ -e DATA/yacy.running ]; do
sleep 1
done

# shutdown complete, start up yacy
./startYaCy.sh
