#!/bin/bash

set -e

function get_packagename() {
	local INPUT_DIR=$1

	if [ ! -d "$INPUT_DIR" ]; then
		echo "input directory $INPUT_DIR does not exist."
		exit 1
	fi	
	RESULT=$(cat "$INPUT_DIR/info.json" | jq ".package" | sed -e 's/^"//' -e 's/"$//')
}

function push() {
	local INPUT_DIR=$1
	local DEVICE=$2

	if [ ! -d "$INPUT_DIR" ]; then
		echo "input directory $INPUT_DIR does not exist."
		exit 1
	fi	

	get_packagename $INPUT_DIR
	local PACKAGE=$RESULT
	echo "Package name: $PACKAGE"

	echo ">>>> Removing previous install (if exists)."
	$ANDROID_HOME/platform-tools/adb -s $DEVICE uninstall $PACKAGE

	echo ">>>> Install the current version."
	local APK="$INPUT_DIR/instrumented.apk"
	$ANDROID_HOME/platform-tools/adb -s $DEVICE install $APK
	echo ">>>> Done."
}

function remove() {
	local INPUT_DIR=$REMOVE_INPUT_DIR
	local DEVICE=$REMOVE_DEVICE

	if [ ! -d "$INPUT_DIR" ]; then
		echo "input directory $INPUT_DIR does not exist."
		exit 1
	fi	

	get_packagename $INPUT_DIR
	local PACKAGE=$RESULT
	echo "Package name: $PACKAGE"

	echo ">>>> Removing previous install (if exists)."
	local APK="$INPUT_DIR/instrumented.apk"
	$ANDROID_HOME/platform-tools/adb -s $DEVICE uninstall $PACKAGE
	echo ">>>> Done."
}

function run_explorer() {
	local INPUT_DIR=$1
	local OUTPUT_DIR=$2
	local DEVICE_TYPE=$3
	local DEVICE_NAME=$4
	local PORT=$5
	local TIME_OUT=$6
	local OPTION="${@:7}"

	local OUTPUT="$OUTPUT_DIR/log/explorer.log"

	# Run explorer
	if [ -f "$OUTPUT" ]; then
		rm $OUTPUT
	fi
	echo ">>>> initiate test explorer"
	echo "     Check explorer.out file for the output."
	echo "     You can use 4$PORT for debugging."

	local CP=./ui-explorer/target/ui-explorer-1.0-SNAPSHOT.jar:./ui-explorer/lib/*
	cp ui-explorer/src/main/resources/view_template.html $OUTPUT_DIR/index.html

	echo $INPUT_DIR

	local DEBUG="-Xrunjdwp:transport=dt_socket,address=4$PORT,server=y,suspend=n"
	java -cp $CP $DEBUG  smarthand.ui_explorer.CommandLine $INPUT_DIR $OUTPUT_DIR $DEVICE_TYPE $DEVICE_NAME $PORT $TIME_OUT $OPTION | tee $OUTPUT_DIR/log/explorer.log
}

INPUT_DIR=$1
OUTPUT_DIR=$2
DEVICE_NAME=$3
PORT=$4
TIME_OUT=360000
STRATEGY=$5
OPTION="${@:6}"

APP_NAME=$(basename $INPUT_DIR)
OUTPUT_BASE=$OUTPUT_DIR
LOG_DIR="$OUTPUT_BASE/log"
IMG_DIR="$OUTPUT_BASE/image"

# Check existence of the app directory
if [ ! -d "$INPUT_DIR" ]; then
	echo "Application directory does not exist"
	exit 1
fi

if [ ! -d "$OUTPUT_BASE" ]; then
	mkdir -p $OUTPUT_BASE
fi


# Install application
push $INPUT_DIR $DEVICE_NAME
RC=$?
if [[ $RC != 0 ]]; then echo "push failed"; exit $RC; fi
REMOVE_INPUT_DIR=$INPUT_DIR
REMOVE_DEVICE=$DEVICE_NAME
trap remove EXIT

# Check existence of the output directory
if [ -d "$OUTPUT_BASE" ]; then
   rm -rf $OUTPUT_BASE
fi

mkdir $OUTPUT_BASE
mkdir $LOG_DIR
mkdir $IMG_DIR


# Write meta information
META="{\
    \"app\":\"$APP_NAME\",\
    \"option\":\"$OPTION\",\
    \"timeout\":\"$TIME_OUT\"}"
echo $META
echo $META > "$OUTPUT_BASE/meta.txt"


# Run driver
$ANDROID_HOME/platform-tools/adb -s $DEVICE_NAME forward tcp:$PORT tcp:9090

echo ">>>> Pushing UIAutomatr."
touch ./ui-driver/target/ui-driver-1.0-SNAPSHOT.dex.jar
$ANDROID_HOME/platform-tools/adb -s $DEVICE_NAME push ./ui-driver/target/ui-driver-1.0-SNAPSHOT.dex.jar /data/local/tmp/

# Run explorer
run_explorer $INPUT_DIR $OUTPUT_BASE -d $DEVICE_NAME $PORT $TIME_OUT $STRATEGY 1 $OPTION
