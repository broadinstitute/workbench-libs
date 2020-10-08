package org.broadinstitute.dsde.workbench.service

// This file is entirely copied from https://github.com/broadinstitute/rawls/blob/develop/model/src/main/scala/org/broadinstitute/dsde/rawls/model/AttributeUpdateOperations.scala#L9
import spray.json._

sealed trait Attribute
sealed trait AttributeListElementable extends Attribute //terrible name for "this type can legally go in an attribute list"
sealed trait AttributeValue extends AttributeListElementable
sealed trait AttributeList[T <: AttributeListElementable] extends Attribute { val list: Seq[T] }
case object AttributeNull extends AttributeValue
case class AttributeString(val value: String) extends AttributeValue
case class AttributeNumber(val value: BigDecimal) extends AttributeValue
case class AttributeBoolean(val value: Boolean) extends AttributeValue
case class AttributeValueRawJson(val value: JsValue) extends AttributeValue
case object AttributeValueEmptyList extends AttributeList[AttributeValue] { val list = Seq.empty }
case object AttributeEntityReferenceEmptyList extends AttributeList[AttributeEntityReference] { val list = Seq.empty }
case class AttributeValueList(val list: Seq[AttributeValue]) extends AttributeList[AttributeValue]
case class AttributeEntityReferenceList(val list: Seq[AttributeEntityReference])
    extends AttributeList[AttributeEntityReference]
case class AttributeEntityReference(val entityType: String, val entityName: String) extends AttributeListElementable

object WDLJsonSupport {
  val attributeFormat = new AttributeFormat with PlainArrayAttributeListSerializer
}

object AttributeStringifier {
  def apply(attribute: Attribute): String =
    attribute match {
      case AttributeNull                     => ""
      case AttributeString(value)            => value
      case AttributeNumber(value)            => value.toString()
      case AttributeBoolean(value)           => value.toString()
      case AttributeValueRawJson(value)      => value.toString()
      case AttributeEntityReference(_, name) => name
      case al: AttributeList[_] =>
        WDLJsonSupport.attributeFormat.write(al).toString()
    }
}

case class AttributeName(namespace: String, name: String) extends Ordered[AttributeName] {
  // enable implicit ordering for sorting
  import scala.math.Ordered.orderingToOrdered
  def compare(that: AttributeName): Int = ((this.namespace, this.name)) compare ((that.namespace, that.name))
  def equalsIgnoreCase(that: AttributeName): Boolean =
    (this.namespace.equalsIgnoreCase(that.namespace) && this.name.equalsIgnoreCase(that.name))
}

object AttributeName {
  val defaultNamespace = "default"
  val libraryNamespace = "library"
  val tagsNamespace = "tag"
  val pfbNamespace = "pfb"
  // removed library from the set because these attributes should no longer be set with updateWorkspace
  val validNamespaces = Set(AttributeName.defaultNamespace, AttributeName.tagsNamespace, AttributeName.pfbNamespace)

  val delimiter = ':'

  def withDefaultNS(name: String) = AttributeName(defaultNamespace, name)

  def withLibraryNS(name: String) = AttributeName(libraryNamespace, name)

  def withTagsNS() = AttributeName(tagsNamespace, "tags")

  def toDelimitedName(aName: AttributeName): String =
    if (aName.namespace == defaultNamespace) aName.name
    else aName.namespace + delimiter + aName.name

  def fromDelimitedName(dName: String): AttributeName =
    dName.split(delimiter).toList match {
      case sName :: Nil               => AttributeName.withDefaultNS(sName)
      case sNamespace :: sName :: Nil => AttributeName(sNamespace, sName)
      case _                          => throw new Exception(s"Attribute string $dName has too many '$delimiter' delimiters")
    }
}

sealed trait AttributeListSerializer {
  def writeListType(obj: Attribute): JsValue

  //distinguish between lists and RawJson types here
  def readComplexType(json: JsValue): Attribute

  def writeAttribute(obj: Attribute): JsValue
  def readAttribute(json: JsValue): Attribute
}

trait PlainArrayAttributeListSerializer extends AttributeListSerializer {
  override def writeListType(obj: Attribute): JsValue = obj match {
    //lists
    case AttributeValueEmptyList           => JsArray()
    case AttributeValueList(l)             => JsArray(l.map(writeAttribute): _*)
    case AttributeEntityReferenceEmptyList => JsArray()
    case AttributeEntityReferenceList(l)   => JsArray(l.map(writeAttribute): _*)
    case _                                 => throw DeserializationException("you can't pass a non-list to writeListType")
  }

