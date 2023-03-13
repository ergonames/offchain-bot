{
    // ===== Contract Description ===== //
    // Name: ErgoNames Minting Contract
    // Description: This contract ensures ErgoNames tokens are distributed properly
    // Version: 1.0.0
    // Author: zackbalbin@github.com
    // Auditor: mgpai22@github.com

    // ===== Box Registers ===== //
    // R4: AvlTree

    // ===== Compile Time Constants ===== //
    //None

    // ===== Context Extension Variables ===== //
    // ergonameHash: Coll[Byte] - hash of the name to register
    // proof: Coll[Byte] - proof ergonameHash and tokenId insertion into the registry

    val registryInputBox = SELF
    val userInputBox = INPUTS(1) // proxy input

    val registry = registryInputBox.R4[AvlTree].get

    val tokenIdToRegister = registryInputBox.id

    val ergonameHash = getVar[Coll[Byte]](0).get
    val proof = getVar[Coll[Byte]](1).get

    val nameToRegister = userInputBox.R4[Coll[Byte]].get
    val receiverAddress = userInputBox.R5[SigmaProp].get

    val mintBox = OUTPUTS(0)
    val updatedRegistryBox = OUTPUTS(1)

    val newErgoNameToken = mintBox.tokens(0)

    val mintNewErgoName = {
        val validToken = newErgoNameToken == (tokenIdToRegister, 1)
        val validErgoNameName = mintBox.R4[Coll[Byte]].get == nameToRegister
        val validReceiver = mintBox.propositionBytes == receiverAddress.propBytes

         allOf(Coll(
            validToken,
            validErgoNameName,
            validReceiver
         ))
    }

    val updateRegistry = {
        val updatedRegistry = registry.insert(Coll((ergonameHash, tokenIdToRegister)), proof).get
        val validRegistryUpdate = updatedRegistryBox.R4[AvlTree].get.digest == updatedRegistry.digest
        val validScript = updatedRegistryBox.propositionBytes == registryInputBox.propositionBytes

        allOf(Coll(
            validRegistryUpdate,
            validScript
        ))
    }

    val validRegistration: Boolean = {

         allOf(Coll(
            mintNewErgoName,
            updateRegistry
         ))
    }

    sigmaProp(validRegistration)

}
