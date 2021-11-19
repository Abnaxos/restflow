package net.netconomy.tools.restflow.impl

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import java.nio.charset.StandardCharsets

/**
 * @since 2018-10-14
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
@ToString
@EqualsAndHashCode
class Authentication {

    Type type
    String evidence

    Authentication(Type type, Object evidence) {
        this.type = type
        this.evidence = evidence
    }

    enum Type {

        BASIC{
            String encodeEvidence(String evidence) { Base64.encoder.encodeToString(evidence.getBytes(StandardCharsets.UTF_8)) }
        },
        BEARER;

        String encodeAuthHeader(String evidence) { name().toLowerCase().capitalize() + ' ' + encodeEvidence(evidence) }

        protected String encodeEvidence(String evidence) { evidence }
    }

}
