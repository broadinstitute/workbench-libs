package org.broadinstitute.dsde.workbench.model

import java.security.SecureRandom

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.commons.codec.binary.Hex
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountSubjectId}
import org.scalacheck.Gen

object Generator {
  private val random = SecureRandom.getInstance("NativePRNGNonBlocking")
  private[workbench] def genRandom(currentMilli: Long): String = {
    val currentMillisString = currentMilli.toString
    // one hexdecimal is 4 bits, one byte can generate 2 hexdecial number, so we only need half the number of bytes, which is 8
    // currentMilli is 13 digits, and it'll be another 200 years before it becomes 14 digits. So we're assuming currentMillis is 13 digits here
    val bytes = new Array[Byte](4)
    random.nextBytes(bytes)
    val r = new String(Hex.encodeHex(bytes))
    // since googleSubjectId starts with 1, we are replacing 1 with 2 to avoid conflicts with existing uid
    val front = if (currentMillisString(0) == '1') currentMillisString.replaceFirst("1", "2") else currentMilli
    front + r
  }

  val genNonPetEmail: Gen[WorkbenchEmail] = Gen.alphaStr.map(x => WorkbenchEmail(s"t$x@gmail.com"))
  val genPetEmail: Gen[WorkbenchEmail] = Gen.alphaStr.map(x => WorkbenchEmail(s"t$x@test.iam.gserviceaccount.com"))
  val genGoogleSubjectId: Gen[GoogleSubjectId] = Gen.const(GoogleSubjectId(genRandom(System.currentTimeMillis())))
  val genWorkbenchUserId: Gen[WorkbenchUserId] = Gen.const(WorkbenchUserId(genRandom(System.currentTimeMillis())))
  val genServiceAccountSubjectId: Gen[ServiceAccountSubjectId] = genGoogleSubjectId.map(x => ServiceAccountSubjectId(x.value))
  val genOAuth2BearerToken: Gen[OAuth2BearerToken] = Gen.alphaStr.map(x => OAuth2BearerToken("s"+x))

  val genUserInfo = for{
    token <- genOAuth2BearerToken
    email <- genNonPetEmail
    expires <- Gen.calendar.map(_.getTimeInMillis)
    userId <- genWorkbenchUserId
  } yield UserInfo(token, userId, email, expires)

  val genWorkbenchUser = for{
    email <- genNonPetEmail
    userId <- genWorkbenchUserId
    googleSubjectId <- Gen.option[GoogleSubjectId](Gen.const(GoogleSubjectId(userId.value)))
  } yield WorkbenchUser(userId, googleSubjectId, email)

  val genWorkbenchGroupName = Gen.alphaStr.map(x => WorkbenchGroupName(s"s$x")) //prepending `s` just so this won't be an empty string
  val genGoogleProject = Gen.alphaStr.map(x => GoogleProject(s"s$x")) //prepending `s` just so this won't be an empty string
  val genWorkbenchSubject: Gen[WorkbenchSubject] = for{
    groupId <- genWorkbenchGroupName
    project <- genGoogleProject
    userId <- genWorkbenchUserId
    res <- Gen.oneOf[WorkbenchSubject](List(userId, groupId, PetServiceAccountId(userId, project)))
  }yield res
}

