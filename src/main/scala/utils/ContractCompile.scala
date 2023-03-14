package utils

import org.ergoplatform.appkit._
import scorex.crypto.hash

class ContractCompile(ctx: BlockchainContext) {

  def compileProxyContract(
      contract: String,
      singleton: ErgoToken,
      minerFee: Long
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder
        .create()
        .item("_singletonToken", singleton.getId.getBytes)
        .item("_minerFee", minerFee)
        .build(),
      contract
    )
  }

  def compileMintContract(
      contract: String,
      singleton: ErgoToken
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder
        .create()
        .item("_singletonToken", singleton.getId.getBytes)
        .build(),
      contract
    )
  }

}
