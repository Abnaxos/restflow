package net.netconomy.tools.restflow.impl
/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class PrintCommLog implements CommLog {

    private static LINE_RE = ~'(\n|\r\n?)'

    volatile boolean brief = false
    volatile PrintStream out = null

    @Override
    void info(Object... msg) {
        print('|', msg)
    }

    @Override
    void warn(Object... msg) {
        print ('!!!', msg)
    }

    @Override
    void send(Object... msg) {
        print('>>>', msg)
    }

    @Override
    void pin(Object... msg) {
        print('==>', msg)
    }

    @Override
    void sendBody(HttpBody body) {
        logBody(body, false, this.&send)
    }

    @Override
    void recv(Object... msg) {
        print('<', msg)
    }

    @Override
    void recvBody(HttpBody body) {
        logBody(body, true, this.&recv)
    }

    private void logBody(HttpBody body, boolean pretty, Closure<?> logger) {
        if (body.empty) {
            logger.call '~~~ empty body ~~~'
        } else if (body.content) {
            def type = 'text content'
            if (pretty && body.prettyPrintable) {
                type = 'pretty printed ' + type
            }
            logger.call "~~~ $type: $body.mimeType ~~~"
            if (!brief) {
                (pretty ? body.prettyContent : body.content).eachLine {logger.call it}
                logger.call '~~~ end content ~~~'
            }
        } else {
            logger.call "~~~ binary content: $body.mimeType, $body.rawContent.length bytes ~~~"
        }
    }

    @Override
    void debug(Object... msg) {
    }

    private void print(String head, Object... msg) {
        LINE_RE.split(msg.join(' ')).each {(out ?: System.out).println head + ' ' + it}
    }

    @Override
    CommLog getVerbose() {
        return brief ? QuietCommLog.INSTANCE : this
    }
}
