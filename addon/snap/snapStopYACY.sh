#!/usr/bin/env sh

# Wrapper script for Snap package specific instructions before calling the stopYACY.sh script

cd "`dirname $0`"

. ./exportYacyDataPath.sh

sh "$SNAP/yacy/stopYACY.sh"