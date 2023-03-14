package utils

import AVL.ErgoName.{ErgoName, ErgoNameHash}

import io.getblok.getblok_plasma.{PlasmaParameters, ByteConversion}
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.appkit.ErgoId
import sigmastate.AvlTreeFlags

import utils.DatabaseUtils.readRegistryInsertion
import io.getblok.getblok_plasma.collections.ProvenResult

object RegistrySync {

  private def createEmptyRegistry(): PlasmaMap[ErgoNameHash, ErgoId] = {
    val registry = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    registry
  }

  def syncRegistry(
      initialTransactionId: String
  ): PlasmaMap[ErgoNameHash, ErgoId] = {
    val registry = createEmptyRegistry()
    var registrationInfo = readRegistryInsertion(initialTransactionId)
    var spentTransactionId = registrationInfo.spentTransactionId
    registrationInfo = readRegistryInsertion(spentTransactionId)
    while (registrationInfo != null) {
      val ergonameData: Seq[(ErgoNameHash, ErgoId)] = Seq(
        registrationInfo.ergoNameRegistered -> registrationInfo.ergoNameTokenId
      )
      val result: ProvenResult[ErgoId] = registry.insert(ergonameData: _*)
      spentTransactionId = registrationInfo.spentTransactionId
      registrationInfo = readRegistryInsertion(spentTransactionId)
    }
    registry
  }

}
