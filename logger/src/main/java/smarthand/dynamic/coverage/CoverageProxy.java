package smarthand.dynamic.coverage;

import android.util.Log;
import smarthand.ui_driver.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;

/**
 * Created by wtchoi on 10/25/15.
 */
class CoverageProxy implements Runnable {
  Socket socket;
  PrintWriter out;
  BufferedReader in;

  LinkedList<Integer> branches = new LinkedList<Integer>();
  LinkedList<Integer> branchesBG = new LinkedList<Integer>();
  LinkedList<Integer> methods = new LinkedList<Integer>();
  LinkedList<Integer> methodsBG = new LinkedList<Integer>();

  HashSet<Integer> coveredMethods = new HashSet<Integer>();
  HashSet<Integer> coveredBranches = new HashSet<Integer>();

  int uid;
  long timestamp;
  boolean initialized = false;
  boolean connected = false;
  boolean disconnected = false;
  boolean killed = false;

  Thread runner;

  public void run() {
    while (!killed) {
      tryInit();
      while (!disconnected && !killed) {
        handleCommand();
      }

      try {
        Thread.sleep(100);
      }
      catch(InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void start() {
    runner = new Thread(this);
    runner.start();
  }

  private void logd(String msg) {
    Log.d("wtchoi", "LogProxy(" + uid + ") : " + msg);
  }

  private void handleCommand() {
    if (connected) {
      try {
        String command = in.readLine();
        if (command == null) {
          logd("received null");
          socket.close();
          connected = false;
        } else if (command.equals("DISCONNECT")) {
          logd("received: DISCONNECT");
          socket.close();
          connected = false;
          disconnected = true;
        }
        else if (command.equals("KILL")) {
          logd("received: KILL");
          socket.close();
          connected = false;
          killed = true;
        } else if (command.equals("REQUEST")) {
          logd("received: REQUEST");
          sendMethodCoverage();
          sendBranchCoverage();
          out.println("DONE");
          out.flush();
        }
        else if (command.equals("REQUEST:CLEAR")) {
          logd("received: REQUEST:CLEAR");
          sendMethodCoverage();
          sendBranchCoverage();
          out.println("DONE");
          out.flush();
          coveredBranches.clear();
          coveredMethods.clear();
        }
        else if (command.equals("VIEW")) {
          logd("received: VIEW");
          collectAndSendViewInfo();
          out.println("DONE");
          out.flush();
        }
      } catch (IOException e) {
        logd("connection error");
        logd(exceptionToString(e));
        connected = false;
      }
    }
  }

  private void collectAndSendViewInfo() {

  }

  private void sendMethodCoverage() {
    LinkedList temp = methods;
    synchronized (this) {
      methods = methodsBG;
      methodsBG = temp;
    }

    StringBuilder builder = new StringBuilder("MethodCoverage:");
    if (buildMessage(methodsBG, coveredMethods, builder)){
      String result = builder.toString();
      out.println(result);
      logd(result);
      methodsBG.clear();
    }
  }

  private void sendBranchCoverage() {
    LinkedList temp = branches;
    synchronized (this) {
      branches = branchesBG;
      branchesBG = temp;
    }

    StringBuilder builder = new StringBuilder("BranchCoverage:");
    if (buildMessage(branchesBG, coveredBranches, builder)){
      String result = builder.toString();
      out.println(result);
      logd(result);
      branchesBG.clear();
    }
  }

  private boolean buildMessage(LinkedList<Integer> ls, HashSet<Integer> covered, StringBuilder builder) {
    boolean first = true;
    for (Integer id : ls) {
      if (covered.contains(id)) continue;
      covered.add(id);
      if (first) first = false;
      else builder.append(",");
      builder.append(id);
    }

    return !first;
  }

  private void tryInit() {
    if (!initialized) {
      logd("log proxy initializing");
      timestamp = System.currentTimeMillis();
      Random rand = new Random(timestamp);
      uid = rand.nextInt();
      initialized = true;
    }

    if (!connected && !killed) {
      try {
        logd("tryInit: log proxy connecting");
        socket = new Socket(Constants.hostName, Constants.LOG_SERVER_PORT);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        logd("tryInit: socket connected");
        logd("tryInit: sending proxy id");
        out.println(uid);

        logd("tryInit: sending time stamp");
        out.println(timestamp);

        connected = true;
        disconnected = false;
        logd("tryInit: connection established");
      }
      catch(IOException e) {
        if (!disconnected) {
          logd("tryInit: connection error");
          logd(exceptionToString(e));
          connected = false;
        }
      }
    }
  }

  public void reportMethod (Integer mid) {
    synchronized (this) {
      methods.add(mid);
    }
  }

  // Assume reportBranch is always followed by reportMethod
  public void reportBranch (Integer bid) {
    synchronized (this) {
      branches.add(bid);
    }
  }

  public static String exceptionToString(Exception e) {
    String trace = e.toString() + "\n";
    for (StackTraceElement e1 : e.getStackTrace()) {
      trace += "\t at " + e1.toString() + "\n";
    }
    return trace;
  }
}
