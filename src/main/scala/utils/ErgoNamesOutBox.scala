package utils

import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.ergoplatform.appkit.{
  Address,
  BlockchainContext,
  Eip4Token,
  ErgoContract,
  ErgoType,
  ErgoValue,
  OutBox
}
import org.ergoplatform.sdk.ErgoToken
import sigmastate.eval.Colls
import special.collection.Coll
import work.lithos.plasma.collections.{LocalPlasmaMap, PlasmaMap}

import java.nio.charset.StandardCharsets

class ErgoNamesOutBox(ctx: BlockchainContext) extends OutBoxes(ctx) {

  private val minAmount = 1000000L

  def ergoNamesInitOutBox[K, V](
      mintContract: ErgoContract,
      singleton: Eip4Token,
      tokenMap: PlasmaMap[K, V],
      amount: Long = minAmount
  ): OutBox = {
    val t: (Coll[Byte], Long) = (Colls.fromArray(singleton.getId.getBytes), 0L)
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(mintContract)
      .mintToken(singleton)
      .registers(tokenMap.ergoValue, ErgoValueBuilder.buildFor(t))
      .build()
  }

  def ergoNamesTokenOut(
      receiver: Address,
      ergoNameToken: Eip4Token,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
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
      tokenMap: LocalPlasmaMap[K, V],
      recipientErgoNameToken: ErgoToken,
      index: Long,
      amount: Long = minAmount
  ): OutBox = {

    val t: (Coll[Byte], Long) =
      (Colls.fromArray(recipientErgoNameToken.getId.getBytes), index)
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(contract)
      .tokens(singleton)
      .registers(
        tokenMap.ergoValue,
        ErgoValueBuilder.buildFor(t)
      )
      .build()
  }

  def ergoNamesRegistryBoxForTesting[K, V](
      contract: ErgoContract,
      singleton: ErgoToken,
      tokenMap: PlasmaMap[K, V],
      recipientErgoNameToken: ErgoToken,
      index: Long,
      amount: Long = minAmount
  ): OutBox = {

    val t: (Coll[Byte], Long) =
      (Colls.fromArray(recipientErgoNameToken.getId.getBytes), index)
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(contract)
      .tokens(singleton)
      .registers(
        tokenMap.ergoValue,
        ErgoValueBuilder.buildFor(t)
      )
      .build()
  }

  def ergoNamesSubNamesBoxForTesting[K, V](
      contract: ErgoContract,
      tokenMap: PlasmaMap[K, V],
      recipientErgoNameToken: ErgoToken,
      amount: Long = minAmount
  ): OutBox = {

    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(contract)
      .registers(
        tokenMap.ergoValue,
        ErgoValue.of(recipientErgoNameToken.getId.getBytes)
      )
      .build()
  }

  def proxyBox(
      proxyContract: ErgoContract,
      nameToRegister: String,
      buyerPk: Address,
      commitmentSecret: Array[Byte],
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .registers(
        ErgoValue.of(nameToRegister.getBytes(StandardCharsets.UTF_8)),
        ErgoValue.of(buyerPk.getPublicKey),
        ErgoValue.of(commitmentSecret)
      )
      .contract(proxyContract)
      .build()
  }

  def commitmentBox(
      commitmentContract: ErgoContract,
      commitmentHash: Array[Byte],
      buyerPk: Address,
      creationHeight: Int = this.ctx.getHeight,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .registers(
        ErgoValue.of(commitmentHash),
        ErgoValue.of(buyerPk.getPublicKey)
      )
      .contract(commitmentContract)
      .creationHeight(creationHeight)
      .build()
  }
}
