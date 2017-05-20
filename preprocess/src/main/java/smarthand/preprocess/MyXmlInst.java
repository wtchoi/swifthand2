package smarthand.preprocess;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.json.JSONObject;
import pxb.android.axml.*;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MyXmlInst {

  static class OutputInfo {
    String dirPath;

    FileToken apk;
    FileToken manifest;
    FileToken info;

    public OutputInfo (String inputFilename, String outputBaseDirPath) throws IOException {
      File outputBaseDir = new File(outputBaseDirPath);
      assert outputBaseDir.exists();
      assert outputBaseDir.isDirectory();

      this.dirPath = outputBaseDirPath + "/" + inputFilename;
      File outputDir = new File(dirPath);

      if (!outputDir.exists()) outputDir.mkdir();
      assert outputDir.isDirectory();

      this.apk = new FileToken(dirPath, "preprocessed.apk", true);
      this.manifest = new FileToken(dirPath, "Manifest.xml", true);
      this.info = new FileToken(dirPath, "info.json", true);
    }
  }

  public static void main(String args[]) throws IOException {
    //use command line argument
    File apkInputFile = new File(args[0]);
    assert apkInputFile.exists();

    String apkFileName = apkInputFile.getName().replace("\\.apk", "");
    OutputInfo outputInfo = new OutputInfo(apkFileName, args[1]);

    OptionParser parser = new OptionParser();
    parser.accepts( "noinst" ); // no instrumentation
    OptionSet options = parser.parse(args);
    boolean noInstrumentation = options.has("noinst");

    ZipInputStream apkInputStream = new ZipInputStream(new FileInputStream(apkInputFile));
    ZipOutputStream apkOutputStream = new ZipOutputStream(outputInfo.apk.getOutputStream());
    FileOutputStream xmlOutputStream = outputInfo.manifest.getOutputStream();
    FileOutputStream jsonOutputStream = outputInfo.info.getOutputStream();

    Manifest manifest = new Manifest();

    while (true) {
      ZipEntry apkEntry = apkInputStream.getNextEntry();
      if (apkEntry == null) break;

      File tempFile = copyEntryToTempFile(args[0], apkInputStream);
      InputStream tempInputStream = new FileInputStream(tempFile);

      if (apkEntry.getName().equals("AndroidManifest.xml")) {
        manifest = transformManifest(tempInputStream, apkOutputStream, xmlOutputStream, noInstrumentation);
      } else {
        copyEntry(tempInputStream, apkOutputStream, apkEntry);
      }

      tempFile.delete();
    }

    JSONObject manifestJson =
        new JSONObject()
        .accumulate("package", manifest.packageName)
        .accumulate("mainActivity", manifest.mainActivity);

    PrintWriter jsonPrinter = new PrintWriter(jsonOutputStream);
    jsonPrinter.println(manifestJson.toString());
    jsonPrinter.flush();

    apkOutputStream.flush();
    apkOutputStream.close();

    xmlOutputStream.flush();
    xmlOutputStream.close();

    jsonOutputStream.flush();
    jsonOutputStream.close();
  }

  public static Manifest transformManifest(InputStream is, ZipOutputStream os, FileOutputStream xmlos, boolean noInstrumentation) throws IOException {
    Manifest result = new Manifest();
    os.putNextEntry(new ZipEntry("AndroidManifest.xml"));

    {
      AxmlReader ar = AxmlReader.create(is);
      AxmlWriter aw = new AxmlWriter();

      //Writer w = new OutputStreamWriter(System.out);
      Writer xmlw = new OutputStreamWriter(xmlos);

      //DumpAdapter dao = new DumpAdapter(w, aw); // dump contents to output, then chain to aw
      //DumpAdapter daf = new DumpAdapter(xmlw, dao); // dump contents to file, then chain to dao

      DumpAdapter daf = new DumpAdapter(xmlw, aw); // dump contents to file, then chain to dao
      AxmlTransformer ad = new AxmlTransformer(daf);
      ad.setNoInstrumentation(noInstrumentation);

      if (ad.appClassDefined) {
        System.out.println("App Cls: " + ad.appClassName);
      }

      ar.accept(ad);

      // flush instrumentation
      //w.flush();
      aw.writeTo(os);

      // flush manifest contents
      xmlw.flush();

      result.packageName = ad.pkgName;
      result.mainActivity = ad.mainActivityClassName;
    }

    os.closeEntry();
    return result;
  }
  
  public static void copyEntry(InputStream is, ZipOutputStream os, ZipEntry entry) throws IOException {
    byte[] buffer = new byte[1024];
    int len = 0;

    os.putNextEntry(new ZipEntry(entry.getName()));
    while((len = is.read(buffer)) > 0) {
      os.write(buffer, 0, len);
    }
    os.closeEntry();
  }

  public static File copyEntryToTempFile(String apkPath, InputStream is) throws IOException {
    byte [] buffer = new byte[1024];
    int len = 0;

    File tempFile = File.createTempFile(apkPath,"temptemp");

    FileOutputStream os = new FileOutputStream(tempFile);
    while((len = is.read(buffer)) > 0) {
      os.write(buffer, 0, len);
    }
    os.close();

    return tempFile;
  }
}

