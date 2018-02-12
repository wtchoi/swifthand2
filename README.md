# SwiftHand2

SwiftHand2 is a GUI testing framework that works on OSX and Linux. We recommend that Windows users employ a virtualization tool, such as [Virtual Box](https://www.virtualbox.org/wiki/VirtualBox) or [Ubuntu on Windows](https://www.microsoft.com/en-us/store/p/ubuntu/9nblggh4msv6).

## Install Guide


### Step 1: Getting required packages
#### Installing necessary packages
Install the following packages. You can use a package manager,
such as **apt-get** or **brew**, to install Maven, expect, jq, and bash.

- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Android SDK](http://developer.android.com/sdk/index.html#downloads) (recommend Android Studio)
- [Maven](https://maven.apache.org/download.cgi)
- [expect](http://www.nist.gov/el/msid/expect.cfm)
- [bash](https://www.gnu.org/software/bash/)
- [jq](https://stedolan.github.io)

If you are using Android Studio to install Android SDK, follow the steps described in the following document:
[Install Android Studio](https://developer.android.com/studio/install.html). The document will guide you to 
download and install the latest Android SDK. After installing Java, use the *java -version* and *javac -version*
commands to ensure that the version of JDK executables in your PATH
(*javac* and *java*) is the same as the one you installed. The displayed version might differ from your expectation if your system already has a different
version of JRE or JDK. In this case, you must fix environment variables (PATH and JAVA_HOME) appropriately.
You can use [java_config](http://ask.xmodulo.com/change-default-java-version-linux.html) in Linux and
[java_home](http://www.java67.com/2015/11/how-to-set-javahome-path-in-mac-os-x.html) in OSX to fix the environment variables. 

#### Setting environment variables
Once all required packages are installed, the following environment variables must be set:

```
export JAVA_HOME=[JAVA HOME]
export ANDROID_HOME=[ANDROID HOME]
export ANDROID_BUILD_TOOL=[ANDROID HOME]/build-tools/[VERSION]
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools
```

*[JAVA HOME]* should be the path to the root directory of JDK. 
If you are using OSX, you can try the following command to set the JAVA_HOME environment variable. 

```
export JAVA_HOME="$(/usr/libexec/java_home)"
```

Similarly, *[ANDROID HOME]* should be the path to the root directory of Android SDK. 
If you installed Android SDK as part of Android Studio in OSX, [ANDROID HOME] will be /Users/[LOGIN]/Library/Android/sdk, where [LOGIN] is replaced by your OSX login. *[VERSION]* should be the version number of the Android build-tool installed in your system.
You can identify the version number in the [ANDROID HOME]/build-tools directory. Use the latest one if multiple versions are installed.

### Step 2: Getting SwiftHand2
Download SwiftHand2 from the repository. You can either download [a zipped archive](https://github.com/wtchoi/swifthand2/archive/master.zip) or clone the repository by using the following command.

```
git clone git@github.com:wtchoi/swifthand2.git
```

After downloading the project, give execution permission to the following script files:

```
chmod 700 instrument.sh
chmod 700 signing.sh
chmod 700 run.sh
chmod 700 pp.sh
```

You must also compile SwiftHand2.

```
mvn package
```

### Step 3: Preparing a device
Make sure that your Android device is connected to your computer.
When using a real device, perform the following steps:

* Enable [developer options](http://www.greenbot.com/article/2457986/how-to-enable-developer-options-on-your-android-phone-or-tablet.html).
* Enable [USB debugging mode](https://www.kingoapp.com/root-tutorials/how-to-enable-usb-debugging-mode-on-android.htm).
* Disable [lock screen](http://www.tomsguide.com/us/disable-android-lock-screen,news-21217.html).
* Make sure that your device trusts your computer when they are connected.

Check connectivity using the *adb devices* command. If everything is properly set up, you will see something like the following:

```
List of devices attached
[DEVICE ID]	device
```

Here, *[DEVICE ID]* will be replaced with your actual device ID. If the device has connected to this computer for the first time, you will see that your computer is *unauthorized* to debug the device, and the device will show a pop-up message asking for USB-debugging permission. Allow permission with the *OK* button. 
If you are using an emulator, you will see a slightly different result:

```
List of devices attached
emulator-5554 emulator
```

Here, *emulator-5554* will be replaced with your actual emulator ID. An emulated device does not ask for the permission because it allows USB-debugging by default. You are now ready to run SwiftHand2.

## Execution Guide
### Instrumentation
To begin with, you must preprocess a target application. You can use one of the applications in the **apps/raw** directory. The directory contains 18 example apps. In this tutorial, we are going to use **anymemo_10.7.1.apk**.

```
./instrument.sh apps/raw/anymemo_10.7.1.apk
```

This command instruments the target application **anymemo_10.7.1.apk**, which might take a few minutes. The instrumentation result is then stored in the **apps/inst/anymemo_10.7.1.apk** directory. Among the files in the instrumented apk directory, *instrumented.apk* and *info.json* are files that will be required for the next steps; do not remove them.

### UI Testing
Once the application is instrumented, you can initiate testing using the **run.sh** script.

```
./run.sh [TARGET APP] [OUTDIR] [DEVICE ID] [PORT] [TIME] [STRATEGY] [RANDOM SEED]
```

Here, *[TARGET APK]* should be the directory containing the *instrumented* target app, *[OUTDIR]*  the directory for storing the testing result, *[DEVICE ID]* the android device ID, *[PORT]* a TCP port to be used by the testing algorithm (any free port will work), *[TIME]* the time budget for the testing algorithm, *[STRATEGY]* the testing strategy to run, and *[RANDOM SEED]* the random seed to be used by the testing strategy. For the tutorial, we can execute the following command:

```
./run.sh apps/inst/anymemo_10.7.1.apk output/anymemo/sh [DEVICE ID] 9090 120 sh 1
```

This command tests the *anymemo_10.7.1.apk* app
on the device identified by the device ID [DEVICE ID] for 120 seconds
using the **sh** (SwiftHand) algorithm.
For actual testing, we recommend running a testing algorithm for four to eight hours to
saturate the test coverage.
The command specifies the testing algorithm to use port 9090 and random seed 1, and
the testing results will be dumped to the *./output/anymemo/sh* directory.
To use a different algorithm,
try **random**, **lstar**, or **sh2** (a variation of SwiftHand algorithm)
instead of **sh**.

#### Output Files
##### Execution Log
The testing algorithm generates log files, and the
running example emits logs to *output/anymemo/sh/log/explorer.log*.
Although *run.sh* also shows log messages on the screen, a log file is helpful for understanding and debugging a testing algorithm. A log file is composed of a sequence of iteration logs, each of which is composed of three parts: the GUI tree of the app at the beginning of the iteration, the set of enabled events inferred from the GUI tree, and the event triggered at the end of the iteration. The following is an iteration log captured from a real execution (with minor editing to improve the readability).

```
Test Iteration = 819

GUI tree:
android.widget.FrameLayout(80:84-1360:2392)
|   android.widget.FrameLayout(80:84-1360:2392)
|   |   android.widget.FrameLayout(80:84-1360:2392)
|   |   |   android.widget.LinearLayout(80:84-1360:2392)
|   |   |   |   android.widget.LinearLayout(91:116-1356:318)
|   |   |   |   |   android.widget.LinearLayout(170:172-1277:282)
|   |   |   |   |   |   android.widget.ImageView(170:172-294:282)
|   |   |   |   |   |   android.widget.TextView(294:184-1277:269)[Software ...]
|   |   |   |   |   android.widget.ImageView(135:314-1312:318)
|   |   |   |   android.widget.LinearLayout(91:318-1356:2136)
|   |   |   |   |   android.widget.ScrollView(135:318-1312:2136)
|   |   |   |   |   |   android.widget.TextView(184:325-1277:2136)[Sanity  v...]
|   |   |   |   android.widget.LinearLayout(91:2136-1356:2381)
|   |   |   |   |   android.widget.LinearLayout(135:2136-1312:2327)
|   |   |   |   |   |   android.widget.Button(142:2150-723:2318)[Ok]
|   |   |   |   |   |   android.widget.Button(723:2150-1305:2318)[Cancel]

Enabled events:
0. back
1. menu
2. click:android.widget.Button:0.0.0.0.2.0.0:432:2234
3. click:android.widget.Button:0.0.0.0.2.0.1:1014:2234
4. scroll:android.widget.ScrollView:0.0.0.0.1.0:Down
5. scroll:android.widget.ScrollView:0.0.0.0.1.0:Up
end

Sending:dclick:android.widget.Button:0.0.0.0.2.0.0:432:2234
```

[](##### Test Viewer)
[](The same information is also available through the viewer interface. For the running example, you can open *output/anymemo/sh/log/index* to open the viewer. The viewer allows you to navigate the iteration logs of the corresponding testing session. The following is a screen capture of the viewer.)
[]( )
[]([TODO: ADD screen shot with explanation])
[]( )
[](The first tab shows how the screen changed during this iteration (before and after). The second tab shows the iteration log. The third tab shows various statistics.)
[]( )
[]([TODO: Explain important statistics])

##### Trace File
The testing algorithm also generates an execution trace file at the end of its execution. For the running example, the resulting execution trace will be stored in the *output/anymemo/sh/trace.json* file. The trace file format is explained later in this document.

### Test Suite Reduction
The trace file generated by running a testing algorithm is often large and difficult to interpret. One can attempt to reduce such a trace file in three steps, using the following command:

```
./pp.sh [TARGET APK] [OUTDIR] [DEVICE ID] [PORT] [STRATEGY] [TRACE FILE] [#REP] ...
```

The new command *pp.sh* (which stands for post-processing) is similar to *run.sh*, but does not take the timeout and the random seed, instead taking other arguments specific to the post-processing steps.

#### Stabilization
The first step is to stabilize the original execution trace. The stabilization step detects and removes the unstable portion of the given execution trace by replaying the trace multiple times.

```
./pp.sh [TARGET APK] [OUTDIR] [DEVICE ID] [PORT] sequence-stabilize [TRACE FILE] [#REP]
```

The command for stabilizing a trace is similar to that for executing a testing strategy, but requires two new arguments. *[TRACE FILE]* is the path to the trace that is stabilized, and *[#REP]* is the number of re-executions that the stabilization step must perform. For our running example, we can use the following command.

```
./pp.sh apps/inst/anymemo_10.7.1.apk output/anymemo/stabilized [DEVICE ID] 9090 sequence-stabilize output/anymemo/sh/trace.json 3
```

Here, we use the trace file from the previous step and set the stabilization algorithm to perform three re-executions. 
In reality, three re-executions is not sufficient to stabilize a trace, and we recommend at least eight. The result of removing non-replayable parts will be stored in the *output/anymemo/stabilized/minimized_trace.json* file.

Once the trace has been stabilized, two reduction steps are available: *eliminate-loop* and *splicing*.

#### Loop-elimination
The loop-elimination step removes loops from a trace file. A loop is a sub-trace of a trace that starts and ends in the same screen. Loops often do not contribute to test coverage (branch coverage and screen coverage), and can therefore be removed without reducing the test coverage. The command to perform the loop-elimination is as follows:

```
./pp.sh [TARGET APK] [OUTDIR] [DEVICE ID] [PORT] eliminate-loop [TRACE FILE] [#REP]
```

Note that this is almost same as the command for the stabilization. Here, [TRACE FILE] is the path to a stabilized trace file to minimize using the loop-elimination algorithm. [#REP] is the number of re-executions. Loop-elimination also performs re-execution to ensure that a trace generated by removing loops is a feasible trace. For the running example, we can use the following exact command:

```
./pp.sh apps/inst/anymemo_10.7.1.apk output/anymemo/eliminate-loop <DEVICE ID> 9090 eliminate-loop output/anymemo/stabilized/minimized_trace.json 3
```

This command takes the trace obtained from the stabilization step (stabilized/minimized_trace.json) and eliminates loops. Note that we configure the algorithm to use three re-executions to check the feasibility of traces. This is to ensure that the demo finishes in a reasonable time; in practice, we recommend using at least eight re-executions. The result of the loop-elimination will be stored in the *output/anymemo/eliminate-loop/minimized_trace.json* file.

##### Splicing
The final step of test suite reduction is splicing. One can think of a trace file as a sequence of small traces that are divided by restart actions. The splicing algorithm attempts to take fragments from a number of such small traces and combine them to form a new small trace that can replace the originals. The command to execute the splicing algorithm is this.

```
./pp.sh [TARGET APK] [OUTDIR] [DEVICE ID] [PORT] splicing [TRACE FILE] [#REP] [#FRAG]
```

[TRACE FILE] is the path to the stabilized trace file for minimizing using the splicing algorithm. In theory, one can use the result obtained from both the stabilization step and the loop-elimination step; however, in practice, running the splicing algorithm without loop-elimination will not scale. [#REF] is the number of re-executions. The splicing algorithm also performs re-execution to check whether a trace generated by splicing is feasible. [#FRAG] is the maximum number of fragments that the splicing algorithm can use to form a single trace; we recommend using either two or three. For our running example, we can use the following exact command:


```
./pp.sh apps/inst/anymemo_10.7.1.apk output/anymemo/splicing [DEVICE ID] 9090 splicing output/anymemo/eliminate-loop/minimized_trace.json 4 3
```

This takes the trace obtained from the eliminate-loop pass (eliminate-loop/minimized_trace.json), re-executes each candidate trace four times, and uses at most three trace fragments to produce a spliced trace. The result of the second phase will be available in the *output/anymemo/splicing/minimized_trace.json* file.

## Trace File Format
The trace file follows the JSON schema below. Essentially, a trace file is composed of a list of iterations, each of which tells what action has been executed and what is the result of the action. The result includes the GUI tree, the set of enabled events, the method coverage, and the branch coverage. The very first iteration shows what occurred while starting an app for the first time. 

```
{
	"$schema": "http://json-schema.org/draft-04/schema#",
	"title": "Trace",
	"type": "array",
	"items": {
		"title": "Iteration",
		"type" "object",
		"properties": {
			"id": {
				"description": "The id of the current iteration",
				"type": "number"
			},
			"action": {
				"description": "The type of action executed during the iteration. This could be start, close, or event.",
				"type": "string"
			},
			"actionIndex": {
				"description": "The index of event. This property only exists when the action has the event type.",
				"type": "number"
			},
			"activity": {
				"description": "The name of activity (after the action).",
				"type": "string"
			},
			"isKeyboardShown": {
				"description": "Whether a software keyboard is shown (after the action).",
				"type": "boolean"
			},
			"ui": {
				"description": "The GUI tree (after the action)"
				"type": "object",
				"id": "GUI-node",
				"properties": {
					"class": {
						"description": "The class name of the GUI node",
						"type": "string",
					},
					"actionable": {
						"description": "Indicate whether the node is actionable.",
						"type": "boolean"
					},
					"focused": {
						"description": "Indicate whether the node has the focus.",
						"type": "boolean"
					},
					"enabled": {
						"description": "Indicate whether the node is enabled (i.e., visible).",
						"type": "boolean"
					},
					"checked": {
						"description": "Indicate whether the node is checked (optional)",
						"type": "boolean"
					},
					"text": {
						"description": "The string contents of the GUI node (optional)",
						"type": "string"
					},
					"bound": {
						"description": "The string representing the bounding box of the GUI node (X:Y-X:Y)"
						"type": "string"
					},
					"children	": {
						"type": "array",
						"items": {
							"type": "object",
							"$ref": "GUI-node"
						}
					},
					"required": ["class", "bound"]
				}
			},
			"abstractState": {
				"description": "The screen abstraction (after the action)",
				"type": "object",
				"properties": {
					"id": {
						"description": "The unique identifier of the screen abstraction.",
						"type": "number"
					},
					"activity": {
						"description": "The name of the activity."
						"type": "string"
					},
					"isKeyboardShown": {
						"description": "Whether a software keyboard is shown.",
						"type": "boolean"
					},
					"enabledEvents": {
						"description": "The set of enabled events",
						"type": "array",
						"items": {
							"title" : "event",
							"type" : "string"
						}
					},
					"required": ["id", "isKeyboardShown", "enabledEvents"]
				}
			},
			"branchCoverage": {
				"description": "The set of branches covered during the iteration",
				"type": "array",
				"items": {
					"title": "branch-id",
					"type": "number"
				}
			},
			"methodCoverage": {
				"description": "The set of methods covered during the iteration",
				"type": "array",
				"items": {
					"title": "method-id",
					"type", "number"
				}
			},
			"required": ["id", "action", "isKeyboardShown", "ui", "abstractState", "branchCoverage", "methodCoverage"]
		}
	}
}
```