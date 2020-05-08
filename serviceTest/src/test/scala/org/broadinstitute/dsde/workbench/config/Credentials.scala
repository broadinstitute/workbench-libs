package org.broadinstitute.dsde.workbench.config

import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.{AuthToken, AuthTokenScopes, UserAuthToken}

case class ScopedCredentials(credentials: Credentials, scopes: Seq[String])

case class Credentials(email: String, password: String) extends LazyLogging {
  // use this form of override instead of specifying a default for the scopes argument below, because lots of
  // code expects the no-arg signature.
  def makeAuthToken(): AuthToken = makeAuthToken(AuthTokenScopes.userLoginScopes)
  def makeAuthToken(scopes: Seq[String]): AuthToken = {
    val auth = Credentials.cache.get(ScopedCredentials(this, scopes))
    // logger.debug(s"AuthToken: ${auth}, ${auth.value}")
    auth
  }
}

object Credentials {
  val cache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(3600, TimeUnit.SECONDS)
    .build(
      new CacheLoader[ScopedCredentials, AuthToken] {
        def load(scopedCredentials: ScopedCredentials): AuthToken =
          UserAuthToken(scopedCredentials.credentials, scopedCredentials.scopes)
      }
    )
}
