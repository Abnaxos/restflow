package net.netconomy.tools.restflow.impl

import net.netconomy.tools.restflow.dsl.RequestConfig
import net.netconomy.tools.restflow.dsl.RestFlow

/**
 * @since 2018-10-22
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
interface PreRequestHandler {

    void preRequest(RestFlow flow, RequestConfig requestConfig)

}
