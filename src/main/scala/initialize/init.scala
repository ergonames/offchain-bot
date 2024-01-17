package initialize

import AVL.ErgoName.ErgoNameHash
import configs.{conf, serviceOwnerConf}
import contracts.ErgoNamesContracts
import execute.Client
import org.ergoplatform.appkit.{InputBox, Parameters, SignedTransaction}
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import sigmastate.AvlTreeFlags
import utils.{
  ContractCompile,
  ErgoNamesOutBox,
  InputBoxes,
  TransactionHelper,
  explorerApi
}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.PlasmaMap

import java.util
import scala.collection.mutable.ListBuffer

object init extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val serviceFilePath = "serviceOwner.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private val exp = new explorerApi()
  private val walletMnemonic = serviceConf.txOperatorMnemonic
  private val walletMnemonicPw = serviceConf.txOperatorMnemonicPw
  private val txHelper =
    new TransactionHelper(this.ctx, walletMnemonic, walletMnemonicPw)

  private val compiler = new ContractCompile(ctx)
  val inputBoxesObj = new InputBoxes(ctx)

  val genesisInput =
    inputBoxesObj
      .getBoxesById(
        ""
      )

  val inputBoxList = new ListBuffer[InputBox]()
  inputBoxList.append(genesisInput: _*)

  private val ErgoNamesOutBox = new ErgoNamesOutBox(this.ctx)

  private val singletonTokenID = inputBoxList.head.getId.toString

  private val subnamesContract = compiler.compileSubnameContract(
    ErgoNamesContracts.SubnameContract.contractScript
  )

  private val mintContract = compiler.compileMintContract(
    ErgoNamesContracts.MintContract.contractScript,
    new ErgoToken(singletonTokenID, 1),
    subnamesContract
  )
  private val proxyContract = compiler.compileProxyContract(
    ErgoNamesContracts.ProxyContract.contractScript,
    new ErgoToken(singletonTokenID, 1),
    1000000
  )

  private val commitmentContract = compiler.compileCommitmentContract(
    ErgoNamesContracts.CommitmentContract.contractScript,
    mintContract,
    1000000L
  )

  private val token = ErgoNamesOutBox.collectionTokenHelper(
    inputBoxList.head,
    "ErgoNames Singleton Test",
    "A singleton test for ErgoNames",
    1,
    0
  )

  val tokenMap = new PlasmaMap[ErgoNameHash, ErgoId](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  private val tokenOutBox =
    ErgoNamesOutBox.ergoNamesInitOutBox(
      mintContract,
      token,
      tokenMap,
      Parameters.OneErg
    )

  private val initTX = txHelper.signTransaction(
    txHelper.buildUnsignedTransaction(inputBoxList, Array(tokenOutBox))
  )

  private val initTx = txHelper.sendTx(initTX)

  (new conf(
    mintContract.toAddress.toString,
    singletonTokenID,
    initTx.toString.stripPrefix("\"").stripSuffix("\""),
    proxyContract.toAddress.toString
  )).write(contractConfFilePath)

  println(s"proxy contract: ${proxyContract.getErgoTree.bytesHex}")
  println(s"commitment contract: ${commitmentContract.getErgoTree.bytesHex}")

  println("init tx: " + initTx)

}

object apiTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val exp = new explorerApi()
  val tokenID =
    "489a6128353d8b52fc42c82473f60f5e024f025b5713ab63e8f2bc3e1132eaaf"

  val res = exp.getBoxesFromTokenID(tokenID)

  val tokenList = new util.ArrayList[(String, String)]()

  for (e <- res) {
    val renderedValue = e.getAdditionalRegisters.get("R5").renderedValue
    val elements =
      renderedValue.substring(1, renderedValue.length - 1).split(",")
    val tuple = (elements(0), elements(1).toInt)
    val name = exp.getTokenByID(tuple._1).getName
    tokenList.add((tuple._1, name))
  }
}
