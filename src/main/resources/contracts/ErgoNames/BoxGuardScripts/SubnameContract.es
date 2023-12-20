// Each box represents an AVLTree Registry for parent ergoname
// R4 -> AVL Tree Digest
// R5 -> TokenId of parent ergoname

{
    val registryInputBox = SELF
    val userInputBox = INPUTS(1)

    val parentErgoname = userInputBox.tokens(0)
    val parentErgonameId = parentErgoname._1
    val subnameRegistry = registryInputBox.R4[AvlTree].get

    val updatedRegistryBox = OUTPUTS(0)

    val insertRecordIntoSubnameTree = {
        val subnameToRegister = userInputBox.R4[Coll[Byte]].get
        val subnameTokenId = userInputBox.R5[Coll[Byte]].get
        val proof = userInputBox.R6[Coll[Byte]].get
        
        val updatedRegistry = subnameRegistry.insert(Coll((subnameToRegister, subnameTokenId)), proof).get
        val validRegistyInsertion = updatedRegistryBox.R4[AvlTree].get.digest == updatedRegistry.digest
        val validScript = updatedRegistryBox.propositionBytes == registryInputBox.propositionBytes
        val validParentErgoname = parentErgonameId == registryInputBox.R5[Coll[Byte]].get
        val validIdentifier = updatedRegistryBox.R5[Coll[Byte]].get == registryInputBox.R5[Coll[Byte]].get

        allOf(Coll(
            validRegistyInsertion,
            validScript,
            validParentErgoname,
            validIdentifier
        ))
    }

    val editRecordInSubnameTree = {
        val subnameToUpdate = userInputBox.R4[Coll[Byte]].get
        val subnameUpdatedTokenId = userInputBox.R5[Coll[Byte]].get
        val proof = userInputBox.R6[Coll[Byte]].get

        val updatedRegistry = subnameRegistry.update(Coll((subnameToUpdate, subnameUpdatedTokenId)), proof).get
        val validRegistryUpdate = updatedRegistryBox.R4[AvlTree].get.digest == updatedRegistry.digest
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
            editRecordInSubnameTree,
            deleteRecordFromSubnameTree
        ))
    }

    sigmaProp(validOperation)
}