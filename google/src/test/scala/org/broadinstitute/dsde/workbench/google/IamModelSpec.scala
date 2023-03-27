package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO.MemberType
import org.broadinstitute.dsde.workbench.google.IamModel.{Binding, Expr, Policy}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class IamModelSpec extends AnyFlatSpecLike with Matchers {

  "updatePolicy" should "only add conditions to new policy bindings" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("desc", "1 > 2", null, "title")
    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("adams@firecloud.org"),
                                              MemberType.User,
                                              Set("role1"),
                                              Set.empty,
                                              Some(conditionExpr)
    )

    val role1Bindings = updatedPolicy.bindings.filter(b => b.role.equals("role1"))
    role1Bindings should have size 2
    role1Bindings.filter(b => b.condition == null) should have size 1
    role1Bindings.filter(b => b.condition != null) should have size 1

    val expectedBindings =
      Set(oldBinding1, oldBinding2, Binding("role1", Set(s"${MemberType.User}:adams@firecloud.org"), conditionExpr))
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "remove members when a condition is not provided" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set.empty,
                                              Set("role1"),
                                              None
    )

    updatedPolicy should be(Policy(Set(oldBinding2), "abcd"))
  }

  it should "remove members when a condition is provided" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("desc", "1 > 2", null, "title")

    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set.empty,
                                              Set("role1"),
                                              Some(conditionExpr)
    )

    updatedPolicy.bindings should be(Set(oldBinding2))
  }

  it should "add and remove roles at the same time without a condition" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set("role3"),
                                              Set("role1"),
                                              None
    )

    val expectedBindings = Set(oldBinding2, Binding("role3", Set(s"${MemberType.User}:lincoln@firecloud.org"), null))
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "add and remove roles at the same time with a condition" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("desc", "1 > 2", null, "title")
    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set("role3"),
                                              Set("role1"),
                                              Some(conditionExpr)
    )

    val expectedBindings =
      Set(oldBinding2, Binding("role3", Set(s"${MemberType.User}:lincoln@firecloud.org"), conditionExpr))
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "leave bindings alone if the one to be removed does not exist" in {
    val oldBinding1 = Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("desc", "1 > 2", null, "title")
    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set.empty,
                                              Set("role3"),
                                              Some(conditionExpr)
    )

    updatedPolicy.bindings should be(oldBindings)
  }

  it should "leave other members in the role binding when one is removed" in {
    val oldBinding1 =
      Binding("role1",
              Set(s"${MemberType.User}:lincoln@firecloud.org", s"${MemberType.User}:adams@firecloud.org"),
              null
      )
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBindings = Set(oldBinding1, oldBinding2)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("desc", "1 > 2", null, "title")
    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set.empty,
                                              Set("role1"),
                                              Some(conditionExpr)
    )

    val expectedBindings = Set(oldBinding2, Binding("role1", Set(s"${MemberType.User}:adams@firecloud.org"), null))
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "leave other conditional bindings in-place when adding new conditional bindings" in {
    val oldBinding1 =
      Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBinding3 = Binding("role3",
                              Set(s"${MemberType.User}:adams@firecloud.org"),
                              Expr("existing condition", "2 > 3", null, "existing condition title")
    )
    val oldBindings = Set(oldBinding1, oldBinding2, oldBinding3)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val conditionExpr = Expr("new condition", "1 > 2", null, "new condition title")
    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("pet-adams@firecloud.org"),
                                              MemberType.ServiceAccount,
                                              Set("role3"),
                                              Set.empty,
                                              Some(conditionExpr)
    )

    val expectedBindings =
      oldBindings + Binding("role3", Set(s"${MemberType.ServiceAccount}:pet-adams@firecloud.org"), conditionExpr)
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "add a user to an existing conditional policy if the condition is the same" in {
    val conditionExpr = Expr("condition", "1 > 2", null, "condition title")
    val oldBinding1 =
      Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBinding3 = Binding("role3", Set(s"${MemberType.User}:adams@firecloud.org"), conditionExpr)
    val oldBindings = Set(oldBinding1, oldBinding2, oldBinding3)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("pet-adams@firecloud.org"),
                                              MemberType.ServiceAccount,
                                              Set("role3"),
                                              Set.empty,
                                              Some(conditionExpr)
    )

    val expectedBindings =
      Set(oldBinding1,
          oldBinding2,
          oldBinding3.copy(members = oldBinding3.members + s"${MemberType.ServiceAccount}:pet-adams@firecloud.org")
      )
    updatedPolicy.bindings should be(expectedBindings)
  }

  it should "remove a user from a role both with and without conditions" in {
    val conditionExpr = Expr("condition", "1 > 2", null, "condition title")

    val oldBinding1 =
      Binding("role1", Set(s"${MemberType.User}:lincoln@firecloud.org"), null)
    val oldBinding2 = Binding("role2", Set(s"${MemberType.User}:washington@firecloud.org"), null)
    val oldBinding3 =
      Binding("role3",
              Set(s"${MemberType.User}:adams@firecloud.org", s"${MemberType.User}:lincoln@firecloud.org"),
              conditionExpr
      )
    val oldBindings = Set(oldBinding1, oldBinding2, oldBinding3)
    val oldPolicy = Policy(oldBindings, etag = "abcd")

    val updatedPolicy = IamModel.updatePolicy(oldPolicy,
                                              WorkbenchEmail("lincoln@firecloud.org"),
                                              MemberType.User,
                                              Set.empty,
                                              Set("role1", "role3"),
                                              None
    )

    val expectedBindings =
      Set(oldBinding2, Binding("role3", Set(s"${MemberType.User}:adams@firecloud.org"), conditionExpr))
    updatedPolicy.bindings should be(expectedBindings)
  }

}
