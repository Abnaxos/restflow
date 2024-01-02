package net.netconomy.tools.restflow.impl

import org.apache.http.client.CookieStore
import org.apache.http.cookie.Cookie

/**
 * No-op implementation of {@code CookieStore} used when cookies are disabled
 * (the default).
 */
class NoCookiesStore implements CookieStore {
  final static INSTANCE = new NoCookiesStore()
  @Override
  void addCookie(Cookie cookie) {}
  @Override
  List<Cookie> getCookies() {new ArrayList<>()}
  @Override
  boolean clearExpired(Date date) {return false}
  @Override
  void clear() {}
}
