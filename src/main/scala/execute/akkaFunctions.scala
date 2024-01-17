package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import configs.{conf, serviceOwnerConf}
import contracts.ErgoNamesContracts
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import special.collection.Coll
import sigmastate.AvlTreeFlags
import utils.{BoxAPI, BoxJson, ContractCompile, TransactionHelper, explorerApi}
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import utils.RegistrySync.{syncDatabase, syncFromDatabase}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.{LocalPlasmaMap, PlasmaMap}

import java.io.File
import java.nio.charset.StandardCharsets
import javax.xml.bind.DatatypeConverter
import scala.collection.JavaConverters._

class akkaFunctions {

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val serviceFilePath = "serviceOwner.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private lazy val contractsConf = conf.read(contractConfFilePath)

  private val exp = new explorerApi()
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
      registrySingleton: ErgoId
  ): PlasmaMap[ErgoNameHash, ErgoId] = {

    val plasmaMap = new LocalPlasmaMap[ErgoNameHash, ErgoId](
      avlStorage,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    syncDatabase(registrySingleton, exp, serviceConf.databaseUrl)
    syncFromDatabase(plasmaMap, serviceConf.databaseUrl)

    plasmaMap.toPlasmaMap
  }

  def mint(
      boxJson: Array[BoxJson],
      tokenMap: PlasmaMap[ErgoNameHash, ErgoId]
  ): Unit = {

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes: Array[InputBox] = boxJson
      .filter(box => validateProxyBox(box, tokenMap, 10000))
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
      val commitmentBoxId = new ErgoId(
        box.getRegisters
          .get(3)
          .getValue
          .asInstanceOf[Coll[Byte]]
          .toArray
      )

      val commitmentBox =
        exp.getUnspentBoxFromMempool(commitmentBoxId.toString())

      val signedTx =
        txBuilderUtil.mintErgoNameToken(
          registerBox,
          box,
          commitmentBox,
          tokenMap
        )

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
  }

  def main(): Unit = {

    val compiler = new ContractCompile(ctx)

    val registrySingleton = new ErgoToken(
      contractsConf.Contracts.mintContract.singleton,
      1
    )

    val proxyAddress = compiler
      .compileProxyContract(
        ErgoNamesContracts.ProxyContract.contractScript,
        registrySingleton,
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

    mint(boxes, getTokenMap(registrySingleton.getId))

  }

  def validateProxyBox(
      box: BoxJson,
      tokenMap: PlasmaMap[ErgoNameHash, ErgoId],
      value: Long
  ): Boolean = {

    try {
      val ergoNameBytes: String = box.additionalRegisters.R4.renderedValue

      val ergoNameToRegister: ErgoNameHash = ErgoName(
        new String(DatatypeConverter.parseHexBinary(ergoNameBytes))
      ).toErgoNameHash

      if (
        box.boxId == "602b2bee011ae644136925ebca87e11cc6685fb49ef2849004b3f2eda7291bf5" || box.boxId == "dc5af26a508354b4070d1ec6be8267883450f0f59bcb95466e4ba7855e4aa641"
      ) {
        return false
      }

      if (
        box.additionalRegisters.R4 != null && box.additionalRegisters.R5 != null && box.additionalRegisters.R5.sigmaType == "SSigmaProp" && box.value >= value
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
