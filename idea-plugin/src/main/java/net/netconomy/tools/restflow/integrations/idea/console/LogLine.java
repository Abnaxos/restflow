package net.netconomy.tools.restflow.integrations.idea.console;

import javax.annotation.Nullable;

import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;


public final class LogLine {

    private final Channel channel;
    private final String text;

    public LogLine(Channel channel, String text) {
        this.channel = channel;
        this.text = text;
    }

    public static LogLine of(String line) {
        Channel channel;
        if (line.isEmpty()) {
            return new LogLine(Channel.UNKNOWN, "");
        }
        switch (line.charAt(0)) {
        case Interface.PREFIX_RUN:
            return new LogLine(Channel.CONSOLE, line.substring(1));
        case Interface.PREFIX_SCRIPT:
            return new LogLine(Channel.SCRIPT, line.substring(1));
        case Interface.PREFIX_OUT_STDOUT:
            return new LogLine(Channel.INFO, line.substring(1));
        case Interface.PREFIX_OUT_STDERR:
            return new LogLine(Channel.ERROR, line.substring(1));
        case Interface.PREFIX_OUT_HTTP_OUT:
            return new LogLine(Channel.HTTP_OUT, line.substring(1));
        case Interface.PREFIX_OUT_HTTP_IN:
            return new LogLine(Channel.HTTP_IN_OK, line.substring(1));
        default:
            return new LogLine(Channel.UNKNOWN, line);
        }
    }

    public Channel channel() {
        return channel;
    }

    public String text() {
        return text;
    }

    public LogLine withHttpIn(@Nullable Channel channel) {
        if (channel == null || this.channel == channel) {
            return this;
        }
        if (channel.isHttpIn() && this.channel.isHttpIn()) {
            return new LogLine(channel, text);
        }
        return this;
    }

    @Override
    public String toString() {
        return channel + "[" + text + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        //noinspection ObjectEquality
        if (o == null || getClass() != o.getClass()) return false;
        LogLine that = (LogLine)o;
        if (channel != that.channel) return false;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        int result = channel.hashCode();
        result = 31 * result + text.hashCode();
        return result;
    }

    public enum Channel {
        CONSOLE,
        SCRIPT,
        INFO,
        ERROR,
        HTTP_OUT,
        HTTP_IN_OK,
        HTTP_IN_ERR,
        HTTP_IN_WARN,
        UNKNOWN;

        private final boolean isHttp;
        private final boolean isHttpIn;
        Channel() {
            isHttp = name().startsWith("HTTP_");
            isHttpIn = name().startsWith("HTTP_IN_");
        }
        public boolean isHttp() {
            return isHttp;
        }
        public boolean isHttpIn() {
            return isHttpIn;
        }
        public boolean isHttpOut() {
            return isHttp && ! isHttpIn;
        }
    }

}
