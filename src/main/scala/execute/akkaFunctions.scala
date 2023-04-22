package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import AVL.utils.avlUtils
import AVL.utils.avlUtils.{AVLFromExport, exportAVL}
import configs.{
  AVLJsonHelper,
  AvlJson,
  ErgoNamesInsertHelper,
  conf,
  serviceOwnerConf
}
import contracts.ErgoNamesContracts

import scala.collection.JavaConverters._
import scala.collection.mutable
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.{
  Address,
  BlockchainContext,
  ErgoId,
  ErgoToken,
  ErgoValue,
  InputBox,
  SigmaProp,
  SignedTransaction
}
import org.ergoplatform.explorer.client.model.OutputInfo
import special.collection.Coll
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap}
import sigmastate.AvlTreeFlags
import utils.{
  BoxAPI,
  BoxJson,
  ContractCompile,
  NodeBoxJson,
  TransactionHelper,
  explorerApi
}
import execute.TxBuildUtility
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.encode.Base64
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import utils.RegistrySync.syncRegistry

import java.io.File
import java.nio.charset.StandardCharsets
import java.util
import javax.xml.bind.DatatypeConverter
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

  private val ldbFile = new File("./ErgonamesPlasmaDB")

  private val ldbStore = new LDBVersionedStore(ldbFile, 10)
  private val avlStorage = new VersionedLDBAVLStorage[Digest32](
    ldbStore,
    PlasmaParameters.default.toNodeParams
  )(Blake2b256)
  private def getTokenMap(
      latestRegisterBox: OutputInfo
  ): LocalPlasmaMap[ErgoNameHash, ErgoId] = {
    val latestRegisterBoxRenderedValue =
      latestRegisterBox.getAdditionalRegisters.get("R5").renderedValue
    val latestRegisterBoxElements =
      latestRegisterBoxRenderedValue
        .substring(1, latestRegisterBoxRenderedValue.length - 1)
        .split(",")

    val latestErgoNameToken = new ErgoToken(latestRegisterBoxElements(0), 1)
    val latestErgoNameTokenIndex =
      latestRegisterBoxElements(1).toInt

    val plasmaMap = new LocalPlasmaMap[ErgoNameHash, ErgoId](
      avlStorage,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    try {
      val avlJson = ErgoNamesInsertHelper.read("avl.json")
//      println(avlJson.latestInsert.index)
//      println(latestErgoNameTokenIndex.toLong)

      if (avlJson.latestInsert.index >= latestErgoNameTokenIndex.toLong) {
        plasmaMap
      } else {
        println("Syncing AVL DB From scratch")
        syncRegistry(
          exp,
          new ErgoToken(contractsConf.Contracts.mintContract.singleton, 1),
          plasmaMap
        )
        plasmaMap
      }
    } catch {
      case e: Exception =>
        println("Exception Caught, Syncing AVL DB From scratch");
        syncRegistry(
          exp,
          new ErgoToken(contractsConf.Contracts.mintContract.singleton, 1),
          plasmaMap
        ); plasmaMap
    }

  }

  def mint(
      boxJson: Array[BoxJson],
      tokenMap: LocalPlasmaMap[ErgoNameHash, ErgoId]
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

    val ergoname: String = DatatypeConverter.printHexBinary(
      ErgoName(
        new String(lastErgoName)
      ).toErgoNameHash.hashedName
    )

    (new ErgoNamesInsertHelper(lastTokenId, lastIndex, ergoname)).write(
      "./avl.json"
    )
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
      tokenMap: LocalPlasmaMap[ErgoNameHash, ErgoId],
      value: Long
  ): Boolean = {

    try {
      val ergoNameBytes: String = box.additionalRegisters.R4.renderedValue

      val ergoNameToRegister: ErgoNameHash = ErgoName(
        new String(DatatypeConverter.parseHexBinary(ergoNameBytes))
      ).toErgoNameHash

      if (
        box.additionalRegisters.R4 != null && box.additionalRegisters.R5 != null && box.value >= value
      ) {
        val lookUpResponse = tokenMap
          .lookUp(ergoNameToRegister)
          .response
          .toArray
          .head
          .tryOp
          .get
          .isEmpty

        return lookUpResponse
      }
      false
    } catch {
      case e: Exception => false
    }

  }

}
