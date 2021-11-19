package net.netconomy.tools.restflow.impl
/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
interface CommLog {

    void setBrief(boolean brief)
    boolean isBrief()

    void info(Object... msg)

    void warn(Object... msg)

    void send(Object... msg)

    void sendBody(HttpBody body)

    void recv(Object... msg)

    void recvBody(HttpBody body)

    void debug(Object... msg)

    CommLog getVerbose()

}
