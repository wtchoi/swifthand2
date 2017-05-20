package smarthand.preprocess;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.DumpAdapter;

import java.io.*;

/**
 * Created by wtchoi on 4/1/16.
 */
public class AxmlPrinter {
    public static void main(String args[]) {
        try {
            File axmlFile = new File(args[0]);
            InputStream is = new FileInputStream(axmlFile);

            AxmlReader ar = AxmlReader.create(is);
            Writer w = new OutputStreamWriter(System.out);
            DumpAdapter da = new DumpAdapter(w);
            ar.accept(da);
            w.flush();
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
