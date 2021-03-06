package org.broadinstitute.dsde.workbench.util

import java.lang.reflect.{InvocationHandler, Method, Proxy}

import cats.data.NonEmptyList

import scala.reflect.ClassTag
import scala.util.Random

/**
 * A DelegatePool is a facade on top of a collection of objects that implement a common trait. Method calls on
 * the pool are routed to one of these objects (delegates) at random.
 *
 * The initial use case for this is to have pool of GoogleDirectoryDAOs each with a different service account
 * to get around per user throttles.
 *
 * Note: each delegate must be stateless
 */
object DelegatePool {

  /**
   * Instantiate a new DelegatePool
   * @param delegates
   * @tparam T the trait that all the delegates implement, must be a trait
   * @return
   */
  def apply[T: ClassTag](delegates: NonEmptyList[T]): T =
    Proxy
      .newProxyInstance(getClass.getClassLoader,
                        Array(implicitly[ClassTag[T]].runtimeClass),
                        new DelegatePoolInvocationHandler[T](delegates)
      )
      .asInstanceOf[T]
}

private class DelegatePoolInvocationHandler[T](delegatesNEL: NonEmptyList[T]) extends InvocationHandler {
  private val delegates = delegatesNEL.toList

  override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef =
    method.invoke(chooseDelegate(), args: _*)

  private def chooseDelegate(): T =
    delegates(Random.nextInt(delegates.size))
}
