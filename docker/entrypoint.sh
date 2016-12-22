#!/bin/bash

trap "{ 
echo Exit TRAP snapped

/opt/yacy_search_server/stopYACY.sh

exit 0

}" SIGTERM

/opt/yacy_search_server/startYACY.sh -d &

wait
