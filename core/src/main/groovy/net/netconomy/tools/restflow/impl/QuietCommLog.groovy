package net.netconomy.tools.restflow.impl
/**
 * @since 2018-10-23
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class QuietCommLog implements CommLog {

    static final INSTANCE = new QuietCommLog()

    @Override
    void setBrief(boolean brief) {
    }

    @Override
    boolean isBrief() {
        return false
    }

    @Override
    void info(Object... msg) {
    }

    @Override
    void warn(Object... msg) {
    }

    @Override
    void send(Object... msg) {
    }

    @Override
    void sendBody(HttpBody body) {
    }

    @Override
    void recv(Object... msg) {
    }

    @Override
    void recvBody(HttpBody body) {
    }

    @Override
    void debug(Object... msg) {
    }

    @Override
    CommLog getVerbose() {
        return this
    }
}
