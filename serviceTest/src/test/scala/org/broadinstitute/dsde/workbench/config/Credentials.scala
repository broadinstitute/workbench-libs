package org.broadinstitute.dsde.workbench.config

import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader}
import org.broadinstitute.dsde.workbench.auth.{AuthToken, UserAuthToken}


case class Credentials (val email: String, val password: String) {
  def makeAuthToken(): AuthToken = Credentials.cache.get(this)
}

object Credentials {
  val cache = CacheBuilder.newBuilder()
    .expireAfterWrite(3600, TimeUnit.SECONDS)
    .build(
      new CacheLoader[Credentials, AuthToken] {
        def load(credentials: Credentials): AuthToken = {
          UserAuthToken(credentials)
        }
      }
    )
}