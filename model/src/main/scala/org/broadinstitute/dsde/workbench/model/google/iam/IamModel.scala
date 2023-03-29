package org.broadinstitute.dsde.workbench.model.google.iam

case class Binding(role: String, members: Set[String], condition: Expr)

case class Policy(bindings: Set[Binding], etag: String)

case class Expr(description: String, expression: String, location: String, title: String)
