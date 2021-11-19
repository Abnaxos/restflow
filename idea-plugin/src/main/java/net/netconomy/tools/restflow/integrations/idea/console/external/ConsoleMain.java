package net.netconomy.tools.restflow.integrations.idea.console.external;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.netconomy.tools.restflow.dsl.RestFlow;
import net.netconomy.tools.restflow.impl.ProfileLoader;
import net.netconomy.tools.restflow.impl.RestFlowScripts;


/**
 * Main class for the console process.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ConsoleMain {

    public static final String SCRIPT_CODEBASE = "/restflow/script";

    private final InputStream stdin;
    private final PrintStream stdout;
    private final PrintStream stderr;

    private boolean echoScript;
    private RestFlow restFlow;

    private ConsoleMain(List<String> args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        args = new ArrayList<>(args);
        echoScript = args.remove(Interface.ARG_ECHO_SCRIPT);
        List<Path> profilePaths = new ArrayList<>();
        int argIndex = args.indexOf(Interface.ARG_PROFILES);
        if (argIndex >= 0) {
            args.remove(argIndex);
            if (argIndex < args.size()) {
                Stream.of(args.remove(argIndex).split(Pattern.quote(File.pathSeparator)))
                        .map(Paths::get)
                        .forEach(profilePaths::add);
            }
        }
        restFlow = new RestFlow(new ProfileLoader(ConsoleMain.class.getClassLoader(), profilePaths),
                new IdeaCommLog(stdout));
    }

    @SuppressWarnings("ZeroLengthArrayAllocation")
    public static void main(String[] args) throws Exception {
        InputStream stdin = System.in;
        PrintStream stdout = System.out;
        PrintStream stderr = System.err;
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(new PrintStream(new LinePrefixOutputStream(
                stdout, Interface.PREFIX_OUT_STDOUT), true, Interface.CHARSET.name()));
        System.setErr(new PrintStream(new LinePrefixOutputStream(
                stdout, Interface.PREFIX_OUT_STDERR), true, Interface.CHARSET.name()));
        new ConsoleMain(Arrays.asList(args), stdin, stdout, stderr).run();
    }

    private void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(stdin, Interface.CHARSET));
        StringBuilder buf = new StringBuilder();
        String line;
        stdout.println(Interface.PREFIX_RUN + Interface.RUN_OUT_READY);
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            char prefix = line.charAt(0);
            String content = line.substring(1);
            switch (prefix) {
            case Interface.PREFIX_SCRIPT:
                buf.append(content).append('\n');
                break;
            case Interface.PREFIX_RUN:
                try {
                    runScript(content, buf.toString().trim());
                } finally {
                    buf.setLength(0);
                }
            }
        }
    }

    private void runScript(String msg, String script) {
        Throwable exception = null;
        try {
            stdout.print(Interface.PREFIX_RUN + Interface.RUN_OUT_RUN + " ");
            stdout.println(msg);
            if (echoScript) {
                Stream.of(script.split("\\n")).forEach(l -> {
                    stdout.print(Interface.PREFIX_SCRIPT);
                    stdout.println(l);
                });
            }
            stdout.flush();
            RestFlowScripts.run(RestFlowScripts.parse(restFlow, script, msg));
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError || e instanceof LinkageError) {
                throw e;
            }
            e.printStackTrace();
            exception = e;
        }
        if (exception == null) {
            stdout.println(Interface.PREFIX_RUN + Interface.RUN_OUT_SUCCESS);
        } else {
            String errorMsg = exception.toString().trim();
            int cut = errorMsg.indexOf('\n');
            int cutCR = errorMsg.indexOf('\r');
            if (cut < 0) {
                cut = cutCR;
            } else if (cutCR >= 0) {
                cut = Math.min(cut, cutCR);
            }
            if (cut >= 0) {
                String rest = errorMsg.substring(cut).trim();
                errorMsg = errorMsg.substring(0, cut).trim();
                if (!rest.isEmpty()) {
                    errorMsg += " [...]";
                }
            }
            stdout.println(Interface.PREFIX_RUN + Interface.RUN_OUT_ERROR + errorMsg);
        }
    }
}