  override def readComplexType(json: JsValue): Attribute = json match {
    case JsArray(a) =>
      val attrList: Seq[Attribute] = a.map(readAttribute)
      attrList match {
        case e: Seq[_] if e.isEmpty => AttributeValueEmptyList
        case v: Seq[AttributeValue @unchecked] if attrList.map(_.isInstanceOf[AttributeValue]).reduce(_ && _) =>
          AttributeValueList(v)
        case r: Seq[AttributeEntityReference @unchecked]
            if attrList.map(_.isInstanceOf[AttributeEntityReference]).reduce(_ && _) =>
          AttributeEntityReferenceList(r)
        case _ => AttributeValueRawJson(json) //heterogeneous array type? ok, we'll treat it as raw json
      }
    case _ => AttributeValueRawJson(json) //something else? ok, we'll treat it as raw json
  }
}

//Serializes attribute lists to e.g. { "itemsType" : "AttributeValue", "items" : [1,2,3] }
trait TypedAttributeListSerializer extends AttributeListSerializer {
  val LIST_ITEMS_TYPE_KEY = "itemsType"
  val LIST_ITEMS_KEY = "items"
  val LIST_OBJECT_KEYS = Set(LIST_ITEMS_TYPE_KEY, LIST_ITEMS_KEY)

  val VALUE_LIST_TYPE = "AttributeValue"
  val REF_LIST_TYPE = "EntityReference"
  val ALLOWED_LIST_TYPES = Seq(VALUE_LIST_TYPE, REF_LIST_TYPE)

  def writeListType(obj: Attribute): JsValue = obj match {
    //lists
    case AttributeValueEmptyList           => writeAttributeList(VALUE_LIST_TYPE, Seq.empty[AttributeValue])
    case AttributeValueList(l)             => writeAttributeList(VALUE_LIST_TYPE, l)
    case AttributeEntityReferenceEmptyList => writeAttributeList(REF_LIST_TYPE, Seq.empty[AttributeEntityReference])
    case AttributeEntityReferenceList(l)   => writeAttributeList(REF_LIST_TYPE, l)
    case _                                 => throw DeserializationException("you can't pass a non-list to writeListType")
  }

  def readComplexType(json: JsValue): Attribute = json match {
    case JsObject(members) if LIST_OBJECT_KEYS subsetOf members.keySet => readAttributeList(members)

    //in this serializer, [1,2,3] is not the representation for an AttributeValueList, so it's raw json
    case _ => AttributeValueRawJson(json)
  }

  def writeAttributeList[T <: Attribute](listType: String, list: Seq[T]): JsValue =
    JsObject(
      Map(LIST_ITEMS_TYPE_KEY -> JsString(listType), LIST_ITEMS_KEY -> JsArray(list.map(writeAttribute).toSeq: _*))
    )

  def readAttributeList(jsMap: Map[String, JsValue]) = {
    val attrList: Seq[Attribute] = jsMap(LIST_ITEMS_KEY) match {
      case JsArray(elems) => elems.map(readAttribute)
      case _              => throw new DeserializationException(s"the value of %s should be an array".format(LIST_ITEMS_KEY))
    }

    (jsMap(LIST_ITEMS_TYPE_KEY), attrList) match {
      case (JsString(VALUE_LIST_TYPE), _: Seq[AttributeValue @unchecked]) if attrList.isEmpty =>
        AttributeValueEmptyList
      case (JsString(VALUE_LIST_TYPE), vals: Seq[AttributeValue @unchecked])
          if attrList.map(_.isInstanceOf[AttributeValue]).reduce(_ && _) =>
        AttributeValueList(vals)

      case (JsString(REF_LIST_TYPE), _: Seq[AttributeEntityReference @unchecked]) if attrList.isEmpty =>
        AttributeEntityReferenceEmptyList
      case (JsString(REF_LIST_TYPE), refs: Seq[AttributeEntityReference @unchecked])
          if attrList.map(_.isInstanceOf[AttributeEntityReference]).reduce(_ && _) =>
        AttributeEntityReferenceList(refs)

      case (JsString(s), _) if !ALLOWED_LIST_TYPES.contains(s) =>
        throw new DeserializationException(
          s"illegal array type: $LIST_ITEMS_TYPE_KEY must be one of ${ALLOWED_LIST_TYPES.mkString(", ")}"
        )
      case _ => throw new DeserializationException("illegal array type: array elements don't match array type")
    }
  }
}

object AttributeFormat {
  //Magic strings we use in JSON serialization

  //Entity refs get serialized to e.g. { "entityType" : "sample", "entityName" : "theBestSample" }
  val ENTITY_TYPE_KEY = "entityType"
  val ENTITY_NAME_KEY = "entityName"
  val ENTITY_OBJECT_KEYS = Set(ENTITY_TYPE_KEY, ENTITY_NAME_KEY)
}

trait AttributeFormat extends RootJsonFormat[Attribute] with AttributeListSerializer {
  import AttributeFormat._

