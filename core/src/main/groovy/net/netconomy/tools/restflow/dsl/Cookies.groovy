package net.netconomy.tools.restflow.dsl

import org.apache.http.impl.client.BasicCookieStore


/**
 * Cookie handling.
 */
class Cookies {

  /**
   * Warning: We'll probably replace Apache HTTP with Java 11 HTTP in the
   * future. This provides access to the underlying store for now, but it
   * may be gone soon.
   */
  final store = new BasicCookieStore()
  boolean enabled = false

  void enable(boolean enabled = true) {this.enabled = enabled}
  void disable(boolean disabled = true) {enable(!disabled)}
  void clear() {store.clear()}
}
