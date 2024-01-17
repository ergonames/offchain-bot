package execute

import AVL.ErgoName.{ErgoName, ErgoNameHash}
import configs.serviceOwnerConf
import contracts.ErgoNamesContracts
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{ErgoValue, _}
import org.ergoplatform.sdk.ErgoId
import sigmastate.AvlTreeFlags
import special.collection.Coll
import utils.{BoxAPI, ContractCompile, ErgoNamesOutBox, TransactionHelper}
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.{
  LocalPlasmaMap,
  PlasmaMap,
  Proof,
  ProvenResult
}

class TxBuildUtility(
    val ctx: BlockchainContext,
    txOperatorMnemonic: String,
    txOperatorMnemonicPw: String
) {
  private val ErgoNamesOutBox = new ErgoNamesOutBox(ctx)
  private val txHelper = new TransactionHelper(
    ctx = ctx,
    walletMnemonic = txOperatorMnemonic,
    mnemonicPassword = txOperatorMnemonicPw
  )

  private val compiler = new ContractCompile(ctx)
  private val subnameContract: ErgoContract = compiler.compileSubnameContract(
    ErgoNamesContracts.SubnameContract.contractScript
  )

  def mintErgoNameToken(
      registerBox: InputBox,
      proxyInput: InputBox,
      commitmentInput: InputBox,
      tokenMap: PlasmaMap[ErgoNameHash, ErgoId]
  ): SignedTransaction = {

//    val tokenMap = new PlasmaMap[ErgoNameHash, ErgoId](
//      AvlTreeFlags.AllOperationsAllowed,
//      PlasmaParameters.default
//    )
//
//    val t = new ErgoId(Hex.decode("cfbb6260b2307fa307a9e950149afa3f22a71d6b1ed9030f8aed60b009d2f3b7"))
//    val n = ErgoName("John").toErgoNameHash
//
//    tokenMap.insert(
//      (n, t)
//    )
//
//    val y = new ErgoId(Hex.decode("6770373a9dbcb235a5595b54ce0fc313f29f822c17e7c590a3aebf9ea93930ca"))
//    val x = ErgoName("mgpai").toErgoNameHash
//
//    tokenMap.insert(
//      (x, y)
//    )

    println(s"digest: ${Hex
      .toHexString(tokenMap.ergoAVLTree.digest.toArray)}")

    println(s"ergoValue: ${(tokenMap.ergoValue.toHex)}")

    val nameToRegisterBytes: Array[Byte] =
      proxyInput.getRegisters.get(0).getValue.asInstanceOf[Coll[Byte]].toArray

    val nameToRegister = new String(nameToRegisterBytes)

    val recipientSigmaProp: special.sigma.SigmaProp = proxyInput.getRegisters
      .get(1)
      .getValue
      .asInstanceOf[special.sigma.SigmaProp]

    val recipient: Address =
      new org.ergoplatform.appkit.SigmaProp(recipientSigmaProp)
        .toAddress(this.ctx.getNetworkType)

    val tokenIndexTuple: (Coll[Byte], Long) = registerBox.getRegisters
      .get(1)
      .getValue
      .asInstanceOf[(Coll[Byte], Long)]

    //    val tokenMap: PlasmaMap[ErgoNameHash, ErgoId] =
    //      syncRegistry(api, registerBox.getTokens.get(0))

    //    tokenMap.insert(
    //      (
    //        ErgoName("hello").toErgoNameHash -> new ErgoToken(
    //          "cf3b6e369c17c7d9eb318799759362e55955ba02d3bdef0bad18fc46101ab016",
    //          1
    //        ).getId
    //      )
    //    )

    println("registering: " + nameToRegister)
    println("proxy: " + proxyInput.getId.toString())

    val nameToRegisterHash: ErgoNameHash = ErgoName(
      nameToRegister
    ).toErgoNameHash

    val serviceFilePath = "serviceOwner.json"
    lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val jsonRegBox = boxAPIObj.getBoxByIdFromApi(registerBox.getId.toString())

    jsonRegBox.ergoTree =
      "100e04000402040204000404040005020101040005020e20ca47874b9d946771597718585c79b16702311176d232e1d25279460a8983d0ad01010eb403100f04000402040004000502040604040400040205020e214ec61f485b98eb87153f7c57db4f5ecd75556fddbc403b41acf8441fde8e1609000400040204000400d805d601b2a5730000d602e4c6a70464d603b2a4730100d604e4c6a7050ed6059683030193c27201c2a793b2db6308720173020086028cb2db6308a7730300017304937204e4c67201050e9591b1a57305d804d606c5a7d607b2db6308b2a5730600730700d608b2a5730800d609db63087203d19683050193db6401e4c672010464db6401e4dc640c72020283013c0e0e8602e4e3000e7206e4e3010e96830201938c7207017206938c720702730972059683030193db6401e4c672080464730a93b2db63087208730b00b27209730c0093e4c67208050e7206938cb27209730d00017204d803d6068cb2db63087203730e0001d607e4e3000ed608e4e3010ed19683020195937206720493db6401e4c672010464db6401e4dc640e72020283010e72077208968302019683020193db6401e4c672010464db6401e4dc640e72020283010e72077208937206e4dc640a7202027207e4e3020eafa5d9010963afdb63087209d9010b4d0e948c720b01720672050e214ec61f485b98eb87153f7c57db4f5ecd75556fddbc403b41acf8441fde8e160900d805d601b2a5730000d602c5a7d603b2a4730100d604b2db6308b2a5730200730300d605b2a5730400d1968305019683030193b2db6308720173050086027202730693e4c67201040ee4c67203040e93c27201d0e4c67203050873079683020193720486028cb2db6308a7730800017309938c720401730a730b9683030193c27205730c93db6401e4c672050464730d93e4c67205050e7202"

    val newRegBox = boxAPIObj.convertJsonBoxToInputBox(jsonRegBox)

    val tokenId = newRegBox.getId

    val result: ProvenResult[ErgoId] =
      tokenMap.insert((nameToRegisterHash, tokenId))

    println(s"digest after: ${Hex
      .toHexString(tokenMap.ergoAVLTree.digest.toArray)}")

    println(s"ergoValue after : ${(tokenMap.ergoValue.toHex)}")

    val proof: Proof = result.proof

    val registryInput = registerBox
      .withContextVars(
        ContextVar.of(0.toByte, ErgoValue.of(nameToRegisterHash.hashedName)),
        ContextVar.of(1.toByte, proof.ergoValue),
        ContextVar.of(2.toByte, nameToRegisterBytes)
      )

    val recipientToken = new Eip4Token(
      registryInput.getId.toString,
      1L,
      nameToRegister,
      "Test ErgoName Token",
      0
    )

    val recipientOutput =
      ErgoNamesOutBox.ergoNamesTokenOut(recipient, recipientToken)

    val registryContract = Address
      .fromPropositionBytes(
        ctx.getNetworkType,
        registryInput.toErgoValue.getValue.propositionBytes.toArray
      )
      .toErgoContract

    val registryOutput =
      ErgoNamesOutBox.ergoNamesRegistryBox(
        registryContract,
        registryInput.getTokens.get(0),
        tokenMap,
        recipientToken,
        tokenIndexTuple._2 + 1L
      )

    val subnamesOutBox = ErgoNamesOutBox.ergoNamesSubNamesBox(
      subnameContract,
      recipientToken,
      commitmentInput.getTokens.get(0)
    )

    val jsonProxyBox = boxAPIObj.getBoxByIdFromApi(proxyInput.getId.toString())

    jsonProxyBox.ergoTree = "10010101d17300"

    val proxy = boxAPIObj
      .convertJsonBoxToInputBox(
        jsonProxyBox
      )

    val unsignedTx: UnsignedTransaction = txHelper.buildUnsignedTransaction(
      inputs = Array(registryInput, proxyInput, commitmentInput),
      outputs = Array(recipientOutput, registryOutput, subnamesOutBox)
    )

    txHelper.signTransaction(unsignedTx)
  }

}
