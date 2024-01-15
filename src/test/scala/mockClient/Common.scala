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
  ErgoContract,
  ErgoValue,
  InputBox,
  SignedTransaction,
  UnsignedTransaction
}
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import scorex.crypto.hash
import sigmastate.AvlTreeFlags
import special.collection.Coll
import utils.{ContractCompile, ErgoNamesOutBox, TransactionHelper}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.{PlasmaMap, Proof, ProvenResult}

import java.nio.charset.StandardCharsets

trait Common extends HttpClientTesting {

  val StorageIndexVarId: Byte = Byte.MaxValue

  val BlocksPerHour = 30

  val BlocksPerDay: Int = BlocksPerHour * 24

  val BlocksPerWeek: Int = BlocksPerDay * 7

  val BlocksPerMonth: Int = BlocksPerDay * 30

  val BlocksPerYear: Int = BlocksPerDay * 365

  //For how many blocks a box could be put into the state with no paying.
  //4 years
  val StoragePeriod: Int = 4 * BlocksPerYear
  val storageFeeFactor = 1250000

  val lpToken =
    "4b2d8b7beb3eaac8234d9e61792d270898a43934d6a27275e4f3a044609c9f2a" // LP tokens
  val dexyUSD =
    "4b2d8b7beb3eaac8234d9e61792d270898a43934d6a27275e4f3a044609c9f2b" // Dexy token
  lazy val minStorageRent = 100000L

  val fakeScript = "sigmaProp(true)"
  val fakeTxId1 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"
  val fakeTxId2 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b808"
  val fakeTxId3 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b807"
  val fakeTxId4 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b806"
  val fakeTxId5 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b105"
  val fakeTxId6 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b185"
  val fakeTxId7 =
    "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b145"
  val fakeIndex = 1.toShort
  val changeAddress = "9gQqZyxyjAptMbfW1Gydm3qaap11zd6X9DrABwgEE9eRdRvd27p"

  val ergoClient: FileMockedErgoClient = createMockedErgoClient(
    MockData(Nil, Nil)
  )

  val mockchainCtx: BlockchainContext = ergoClient.execute(ctx => ctx)

  val compiler = new ContractCompile(mockchainCtx)
  val outBoxObj = new ErgoNamesOutBox(mockchainCtx)

  val minerFee: Long = 1000000L

  val singletonTokenID =
    "85f9d234f320194b7ada4d150d7369f16b0057c7811d9f8c1218c9ce991849e8"

  val singletonToken = new ErgoToken(singletonTokenID, 1)

  val subnameSingletonID =
    "9a06d9e545a41fd51eeffc5e20d818073bf820c635e2a9d922269913e0de369d"
  val subnameSingletonToken = new ErgoToken(subnameSingletonID, 1L)

  val subnameChildSingletonID =
    "78263e5613557e129f075f0a241287e09c4204be76ad53d77d6e7feebcccb001"
  val subnameChildSingletonToken =
    new ErgoToken(subnameChildSingletonID, 1L)

  val subnameContract: ErgoContract = compiler.compileSubnameContract(
    ErgoNamesContracts.SubnameContract.contractScript
  )

  val mintContract: ErgoContract = compiler.compileMintContract(
    ErgoNamesContracts.MintContract.contractScript,
    singletonToken,
    subnameContract
  )

  val proxyContract: ErgoContract = compiler.compileProxyContract(
    ErgoNamesContracts.ProxyContract.contractScript,
    singletonToken,
    minerFee
  )

  val commitmentContract: ErgoContract = compiler.compileCommitmentContract(
    ErgoNamesContracts.CommitmentContract.contractScript,
    mintContract,
    minerFee
  )

  def mintRootErgoName(
      ctx: BlockchainContext,
      txHelper: TransactionHelper,
      recipientAddress: Address,
      nameToRegister: String,
      rootMap: PlasmaMap[ErgoNameHash, ErgoId],
      emptyMap: PlasmaMap[ErgoNameHash, ErgoId]
  ): SignedTransaction = {

    val compiler = new ContractCompile(ctx)
    val outBoxObj = new ErgoNamesOutBox(ctx)

    val dummyAddress = compiler.compileDummyContract().toAddress

    val buyerBox = outBoxObj
      .simpleOutBox(dummyAddress, 1000000L)
      .convertToInputWith(fakeTxId1, fakeIndex)

    val secretString = "secret"
    val secretStringHash: Array[Byte] =
      hash.Blake2b256(secretString.getBytes(StandardCharsets.UTF_8))

    val secretStringHashHex = new String(Hex.encode(secretStringHash))

    val recipientAddressPropBytes = recipientAddress.asP2PK().script.bytes
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
        subnameSingletonToken,
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
        rootMap,
        singletonToken,
        0L
      )
      .convertToInputWith(fakeTxId3, fakeIndex)

