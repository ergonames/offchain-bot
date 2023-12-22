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

    // ==== User Input Box Registers For Mint //
    // R4: Parent Type
    // R5: Subname To Register
    // R6: Operation Proof
    // R7: Contains Proof
    // R8?: Parent AVL AvlTree

    // ==== User Input Box Tokens //
    // 0: Parent Token

    // ===== Compile Time Constants ===== //
    // None

    // ===== Context Extension Variables ===== //
    // None

    val ergonameParent = 0.toByte
    val subnameParent = 1.toByte

    val registryInputBox = SELF
    val userInputBox = INPUTS(1)

    val ergonamesRegistryBox = CONTEXT.dataInputs(0)

    val parentType = userInputBox.R4[Byte].get
    val subnameRegistry = registryInputBox.R4[AvlTree].get

    val ergonamesRegistry = ergonamesRegistryBox.R4[AvlTree].get

    val parentErgoname = userInputBox.tokens(0)
    val parentErgonameId = parentErgoname._1

    val createNewSubname = {
        val subnameToRegister = userInputBox.R5[Coll[Byte]].get
        val insertionProof = userInputBox.R6[Coll[Byte]].get
        val ergonameRegistryContainsProof = userInputBox.R7[Coll[Byte]].get

        val subnameTokenId = INPUTS(0).id

        val userOutputBox = OUTPUTS(0)
        val updatedSubnameRegistryBox = OUTPUTS(1)

        val subnameTokenToMint = userOutputBox.tokens(0)
        
        val updatedRegistry = updatedSubnameRegistryBox.R4[AvlTree].get

        if (parentType == ergonameParent) {
            val correctAvlTreeInsertion = {
                val treeInsertion = subnameRegistry.insert(Coll((subnameToRegister, subnameTokenId)), insertionProof).get

                val validInsertion = updatedSubnameRegistryBox.R4[AvlTree].get.digest == treeInsertion.digest
                val validScript = updatedSubnameRegistryBox.propositionBytes == registryInputBox.propositionBytes

                allOf(Coll(
                    validInsertion,
                    validScript
                ))
            }

            val correctSubnameTokenMint = {
                val validTokenId = subnameTokenToMint._1 == SELF.id
                val validTokenAmount = subnameTokenToMint._2 == 1L

                allOf(Coll(
                    validTokenId,
                    validTokenAmount
                ))
            }

            val mainErgonameInRegistery = ergonamesRegistry.contains(parentErgonameId, ergonameRegistryContainsProof)

            allOf(Coll(
                correctAvlTreeInsertion,
                correctSubnameTokenMint,
                mainErgonameInRegistery
            ))
        } else if (parentType == subnameParent) {
            val parentSubnameTree = userInputBox.R8[AvlTree].get
            val correctAvlTreeInsertion = {
                val treeInsertion = parentSubnameTree.insert(Coll((subnameToRegister, subnameTokenId)), insertionProof).get

                val validInsertion = updatedSubnameRegistryBox.R4[AvlTree].get.digest == treeInsertion.digest
                val validScript = updatedSubnameRegistryBox.propositionBytes == registryInputBox.propositionBytes

                allOf(Coll(
                    validInsertion,
                    validScript
                ))
            }

            val correctSubnameTokenMint = {
                val validTokenId = subnameTokenToMint._1 == SELF.id
                val validTokenAmount = subnameTokenToMint._2 == 1L

                allOf(Coll(
                    validTokenId,
                    validTokenAmount
                ))
            }

            val mainErgonameInRegistery = ergonamesRegistry.contains(parentErgonameId, ergonameRegistryContainsProof)

            allOf(Coll(
                correctAvlTreeInsertion,
                correctSubnameTokenMint,
                mainErgonameInRegistery
            ))
        } else { false }
    }

    val deleteSubname = {
        val subnameToRegister = userInputBox.R5[Coll[Byte]].get
        val insertionProof = userInputBox.R6[Coll[Byte]].get
        val ergonameRegistryContainsProof = userInputBox.R7[Coll[Byte]].get

        val subnameTokenId = INPUTS(0).id

        val userOutputBox = OUTPUTS(0)
        val updatedSubnameRegistryBox = OUTPUTS(1)

        val subnameTokenToMint = userOutputBox.tokens(0)
        
        val updatedRegistry = updatedSubnameRegistryBox.R4[AvlTree].get

        if (parentType == ergonameParent) {
            val correctAvlTreeDeletion = {
                val treeInsertion = subnameRegistry.remove(Coll((subnameToRegister)), insertionProof).get

                val validDeletion = updatedSubnameRegistryBox.R4[AvlTree].get.digest == treeInsertion.digest
                val validScript = updatedSubnameRegistryBox.propositionBytes == registryInputBox.propositionBytes

                allOf(Coll(
                    validDeletion,
                    validScript
                ))
            }

            val mainErgonameInRegistery = ergonamesRegistry.contains(parentErgonameId, ergonameRegistryContainsProof)

            allOf(Coll(
                correctAvlTreeDeletion,
                mainErgonameInRegistery
            ))
        } else if (parentType == subnameParent) {
            val parentSubnameTree = userInputBox.R8[AvlTree].get
            val correctAvlTreeDeletion = {
                val treeInsertion = parentSubnameTree.remove(Coll((subnameToRegister)), insertionProof).get

                val validDeletion = updatedSubnameRegistryBox.R4[AvlTree].get.digest == treeInsertion.digest
                val validScript = updatedSubnameRegistryBox.propositionBytes == registryInputBox.propositionBytes

                allOf(Coll(
                    validDeletion,
                    validScript
                ))
            }

            val mainErgonameInRegistery = ergonamesRegistry.contains(parentErgonameId, ergonameRegistryContainsProof)

            allOf(Coll(
                correctAvlTreeDeletion,
                mainErgonameInRegistery
            ))
        } else { false }
    }

    val validOperation = {
        anyOf(Coll(
            createNewSubname,
            deleteSubname
        ))
    }

    sigmaProp(validOperation)
}