package utils

import org.ergoplatform.appkit._
import org.ergoplatform.sdk.ErgoToken
import scorex.crypto.hash

class ContractCompile(ctx: BlockchainContext) {

  def compileDummyContract(
      contract: String = "sigmaProp(true)"
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder.empty(),
      contract
    )
  }

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

  def compileCommitmentContract(
      contract: String,
      mintContract: ErgoContract,
      minerFee: Long
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder
        .create()
        .item(
          "_mintContractPropBytes",
          mintContract.toAddress.asP2S().scriptBytes
        )
        .item("_minerFee", minerFee)
        .build(),
      contract
    )
  }

}
