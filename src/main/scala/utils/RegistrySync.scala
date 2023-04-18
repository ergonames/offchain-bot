package utils

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import io.getblok.getblok_plasma.{ByteConversion, PlasmaParameters}
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.appkit.{ErgoId, ErgoToken}
import sigmastate.AvlTreeFlags

import java.util
import utils.DatabaseUtils.readRegistryInsertion
import io.getblok.getblok_plasma.collections.ProvenResult

import scala.collection.mutable.ListBuffer
import scala.util.Random

object RegistrySync {

  private def createEmptyRegistry(): PlasmaMap[ErgoNameHash, ErgoId] = {
    val registry = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    registry
  }

  def syncRegistry(
      exp: explorerApi,
      singleton: ErgoToken
  ): PlasmaMap[ErgoNameHash, ErgoId] = {
    val registry = createEmptyRegistry()
    val res = exp.getBoxesFromTokenID(singleton.getId.toString)
    val tokenList = new ListBuffer[(Long, (String, String))]
    for (e <- res) {
      val renderedValue = e.getAdditionalRegisters.get("R5").renderedValue
      val elements =
        renderedValue.substring(1, renderedValue.length - 1).split(",")
      val tuple = (elements(0), elements(1).toLong)
      val name = exp.getTokenByID(tuple._1).getName
      if (tuple._1 != singleton.getId.toString) {
        tokenList.append((tuple._2, (tuple._1, name)))
        //        println("appended: " + name + " index: " + tuple._2)
      }
    }

    val sortedTokenList = tokenList.sortBy(_._1)

    sortedTokenList.foreach(e => {
//      println(
//        "inserting: " + e._2._2 + " tokenId: " + e._2._1 + " index: " + e._1
//      )
      val res = registry.insert(
        (
          ErgoName(e._2._2).toErgoNameHash,
          new ErgoToken(
            e._2._1,
            1
          ).getId
        )
      )

//      println(registry.ergoValue.toHex)
    })

    registry
  }

}
