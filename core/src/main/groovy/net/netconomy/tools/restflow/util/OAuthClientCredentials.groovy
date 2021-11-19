package net.netconomy.tools.restflow.util

import net.netconomy.tools.restflow.dsl.RestFlow

/**
 * @since 2018-10-23
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class OAuthClientCredentials {

    private final RestFlow flow

    String clientId
    String secret
    String bearerToken

    OAuthClientCredentials(RestFlow flow, clientId = 'restflow', secret = 'restflow') {
        this.flow = flow
        this.clientId = clientId
        this.secret = secret
    }

    @SuppressWarnings("GroovyMissingReturnStatement")
    void authenticate(Map params = Collections.emptyMap()) {
        flow.run {
            POST('//authorizationserver/oauth/token') {
                query client_id: params.getOrDefault('clientId', clientId)
                query client_secret: params.getOrDefault('secret', secret)
                query grant_type: 'client_credentials'
            }
            if (params.getOrDefault('assertSuccess', true)) {
                assert response.statusCode == 200 // ok
                assert response.json.access_token
            }
            bearerToken = response.json.access_token
            if (params.getOrDefault('applyToken', true)) {
                baseRequest {
                    bearerAuth bearerToken
                }
            }
        }
    }

}
