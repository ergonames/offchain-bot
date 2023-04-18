package utils

import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.JavaHelpers.JLongRType
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.ergoplatform.appkit.scalaapi.Iso.isoInt
import org.ergoplatform.appkit.{Address, BlockchainContext, Eip4Token, ErgoContract, ErgoToken, ErgoType, ErgoValue, OutBox}
import sigmastate.eval.Colls
import special.collection.Coll

class ErgoNamesOutBox(ctx: BlockchainContext) extends OutBoxes(ctx) {

  def ergoNamesInitOutBox[K, V](
      mintContract: ErgoContract,
      singleton: Eip4Token,
      tokenMap: PlasmaMap[K, V],
      amount: Double = 0.001
  ): OutBox = {
    val t: (Coll[Byte], Long) = (Colls.fromArray(singleton.getId.getBytes), 0L)
    this.txBuilder
      .outBoxBuilder()
      .value(getAmount(amount))
      .contract(mintContract)
      .mintToken(singleton)
      .registers(tokenMap.ergoValue, ErgoValueBuilder.buildFor(t))
      .build()
  }

  def ergoNamesTokenOut(
      receiver: Address,
      ergoNameToken: Eip4Token,
      amount: Double = 0.001
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(getAmount(amount))
      .contract(
        new ErgoTreeContract(
          receiver.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .mintToken(ergoNameToken)
      .build()
  }

  def ergoNamesRegistryBox[K, V](
      contract: ErgoContract,
      singleton: ErgoToken,
      tokenMap: PlasmaMap[K, V],
      recipientErgoNameToken: Eip4Token,
      index: Long,
      amount: Double = 0.001
  ): OutBox = {

    val t: (Coll[Byte], Long) = (Colls.fromArray(recipientErgoNameToken.getId.getBytes), index)
    this.txBuilder
      .outBoxBuilder()
      .value(getAmount(amount))
      .contract(contract)
      .tokens(singleton)
      .registers(
        tokenMap.ergoValue,
        ErgoValueBuilder.buildFor(t)
        )
      .build()
  }
}
