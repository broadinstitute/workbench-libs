package org.broadinstitute.dsde.workbench.config

import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.{AuthToken, AuthTokenScopes, UserAuthToken}

case class ScopedCredentials(credentials: Credentials, scopes: Seq[String])

case class Credentials(email: String, password: String) {
  // use this form of override instead of specifying a default for the scopes argument below, because lots of
  // code expects the no-arg signature.
  def makeAuthToken(): AuthToken = makeAuthToken(AuthTokenScopes.userLoginScopes)
  def makeAuthToken(scopes: Seq[String]): AuthToken = Credentials.cache.get(ScopedCredentials(this, scopes))
}

object Credentials extends LazyLogging {
  val cache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(50, TimeUnit.MINUTES) // choosing a value < 60 mins to avoid token expiration issues
    .build(
      new CacheLoader[ScopedCredentials, AuthToken] {
        def load(scopedCredentials: ScopedCredentials): AuthToken = {
          logger.info("Generating a new auth token...")
          UserAuthToken(scopedCredentials.credentials, scopedCredentials.scopes)
        }
      }
    )
}
