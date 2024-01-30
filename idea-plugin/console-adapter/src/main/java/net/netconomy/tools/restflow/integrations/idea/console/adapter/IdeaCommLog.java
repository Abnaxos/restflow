package net.netconomy.tools.restflow.integrations.idea.console.adapter;

import java.io.PrintStream;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.netconomy.tools.restflow.impl.CommLog;
import net.netconomy.tools.restflow.impl.HttpBody;


final class IdeaCommLog implements CommLog {

    private static final Pattern LINE_RE = Pattern.compile("(\\n|\\r\\n?)", Pattern.MULTILINE);
    public static final int BASE64_LINE_LEN = 120;

    private volatile boolean brief;

    private final PrintStream out;

    IdeaCommLog(PrintStream out) {
        this(out, false);
    }

    IdeaCommLog(PrintStream out, boolean brief) {
        this.out = out;
        this.brief = brief;
    }

    @Override
    public void setBrief(boolean brief) {
        this.brief = brief;
    }

    @Override
    public boolean isBrief() {
        return brief;
    }

    @Override
    public void info(Object... msg) {
        print(Interface.PREFIX_OUT_STDOUT, msg);
    }

    @Override
    public void warn(Object... msg) {
        print(Interface.PREFIX_OUT_STDERR, msg);
    }

    @Override
    public void activity(Object... msg) {
        print(Interface.PREFIX_ACTIVITY, msg);
    }

    @Override
    public void pin(Object... msg) {
        print(Interface.PREFIX_PIN, msg);
    }

    @Override
    public void send(Object... msg) {
        print(Interface.PREFIX_OUT_HTTP_OUT, msg);
    }

    @Override
    public void sendBody(HttpBody body) {
        logBody(body, false, this::send);
    }

    @Override
    public void recv(Object... msg) {
        print(Interface.PREFIX_OUT_HTTP_IN, msg);
    }

    @Override
    public void recvBody(HttpBody body) {
        logBody(body, false, this::recv);
    }

    private void logBody(HttpBody body, boolean pretty, Consumer<? super String> logger) {
        if (body.isEmpty()) {
            logger.accept("BODY EMPTY");
            return;
        } else if (body.getContent() != null) {
            String type = "BODY TEXT";
            if (pretty && body.isPrettyPrintable()) {
                type = type + " PRETTY ";
            } else {
                type = type + " RAW ";
            }
            String[] lines = LINE_RE.split(pretty ? body.getPrettyContent() : body.getContent());
            type = type + lines.length + " lines: " + body.getMimeType();
            logger.accept(type);
            Stream.of(lines).forEach(logger);
        } else {
            logger.accept("BODY BINARY " + body.getRawContent().length + " bytes: " + body.getMimeType());
            String base64 = Base64.getEncoder().encodeToString(body.getRawContent());
            int mark = 0;
            while (mark < base64.length()) {
                logger.accept(base64.substring(mark, Math.min(mark + BASE64_LINE_LEN, base64.length())));
                mark += BASE64_LINE_LEN;
            }
        }
        logger.accept("BODY END");
    }

    @Override
    public void debug(Object... msg) {
        Object[] newMsg = new Object[msg.length + 1];
        newMsg[0] = Interface.RUN_OUT_DEBUG;
        System.arraycopy(msg, 0, newMsg, 1, msg.length);
        print(Interface.PREFIX_RUN, newMsg);
    }

    private void print(char head, Object... msg) {
        Util.splitMessage(msg).forEach(l -> {
            out.print(head);
            out.println(l);
        });
    }

    @Override
    public CommLog getVerbose() {
        if (brief) {
            return new IdeaCommLog(out);
        }
        return this;
    }
}
