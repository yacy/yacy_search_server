#!/usr/bin/env sh

# Wrapper script for Snap package specific instructions before calling the startYACY.sh script

# Check the snap configuration to properly fill the YACY_DATA_PATH environment variable
echo "*******************************************************************************"
DATA_VERSIONED="$(snapctl get data.versioned)"
if [ "$DATA_VERSIONED" = "false" ]; then
	# YaCy data is in the Snap common (non versioned) user data
	YACY_PARENT_DATA_PATH="$SNAP_USER_COMMON"
	
	if [ ! -d "$YACY_PARENT_DATA_PATH/DATA" ] && [ -d "$SNAP_USER_DATA/DATA" ]; then
		if [ -f "$SNAP_USER_DATA/DATA/yacy.running" ]; then
			echo "****  Warning : can not move YaCy snap data from versioned to non versioned folder as YaCy appears to be already running." 
		else
			(mv "$SNAP_USER_DATA/DATA" "$YACY_PARENT_DATA_PATH" && \
			 echo "*** YaCy snap data moved from versioned to non versioned snap data." ) \
			|| echo "****  Warning : could not move YaCy snap data from versioned to non versioned folder."
		fi
	fi
	
	echo "**  YaCy snap is using non versioned data at $YACY_PARENT_DATA_PATH/DATA"
	echo "**  You can configure it to use snap versioned data with the following command :"
	echo "**  sudo snap set $SNAP_NAME data.versioned=true"
else
	# Defaults : YaCy data is in the Snap versioned user data
	YACY_PARENT_DATA_PATH="$SNAP_USER_DATA"
	
	if [ ! -d "$YACY_PARENT_DATA_PATH/DATA" ] && [ -d "$SNAP_USER_COMMON/DATA" ]; then
		if [ -f "$SNAP_USER_COMMON/DATA/yacy.running" ]; then
			echo "****  Warning : can not move YaCy snap data from non versioned to versioned folder as YaCy appears to be already running." 
		else
			(mv "$SNAP_USER_COMMON/DATA" "$YACY_PARENT_DATA_PATH" && \
			 echo "*** YaCy snap data moved from non versioned to versioned snap data." ) \
			|| echo "****  Warning : could not move YaCy snap data from non versioned to versioned folder."
		fi
	fi

	echo "**  YaCy snap is using versioned data at $YACY_PARENT_DATA_PATH/DATA"
	echo "**  To reduce disk usage, you can configure it to use snap non versioned data with the following command :"
	echo "**  sudo snap set $SNAP_NAME data.versioned=false"
fi
export YACY_PARENT_DATA_PATH

sh "$SNAP/yacy/startYACY.sh" -f -s "$YACY_PARENT_DATA_PATH" 