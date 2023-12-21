package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import contracts.ErgoNamesContracts
import org.ergoplatform.ErgoScriptPredef
import org.ergoplatform.appkit.{ErgoValue, _}
import org.ergoplatform.sdk.ErgoId
import special.collection.Coll
import utils.{ContractCompile, ErgoNamesOutBox, TransactionHelper, explorerApi}
import work.lithos.plasma.collections.{
  LocalPlasmaMap,
  OpResult,
  Proof,
  ProvenResult
}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class TxBuildUtility(
    val ctx: BlockchainContext,
    txOperatorMnemonic: String,
    txOperatorMnemonicPw: String
) {
  private val ErgoNamesOutBox = new ErgoNamesOutBox(ctx)
  private val txHelper = new TransactionHelper(
    ctx = ctx,
    walletMnemonic = txOperatorMnemonic,
    mnemonicPassword = txOperatorMnemonicPw
  )

  private val compiler = new ContractCompile(ctx)

  def mintErgoNameToken(
      proxyInput: InputBox,
      registerBox: InputBox,
      commitmentInput: InputBox,
      tokenMap: LocalPlasmaMap[ErgoNameHash, ErgoId]
  ): SignedTransaction = {

    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()

    val nameToRegister: Array[Byte] =
      proxyInput.getRegisters.get(0).getValue.asInstanceOf[Coll[Byte]].toArray

    val r5: special.sigma.SigmaProp = proxyInput.getRegisters
      .get(1)
      .getValue
      .asInstanceOf[special.sigma.SigmaProp]

    val registryR5: (Coll[Byte], Long) = registerBox.getRegisters
      .get(1)
      .getValue
      .asInstanceOf[(Coll[Byte], Long)]

    val recipient: Address = new org.ergoplatform.appkit.SigmaProp(r5)
      .toAddress(this.ctx.getNetworkType)

    //    val tokenMap: PlasmaMap[ErgoNameHash, ErgoId] =
    //      syncRegistry(api, registerBox.getTokens.get(0))

    //    tokenMap.insert(
    //      (
    //        ErgoName("hello").toErgoNameHash -> new ErgoToken(
    //          "cf3b6e369c17c7d9eb318799759362e55955ba02d3bdef0bad18fc46101ab016",
    //          1
    //        ).getId
    //      )
    //    )

    println("registering: " + new String(nameToRegister))

    val ergoname: ErgoNameHash = ErgoName(
      new String(nameToRegister)
    ).toErgoNameHash

    val tokenId = registerBox.getId

    val result: ProvenResult[ErgoId] = tokenMap.insert((ergoname, tokenId))
    //    println("Final Token Map Hex: " + tokenMap.ergoValue.toHex)
    val opResults: Seq[OpResult[ErgoId]] = result.response
    val proof: Proof = result.proof

    val registerBoxInput = registerBox.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(ergoname.hashedName)),
      ContextVar.of(1.toByte, proof.ergoValue),
      ContextVar.of(2.toByte, nameToRegister)
    )

    inputs.append(registerBoxInput)
    inputs.append(proxyInput)
    inputs.append(commitmentInput)

    val ergoNameRecipientToken = new Eip4Token(
      tokenId.toString,
      1L,
      new String(nameToRegister),
      "Test ErgoName Token",
      0
    )

    val ergoNamesMintContract = compiler.compileMintContract(
      ErgoNamesContracts.MintContract.contractScript,
      registerBoxInput.getTokens.get(0),
      null
    )

    val recipientOutBox =
      ErgoNamesOutBox.ergoNamesTokenOut(recipient, ergoNameRecipientToken)
    val registryOutBox =
      ErgoNamesOutBox.ergoNamesRegistryBox(
        ergoNamesMintContract,
        registerBoxInput.getTokens.get(0),
        tokenMap,
        ergoNameRecipientToken,
        registryR5._2 + 1L
      )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs,
      List(recipientOutBox, registryOutBox)
    )

    txHelper.signTransaction(unsignedTx)

  }

}
