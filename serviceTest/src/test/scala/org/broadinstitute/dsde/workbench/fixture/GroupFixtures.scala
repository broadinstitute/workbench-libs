package org.broadinstitute.dsde.workbench.fixture

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource, Sync}
import cats.implicits.{catsSyntaxApply, toFoldableOps}
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.workbench.service.test.RandomUtil

/**
 * Fixtures for creating and cleaning up test groups.
 */
object GroupFixtures extends LazyLogging with RandomUtil {

  def groupNameToEmail(groupName: String)(implicit token: AuthToken): String =
    Orchestration.groups.getGroup(groupName).groupEmail

  def groupNameToMembersEmails(groupName: String)(implicit token: AuthToken): Seq[String] =
    Orchestration.groups.getGroup(groupName).membersEmails

  /**
   * Create and use a temporary Google Group in `testCode`.
   *
   * @param namePrefix  Optional name prefix for group            [default: "tmp-group-"]
   * @param members     Optional list of additional group members [default: None]
   * @param token       Auth token of group owner
   */
  def withTemporaryGroup[A](namePrefix: Option[String] = None, members: Option[List[String]] = None)(
    testCode: (String) => A
  )(implicit token: AuthToken): A =
    GroupFixtures
      .temporaryGroup[IO](token, namePrefix, members)
      .use(groupName => IO.delay(testCode(groupName)))
      .unsafeRunSync

  /**
   * Create a temporary Google Group `Resource` whose lifetime is bound to the scope of the
   * `Resource`'s `use` method.
   *
   * @param ownerAuthToken Auth token of group owner
   * @param namePrefix     Optional name prefix for group            [default: "tmp-group-"]
   * @param members        Optional list of additional group members [default: None]
   */
  def temporaryGroup[F[_]](ownerAuthToken: AuthToken,
                           namePrefix: Option[String] = None,
                           members: Option[List[String]] = None
  )(implicit F: Sync[F]): Resource[F, String] = {

    def createGroup: F[String] = F.delay {
      val groupName = uuidWithPrefix(namePrefix.getOrElse("tmp-group-"))
      Orchestration.groups.create(groupName)(ownerAuthToken)
      groupName
    }

    def destroyGroup(groupName: String): F[Unit] = F.unit <* F.delay {
      Orchestration.groups.delete(groupName)(ownerAuthToken)
    }

    def addGroupMembers(groupName: String, members: List[String]): Resource[F, Unit] =
      members.traverse_ { email =>
        Resource.eval(F.delay {
          logger.info(s"Adding user $email with role of member to group $groupName")
          Orchestration.groups.addUserToGroup(groupName, email, GroupRole.Member)(ownerAuthToken)
        })
      }

    for {
      groupName <- Resource.make(createGroup)(destroyGroup)
      _ <- members.traverse_(emails => addGroupMembers(groupName, emails))
    } yield groupName
  }

}
