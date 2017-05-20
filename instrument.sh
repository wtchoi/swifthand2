#!/bin/bash

INPUT_APK_PATH=$1
INPUT_APK_NAME=$(basename "$INPUT_APK_PATH")

OUTPUT_BASE_DIR="apps/inst"
OUTPUT_DIR="$OUTPUT_BASE_DIR/$INPUT_APK_NAME"
LOG="$OUTPUT_DIR/inst.log"

set -e

# Check existence of the input file
if [ ! -f "$INPUT_APK_PATH" ]; then
    echo "File \"$1\" does not exist"
	exit 1
fi

if [ ! -d "$OUTPUT_BASE_DIR" ]; then
   mkdir $OUTPUT_BASE_DIR
fi

if [ ! -d "$OUTPUT_DIR" ]; then
    mkdir $OUTPUT_DIR
fi

# Compile the instrumentation tool and logger library
#echo ">>>> Compiling the instrumentation tool and logger library"
#mvn package

# Instrument xml
echo ">>>> Instrumenting the xml file" >>$LOG 2>&1
XML_CP="./preprocess/target/preprocess-1.0-SNAPSHOT.jar:./preprocess/lib/*"
java -cp $XML_CP smarthand.preprocess.MyXmlInst $INPUT_APK_PATH $OUTPUT_BASE_DIR >$LOG 2>&1

# Run instrumentation
echo ">>>> Instrumenting the application" >>$LOG 2>&1
CP=./instrument/target/instrument-1.0-SNAPSHOT.jar:./instrument/lib/soot/*:./instrument/lib/json-20140107.jar
SOOT_CP=$JAVA_HOME/jre/lib/rt.jar:./instrument/resource/android.jar:./logger/target/logger-1.0-SNAPSHOT.jar
java -cp $CP smarthand.instrument.MyInst -no-bodies-for-excluded -android-jars "./instrument/resource" -cp $SOOT_CP -process-dir "$OUTPUT_DIR/preprocessed.apk" >>$LOG 2>&1

# Re-Signing the application
echo ">>>> Resigning the application" >>$LOG 2>&1
./signing.sh ./sootOutput/preprocessed.apk >>$LOG 2>&1

# Move it to the current directory
echo ">>>> Copying the result" >>$LOG 2>&1
mv ./sootOutput/preprocessed.apk "$OUTPUT_DIR/instrumented.apk" >>$LOG 2>&1
mv ./sootOutput/method_table "$OUTPUT_DIR/method_table" >>$LOG 2>&1
mv ./sootOutput/branch_table "$OUTPUT_DIR/branch_table" >>$LOG 2>&1

# Copy unmodified apk to the output directory
echo ">>>> Copying the unmodified apk" >>$LOG 2>&1
cp $INPUT_APK_PATH "$OUTPUT_DIR/original.apk" >>$LOG 2>&1

echo ">>>> Done" >>$LOG 2>&1
