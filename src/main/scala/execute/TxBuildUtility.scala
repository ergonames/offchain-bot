package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import contracts.ErgoNamesContracts
import io.getblok.getblok_plasma.collections.{
  LocalPlasmaMap,
  OpResult,
  PlasmaMap,
  Proof,
  ProvenResult
}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.ErgoBox.R9
import org.ergoplatform.{ErgoBox, ErgoScriptPredef}
import org.ergoplatform.appkit.{ErgoValue, _}
import org.ergoplatform.appkit.impl.{Eip4TokenBuilder, ErgoTreeContract}
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scalan.RType.LongType
import scorex.crypto.encode.Base16
import sigmastate.basics.DLogProtocol
import sigmastate.eval.Colls
import special.collection.Coll
import utils.RegistrySync.syncRegistry
import utils.{ContractCompile, OutBoxes, TransactionHelper, explorerApi}

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class TxBuildUtility(
    val ctx: BlockchainContext,
    txOperatorMnemonic: String,
    txOperatorMnemonicPw: String
) {
  private val txPropBytes =
    Base16.decode(ErgoScriptPredef.feeProposition(720).bytesHex).get
  private val api = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val outBoxObj = new OutBoxes(ctx)
  private val txHelper = new TransactionHelper(
    ctx = ctx,
    walletMnemonic = txOperatorMnemonic,
    mnemonicPassword = txOperatorMnemonicPw
  )

  private val compiler = new ContractCompile(ctx)

  def mintErgoNameToken(
      proxyInput: InputBox,
      registerBox: InputBox,
      initialTransactionId: String
  ): SignedTransaction = {

    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()

    val nameToRegister: Array[Byte] =
      proxyInput.getRegisters.get(0).getValue.asInstanceOf[Coll[Byte]].toArray

    val r5: special.sigma.SigmaProp = proxyInput.getRegisters
      .get(1)
      .getValue
      .asInstanceOf[special.sigma.SigmaProp]

    val recipient: Address = new org.ergoplatform.appkit.SigmaProp(r5)
      .toAddress(this.ctx.getNetworkType)

    val tokenMap: PlasmaMap[ErgoNameHash, ErgoId] = syncRegistry(
      initialTransactionId
    )

    val ergoname: ErgoNameHash = ErgoName(
      new String(nameToRegister)
    ).toErgoNameHash

    val tokenId = registerBox.getId

    val ergonameData: Seq[(ErgoNameHash, ErgoId)] = Seq(ergoname -> tokenId)
    val result: ProvenResult[ErgoId] = tokenMap.insert(ergonameData: _*)
    val opResults: Seq[OpResult[ErgoId]] = result.response
    val proof: Proof = result.proof

    val registerBoxInput = registerBox.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(ergoname.hashedName)),
      ContextVar.of(1.toByte, proof.ergoValue),
      ContextVar.of(2.toByte, nameToRegister)
    )

    inputs.append(registerBoxInput)
    inputs.append(proxyInput)

    val ergoNameRecipientToken = new Eip4Token(
      tokenId.toString,
      1L,
      new String(nameToRegister),
      "Test ErgoName Token",
      0
    )

    val ergoNamesMintContract = compiler.compileMintContract(
      ErgoNamesContracts.MintContract.contractScript,
      registerBoxInput.getTokens.get(0)
    )

    val recipientOutBox =
      outBoxObj.ergoNamesTokenOut(recipient, ergoNameRecipientToken)
    val registryOutBox =
      outBoxObj.ergoNamesRegistryBox(
        ergoNamesMintContract,
        registerBoxInput.getTokens.get(0),
        tokenMap
      )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs.asJava,
      List(recipientOutBox, registryOutBox)
    )

    txHelper.signTransaction(unsignedTx)

  }

}
