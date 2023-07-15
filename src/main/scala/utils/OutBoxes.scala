package utils

import org.ergoplatform.appkit.impl.{Eip4TokenBuilder, ErgoTreeContract}
import org.ergoplatform.appkit._
import org.ergoplatform.sdk.ErgoToken
import work.lithos.plasma.collections.LocalPlasmaMap

import java.nio.charset.StandardCharsets
import java.util
import scala.collection.mutable.ListBuffer

class OutBoxes(ctx: BlockchainContext) {

  private val minAmount = 1000000L
  private val txBuilder = this.ctx.newTxBuilder()

  def pictureNFTHelper(
      inputBox: InputBox,
      name: String,
      description: String,
      imageLink: String,
      sha256: Array[Byte]
  ): Eip4Token = {
    val tokenID = inputBox.getId.toString
    Eip4TokenBuilder.buildNftPictureToken(
      tokenID,
      1,
      name,
      description,
      0,
      sha256,
      imageLink
    )

  }

  def tokenHelper(
      inputBox: InputBox,
      name: String,
      description: String,
      tokenAmount: Long,
      tokenDecimals: Int
  ): Eip4Token = {
    new Eip4Token(
      inputBox.getId.toString,
      tokenAmount,
      name,
      description,
      tokenDecimals
    )
  }

  def collectionTokenHelper(
      inputBox: InputBox,
      name: String,
      description: String,
      tokenAmount: Long,
      tokenDecimals: Int
  ): Eip4Token = {

    Eip4TokenBuilder.buildNftPictureToken(
      inputBox.getId.toString,
      tokenAmount,
      name,
      description,
      tokenDecimals,
      Array(0.toByte),
      "link"
    )
  }

  def NFToutBox(
      nft: Eip4Token,
      receiver: Address,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .mintToken(nft)
      .contract(
        new ErgoTreeContract(
          receiver.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .build()
  }

  def tokenOutBox(
      token: Eip4Token,
      receiver: Address,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .mintToken(token)
      .contract(
        new ErgoTreeContract(
          receiver.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .build()
  }

  def tokenSendOutBox(
      receiver: List[Address],
      amountList: List[Long],
      tokens: List[List[String]],
      amountTokens: List[List[Long]] = null
  ): List[OutBox] = {
    val outbx = new ListBuffer[OutBox]()
    var amountCounter = 0
    var tokenList1 = new ListBuffer[ListBuffer[ErgoToken]]()
    var tokenList2 = new util.ArrayList[ErgoToken]()
    var tokenAmountCounter = 0
    val tokenList = new ListBuffer[ErgoToken]()
    var tList = new ListBuffer[ErgoToken]()
    if (amountTokens == null) {
      for (token <- tokens) {
        for (x <- token) {
          val t: ErgoToken = new ErgoToken(x, 1)
          tokenList.append(t)
        }
      }
    } else {
      for (token <- tokens) {
        var tokenAmountCounterLocal = 0
        var tokenAmountList = amountTokens.apply(tokenAmountCounter)
        for (x <- token) {
          var tokenAmm = tokenAmountList.apply(tokenAmountCounterLocal)
          tList.append(new ErgoToken(x, tokenAmm))
          tokenAmountCounterLocal = tokenAmountCounterLocal + 1
        }
        tokenAmountCounter = tokenAmountCounter + 1
        tokenList1.append(tList)
        tList = new ListBuffer[ErgoToken]()
      }
    }
    for (address: Address <- receiver) {
      val erg: Long = amountList.apply(amountCounter)
      val box = this.ctx
        .newTxBuilder()
        .outBoxBuilder()
        .value(erg)
        .tokens(tokenList1.apply(amountCounter).toArray: _*)
        .contract(
          new ErgoTreeContract(
            address.getErgoAddress.script,
            this.ctx.getNetworkType
          )
        )
        .build()
      outbx.append(box)
      amountCounter += 1
    }
    outbx.toList
  }

  def buyerNFTOutBox(
      nft: Eip4Token,
      receiver: Address,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .mintToken(nft)
      .contract(
        new ErgoTreeContract(
          receiver.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .build()
  }

  def payoutBox(
      receiver: Address,
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
      .build()
  }

  def AVLDebugBox[K, V](
      contract: ErgoContract,
      metaDataMap: LocalPlasmaMap[K, V],
      index: Long,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .registers(
        metaDataMap.ergoValue,
        ErgoValue.of(index)
      )
      .contract(contract)
      .build()
  }

  def AVLDebugBoxSpending(
      senderAddress: Address,
      description: String,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .registers(ErgoValue.of(description.getBytes(StandardCharsets.UTF_8)))
      .contract(
        new ErgoTreeContract(
          senderAddress.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .build()
  }

  def genericContractBox(
      contract: ErgoContract,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(contract)
      .build()
  }

  def simpleOutBox(
      senderAddress: Address,
      amount: Long = minAmount
  ): OutBox = {
    this.txBuilder
      .outBoxBuilder()
      .value(amount)
      .contract(
        new ErgoTreeContract(
          senderAddress.getErgoAddress.script,
          this.ctx.getNetworkType
        )
      )
      .build()
  }

}
