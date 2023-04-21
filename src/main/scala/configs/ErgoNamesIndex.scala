package configs

import com.google.gson.GsonBuilder

import java.io.{FileWriter, Writer}
import scala.io.Source

case class ErgoNamesIndex(
                    latestInsert: ErgoNamesInsert
                  )

class ErgoNamesInsertHelper(
                     tokenId: String,
                     index: Long,
                     name: Array[Byte]
                   ) {
  private val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create()

  private val latestInsert = ErgoNamesInsert(tokenId, index, name)

  private val conf = ErgoNamesIndex(
    latestInsert
  )

  def getJsonString: String = {
    return gson.toJson(conf)
  }

  def read(filePath: String): ErgoNamesIndex = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[ErgoNamesIndex])
  }

  def read: ErgoNamesIndex = {
    gson.fromJson(gson.toJson(conf), classOf[ErgoNamesIndex])
  }

  def write(filePath: String): Unit = {
    val writer: Writer = new FileWriter(filePath)
    writer.write(this.getJsonString)
    writer.close()
  }
}

object ErgoNamesInsertHelper {
  private val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create()

  def read(filePath: String): ErgoNamesIndex = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson
      .fromJson(jsonString, classOf[ErgoNamesIndex])
  }

  def readJsonString(jsonString: String): ErgoNamesIndex = {
    gson
      .fromJson(jsonString, classOf[ErgoNamesIndex])
  }
}
