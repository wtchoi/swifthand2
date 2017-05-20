package smarthand.ui_explorer.visualize;

import smarthand.ui_explorer.Options;

import java.io.File;
import java.io.FileOutputStream;
import java.util.SortedSet;

/**
 * Created by wtchoi on 3/7/16.
 */
public class PrinterHelper {
    public static String buildIntervalString(SortedSet<Integer> sortedLabels) {
        Integer lb = null;
        Integer ub = null;
        boolean first = true;
        StringBuilder labelBuilder = new StringBuilder();

        for (Integer label : sortedLabels) {
            if (lb == null) {
                lb = label;
                ub = label;
                continue;
            }

            if (label.equals(ub + 1)) {
                ub = label;
                continue;
            } else {
                if (first) first = false;
                else labelBuilder.append(", ");
                labelBuilder.append(encodeRange(lb, ub));
                lb = label;
                ub = label;
            }
        }

        if (lb != null) {
            if (first) first = false;
            else labelBuilder.append(", ");
            labelBuilder.append(encodeRange(lb, ub));
        }

        return labelBuilder.toString();
    }


    private static String encodeRange(Integer lb, Integer ub) {
        if (lb.equals(ub)) return String.valueOf(lb);
        return String.valueOf(lb) + "-" + String.valueOf(ub);
    }


    public static <S, T extends ForwardLabeledGraphPrintTrait<S>> void
    dumpForwardLabeledGraphToDot(int id, String prefix, String postfix, ForwardLabeledGraphPrinter<S,T> printer) {
        String image_dir = Options.get(Options.Keys.IMAGE_OUTPUT_DIR);
        File image_output_dir = new File(image_dir);
        if (!image_output_dir.exists()) image_output_dir.mkdir();

        String dotty_file_path = image_dir + "/" + prefix + id + postfix + ".dot";
        File dotty_file = new File(dotty_file_path);
        try {
            FileOutputStream fos = new FileOutputStream(dotty_file);
            printer.print(fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
