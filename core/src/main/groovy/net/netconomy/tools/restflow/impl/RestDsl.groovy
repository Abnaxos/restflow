package net.netconomy.tools.restflow.impl

import groovy.transform.AnnotationCollector

/**
 * @since 2018-10-14
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
@DelegatesTo(strategy = Closure.DELEGATE_FIRST)
@AnnotationCollector
@interface RestDsl {

}
