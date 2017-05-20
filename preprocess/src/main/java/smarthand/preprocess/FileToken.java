package smarthand.preprocess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wtchoi on 10/23/15.
 */

class FileToken {
  String path;
  String name;
  File file;
  AtomicBoolean inUse;

  FileToken(String dir, String name, boolean createFresh) throws IOException {
    this.path = dir + "/" + name;
    this.name = name;
    file = new File(path);
    inUse = new AtomicBoolean(false);

    if (createFresh) {
      if (file.exists()) {
        file.delete();
      }
      file.createNewFile();
    }
  }

  public FileOutputStream getOutputStream() throws IOException {
    synchronized (this.inUse) {
      if (inUse.get()) return null;
      inUse.set(true);
    }
    return new FileOutputStream(file);
  }

  public void releaseOutputStream() {
    inUse.set(false);
  }
}
