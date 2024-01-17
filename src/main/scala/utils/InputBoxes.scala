package utils

import org.ergoplatform.appkit._
import org.ergoplatform.sdk.ErgoToken

import scala.collection.JavaConverters._

class InputBoxes(val ctx: BlockchainContext) {

  def getBoxesById(boxIds: String*): Array[InputBox] = {
    this.ctx.getBoxesById(boxIds: _*)
  }

  def getInputs(
      amountList: Seq[Long],
      senderAddress: Address,
      tokens: Seq[ErgoToken] = Seq()
  ): Seq[InputBox] = {
    val amountTotal: Long = amountList.sum

    val inputs = BoxOperations
      .createForSender(senderAddress, this.ctx)
      .withAmountToSpend(amountTotal)
      .withInputBoxesLoader(new ExplorerAndPoolUnspentBoxesLoader())

    if (tokens.nonEmpty) {
      inputs.withTokensToSpend(tokens.asJava)
    }
    inputs.loadTop().asScala
  }

}
