{
    // ===== Contract Description ===== //
    // Name: ErgoNames Proxy Contract
    // Description: This contract is a proxy contract and ensures funds are used properly
    // Version: 1.0.0
    // Author: zackbalbin@github.com
    // Auditor: mgpai22@github.com

    // ===== Box Registers ===== //
    // R4: Coll[Byte] => name to register
    // R5: SigmaProp => receiver sigmaProp
    // R6: Coll[Byte] => commitment secret
    // R7: Coll[Byte] => commitment box id

    // ===== Compile Time Constants ===== //
    // _singletonToken: Coll[Byte]
    // _minerFee: Long //miner fee in nano ergs

    // ===== Context Extension Variables ===== //
    // None



    val isRefund: Boolean = (INPUTS.size == 1)
    val buyerPK: SigmaProp = SELF.R5[SigmaProp].get

    if (!isRefund) {

        val validTx: Boolean = {

            // inputs
            val registryInputBox = INPUTS(0)

            // outputs
            val tokenReceiverBox = OUTPUTS(0)

            val commitmentBoxId: Coll[Byte] = SELF.R7[Coll[Byte]].get


            val validRecipient: Boolean = {
                tokenReceiverBox.propositionBytes == buyerPK.propBytes
            }

//            val validAmount: Boolean = {
//                tokenReceiverBox.value == INPUTS(0).value
//            }

            val validRegistryBox: Boolean = {
                (registryInputBox.tokens(0)._1 == _singletonToken)
            }

            val validCommitmentBox: Boolean = {
                (INPUTS(2).id == commitmentBoxId)
            }

            allOf(Coll(
                validRecipient,
                validRegistryBox,
                validCommitmentBox
            ))

        }

        sigmaProp(validTx)

    } else {

        val validRefundTx: Boolean = {

            // outputs
            val refundBoxOUT: Boolean = OUTPUTS(0)
            val minerBoxOUT: Box = OUTPUTS(1)

            val validRefundBox: Boolean = {

                allOf(Coll(
                    (refundBoxOUT.value == SELF.value - _minerFee),
                    (refundBoxOUT.propositionBytes == buyerPK.propBytes)
                ))

            }

            val validMinerFee: Boolean = (minerBoxOUT.value == _minerFee)

            allOf(Coll(
                validRefundBox,
                validMinerFee,
                (OUTPUTS.size == 2)
            ))

        }

        sigmaProp(validRefundTx) && buyerPK // buyer must sign tx themself as well

    }

}
