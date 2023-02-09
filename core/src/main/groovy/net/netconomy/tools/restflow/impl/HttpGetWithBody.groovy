package net.netconomy.tools.restflow.impl

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase


class HttpGetWithBody extends HttpEntityEnclosingRequestBase {
    final String METHOD_NAME = 'GET'

    HttpGetWithBody() {}
    HttpGetWithBody(URI uri) {
        this()
        setURI(uri)
    }
    HttpGetWithBody(String uri) {
        this(URI.create(uri))
    }

    @Override
    String getMethod() {return METHOD_NAME}
}
