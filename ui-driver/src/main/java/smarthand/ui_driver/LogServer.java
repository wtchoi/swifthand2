package smarthand.ui_driver;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wtchoi on 10/24/15.
 */
class LogServer implements Runnable {

  ServerSocket serverSocket;
  Connection latestConnection;
  AtomicBoolean shutedDown = new AtomicBoolean(false);
  Thread runner;

  private final static int pullingInterval = 100; //100 ms

  class Connection extends Thread {
    long timestamp;
    String id;

    Socket sock;
    PrintWriter out;
    BufferedReader in;

    boolean justReceivedMessage = false;

    LinkedList<String> buffer = new LinkedList<String>();
    AtomicBoolean connected = new AtomicBoolean(false);

    @Override
    public void run() {
      connected.set(true);

      try {
        while (true) {
          sleep(pullingInterval);
          if (!connected.get()) break;
          pull(false);
        }
      } catch (InterruptedException e) {
        logd("Cannot sleep");
        logd(exceptionToString(e));
        shutdown("KILL");
      }
    }

    public void shutdown(String cmd) {
      if (connected.get()) {
        synchronized (this) {
          logd("closing connection: " + id + "(" + cmd + ")");
          out.println(cmd);
          try {
            connected.set(false);
            sock.close();
          } catch (IOException e) {
            logd("cannot close socket");
            logd(exceptionToString(e));
          }
        }
      }
    }


    private void pull(boolean clear) {
      String cmd = clear ? "REQUEST:CLEAR" : "REQUEST";
      if (connected.get()) {
        boolean hasMessage = false;

        synchronized (this) {
          try {
            out.println(cmd);
            while (true) {
              String contents = in.readLine();
              if (contents == null) {
                logd("cannot retrieve a command");
                shutdown("KILL");
                break;
              }
              if (contents.equals("DONE")) break;
              buffer.add(contents);
              hasMessage = true;
            }
          } catch (Exception e) {
            logd(exceptionToString(e));
            logd("exception occur while getting a command!");
            shutdown("KILL");
          }
        }

        if (hasMessage) justReceivedMessage = true;
        else justReceivedMessage = false;
      }
    }

    public LinkedList<String> getMessages() {
      pull(true);
      LinkedList<String> result = new LinkedList<String>();

      synchronized (this) {
        LinkedList<String> temp = result;
        result = buffer;
        buffer = temp;
      }
      return result;
    }
  }

  private void logd(String msg) {
    //Log.d("wtchoi", "LogServer: " + msg );
    System.out.println("LogServer: " + msg);
  }

  private Connection accept() throws IOException{
    Socket sock = serverSocket.accept();
    logd("run: new connection requested");

    Connection connection = new Connection();
    connection.out = new PrintWriter(sock.getOutputStream(), true);
    connection.in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
    connection.sock = sock;

    String id = connection.in.readLine();
    logd("run: connection ID: " + id);
    connection.id = id;

    // connection is erroneous. closing the socket and returning null;
    if (id == null) {
      if (!connection.sock.isClosed()) {
        connection.sock.close();
      }
      return null;
    }

    String timestamp = connection.in.readLine();
    logd("run: connection timestamp: " + timestamp);
    connection.timestamp = Long.parseLong(timestamp);
    connection.start();
    return connection;
  }

  public void start() {
    runner = new Thread(this);
    runner.start();
  }

  public void run(){
    try {
      serverSocket = new ServerSocket(Constants.LOG_SERVER_PORT);

      while(true) {
        try {
          if (shutedDown.get()) break;
          Connection incomingConnection = accept();

          if (incomingConnection == null) {
            logd("run: ignore an erroneous connection attempt");
            continue;
          }

          synchronized (this) {
            if (latestConnection != null) {
              if (latestConnection.timestamp > incomingConnection.timestamp) {
                // discard connection from an old app instance
                logd("run: discard a connection from an old instance.");
                incomingConnection.sock.close();
                incomingConnection.out.close();
                incomingConnection.in.close();
                incomingConnection.shutdown("KILL");
                continue;
              } else {
                logd("run: replace connection.");
                latestConnection.sock.close();
                latestConnection.out.close();
                latestConnection.in.close();
                latestConnection.shutdown("KILL");
              }
            }

            logd("run: connection established.");
            latestConnection = incomingConnection;
          }
        } catch(IOException e){
          if (!shutedDown.get()) {
            logd("run: connection error!");
            logd("run (181): " + exceptionToString(e));
          }
        }
      }
    }
    catch (Exception e) {
      try {
        if (!serverSocket.isClosed()) {
          serverSocket.close();
        }
      }
      catch (IOException ee){}
      logd("run (192): " + exceptionToString(e));
    }
  }

  // kill proxy running in app
  public void kill() {
    synchronized (this) {
      logd("connection kill requested");
      if (latestConnection != null) {
        latestConnection.shutdown("KILL");
        latestConnection = null;
      }
    }
  }

  // shutdown log server
  public void shutdown() {
    shutedDown.set(true);
    synchronized (this) {
      logd("log server shutdown requested");
      try {
        runner.interrupt();
        serverSocket.close();
      }
      catch (Exception e) {
        logd(exceptionToString(e));
      }

      if (latestConnection != null) {
        latestConnection.shutdown("DISCONNECT");
        latestConnection = null;
      }
    }
  }

  public LinkedList<String> getMessages() {
    logd("get message requested");
    if (latestConnection == null) {
      return new LinkedList<String>();
    }

    /*
    try {
      while (latestConnection.isAlive() && latestConnection.justReceivedMessage) {
        logd("Just received a message. Wait a while.");
        Thread.sleep(pullingInterval*2);
      }
    }
    catch (InterruptedException e) {
      logd("getMessage: failed to sleep");
    }
    */

    return latestConnection.getMessages();
  }

  public static String exceptionToString(Exception e) {
    String trace = e.toString() + "\n";
    for (StackTraceElement e1 : e.getStackTrace()) {
      trace += "\t at " + e1.toString() + "\n";
    }
    return trace;
  }
}
