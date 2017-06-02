# SwiftHand2

A GUI testing framework

## Execution Guide

### Step 1: Getting required packages
Please install following packages. You can use a package manager,
such as **apt-get** or **brew**, to install Maven, expect, and bash.

- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Android SDK](http://developer.android.com/sdk/index.html#downloads) (latest)
- [Maven](https://maven.apache.org/download.cgi)
- [expect](http://www.nist.gov/el/msid/expect.cfm)
- [bash](https://www.gnu.org/software/bash/)

Once packages are installed, you need to set few environment variables:

```
export JAVA_HOME=<JAVA_HOME>
export ANDROID_HOME=<ANDROID_HOME>
export PATH=$PATH:$ANDROID_HOME/platform_tools:$ANDROID_HOME/tools
```
<JAVA_HOME> should be the path to the root directory of JDK.
Similarly, <ANDROID_HOME> should be the path to the root directory of Android SDK.


### Step 2: Getting SwiftHand2
You need to download SwiftHand2 from the repository. You also need to make few shell scripts executable:
```
chmod 700 instrument.sh
chmod 700 signing.sh
chmod 700 run.sh
```

You also need to compile SwiftHand2.
```
mvn package
```

### Step 3: Preparing a device
Please make sure that your Android device is connected to your computer.
If you are using a real device, you need to do following steps:

* Enable [developer options.](http://www.greenbot.com/article/2457986/how-to-enable-developer-options-on-your-android-phone-or-tablet.html)
* Enable [USB debugging mode.](https://www.kingoapp.com/root-tutorials/how-to-enable-usb-debugging-mode-on-android.htm)
* Disable [lock screen.](http://www.tomsguide.com/us/disable-android-lock-screen,news-21217.html)
* Make sure that your device trusts your computer when they are connected.

Please check the connectivity using **adb devices** command. If everything is properly done, you should see something like
```
List of devices attached
01c0a1bcda138dd9	device
```
, where "01c0a1bcda138dd9" is replaced with your device id. If you are using an emulator, you will see "emulator" instead of "device".



### Step 4: Execution
###### Instrumentation
To begin with, you need to preprocess a target application. You can use one of applications in **apps/raw** directory. For example:

```
./instrument.sh apps/raw/anymemo_10.7.1.apk
```

This command analyzes and instruments the target application **anymemo_10.7.1.apk**.
The instrumentation result will be stored in **apps/inst/anymemo_10.7.1.apk** directory.
Preprocessing may take few minutes.


###### UI Testing
Once the application is instrumented, you can start UI testing using **run.sh** script. For example:

```
./run.sh apps/inst/anymemo_10.7.1.apk output/anymemo/sh <DEVICE ID> 9090 120 sh 1
```

The example command tests anymemo_10.7.1.apk
on the device identified by device id <DEVICE ID> for 120 seconds
using the sh (SwiftHand) algorithm.
9090 indicates the tcp port number to be used during the testing, and 1 is
the random seed to be used by the testing algorithm.
The testing results will be dumped to the ./output/anymemo/sh directory.
To use a different algorithm,
try **random** or **sh2** (a variation of SwiftHand algorithm)
instead of **sh**.

SwiftHand2 generates log files.
The running example emits logs to *output/anymemo/sh/log/explorer.log*.
While UI testing is running, you can use *tail* command line tool to get a live view of the logfile.
For example:
```
tail -f output/anymemo/sh/log/explorer.log
```

###### Test Suite Reduction
An automated testing strategy (such as *random*, *sh*, and *sh2*) generates an execution trace.
In the above example, the resulting execution trace will be stored in *output/anymemo/sh/trace.json* file.
One can try to reduce such an execution trace to generate a small regression test suite.
This can be done in three steps. The first step is to stabilize the original execution trace.

```
./run.sh apps/inst/anymemo_10.7.1.apk output/anymemo/stabilized <DEVICE ID> 9090 240 sequence-stabilize 1 output/anymemo/sh/trace.json
```

The example command reexcute the trace recorded in the given trace file (trace.json) and remove non-replayable parts of the trace. 
Note we are using 240 seconds for time out. 
The result of removing non-replayable parts will be stored in *output/anymemo/stabilized/minimized_trace.json* file.
A single pass of stabilization might not be enough to fully stabilize the trace. We recommend to repeat this step 3 to 5 times.
Once the trace has been stabilized, one can use two reduction steps: *eliminate-loop* and *splicing*.

```
./run.sh apps/inst/anymemo_10.7.1.apk output/anymemo/eliminate-loop <DEVICE ID> 9090 720 eliminate-loop 1 output/anymemo/stabilized/minimized_trace.json
```

The above command executes the first phase of the DetReduce algorithm (eliminate-loop) for 720 seconds.
It takes the trace obtained from the stabilization pass (stabilized/minimized_trace.json).
Once the first phase is finished, one can move to the second phase.

```
./run.sh apps/inst/anymemo_10.7.1.apk output/anymemo/splicing <DEVICE ID> 9090 720 splicing 1 output/anymemo/eliminate-loop/minimized_trace.json 3
```

The above command executes the second phase of the RetReduce algorithm (splicing) for 720 seconds.
It takes the trace obtained from the eliminate-loop pass (eliminate-loop/minimized_trace.json).
The result of the second phase will be available in the *output/anymemo/splicing/minimized_trace.json* file.