    val ergoname: ErgoNameHash = ErgoName(
      new String(nameToRegister)
    ).toErgoNameHash

    val tokenId = ergoNamesRegBoxInput.getId

    val result: ProvenResult[ErgoId] = rootMap.insert((ergoname, tokenId))
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
        rootMap,
        ergoNameRecipientToken,
        1L
      )

    val subnamesOutBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      emptyMap,
      ergoNameRecipientToken,
      subnameSingletonToken
    )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs = Seq(registerBoxInput, proxyBox, commitmentBox),
      outputs = Seq(recipientOutBox, registryOutBox, subnamesOutBox)
    )

    txHelper.signTransaction(unsignedTx)
  }

  def continueRootErgoNameMint(
      ctx: BlockchainContext,
      txHelper: TransactionHelper,
      recipientAddress: Address,
      nameToRegister: String,
      rootMap: PlasmaMap[ErgoNameHash, ErgoId],
      emptyMap: PlasmaMap[ErgoNameHash, ErgoId],
      ergoNamesRegBoxInput: InputBox
  ): SignedTransaction = {

    val compiler = new ContractCompile(ctx)
    val outBoxObj = new ErgoNamesOutBox(ctx)

    val dummyAddress = compiler.compileDummyContract().toAddress

    val buyerBox = outBoxObj
      .simpleOutBox(dummyAddress, 1000000L)
      .convertToInputWith(fakeTxId1, fakeIndex)

    val secretString = "secret"
    val secretStringHash: Array[Byte] =
      hash.Blake2b256(secretString.getBytes(StandardCharsets.UTF_8))

    val secretStringHashHex = new String(Hex.encode(secretStringHash))

    val recipientAddressPropBytes = recipientAddress.asP2PK().script.bytes
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
        subnameSingletonToken,
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

    val ergoname: ErgoNameHash = ErgoName(
      new String(nameToRegister)
    ).toErgoNameHash

    val tokenId = ergoNamesRegBoxInput.getId

    val result: ProvenResult[ErgoId] = rootMap.insert((ergoname, tokenId))
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
        rootMap,
        ergoNameRecipientToken,
        registerBoxInput.getRegisters
          .get(1)
          .getValue
          .asInstanceOf[(Coll[Byte], Long)]
          ._2 + 1L
      )

    val subnamesOutBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      emptyMap,
      ergoNameRecipientToken,
      subnameSingletonToken
    )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs = Seq(registerBoxInput, proxyBox, commitmentBox),
      outputs = Seq(recipientOutBox, registryOutBox, subnamesOutBox)
    )

    txHelper.signTransaction(unsignedTx)
  }

  def mintSubName(
      txHelper: TransactionHelper,
      recipientAddress: Address,
      registryBox: InputBox,
      subname: String,
      parentMap: PlasmaMap[ErgoNameHash, ErgoId],
      parentErgoName: ErgoToken
  ): (SignedTransaction, PlasmaMap[ErgoNameHash, ErgoId]) = {

    val emptyMap = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    val subnameErgoname: ErgoNameHash = ErgoName(
      new String(subname)
    ).toErgoNameHash

    val subnameTokenId = registryBox.getId

    val subnameMapResult: ProvenResult[ErgoId] =
      parentMap.insert((subnameErgoname, subnameTokenId))
    val subnameProof: Proof = subnameMapResult.proof

    val subnameRegistryInput = registryBox.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(subnameErgoname.hashedName)),
      ContextVar.of(1.toByte, subnameProof.ergoValue)
    )

    val subnameUserInput = outBoxObj
      .subnamesUserBox(
        txHelper.senderAddress,
        parentErgoName,
        subnameChildSingletonToken,
        4000000L
      )
      .convertToInputWith(fakeTxId1, fakeIndex)

    val subnameRecreationBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      parentMap,
      parentErgoName,
      registryBox.getTokens.get(0)
    )

    val subnameErgonameToken = new Eip4Token(
      subnameRegistryInput.getId.toString(),
      1L,
      subname,
      "Test ErgoName Token",
      0
    )

    val subnamesChildOutBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      emptyMap,
      subnameErgonameToken,
      subnameChildSingletonToken
    )

    val subnameRecipientOutBox =
      outBoxObj.subnamesTokenOut(
        recipientAddress,
        parentErgoName,
        subnameErgonameToken
      )

    val subnameUnsignedTx: UnsignedTransaction =
      txHelper.buildUnsignedTransaction(
        inputs = Seq(subnameRegistryInput, subnameUserInput),
        outputs =
          Seq(subnameRecreationBox, subnamesChildOutBox, subnameRecipientOutBox)
      )

    (txHelper.signTransaction(subnameUnsignedTx), emptyMap)
  }

  def deleteSubname(
      txHelper: TransactionHelper,
      registryBox: InputBox,
      subname: String,
      parentMap: PlasmaMap[ErgoNameHash, ErgoId],
      subnameTokenToDelete: ErgoToken
  ): SignedTransaction = {

    val subnameErgoname: ErgoNameHash = ErgoName(
      new String(subname)
    ).toErgoNameHash

    val subnameExistenceMapResult: ProvenResult[ErgoId] =
      parentMap.lookUp(subnameErgoname)

    val existenceProof: Proof = subnameExistenceMapResult.proof

    val subnameDeletionMapResult: ProvenResult[ErgoId] =
      parentMap.delete(subnameErgoname)

    val deletionProof: Proof = subnameDeletionMapResult.proof

    val subnameRegistryInput = registryBox.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(subnameErgoname.hashedName)),
      ContextVar.of(1.toByte, deletionProof.ergoValue),
      ContextVar.of(2.toByte, existenceProof.ergoValue)
    )

    val subnameUserInput = outBoxObj
      .subnamesDeletionBox(
        txHelper.senderAddress,
        subnameTokenToDelete
      )
      .convertToInputWith(fakeTxId1, fakeIndex)

    val subnameRecreationBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      parentMap,
      ErgoToken(
        new ErgoId(
          registryBox.getRegisters
            .get(1)
            .getValue
            .asInstanceOf[Coll[Byte]]
            .toArray
        ),
        1
      ),
      registryBox.getTokens.get(0)
    )

    val subnameUnsignedTx: UnsignedTransaction =
      txHelper.buildUnsignedTransaction(
        inputs = Seq(subnameRegistryInput, subnameUserInput),
        outputs = Seq(subnameRecreationBox),
        tokensToBurn = Array(subnameTokenToDelete)
      )

    txHelper.signTransaction(subnameUnsignedTx)
  }

  def parentDeletesSubname(
      txHelper: TransactionHelper,
      registryBox: InputBox,
      subname: String,
      parentMap: PlasmaMap[ErgoNameHash, ErgoId],
      parentSubname: ErgoToken
  ): SignedTransaction = {

    val subnameErgoname: ErgoNameHash = ErgoName(
      new String(subname)
    ).toErgoNameHash

    val subnameDeletionMapResult: ProvenResult[ErgoId] =
      parentMap.delete(subnameErgoname)

    val deletionProof: Proof = subnameDeletionMapResult.proof

    val subnameRegistryInput = registryBox.withContextVars(
      ContextVar.of(0.toByte, ErgoValue.of(subnameErgoname.hashedName)),
      ContextVar.of(1.toByte, deletionProof.ergoValue)
    )

    val subnameUserInput = outBoxObj
      .subnamesDeletionBox(
        txHelper.senderAddress,
        parentSubname,
        2000000
      )
      .convertToInputWith(fakeTxId1, fakeIndex)

    val subnameRecreationBox = outBoxObj.ergoNamesSubNamesBoxForTesting(
      subnameContract,
      parentMap,
      ErgoToken(
        new ErgoId(
          registryBox.getRegisters
            .get(1)
            .getValue
            .asInstanceOf[Coll[Byte]]
            .toArray
        ),
        1
      ),
      registryBox.getTokens.get(0)
    )

    val userOutput = outBoxObj.subnamesDeletionUserBox(
      txHelper.senderAddress,
      parentSubname
    )

    val subnameUnsignedTx: UnsignedTransaction =
      txHelper.buildUnsignedTransaction(
        inputs = Seq(subnameRegistryInput, subnameUserInput),
        outputs = Seq(subnameRecreationBox, userOutput)
      )

    txHelper.signTransaction(subnameUnsignedTx)
  }

}
