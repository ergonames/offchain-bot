package utils

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.sdk.{ErgoId}
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import sigmastate.AvlTreeFlags
import special.collection.Coll
import special.sigma.AvlTree

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

  def syncDatabase(
      singleton: ErgoId,
      exp: explorerApi,
      databaseUrl: String
  ): Unit = {

    try {
      val db = new Database(databaseUrl)

      val maxIndex = db.getRowWithMaxIndex
      var startingIndex = 0L
      var startingToken = singleton.toString()
      var startingName = "singleton"
      var startingDigest =
        "4ec61f485b98eb87153f7c57db4f5ecd75556fddbc403b41acf8441fde8e160900"

      if (maxIndex.isEmpty) {
        if (!db.clearTable()) {
          throw new Exception("error clearing DB")
        }
      } else {
        startingIndex = maxIndex.get._2
        startingToken = maxIndex.get._1
        startingName = maxIndex.get._3
        startingDigest = maxIndex.get._4
      }

      val latestRegistry = exp.getUnspentBoxFromTokenID(singleton.toString())
      val latestIndex = ErgoValue
        .fromHex(
          latestRegistry.getAdditionalRegisters.get("R5").serializedValue
        )
        .getValue
        .asInstanceOf[(Coll[Byte], Long)]
        ._2

      if (latestIndex == maxIndex.get._2) {
        println("DB is already synced")
        return
      }

      if (startingToken == singleton.toString()) {

        val response = db.writeRow(
          startingToken,
          startingIndex,
          startingName,
          startingDigest
        )
        if (!response) {
          throw new Exception("error writing to DB")
        }
      }

      startingIndex += 1

      val registryBoxes = exp.getBoxesFromTokenID(singleton.toString())

      val tokenList = new ListBuffer[(Long, (String, String, String))]

      registryBoxes.foreach(box => {
        val avlSerializedValue =
          box.getAdditionalRegisters.get("R4").serializedValue
        val avlDigest = Hex.toHexString(
          ErgoValue
            .fromHex(avlSerializedValue)
            .getValue
            .asInstanceOf[AvlTree]
            .digest
            .toArray
        )
        val registrationInfo = ErgoValue
          .fromHex(box.getAdditionalRegisters.get("R5").serializedValue)
          .getValue
          .asInstanceOf[(Coll[Byte], Long)]
        val tokenId = new ErgoId(registrationInfo._1.toArray)
        val name = exp.getTokenByID(tokenId.toString()).getName
        if (tokenId.toString() != singleton.toString()) {
          tokenList.append(
            (registrationInfo._2, (tokenId.toString(), name, avlDigest))
          )
        }
      })

      val sortedTokenList = tokenList.filter(_._1 >= startingIndex).sortBy(_._1)

      val response = db.writeRowsBatch(sortedTokenList)

      if (!response) {
        throw new Exception("error batch writing to DB")
      }

      println("DB synced")

    } catch {
      case e: Exception => throw e
      case a: Throwable => println("error syncing DB")
    }
  }

  def syncFromDatabase(
      registry: LocalPlasmaMap[ErgoNameHash, ErgoId],
      databaseUrl: String
  ): Unit = {

    try {
      val db = new Database(databaseUrl)

      val digest = Hex.toHexString(registry.ergoAVLTree.digest.toArray)

      val dbRow = db.getRowByDigest(digest)

      if (dbRow.isEmpty) {
        throw new Exception("could not get digest")
      }

      val (tokenId, index, name, digestClone) = dbRow.get

      val maxIndex = db.getRowWithMaxIndex

      if (maxIndex.isEmpty) {
        throw new Exception("could not get max index")
      }

      if (index == maxIndex.get._2) {
        println("AVL map already synced")
        return
      }

      val infoToWrite = db.getAllRowsGreaterThanIndex(index)

      println(
        s"Initial AVL digest: ${Hex.toHexString(registry.ergoAVLTree.digest.toArray)}"
      )

      infoToWrite.foreach(info => {
        val hashedName = ErgoName(info._3).toErgoNameHash
        val tokenId = new ErgoId(Hex.decode(info._1))

        registry.insert((hashedName, tokenId))

        println(
          s"Inserting ${info._3}, token id: ${tokenId.toString()}, digest after insertion: ${Hex
            .toHexString(registry.ergoAVLTree.digest.toArray)}"
        )

      })

      println("AVL map synced")
    } catch {
      case e: Throwable => throw new Exception("could not sync")
    }
  }

}
