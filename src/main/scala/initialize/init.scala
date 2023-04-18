package initialize

import AVL.ErgoName.ErgoNameHash
import configs.{conf, serviceOwnerConf}
import contracts.ErgoNamesContracts
import execute.{Client, DefaultNodeInfo}
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.ErgoBox
import org.ergoplatform.ErgoBox.R5
import org.ergoplatform.appkit.{ErgoId, ErgoToken, ErgoValue, InputBox, SignedTransaction}
import sigmastate.AvlTreeFlags
import special.collection.Coll
import utils.{ContractCompile, ErgoNamesOutBox, OutBoxes, TransactionHelper, explorerApi}

import java.nio.charset.StandardCharsets
import java.util

object init extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val serviceFilePath = "serviceOwner.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private lazy val contractsConf = conf.read(contractConfFilePath)
  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val walletMnemonic = serviceConf.txOperatorMnemonic
  private val walletMnemonicPw = serviceConf.txOperatorMnemonicPw
  private val txHelper =
    new TransactionHelper(this.ctx, walletMnemonic, walletMnemonicPw)

  private val compiler = new ContractCompile(ctx)

  private val genesisTX: SignedTransaction =
    txHelper.simpleSend(
      List(txHelper.senderAddress),
      List(
        0.02
      )
    )

  val genesisOutBox: InputBox = genesisTX.getOutputsToSpend.get(0)
  val inputBoxList = new util.ArrayList[InputBox]()
  inputBoxList.add(genesisOutBox)

  println("Genesis: " + txHelper.sendTx(genesisTX))

  private val ErgoNamesOutBox = new ErgoNamesOutBox(this.ctx)

  private val singletonTokenID = inputBoxList.get(0).getId.toString

  private val mintContract = compiler.compileMintContract(
    ErgoNamesContracts.MintContract.contractScript,
    new ErgoToken(singletonTokenID, 1)
  )
  private val proxyContract = compiler.compileProxyContract(
    ErgoNamesContracts.ProxyContract.contractScript,
    new ErgoToken(singletonTokenID, 1),
    1000000
  )

  private val token = ErgoNamesOutBox.collectionTokenHelper(
    inputBoxList.get(0),
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
    ErgoNamesOutBox.ergoNamesInitOutBox(mintContract, token, tokenMap)

  private val initTX = txHelper.signTransaction(
    txHelper.buildUnsignedTransaction(inputBoxList, List(tokenOutBox))
  )

  private val initTx = txHelper.sendTx(initTX)

  (new conf(
    mintContract.toAddress.toString,
    singletonTokenID,
    initTx.toString.stripPrefix("\"").stripSuffix("\""),
    proxyContract.toAddress.toString
  )).write(contractConfFilePath)

  println("init tx: " + initTx)

}

object getContracts extends App {

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val compiler = new ContractCompile(ctx)

  private val singletonTokenID =
    "85f9d234f320194b7ada4d150d7369f16b0057c7811d9f8c1218c9ce991849e8"

  private val mintContract = compiler.compileMintContract(
    ErgoNamesContracts.MintContract.contractScript,
    new ErgoToken(singletonTokenID, 1)
  )

  private val proxyContract = compiler.compileProxyContract(
    ErgoNamesContracts.ProxyContract.contractScript,
    new ErgoToken(singletonTokenID, 1),
    1000000
  )

  println("Mint Contract: " + mintContract.toAddress.toString)
  println("Mint Contract ErgoTree: " + mintContract.getErgoTree.bytesHex)
  println("Proxy Contract: " + proxyContract.toAddress.toString)
  println("Proxy Contract ErgoTree: " + proxyContract.getErgoTree.bytesHex)
}

object apiTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  val tokenID =
    "489a6128353d8b52fc42c82473f60f5e024f025b5713ab63e8f2bc3e1132eaaf"

  val res = exp.getBoxesFromTokenID(tokenID)

  val tokenList = new util.ArrayList[(String, String)]()

  for(e <- res){
    val renderedValue = e.getAdditionalRegisters.get("R5").renderedValue
    val elements =
      renderedValue.substring(1, renderedValue.length - 1).split(",")
    val tuple = (elements(0), elements(1).toInt)
    val name = exp.getTokenByID(tuple._1).getName
    tokenList.add((tuple._1, name))
  }
}
