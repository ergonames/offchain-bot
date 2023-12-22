{
    // ===== Contract Description ===== //
    // Name: ErgoNames Subnames Contract
    // Description: This contract allows for recursive subnames
    // Version: 1.0.0
    // Author: zackbalbin@github.com
    // Auditor: mgpai22@github.com

    // ===== SELF Registers ===== //
    // R4: AvlTree
    // R5: Parent Ergoname Id

    // ==== User Input Box Registers //
    // R4: Parent AVL AvlTree
    // R5: Subname To Register
    // R6: Operation Proof
    // R7: Contains Proof

    // ==== User Input Box Tokens //
    // 0: Parent Token

    // ===== Compile Time Constants ===== //
    // None

    // ===== Context Extension Variables ===== //
    // None

    val registryInputBox = SELF
    val userInputBox = INPUTS(1)

    val ergonamesRegistryBox = CONTEXT.dataInputs(0)

    val parentErgoname = userInputBox.tokens(0)
    val parentErgonameId = parentErgoname._1
    val subnameRegistry = registryInputBox.R4[AvlTree].get

    val ergonamesRegistry = ergonamesRegistryBox.R4[AvlTree].get

    val updatedRegistryBox = OUTPUTS(0)

    val insertRecordIntoSubnameTree = {
        val subnameToRegister = userInputBox.R5[Coll[Byte]].get
        val subnameTokenId = registryInputBox.id
        val operationProof = userInputBox.R6[Coll[Byte]].get
        val containsProof = userInputBox.R7[Coll[Byte]].get

        val updatedRegistry = subnameRegistry.insert(Coll((subnameToRegister, subnameTokenId)), operationProof).get
        val validRegistyInsertion = updatedRegistryBox.R4[AvlTree].get.digest == updatedRegistry.digest
        val validScript = updatedRegistryBox.propositionBytes == registryInputBox.propositionBytes
        val validParentErgoname = parentErgonameId == registryInputBox.R5[Coll[Byte]].get
        val validIdentifier = updatedRegistryBox.R5[Coll[Byte]].get == registryInputBox.R5[Coll[Byte]].get
        val parentErgonameIsInRegistry = ergonamesRegistry.contains(parentErgonameId, containsProof)

        allOf(Coll(
            validRegistyInsertion,
            validScript,
            validParentErgoname,
            validIdentifier,
            parentErgonameIsInRegistry
        ))
    }

    val deleteRecordFromSubnameTree = {
        val subnameToDelete = userInputBox.R4[Coll[Byte]].get
        val proof = userInputBox.R5[Coll[Byte]].get

        val updatedRegistry = subnameRegistry.remove(Coll(subnameToDelete), proof)
        val validRegistryUpdate = updatedRegistryBox.R4[AvlTree].get.digest == updatedRegistry.get.digest
        val validScript = updatedRegistryBox.propositionBytes == registryInputBox.propositionBytes
        val validParentErgoname = parentErgonameId == registryInputBox.R5[Coll[Byte]].get
        val validIdentifier = updatedRegistryBox.R5[Coll[Byte]].get == registryInputBox.R5[Coll[Byte]].get

        allOf(Coll(
            validRegistryUpdate,
            validScript,
            validParentErgoname,
            validIdentifier
        ))
    }

    val validOperation = {
        anyOf(Coll(
            insertRecordIntoSubnameTree,
            deleteRecordFromSubnameTree
        ))
    }

    sigmaProp(validOperation)
}