package utils

import configs.serviceOwnerConf
import explorer.Explorer
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.{InputBoxImpl, ScalaBridge}
import org.ergoplatform.explorer.client.model.{
  InputInfo,
  OutputInfo,
  TokenInfo,
  TransactionInfo
}
import org.ergoplatform.explorer.client.{DefaultApi, ExplorerApiClient}
import org.ergoplatform.restapi.client._

import java.util
import scala.collection.JavaConverters._

class explorerApi(
    apiUrl: String = serviceOwnerConf.read("serviceOwner.json").apiUrl,
    nodeUrl: String = serviceOwnerConf.read("serviceOwner.json").nodeUrl
) extends Explorer(
      nodeInfo = execute.DefaultNodeInfo(
        nodeUrl,
        apiUrl,
        new network(
          serviceOwnerConf.read("serviceOwner.json").nodeUrl
        ).getNetworkType
      )
    ) {

  def getExplorerApi(apiUrl: String): DefaultApi = {
    new ExplorerApiClient(apiUrl).createService(classOf[DefaultApi])
  }

  def buildNodeService(nodeUrl: String): ApiClient = {
    new ApiClient(nodeUrl)
  }

  def getUnspentBoxFromTokenID(tokenId: String): OutputInfo = {
    val api = this.getExplorerApi(this.apiUrl)
    val res =
      api.getApiV1BoxesUnspentBytokenidP1(tokenId, 0, 1).execute().body()
    try {
      res.getItems.get(0)
    } catch {
      case e: Exception => null
    }
  }

  def getBoxesFromTokenID(tokenId: String): Array[OutputInfo] = {
    val api = this.getExplorerApi(this.apiUrl)
    var limit = 100
    var offset = 0
    var allBoxes = Array[OutputInfo]()

    while (true) {
      val res: Array[OutputInfo] = api
        .getApiV1BoxesBytokenidP1(tokenId, offset, limit)
        .execute()
        .body()
        .getItems
        .asScala
        .toArray

      allBoxes = allBoxes ++ res

      if (res == null) {
        return allBoxes
      }

      if (res.length < limit) {
        return allBoxes
      }

      offset += limit
    }

    allBoxes
  }

  def getTokenByID(tokenId: String): TokenInfo = {
    val api = this.getExplorerApi(this.apiUrl)
    val res =
      api.getApiV1TokensP1(tokenId).execute().body()
    try {
      res
    } catch {
      case e: Exception => null
    }
  }

  def getBoxesfromTransaction(txId: String): TransactionInfo = {
    val api = this.getExplorerApi(this.apiUrl)
    api.getApiV1TransactionsP1(txId).execute().body()
  }

  def getAddressInfo(address: String): util.List[OutputInfo] = {
    val api = this.getExplorerApi(this.apiUrl)
    api.getApiV1BoxesByaddressP1(address, 0, 100).execute().body().getItems
  }

  def getUnspentBoxesByAddress(address: String): util.List[OutputInfo] = {
    val api = this.getExplorerApi(this.apiUrl)
    api.getApiV1BoxesByaddressP1(address, 0, 100).execute().body().getItems
  }

  def getBoxesfromUnconfirmedTransaction(
      txId: String
  ): Either[ErgoTransaction, TransactionInfo] = {
    val nodeService = this
      .buildNodeService(this.nodeUrl)
      .createService(classOf[TransactionsApi])

    val res = nodeService.getUnconfirmedTransactionById(txId).execute()
    if (res.code() == 404) {
      return Right(this.getBoxesfromTransaction(txId))
    }
    Left(res.body())
  }

  def getUnspentBoxFromMempool(boxId: String): InputBox = {
    val nodeService =
      this.buildNodeService(this.nodeUrl).createService(classOf[UtxoApi])
    val response = nodeService.getBoxWithPoolById(boxId).execute().body()
    if (response == null) {
      return new InputBoxImpl(this.getErgoBoxfromID(boxId))
        .asInstanceOf[InputBox]
    }
    new InputBoxImpl(response).asInstanceOf[InputBox]
  }

  def getMem(boxId: String): Boolean = {
    val nodeService =
      this.buildNodeService(this.nodeUrl).createService(classOf[UtxoApi])
    val response = nodeService.getBoxWithPoolById(boxId).execute().body()
    if (response == null) {
      return false
    }
    true
  }

  def getBoxbyIDfromExplorer(boxID: String): OutputInfo = {
    val api = this.getExplorerApi(this.apiUrl)
    api.getApiV1BoxesP1(boxID).execute().body()
  }

  def getErgoBoxfromID(boxID: String): ErgoBox = {
    val nodeService =
      this.buildNodeService(this.nodeUrl).createService(classOf[UtxoApi])
    val response: ErgoTransactionOutput =
      nodeService.getBoxWithPoolById(boxID).execute().body()

    if (response == null) {
      val box = this.getBoxbyIDfromExplorer(boxID)
      val tokens = new util.ArrayList[Asset](box.getAssets.size)
      for (asset <- box.getAssets.asScala) {
        tokens.add(
          new Asset().tokenId(asset.getTokenId).amount(asset.getAmount)
        )
      }
      val registers = new Registers
      for (registerEntry <- box.getAdditionalRegisters.entrySet.asScala) {
        registers.put(
          registerEntry.getKey,
          registerEntry.getValue.serializedValue
        )
      }
      val boxConversion: ErgoTransactionOutput = new ErgoTransactionOutput()
        .ergoTree(box.getErgoTree)
        .boxId(box.getBoxId)
        .index(box.getIndex)
        .value(box.getValue)
        .transactionId(box.getTransactionId)
        .creationHeight(box.getCreationHeight)
        .assets(tokens)
        .additionalRegisters(registers)
      return ScalaBridge.isoErgoTransactionOutput.to(boxConversion)
    }
    val tokens = new util.ArrayList[Asset](response.getAssets.size)
    for (asset <- response.getAssets.asScala) {
      tokens.add(new Asset().tokenId(asset.getTokenId).amount(asset.getAmount))
    }
    val registers = new Registers
    for (registerEntry <- response.getAdditionalRegisters.entrySet().asScala) {
      registers.put(registerEntry.getKey, registerEntry.getValue)
    }
    val boxConversion: ErgoTransactionOutput = new ErgoTransactionOutput()
      .ergoTree(response.getErgoTree)
      .boxId(response.getBoxId)
      .index(response.getIndex)
      .value(response.getValue)
      .transactionId(response.getTransactionId)
      .creationHeight(response.getCreationHeight)
      .assets(tokens)
      .additionalRegisters(registers)
    ScalaBridge.isoErgoTransactionOutput.to(boxConversion)
  }

  def getErgoBoxfromIDNoApi(box: InputInfo): ErgoBox = {

    val tokens = new util.ArrayList[Asset](box.getAssets.size)
    for (asset <- box.getAssets.asScala) {
      tokens.add(new Asset().tokenId(asset.getTokenId).amount(asset.getAmount))
    }
    val registers = new Registers
    for (registerEntry <- box.getAdditionalRegisters.entrySet.asScala) {
      registers.put(
        registerEntry.getKey,
        registerEntry.getValue.serializedValue
      )
    }
    val boxConversion: ErgoTransactionOutput = new ErgoTransactionOutput()
      .ergoTree(box.getErgoTree)
      .boxId(box.getBoxId)
      .index(box.getIndex)
      .value(box.getValue)
      .transactionId(null)
      .creationHeight(null)
      .assets(tokens)
      .additionalRegisters(registers)
    return ScalaBridge.isoErgoTransactionOutput.to(boxConversion)

  }

}
