package net.netconomy.tools.restflow.integrations.idea.console.external;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;


public class Interface {

    public static final Charset CHARSET = UTF_8;

    public static final String ARG_ECHO_SCRIPT = "-echo-script";
    public static final String ARG_PROFILES = "-profiles";

    public static final char PREFIX_SCRIPT = '+';
    public static final char PREFIX_RUN = '.';
    public static final char PREFIX_ACTIVITY = '%';
    public static final char PREFIX_PIN = '#';
    public static final char PREFIX_OUT_HTTP_OUT = '>';
    public static final char PREFIX_OUT_HTTP_IN = '<';
    public static final char PREFIX_OUT_STDOUT = '|';
    public static final char PREFIX_OUT_STDERR = '!';

    public static final String RUN_OUT_DEBUG = "debug:";
    public static final String RUN_OUT_RUN = "run:";
    public static final String RUN_OUT_READY = "ready";
    public static final String RUN_OUT_SUCCESS = "done: ok";
    public static final String RUN_OUT_ERROR = "done: error ";

    public static final String PACKAGE_NAME;
    static {
        String n = Interface.class.getName();
        int pos = n.lastIndexOf('.');
        PACKAGE_NAME = n.substring(0, pos);
    }

    public static final String CONSOLE_MAIN_CLASS = PACKAGE_NAME + ".ConsoleMain";

    private static final Pattern LINE_RE = Pattern.compile("\\n|\\r\\n?");
    private static final Pattern MSG_FILTER_RE = Pattern.compile("[\\n\\r]", Pattern.MULTILINE);

    private Interface() {
    }

    public static void sendScript(String msg, String script, @Nullable OutputStream out) throws IOException {
        if (out == null) {
            throw new IOException("Output stream is null");
        }
        String[] lines = LINE_RE.split(script);
        StringBuilder buf = new StringBuilder();
        for (String l : lines) {
            buf.append(PREFIX_SCRIPT).append(l).append('\n');
        }
        buf.append(PREFIX_RUN).append(LINE_RE.matcher(msg).replaceAll("")).append('\n');
        byte[] bytes = buf.toString().getBytes(CHARSET);
        out.write(bytes);
        out.flush();
    }
}
