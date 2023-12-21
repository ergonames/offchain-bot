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
      singleton: ErgoToken,
      subnameContract: ErgoContract
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder
        .create()
        .item("_singletonToken", singleton.getId.getBytes)
        .item(
          "_subnameContractBytes",
          subnameContract.toAddress.asP2S().scriptBytes
        )
        .build(),
      contract
    )
  }

  def compileSubnameContract(
      contract: String
  ): ErgoContract = {
    this.ctx.compileContract(
      ConstantsBuilder.empty(),
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
