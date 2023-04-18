package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import AVL.utils.avlUtils
import AVL.utils.avlUtils.{AVLFromExport, exportAVL}
import configs.{AVLJsonHelper, AvlJson, conf, serviceOwnerConf}
import contracts.ErgoNamesContracts

import java.util.{HexFormat, Map => JMap}
import scala.collection.JavaConverters._
import scala.collection.mutable
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, ErgoToken, ErgoValue, InputBox, SigmaProp, SignedTransaction}
import org.ergoplatform.explorer.client.model.OutputInfo
import special.collection.Coll
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import utils.{BoxAPI, BoxJson, ContractCompile, NodeBoxJson, TransactionHelper, explorerApi}
import execute.TxBuildUtility
import utils.RegistrySync.syncRegistry

import java.nio.charset.StandardCharsets
import java.util
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection asJava`
import scala.collection.mutable.ListBuffer
import scala.collection.mutable

class akkaFunctions {

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
  private val txBuilderUtil =
    new TxBuildUtility(ctx, walletMnemonic, walletMnemonicPw)

  println("Service Runner Address: " + txHelper.senderAddress)

  def getTokenMap(
      latestRegisterBox: OutputInfo
  ): PlasmaMap[ErgoNameHash, ErgoId] = {
    val latestRegisterBoxRenderedValue =
      latestRegisterBox.getAdditionalRegisters.get("R5").renderedValue
    val latestRegisterBoxElements =
      latestRegisterBoxRenderedValue
        .substring(1, latestRegisterBoxRenderedValue.length - 1)
        .split(",")

    val latestErgoNameToken = new ErgoToken(latestRegisterBoxElements(0), 1)
    val latestErgoNameTokenIndex =
      latestRegisterBoxElements(1).toInt

    val plasmaMap = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    val avlJson = AVLJsonHelper.read("avl.json")
    println(avlJson.latestInsert.index)
    println(latestErgoNameTokenIndex.toLong)

//    if (avlJson.latestInsert.index >= latestErgoNameTokenIndex.toLong) {
//      AVLFromExport(avlJson, plasmaMap)
//      plasmaMap
//    } else {
//      println("Syncing AVL DB From scratch")
//      syncRegistry(
//        exp,
//        new ErgoToken(contractsConf.Contracts.mintContract.singleton, 1)
//      )
//    }

//    AVLFromExport(avlJson, plasmaMap)
//
    val syncedTokenMap = syncRegistry(
      exp,
      new ErgoToken(contractsConf.Contracts.mintContract.singleton, 1)
    )
//
//    println(plasmaMap.ergoValue.toHex)
//    println(syncedTokenMap.ergoValue.toHex)


    syncedTokenMap
  }

  def mint(
      boxJson: Array[BoxJson],
      tokenMap: PlasmaMap[ErgoNameHash, ErgoId]
  ): Unit = { //mints tickets

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes: Array[InputBox] = boxJson
      .filter(box => validateProxyBox(box, tokenMap, 1000000000))
      .map(boxAPIObj.convertJsonBoxToInputBox)

    if (boxes.length == 0) {
      println("No Boxes Found")
      return
    }

    var registerBox: InputBox = exp.getUnspentBoxFromMempool(
      exp
        .getUnspentBoxFromTokenID(
          contractsConf.Contracts.mintContract.singleton
        )
        .getBoxId
    )

    var lastTokenId: String = ""
    var lastIndex: Long = 0
    var lastErgoName: Array[Byte] = "".getBytes(StandardCharsets.UTF_8)

    boxes.foreach(box => {
      val signedTx =
        txBuilderUtil.mintErgoNameToken(box, registerBox, tokenMap = tokenMap)
      val hash = txHelper.sendTx(signedTx)
      registerBox = signedTx.getOutputsToSpend.get(1)
      val registerBoxR5 = registerBox.getRegisters
        .get(1)
        .getValue
        .asInstanceOf[(Coll[Byte], Long)]
      val recipientBox = signedTx.getOutputsToSpend.get(0)
      lastTokenId = recipientBox.getTokens.get(0).getId.toString
      lastIndex = registerBoxR5._2
      lastErgoName = recipientBox.getRegisters
        .get(0)
        .getValue
        .asInstanceOf[Coll[Byte]]
        .toArray
      println("Mint Tx: " + hash)
    })
    exportAVL(tokenMap, lastTokenId, lastIndex, lastErgoName).write("avl.json")
  }

  def main(): Unit = {

    val compiler = new ContractCompile(ctx)

    val proxyAddress = compiler
      .compileProxyContract(
        ErgoNamesContracts.ProxyContract.contractScript,
        new ErgoToken(
          contractsConf.Contracts.mintContract.singleton,
          1
        ),
        1000000
      )
      .toAddress

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes =
      boxAPIObj
        .getUnspentBoxesFromApi(proxyAddress.toString, selectAll = true)
        .items

    val latestRegisterBox = exp.getUnspentBoxFromTokenID(
      contractsConf.Contracts.mintContract.singleton
    )
    println("Latest Box: " + latestRegisterBox.getBoxId)

    mint(boxes, getTokenMap(latestRegisterBox))

  }

  def validateProxyBox(
      box: BoxJson,
      tokenMap: PlasmaMap[ErgoNameHash, ErgoId],
      value: Long
  ): Boolean = {

    try {
      val ergoNameBytes: String = box.additionalRegisters.R4.renderedValue

      val ergoNameToRegister: ErgoNameHash = ErgoName(
        new String(HexFormat.of.parseHex(ergoNameBytes))
      ).toErgoNameHash

      box.additionalRegisters.R4 != null && box.additionalRegisters.R5 != null && box.value >= value && tokenMap
        .lookUp(ergoNameToRegister)
        .response
        .toArray
        .head
        .tryOp
        .get
        .isEmpty

    } catch {
      case e: Exception => false
    }

  }

}
