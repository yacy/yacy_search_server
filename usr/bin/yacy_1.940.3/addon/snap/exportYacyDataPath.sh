#!/usr/bin/env sh
# Check the snap configuration to properly fill the YACY_DATA_PATH environment variable

DATA_VERSIONED="$(snapctl get data.versioned)"
if [ "$DATA_VERSIONED" = "false" ]; then
	# YaCy data is in the Snap common (non versioned) user data
	YACY_DATA_PATH="$SNAP_USER_COMMON/DATA"
else
	# Defaults : YaCy data is in the Snap versioned user data
	YACY_DATA_PATH="$SNAP_USER_DATA/DATA"
fi
export YACY_DATA_PATH