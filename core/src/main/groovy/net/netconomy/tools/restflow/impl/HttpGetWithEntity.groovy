package net.netconomy.tools.restflow.impl

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpGet


class HttpGetWithEntity extends HttpEntityEnclosingRequestBase {
    final String METHOD_NAME = HttpGet.METHOD_NAME

    HttpGetWithEntity() {}
    HttpGetWithEntity(URI uri) {setURI(uri)}
    HttpGetWithEntity(String uri) {this(URI.create(uri))}

    @Override
    String getMethod() {return METHOD_NAME}
}
