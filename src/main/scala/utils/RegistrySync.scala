package utils

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import org.ergoplatform.explorer.client.model.OutputInfo
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import sigmastate.AvlTreeFlags

import java.util
import utils.DatabaseUtils.readRegistryInsertion
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.LocalPlasmaMap

import java.io.File
import scala.collection.mutable.ListBuffer

object RegistrySync {

  private def createEmptyRegistry(): LocalPlasmaMap[ErgoNameHash, ErgoId] = {
    val ldbName = "ErgoNamesPlasmaDB"
    val ldbStore = new LDBVersionedStore(new File(ldbName), 0)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)

    new LocalPlasmaMap[ErgoNameHash, ErgoId](
      avlStorage,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
  }

  def syncRegistry(
      exp: explorerApi,
      singleton: ErgoToken,
      registry: LocalPlasmaMap[ErgoNameHash, ErgoId]
  ): Unit = {
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

  }

}
