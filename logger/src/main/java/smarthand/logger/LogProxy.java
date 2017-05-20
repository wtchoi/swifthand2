package smarthand.logger;

import smarthand.ui_driver.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;

/**
 * Created by wtchoi on 10/25/15.
 */
class LogProxy extends Thread {
  Socket socket;
  LinkedList<String> logQueue = new LinkedList<String>();
  LinkedList<String> sendQueue = new LinkedList<String>();

  @Override
  public void run()
  {
    System.out.println("wtchoi: log proxy started");
    try {
      while (true) {
        socket = new Socket(Constants.hostName, Constants.LOG_SERVER_PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));
        System.out.println("wtchoi: log proxy connected");

        while (true) {
          synchronized (this) {
            if (logQueue.size() > 0) {
              LinkedList<String> temp = sendQueue;
              sendQueue = logQueue;
              logQueue = temp;
            }
          }

          for (String s : sendQueue) {
            out.println(s);
          }
          sendQueue.clear();
        }
      }
    }
    catch (IOException e) {
      System.out.println("wtchoi: cannot connect to server");
      System.out.println(e.toString());
    }
  }

  public void request (String s) {
    synchronized (this) {
      logQueue.add(s);
    }
  }
}
