package net.netconomy.tools.restflow.impl

import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase


class HttpDeleteWithEntity extends HttpEntityEnclosingRequestBase {
    final String METHOD_NAME = HttpDelete.METHOD_NAME

    HttpDeleteWithEntity() {}
    HttpDeleteWithEntity(URI uri) {setURI(uri)}
    HttpDeleteWithEntity(String uri) {this(URI.create(uri))}

    @Override
    String getMethod() {return METHOD_NAME}
}
