package smarthand.instrument;

import soot.*;
import soot.options.Options;

import java.io.IOException;

/**
 * Created by wtchoi on 10/9/15.
 */

public class MyInst {

  public static void main(String[] args) throws IOException {

    //prefer Android APK files for input/ouput
    Options.v().set_src_prec(Options.src_prec_apk);
    Options.v().set_output_format(Options.output_format_dex);
    Options.v().set_keep_line_number(true);

    /*
    InstMethodLogHelper.init();
    InstInvokeLog invokeLog = new InstInvokeLog("/log_invoke.json");
    PackManager.v().getPack("jtp").add(new Transform("jtp.instrumentInvokeLog", invokeLog));

    InstMethodLogInjector lcLog = new InstMethodLogInjector("/log_lifecycle_method.json");
    PackManager.v().getPack("jtp").add(new Transform("jtp.instrumentLifeCycleMethodLog", lcLog));
    */


    InstCoverageInjector methodCoverage = new InstCoverageInjector("/log_method_coverage.json");

    PackManager.v().getPack("jtp").add(new Transform("jtp.instrumentMethodCoverage.json", methodCoverage));
    soot.Main.main(args);

    methodCoverage.dump("sootOutput");
  }
}
