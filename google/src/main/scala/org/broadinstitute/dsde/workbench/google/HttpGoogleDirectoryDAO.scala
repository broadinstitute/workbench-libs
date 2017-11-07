package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.{GoogleClientSecrets, GoogleCredential}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpResponseException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.admin.directory.model.{Group, Member}
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.util.FutureSupport

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 8/17/17.
  */

class HttpGoogleDirectoryDAO(serviceAccountClientId: String,
                             pemFile: String,
                             subEmail: String,
                             appsDomain: String,
                             appName: String,
                             override val workbenchMetricBaseName: String)( implicit val system: ActorSystem, implicit val executionContext: ExecutionContext ) extends GoogleDirectoryDAO with FutureSupport with GoogleUtilities {

  @deprecated(message = "This way of instantiating a HttpGoogleDirectoryDAO has been deprecated. Please upgrade your configs appropriately.")
  def this(clientSecrets: GoogleClientSecrets,
           pemFile: String,
           appsDomain: String,
           appName: String,
           workbenchMetricBaseName: String)
          (implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(clientSecrets.getDetails.get("client_email").toString, pemFile, clientSecrets.getDetails.get("sub_email").toString, appsDomain, appName, workbenchMetricBaseName)
  }

  val directoryScopes = Seq(DirectoryScopes.ADMIN_DIRECTORY_GROUP)

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance

  val groupMemberRole = "MEMBER" // the Google Group role corresponding to a member (note that this is distinct from the GCS roles defined in WorkspaceAccessLevel)

  implicit val service = GoogleInstrumentedService.Groups

  override def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchGroupEmail): Future[Unit] = {
    val directory = getGroupDirectory
    val groups = directory.groups
    val group = new Group().setEmail(groupEmail.value).setName(groupName.value.take(60)) //max google group name length is 60 characters
    val inserter = groups.insert(group)

    retryWhen500orGoogleError (() => { executeGoogleRequest(inserter) })
  }

  override def deleteGroup(groupEmail: WorkbenchGroupEmail): Future[Unit] = {
    val directory = getGroupDirectory
    val groups = directory.groups
    val deleter = groups.delete(groupEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(deleter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => () // if the group is already gone, don't fail
    }
  }

  override def addMemberToGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val member = new Member().setEmail(memberEmail.value).setRole(groupMemberRole)
    val inserter = getGroupDirectory.members.insert(groupEmail.value, member)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.Conflict.intValue => () //if the member is already there, then don't keep trying to add them
      // Recover from http 412 errors because they can be spuriously thrown by Google, but the operation succeeds
      case e: HttpResponseException if e.getStatusCode == StatusCodes.PreconditionFailed.intValue => ()
    }
  }

  override def removeMemberFromGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val deleter = getGroupDirectory.members.delete(groupEmail.value, memberEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(deleter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => () //if the member is already absent, then don't keep trying to delete them
    }
  }

  override def getGoogleGroup(groupEmail: WorkbenchGroupEmail): Future[Option[Group]] = {
    val getter = getGroupDirectory.groups().get(groupEmail.value)

    retryWithRecoverWhen500orGoogleError(() => { Option(executeGoogleRequest(getter)) }){
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    }
  }

  override def isGroupMember(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Boolean] = {
    val getter = getGroupDirectory.members.get(groupEmail.value, memberEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(getter)
      true
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }
  }

  private def getGroupDirectory = {
    new Directory.Builder(httpTransport, jsonFactory, getGroupServiceAccountCredential).setApplicationName(appName).build()
  }

  private def getGroupServiceAccountCredential: Credential = {
    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(serviceAccountClientId)
      .setServiceAccountScopes(directoryScopes.asJava)
      .setServiceAccountUser(subEmail)
      .setServiceAccountPrivateKeyFromPemFile(new java.io.File(pemFile))
      .build()
  }

}
