package smarthand.dynamic.coverage;

/**
 * Created by wtchoi on 10/8/15.
 */

import android.os.Looper;

public class Coverage {
  private static CoverageProxy coverageProxy;

  private volatile static Looper mainLooper;
  private static ThreadLocal<Looper> mainThreadCache = new ThreadLocal<Looper>();
  /**
   *
   * @param id Method identifier
   */
  public static void reportMethod(int id) {
    tryInitialize();
    if (Looper.myLooper() == mainThreadCache.get()) {
      coverageProxy.reportMethod(id);
    }
  }

  public static void reportBranch(int bid) {
    tryInitialize();
    if (Looper.myLooper() == mainThreadCache.get()) {
      coverageProxy.reportBranch(bid);
    }
  }

  public static void hello() {
    logcat("Hello Hello");
  }

  private static void tryInitialize() {
    if (coverageProxy == null) {
      mainLooper = Looper.getMainLooper();
      coverageProxy = new CoverageProxy();
      coverageProxy.start();
    }

    if (mainThreadCache.get() == null) {
      mainThreadCache.set(mainLooper);
    }
  }

  private static void logcat(String m) {
    System.out.println("wtchoi:" + m);
  }

  // To prevent the instantiation of the class.
  private Coverage() {
    throw new AssertionError();
  }
}


