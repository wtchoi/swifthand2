package smarthand.ui_explorer.util;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * A non-blocking subprocess manager (for JDK7).
 *
 * @author  Wenyu Wang
 * @version 1.0
 * @since   2016-05-26
 */
public class SubProcess {

    private Process mProcess = null;
    private Thread mThread = null;
    private OutputAdopter mOutputWriter = null;

    public interface OutputAdopter {
        void flush();
        void println(String s);
        void close();
    }

    public static SubProcess execCommand(final String sCommand, final String sOutputFilename, final boolean appendLog) {
        System.out.println("\tExecuting " + sCommand + ", writing to " + sOutputFilename);
        OutputAdopter writer = null;
        if (sOutputFilename != null) {
            try {
                writer = new OutputAdopter() {
                    private PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(sOutputFilename, appendLog)));

                    @Override
                    public void flush() {
                        w.flush();
                    }

                    @Override
                    public void println(String s) {
                        w.println(s);
                    }

                    @Override
                    public void close() {
                        w.close();
                    }
                };
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return execCommand(sCommand, writer);
    }


    public static SubProcess execCommand(final String sCommand, final OutputAdopter writer) {
        System.out.println("\tExecuting " + sCommand);
        final SubProcess subProcess = new SubProcess();

        subProcess.mOutputWriter = writer;

        try {
            subProcess.mProcess = Runtime.getRuntime().exec(sCommand);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        synchronized (SubProcessPool) {
            SubProcessPool.add(subProcess);
        }

        // Create a new thread to handle this subprocess.
        subProcess.mThread = new Thread() {
            @Override
            public void run() {
                if (subProcess.mOutputWriter == null) {
                    try {
                        subProcess.mProcess.waitFor();
                    } catch (InterruptedException e) {
                        // No need to do anything
                    }
                } else {
                    try {
                        String buf;
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(subProcess.mProcess.getInputStream()));
                        while ((buf = bufferedReader.readLine()) != null) {
                            subProcess.mOutputWriter.println(buf);
                            subProcess.mOutputWriter.flush();
                        }
                    } catch (IOException e) {
                        // No need to do anything
                    }
                    subProcess.mOutputWriter.close();
                }
                synchronized (SubProcessPool) {
                    SubProcessPool.remove(subProcess);
                }
                System.out.println("\tFinished running: " + sCommand);
            }
        };

        subProcess.mThread.setDaemon(true);
        subProcess.mThread.start();

        return subProcess;
    }

    public boolean isAlive() {
        return mThread.isAlive();
    }

    public void join() {
        try {
            mThread.join();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void kill() {
        if (!isAlive())
            return;
        System.out.println("\t\tWill call destroy()");
        mProcess.destroy();
        System.out.println("\t\tCalled destroy()");
        // if (mOutputWriter != null)
        //     mOutputWriter.close();
        // synchronized (SubProcessPool) {
        //     SubProcessPool.remove(this);
        // }
    }

    // Sub process pool for all running sub processes.
    private static final Set<SubProcess> SubProcessPool = new HashSet<>();

    static {
        // Kill all running sub processes.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // System.out.println("\t\tHook called.");
                synchronized (SubProcessPool) {
                    for (SubProcess subProcess : SubProcessPool)
                        subProcess.kill();
                }
            }
        });
    }

    public static void forceKill(final String sAdbCommand, final String sDeviceName, final String sProcessName) {
        // Force kill running process by finding its PID and invoking 'kill' command.
        String[] cmd = {
                "/bin/sh",
                "-c",
                String.format("%s -s %s shell kill $(%s -s %s shell ps | grep '%s' | awk '{print $2}')",
                        sAdbCommand, sDeviceName, sAdbCommand, sDeviceName, sProcessName)
        };

        try {
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