  override def write(obj: Attribute): JsValue = writeAttribute(obj)
  def writeAttribute(obj: Attribute): JsValue = obj match {
    //vals
    case AttributeNull            => JsNull
    case AttributeBoolean(b)      => JsBoolean(b)
    case AttributeNumber(n)       => JsNumber(n)
    case AttributeString(s)       => JsString(s)
    case AttributeValueRawJson(j) => j
    //ref
    case AttributeEntityReference(entityType, entityName) =>
      JsObject(Map(ENTITY_TYPE_KEY -> JsString(entityType), ENTITY_NAME_KEY -> JsString(entityName)))
    //list types
    case x: AttributeList[_] => writeListType(x)

    case _ =>
      throw new SerializationException(
        "AttributeFormat doesn't know how to write JSON for type " + obj.getClass.getSimpleName
      )
  }

  override def read(json: JsValue): Attribute = readAttribute(json)
  def readAttribute(json: JsValue): Attribute = json match {
    case JsNull       => AttributeNull
    case JsString(s)  => AttributeString(s)
    case JsBoolean(b) => AttributeBoolean(b)
    case JsNumber(n)  => AttributeNumber(n)
    //NOTE: we handle AttributeValueRawJson in readComplexType below

    case JsObject(members) if ENTITY_OBJECT_KEYS subsetOf members.keySet =>
      (members(ENTITY_TYPE_KEY), members(ENTITY_NAME_KEY)) match {
        case (JsString(typeKey), JsString(nameKey)) => AttributeEntityReference(typeKey, nameKey)
        case _ =>
          throw DeserializationException(s"the values for $ENTITY_TYPE_KEY and $ENTITY_NAME_KEY must be strings")
      }

    case _ => readComplexType(json)
  }
}

object AttributeUpdateOperations {
  import spray.json.DefaultJsonProtocol._

  sealed trait AttributeUpdateOperation {
    def name: AttributeName = this match {
      case AddUpdateAttribute(attributeName, _)                  => attributeName
      case RemoveAttribute(attributeName)                        => attributeName
      case AddListMember(attributeListName, _)                   => attributeListName
      case RemoveListMember(attributeListName, _)                => attributeListName
      case CreateAttributeEntityReferenceList(attributeListName) => attributeListName
      case CreateAttributeValueList(attributeListName)           => attributeListName
    }
  }

  case class AddUpdateAttribute(attributeName: AttributeName, addUpdateAttribute: Attribute)
      extends AttributeUpdateOperation
  case class RemoveAttribute(attributeName: AttributeName) extends AttributeUpdateOperation
  case class CreateAttributeEntityReferenceList(attributeListName: AttributeName) extends AttributeUpdateOperation
  case class CreateAttributeValueList(attributeName: AttributeName) extends AttributeUpdateOperation
  case class AddListMember(attributeListName: AttributeName, newMember: Attribute) extends AttributeUpdateOperation
  case class RemoveListMember(attributeListName: AttributeName, removeMember: Attribute)
      extends AttributeUpdateOperation

  implicit object AttributeNameFormat extends JsonFormat[AttributeName] {
    override def write(an: AttributeName): JsValue = JsString(AttributeName.toDelimitedName(an))

    override def read(json: JsValue): AttributeName = json match {
      case JsString(name) => AttributeName.fromDelimitedName(name)
      case _              => throw DeserializationException("unexpected json type")
    }
  }
  implicit val attributeFormat: AttributeFormat = new AttributeFormat with TypedAttributeListSerializer

  private val AddUpdateAttributeFormat = jsonFormat2(AddUpdateAttribute)
  private val RemoveAttributeFormat = jsonFormat1(RemoveAttribute)
  private val CreateAttributeEntityReferenceListFormat = jsonFormat1(CreateAttributeEntityReferenceList)
  private val CreateAttributeValueListFormat = jsonFormat1(CreateAttributeValueList)
  private val AddListMemberFormat = jsonFormat2(AddListMember)
  private val RemoveListMemberFormat = jsonFormat2(RemoveListMember)

  implicit object AttributeUpdateOperationFormat extends JsonWriter[AttributeUpdateOperation] {

    override def write(obj: AttributeUpdateOperation): JsValue = {
      val json = obj match {
        case x: AddUpdateAttribute                 => AddUpdateAttributeFormat.write(x)
        case x: RemoveAttribute                    => RemoveAttributeFormat.write(x)
        case x: CreateAttributeEntityReferenceList => CreateAttributeEntityReferenceListFormat.write(x)
        case x: CreateAttributeValueList           => CreateAttributeValueListFormat.write(x)
        case x: AddListMember                      => AddListMemberFormat.write(x)
        case x: RemoveListMember                   => RemoveListMemberFormat.write(x)
      }

      JsObject(json.asJsObject.fields + ("op" -> JsString(obj.getClass.getSimpleName)))
    }
  }
}
