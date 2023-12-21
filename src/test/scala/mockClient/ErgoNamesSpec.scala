package mockClient

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import contracts.ErgoNamesContracts
import mockUtils.FileMockedErgoClient
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{
  Address,
  BlockchainContext,
  ContextVar,
  Eip4Token,
  ErgoValue,
  UnsignedTransaction
}
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scorex.crypto.hash
import sigmastate.AvlTreeFlags
import utils.{
  ContractCompile,
  ErgoNamesOutBox,
  OutBoxes,
  TransactionHelper,
  explorerApi
}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.{
  LocalPlasmaMap,
  PlasmaMap,
  Proof,
  ProvenResult
}

import java.nio.charset.StandardCharsets

class ErgoNamesSpec
    extends AnyFlatSpec
    with Matchers
    with HttpClientTesting
    with Common {

  val nodeUrl = "https://ergonode-api-uy.ergohost.io/"
  val apiUrl = "https://ergo-explorer.anetabtc.io"

  val testnetNodeUrl = "http://192.168.50.14:9052"
  val testnetApiUrl = "https://api-testnet.ergoplatform.com"

  val mainnetExp = new explorerApi(apiUrl, nodeUrl)
  val testnetExp = new explorerApi(testnetApiUrl, testnetNodeUrl)

  val ergoClient: FileMockedErgoClient = createMockedErgoClient(
    MockData(Nil, Nil)
  )

  val mockchainCtx: BlockchainContext = ergoClient.execute(ctx => ctx)

  val txHelper = new TransactionHelper(
    ctx = mockchainCtx,
    walletMnemonic =
      "caution crowd hawk trip enroll board puppy degree omit injury tired mail banana issue broccoli"
  )

  val compiler = new ContractCompile(mockchainCtx)
  val outBoxObj = new ErgoNamesOutBox(mockchainCtx)

  private val singletonTokenID =
    "85f9d234f320194b7ada4d150d7369f16b0057c7811d9f8c1218c9ce991849e8"

  private val singletonToken = new ErgoToken(singletonTokenID, 1)

  private val emptyMap = new PlasmaMap[ErgoNameHash, ErgoId](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  private val minerFee: Long = 1000000L

  private val subnameContract = compiler.compileSubnameContract(
    ErgoNamesContracts.SubnameContract.contractScript
  )

  private val mintContract = compiler.compileMintContract(
    ErgoNamesContracts.MintContract.contractScript,
    singletonToken,
    subnameContract
  )

  private val proxyContract = compiler.compileProxyContract(
    ErgoNamesContracts.ProxyContract.contractScript,
    singletonToken,
    minerFee
  )

  private val commitmentContract = compiler.compileCommitmentContract(
    ErgoNamesContracts.CommitmentContract.contractScript,
    mintContract,
    minerFee
  )

  "contractAddresses" should "print" in {
    println("Mint Contract: " + mintContract.toAddress)
    println("Proxy Contract: " + proxyContract.toAddress)
    println("Commitment Contract: " + commitmentContract.toAddress)
  }

  "endToEndTest" should "work" in {
    val dummyAddress = compiler.compileDummyContract().toAddress
    val recipientAddress =
      Address.create("9hU5VUSUAmhEsTehBKDGFaFQSJx574UPoCquKBq59Ushv5XYgAu")

    val buyerBox = outBoxObj
      .simpleOutBox(dummyAddress, 1000000L)
      .convertToInputWith(fakeTxId1, fakeIndex)

    val secretString = "secret"
    val secretStringHash: Array[Byte] =
      hash.Blake2b256(secretString.getBytes(StandardCharsets.UTF_8))

    val secretStringHashHex = new String(Hex.encode(secretStringHash))

    val recipientAddressPropBytes = recipientAddress.asP2PK().script.bytes

    val nameToRegister = "John"
    val nameToRegisterBytes: Array[Byte] =
      nameToRegister.getBytes(StandardCharsets.UTF_8)

    val commitmentSecretHash =
      hash.Blake2b256(
        secretStringHash ++ recipientAddressPropBytes ++ nameToRegisterBytes
      )

    val commitmentBox = outBoxObj
      .commitmentBox(
        commitmentContract,
        commitmentSecretHash,
        recipientAddress,
        mockchainCtx.getHeight - 3,
        minerFee + minerFee
      )
      .convertToInputWith(fakeTxId1, fakeIndex)

    val proxyBox = outBoxObj
      .proxyBox(
        proxyContract,
        nameToRegister,
        recipientAddress,
        secretStringHash
      )
      .convertToInputWith(fakeTxId2, fakeIndex)

    val ergoNamesRegBoxInput = outBoxObj
      .ergoNamesRegistryBoxForTesting(
        mintContract,
        singletonToken,
        emptyMap,
        singletonToken,
        0L
      )
      .convertToInputWith(fakeTxId3, fakeIndex)

    val ergoname: ErgoNameHash = ErgoName(
      new String(nameToRegister)
    ).toErgoNameHash

    val tokenId = ergoNamesRegBoxInput.getId

    val result: ProvenResult[ErgoId] = emptyMap.insert((ergoname, tokenId))
    val proof: Proof = result.proof

    val registerBoxInput = ergoNamesRegBoxInput.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(ergoname.hashedName)),
      ContextVar.of(1.toByte, proof.ergoValue),
      ContextVar.of(2.toByte, nameToRegisterBytes)
    )

    val ergoNameRecipientToken = new Eip4Token(
      tokenId.toString,
      1L,
      nameToRegister,
      "Test ErgoName Token",
      0
    )

    val recipientOutBox =
      outBoxObj.ergoNamesTokenOut(recipientAddress, ergoNameRecipientToken)

    val registryOutBox =
      outBoxObj.ergoNamesRegistryBoxForTesting(
        mintContract,
        registerBoxInput.getTokens.get(0),
        emptyMap,
        ergoNameRecipientToken,
        1L
      )

    val subnamesOutBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      emptyMap,
      ergoNameRecipientToken
    )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs = Seq(registerBoxInput, proxyBox, commitmentBox),
      outputs = Seq(recipientOutBox, registryOutBox, subnamesOutBox)
    )

    val signedTx = txHelper.signTransaction(unsignedTx)
    val signedTxJson = signedTx.toJson(true)

    println(signedTxJson)

  }

}
