package org.broadinstitute.dsde.workbench.util2
import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{Resource, Sync}

import scala.concurrent.ExecutionContext


object ExecutionContexts {
  /**
    * Resource yielding an `ExecutionContext` backed by a fixed-size pool.
    * For more info: https://gist.github.com/djspiewak/46b543800958cf61af6efa8e072bfd5c
    */
  def fixedThreadPool[F[_]](size: Int)(
    implicit sf: Sync[F]
  ): Resource[F, ExecutionContext] = {
    val alloc = sf.delay(Executors.newFixedThreadPool(size))
    val free  = (es: ExecutorService) => sf.delay(es.shutdown())
    Resource.make(alloc)(free).map(ExecutionContext.fromExecutor)
  }

  /**
    * Resource yielding an `ExecutionContext` backed by an unbounded thread pool.
    * For more info: https://gist.github.com/djspiewak/46b543800958cf61af6efa8e072bfd5c
    */
  def cachedThreadPool[F[_]](
                              implicit sf: Sync[F]
                            ): Resource[F, ExecutionContext] = {
    val alloc = sf.delay(Executors.newCachedThreadPool)
    val free  = (es: ExecutorService) => sf.delay(es.shutdown())
    Resource.make(alloc)(free).map(ExecutionContext.fromExecutor)
  }
}