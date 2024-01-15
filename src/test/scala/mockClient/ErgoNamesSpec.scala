package mockClient

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{
  Address,
  ContextVar,
  Eip4Token,
  ErgoValue,
  InputBox,
  UnsignedTransaction
}
import org.ergoplatform.sdk.{ErgoId, ErgoToken}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scorex.crypto.hash
import sigmastate.AvlTreeFlags
import special.collection.Coll
import special.sigma.AvlTree
import utils.{TransactionHelper, explorerApi}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.{PlasmaMap, Proof, ProvenResult}

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class ErgoNamesSpec extends AnyFlatSpec with Matchers with Common {

  val nodeUrl = "https://ergonode-api-uy.ergohost.io/"
  val apiUrl = "https://ergo-explorer.anetabtc.io"

  val testnetNodeUrl = "http://192.168.50.14:9052"
  val testnetApiUrl = "https://api-testnet.ergoplatform.com"

  val mainnetExp = new explorerApi(apiUrl, nodeUrl)
  val testnetExp = new explorerApi(testnetApiUrl, testnetNodeUrl)

  val txHelper = new TransactionHelper(
    ctx = mockchainCtx,
    walletMnemonic =
      "caution crowd hawk trip enroll board puppy degree omit injury tired mail banana issue broccoli"
  )

  private val emptyMap = new PlasmaMap[ErgoNameHash, ErgoId](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  private val subnameEmptyMap = new PlasmaMap[ErgoNameHash, ErgoId](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  "contractAddresses" should "print" in {
    println("Mint Contract: " + mintContract.toAddress)
    println("Proxy Contract: " + proxyContract.toAddress)
    println("Commitment Contract: " + commitmentContract.toAddress)
    println("Subnames Contract: " + subnameContract.toAddress)
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
      ContextVar.of(1.toByte, proof.ergoValue)
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
      ergoNameRecipientToken,
      subnameSingletonToken
    )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs = Seq(registerBoxInput, proxyBox, commitmentBox),
      outputs = Seq(recipientOutBox, registryOutBox, subnamesOutBox)
    )

    val signedTx = txHelper.signTransaction(unsignedTx)
    val signedTxJson = signedTx.toJson(true)

    println(signedTxJson)

  }

  "subnames mint" should "work" in {

    val rootMap = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    val recipientAddress =
      Address.create("9hU5VUSUAmhEsTehBKDGFaFQSJx574UPoCquKBq59Ushv5XYgAu")

    // creates the first ergoname: john.erg
    val rootSignedTx =
      mintRootErgoName(
        mockchainCtx,
        txHelper,
        recipientAddress,
        "john",
        rootMap,
        subnameEmptyMap
      )

    var successiveRootRegistryOutput = rootSignedTx.getOutputsToSpend.get(1)

    val names = Array("adoo", "balb", "lgd")
    val levelOneSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelOneSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    names.foreach(name => {
      // creates several ergonames
      // 1) adoo
      // 2) balb
      // 3) ldg

      val subnameChildrenMap = new PlasmaMap[ErgoNameHash, ErgoId](
        AvlTreeFlags.AllOperationsAllowed,
        PlasmaParameters.default
      )

      val successiveRootMintSignedTx = continueRootErgoNameMint(
        mockchainCtx,
        txHelper,
        recipientAddress,
        name,
        rootMap,
        subnameChildrenMap,
        successiveRootRegistryOutput
      )

      successiveRootRegistryOutput =
        successiveRootMintSignedTx.getOutputsToSpend.get(1)
      levelOneSubNameRegistry.append(
        successiveRootMintSignedTx.getOutputsToSpend.get(2)
      )
      levelOneSubNameMaps.append(subnameChildrenMap)
    })

    val levelTwoSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelTwoSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    levelOneSubNameRegistry.zipWithIndex.foreach { case (registry, index) =>
      // creates several level one subnames
      // 1) adoo.adoo.erg
      // 2) balb.balb.erg
      // 3) ldg.ldg.erg
      val (signedTx, subnameChildrenMap) = mintSubName(
        txHelper,
        recipientAddress,
        registry,
        names(index),
        levelOneSubNameMaps(index),
        new ErgoToken(
          new String(
            Hex.encode(
              registry.getRegisters
                .get(1)
                .getValue
                .asInstanceOf[Coll[Byte]]
                .toArray
            )
          ),
          1
        )
      )

      levelTwoSubNameRegistry.append(
        signedTx.getOutputsToSpend.get(1)
      )
      levelTwoSubNameMaps.append(subnameChildrenMap)
    }

    levelTwoSubNameRegistry.zipWithIndex.foreach { case (registry, index) =>
      // creates several level two subnames
      // 1) adoo.adoo.adoo.erg
      // 2) adoo.balb.balb.erg
      // 3) ldg.ldg.ldg.erg
      val (signedTx, subnameChildrenMap) = mintSubName(
        txHelper,
        recipientAddress,
        registry,
        names(index),
        levelTwoSubNameMaps(index),
        new ErgoToken(
          new String(
            Hex.encode(
              registry.getRegisters
                .get(1)
                .getValue
                .asInstanceOf[Coll[Byte]]
                .toArray
            )
          ),
          1
        )
      )
    }

  }

  "subnames delete" should "work" in {

    val rootMap = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    val recipientAddress =
      Address.create("9hU5VUSUAmhEsTehBKDGFaFQSJx574UPoCquKBq59Ushv5XYgAu")

    // creates the first ergoname: john.erg
    val rootSignedTx =
      mintRootErgoName(
        mockchainCtx,
        txHelper,
        recipientAddress,
        "john",
        rootMap,
        subnameEmptyMap
      )

    var successiveRootRegistryOutput = rootSignedTx.getOutputsToSpend.get(1)

    val names = Array("adoo")
    val levelOneSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelOneSubNameRegistryRecreated = new mutable.ListBuffer[InputBox]()
    val levelOneSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    names.foreach(name => {
      // creates several ergonames
      // 1) adoo
      // 2) balb
      // 3) ldg

      val subnameChildrenMap = new PlasmaMap[ErgoNameHash, ErgoId](
        AvlTreeFlags.AllOperationsAllowed,
        PlasmaParameters.default
      )

      val successiveRootMintSignedTx = continueRootErgoNameMint(
        mockchainCtx,
        txHelper,
        recipientAddress,
        name,
        rootMap,
        subnameChildrenMap,
        successiveRootRegistryOutput
      )

      successiveRootRegistryOutput =
        successiveRootMintSignedTx.getOutputsToSpend.get(1)
      levelOneSubNameRegistry.append(
        successiveRootMintSignedTx.getOutputsToSpend.get(2)
      )
      levelOneSubNameMaps.append(subnameChildrenMap)
    })

    val levelTwoSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelTwoSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    levelOneSubNameRegistry.zipWithIndex.foreach { case (registry, index) =>
      // creates several level one subnames
      // 1) adoo.adoo.erg
      // 2) balb.balb.erg
      // 3) ldg.ldg.erg
      val (signedTx, subnameChildrenMap) = mintSubName(
        txHelper,
        recipientAddress,
        registry,
        names(index),
        levelOneSubNameMaps(index),
        new ErgoToken(
          new String(
            Hex.encode(
              registry.getRegisters
                .get(1)
                .getValue
                .asInstanceOf[Coll[Byte]]
                .toArray
            )
          ),
          1
        )
      )

//      println(s"Root Subname Registry: ${new ErgoId(
//        registry.getRegisters
//          .get(1)
//          .getValue
//          .asInstanceOf[Coll[Byte]]
//          .toArray
//      ).toString()}")

      levelTwoSubNameRegistry.append(
        signedTx.getOutputsToSpend.get(1)
      )
      levelTwoSubNameMaps.append(subnameChildrenMap)
      levelOneSubNameRegistryRecreated.append(
        signedTx.getOutputsToSpend.get(0)
      )
    }

    levelOneSubNameRegistryRecreated.zipWithIndex.foreach {
      case (registry, index) =>
        // creates several level two subnames
        // 1) adoo.adoo.adoo.erg
        // 2) adoo.balb.balb.erg
        // 3) ldg.ldg.ldg.erg

//      println(s"AVL Map Digest: ${Hex
//        .toHexString(levelOneSubNameMaps(index).ergoAVLTree.digest.toArray)}")

        val signedTx = deleteSubname(
          txHelper,
          registry,
          names(index),
          levelOneSubNameMaps(index),
          ErgoToken(
            levelOneSubNameRegistry(index).getId,
            1
          )
        )
    }

  }

  "subnames parent delete" should "work" in {

    val rootMap = new PlasmaMap[ErgoNameHash, ErgoId](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    val recipientAddress =
      Address.create("9hU5VUSUAmhEsTehBKDGFaFQSJx574UPoCquKBq59Ushv5XYgAu")

    // creates the first ergoname: john.erg
    val rootSignedTx =
      mintRootErgoName(
        mockchainCtx,
        txHelper,
        recipientAddress,
        "john",
        rootMap,
        subnameEmptyMap
      )

    var successiveRootRegistryOutput = rootSignedTx.getOutputsToSpend.get(1)

    val names = Array("adoo")
    val rootErgonames = new ListBuffer[ErgoToken]()
    val levelOneSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelOneSubNameRegistryRecreated = new mutable.ListBuffer[InputBox]()
    val levelOneSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    names.foreach(name => {
      // creates several ergonames
      // 1) adoo
      // 2) balb
      // 3) ldg

      val subnameChildrenMap = new PlasmaMap[ErgoNameHash, ErgoId](
        AvlTreeFlags.AllOperationsAllowed,
        PlasmaParameters.default
      )

      val successiveRootMintSignedTx = continueRootErgoNameMint(
        mockchainCtx,
        txHelper,
        recipientAddress,
        name,
        rootMap,
        subnameChildrenMap,
        successiveRootRegistryOutput
      )

      rootErgonames.append(
        successiveRootMintSignedTx.getOutputsToSpend.get(0).getTokens.get(0)
      )

      successiveRootRegistryOutput =
        successiveRootMintSignedTx.getOutputsToSpend.get(1)
      levelOneSubNameRegistry.append(
        successiveRootMintSignedTx.getOutputsToSpend.get(2)
      )
      levelOneSubNameMaps.append(subnameChildrenMap)
    })

    val levelTwoSubNameRegistry = new mutable.ListBuffer[InputBox]()
    val levelTwoSubNameMaps =
      new mutable.ListBuffer[PlasmaMap[ErgoNameHash, ErgoId]]()

    levelOneSubNameRegistry.zipWithIndex.foreach { case (registry, index) =>
      // creates several level one subnames
      // 1) adoo.adoo.erg
      // 2) balb.balb.erg
      // 3) ldg.ldg.erg
      val (signedTx, subnameChildrenMap) = mintSubName(
        txHelper,
        recipientAddress,
        registry,
        names(index),
        levelOneSubNameMaps(index),
        new ErgoToken(
          new String(
            Hex.encode(
              registry.getRegisters
                .get(1)
                .getValue
                .asInstanceOf[Coll[Byte]]
                .toArray
            )
          ),
          1
        )
      )

      //      println(s"Root Subname Registry: ${new ErgoId(
      //        registry.getRegisters
      //          .get(1)
      //          .getValue
      //          .asInstanceOf[Coll[Byte]]
      //          .toArray
      //      ).toString()}")

      levelTwoSubNameRegistry.append(
        signedTx.getOutputsToSpend.get(1)
      )
      levelTwoSubNameMaps.append(subnameChildrenMap)
      levelOneSubNameRegistryRecreated.append(
        signedTx.getOutputsToSpend.get(0)
      )
    }

    levelOneSubNameRegistryRecreated.zipWithIndex.foreach {
      case (registry, index) =>
        // creates several level two subnames
        // 1) adoo.adoo.adoo.erg
        // 2) adoo.balb.balb.erg
        // 3) ldg.ldg.ldg.erg

        //      println(s"AVL Map Digest: ${Hex
        //        .toHexString(levelOneSubNameMaps(index).ergoAVLTree.digest.toArray)}")

        val signedTx = parentDeletesSubname(
          txHelper,
          registry,
          names(index),
          levelOneSubNameMaps(index),
          rootErgonames(index)
        )
    }

  }

}
