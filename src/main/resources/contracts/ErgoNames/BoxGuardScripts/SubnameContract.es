{
    // ===== Contract Description ===== //
    // Name: ErgoNames Subnames Contract
    // Description: This contract allows for recursive subnames
    // Version: 1.0.0
    // Author: zackbalbin@github.com, mgpai22@github.com
    // Auditor: mgpai22@github.com

    // ===== SELF Registers ===== //
    // R4: AvlTree
    // R5: Parent Ergoname Id

    // ===== Compile Time Constants ===== //
    // None

    // ===== Context Extension Variables ===== //
    // subnameToRegisterHash or subnameToDeleteHash: Coll[Byte] - hash of the name to register/delete
    // insertionProof or deletionProof: Coll[Byte] - proof of insertion/deletion
    // existenceProof? : Coll[Byte] - proof of subname to delete existence

    val registryInputBox = SELF
    val userInputBox = INPUTS(1)

    val updatedSubnameRegistryBox = OUTPUTS(0)

    val subnameRegistry = registryInputBox.R4[AvlTree].get
    val subnameRegistryOwner = registryInputBox.R5[Coll[Byte]].get

    val isMintSubname = OUTPUTS.size > 3


    val validRecreation = {

        val validScript = updatedSubnameRegistryBox.propositionBytes == registryInputBox.propositionBytes
        val validSingletonTransfer = (updatedSubnameRegistryBox.tokens(0) == (registryInputBox.tokens(0)._1, 1L))
        val validRegisters = subnameRegistryOwner == updatedSubnameRegistryBox.R5[Coll[Byte]].get

        allOf(Coll(
            validScript,
            validSingletonTransfer,
            validRegisters
        ))
    }

    if (isMintSubname) {

        val mintSubname = {

            val parentErgoname = userInputBox.tokens(0)
            val parentErgonameId = parentErgoname._1

            val newSingleton = userInputBox.tokens(1)
            val subnameToRegisterHash = getVar[Coll[Byte]](0).get
            val insertionProof = getVar[Coll[Byte]](1).get

            val subnameTokenId = registryInputBox.id

            val newSubnameRegistryBox = OUTPUTS(1)
            val userOutputBox = OUTPUTS(2)

            val subnameTokenToMint = userOutputBox.tokens(0)

            val updatedRegistry = updatedSubnameRegistryBox.R4[AvlTree].get

            val correctAvlTreeInsertion = {
                val treeInsertion = subnameRegistry.insert(Coll((subnameToRegisterHash, subnameTokenId)), insertionProof).get

                val validInsertion = updatedSubnameRegistryBox.R4[AvlTree].get.digest == treeInsertion.digest

                validInsertion
            }

            val correctSubnameTokenMint = {
                val validTokenId = subnameTokenToMint._1 == subnameTokenId
                val validTokenAmount = subnameTokenToMint._2 == 1L

                allOf(Coll(
                    validTokenId,
                    validTokenAmount
                ))
            }

            val newRegistryCreation = {
                val emptyDigest = fromBase16("4ec61f485b98eb87153f7c57db4f5ecd75556fddbc403b41acf8441fde8e160900")
                val validEmptyAVLTree = newSubnameRegistryBox.R4[AvlTree].get.digest == emptyDigest
                val validSingletonTransfer = newSubnameRegistryBox.tokens(0) == newSingleton
                val validOwner = newSubnameRegistryBox.R5[Coll[Byte]].get == subnameTokenId

                allOf(Coll(
                    validEmptyAVLTree,
                    validSingletonTransfer,
                    validOwner
                ))
            }

            val hasPermission = parentErgonameId == subnameRegistryOwner


            allOf(Coll(
                correctAvlTreeInsertion,
                correctSubnameTokenMint,
                validRecreation,
                newRegistryCreation,
                hasPermission
            ))
        }

        sigmaProp(mintSubname)
    } else {

        val deleteSubname = {

            val subnameToDeleteHash = getVar[Coll[Byte]](0).get
            val deletionProof = getVar[Coll[Byte]](1).get

            val parentKillingChild = userInputBox.tokens(0)._1 == subnameRegistryOwner

            val validDeletion = {

                if (parentKillingChild) {

                    val correctAvlTreeDeletion = {

                        val deletion = subnameRegistry.remove(Coll((subnameToDeleteHash)), deletionProof).get
                        val validRemoval = updatedSubnameRegistryBox.R4[AvlTree].get.digest == deletion.digest

                        validRemoval
                    }

                    correctAvlTreeDeletion
                } else {

                    val existenceProof = getVar[Coll[Byte]](2).get

                    val correctAvlTreeDeletion = {

                        val tokenIdToDeleteFromTree = subnameRegistry.get(subnameToDeleteHash, existenceProof).get
                        val deletion = subnameRegistry.remove(Coll((subnameToDeleteHash)), deletionProof).get

                        val validToken = userInputBox.tokens(0)._1 == tokenIdToDeleteFromTree
                        val validRemoval = updatedSubnameRegistryBox.R4[AvlTree].get.digest == deletion.digest

                        allOf(Coll(
                            validRemoval,
                            validToken
                        ))
                    }

                    val validTokenBurn = {

                        val tokenIdToBurn = userInputBox.tokens(0)._1

                        OUTPUTS.forall{ (output: Box) =>
                           output.tokens.forall { (token: (Coll[Byte], Long)) =>  token._1 != tokenIdToBurn }
                        }
                    }

                    allOf(Coll(
                        correctAvlTreeDeletion,
                        validTokenBurn
                    ))
                }

            }

            allOf(Coll(
                validDeletion,
                validRecreation
            ))
        }

        sigmaProp(deleteSubname)
    }
}