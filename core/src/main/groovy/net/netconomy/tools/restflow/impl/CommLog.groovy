package net.netconomy.tools.restflow.impl

import java.util.stream.Collectors
import java.util.stream.Stream


/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
interface CommLog {

    void setBrief(boolean brief)
    boolean isBrief()

    void info(Object... msg)

    void warn(Object... msg)

    default void activity(Object... msg) {info(msg)}
    default void pin(Object... msg) {info(msg)}

    void send(Object... msg)

    void sendBody(HttpBody body)

    void recv(Object... msg)

    void recvBody(HttpBody body)

    void debug(Object... msg)

    CommLog getVerbose()

    static class Util {
        private Util() {}
        private static LINE_RE = ~'(\n|\r\n?)'
        static Stream<String> splitMessage(Object[] msg) {
            Optional.ofNullable(msg)
              .filter(a -> a.length > 0)
              .map(a -> Stream.of(LINE_RE.split(Stream.of(a).map(String::valueOf).collect(Collectors.joining(" ")))))
              .orElseGet(() -> Stream.of("<empty>"))        }
    }
}
