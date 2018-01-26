package org.broadinstitute.dsde.workbench.config

object UserPool {

  val allUsers: UserSet = UserSet(Config.Users.Owners.userMap ++ Config.Users.Students.userMap ++ Config.Users.Curators.userMap ++ Config.Users.AuthDomainUsers.userMap)

  /**
    * Chooses a user suitable for a generic test.
    * Users in Owners, Curators, AuthDomainUsers, and Students
    */
  def chooseAnyUser: Credentials = chooseAnyUsers(1).head

  def chooseAnyUsers(n: Int): Seq[Credentials] = {
    allUsers.getRandomCredentials(n)
  }

  /**
    * Chooses an admin user.
    */
  def chooseAdmin: Credentials = chooseAdmins(1).head

  def chooseAdmins(n: Int): Seq[Credentials] = {
    Config.Users.Admins.getRandomCredentials(n)
  }

  /**
    * Chooses a project owner.
    */
  def chooseProjectOwner: Credentials = chooseProjectOwners(1).head

  def chooseProjectOwners(n: Int): Seq[Credentials] = {
    Config.Users.Owners.getRandomCredentials(n)
  }

  /**
    * Chooses a curator.
    */
  def chooseCurator: Credentials = chooseCurators(1).head

  def chooseCurators(n: Int): Seq[Credentials] = {
    Config.Users.Curators.getRandomCredentials(n)
  }

  /**
    * Chooses a student.
    */
  def chooseStudent: Credentials = chooseStudents(1).head

  def chooseStudents(n: Int): Seq[Credentials] = {
    Config.Users.Students.getRandomCredentials(n)
  }

  /**
    * Chooses an auth domain user.
    */
  def chooseAuthDomainUser: Credentials = chooseAuthDomainUsers(1).head

  def chooseAuthDomainUsers(n: Int): Seq[Credentials] = {
    Config.Users.AuthDomainUsers.getRandomCredentials(n)
  }

  /**
    * Chooses a temp user.
    */
  def chooseTemp: Credentials = chooseTemps(1).head

  def chooseTemps(n: Int): Seq[Credentials] = {
    Config.Users.Temps.getRandomCredentials(n)
  }

  /**
    * Chooses a notebooksWhitelisted user.
    */
  def chooseNotebooksWhitelisted: Credentials = chooseNotebooksWhitelisteds(1).head

  def chooseNotebooksWhitelisteds(n: Int): Seq[Credentials] = {
    Config.Users.NotebooksWhitelisted.getRandomCredentials(n)
  }

  /**
    *
    * Chooses a campaign manager
    */
  def chooseCampaignManager: Credentials = chooseCampaignManagers(1).head

  def chooseCampaignManagers(n: Int): Seq[Credentials] = {
    Config.Users.CampaignManager.getRandomCredentials(n)
  }
}