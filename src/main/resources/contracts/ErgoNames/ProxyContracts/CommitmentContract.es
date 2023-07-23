{
    // ===== Contract Description ===== //
    // Name: ErgoNames Commitment Contract
    // Version: 1.0.0
    // Author: zackbalbin@github.com
    // Auditor: mgpai22@github.com

    // ===== Box Registers ===== //
    // R4: Coll[Byte] => commitment hash
    // R5: SigmaProp => receiver sigmaProp

    // ===== Compile Time Constants ===== //
    // _mintContractPropBytes: PropositionBytes
    // _minerFee: Long

    val successorBox = OUTPUTS(0)
    val buyerPK = SELF.R5[SigmaProp].get

    val validSpend = {
        val validSpender = successorBox.propositionBytes == buyerPK.propBytes
        val validFee = successorBox.value >= SELF.value - _minerFee

        allOf(Coll(
            validSpender,
            validFee
        ))
    }

    val refund = {
        val refundBoxOUT: Boolean = OUTPUTS(0)
        val minerBoxOut: Box = OUTPUTS(1)

        val validRefundBox: Boolean = {
            allOf(Coll(
                (refundBoxOUT.value == SELF.value - _minerFee),
                (refundBoxOUT.propositionBytes == buyerPK.propBytes),
            ))
        }

        val validMinerFee: Boolean = (minerBoxOut.value == _minerFee)

        allOf(Coll(
            validRefundBox,
            validMinerFee,
            (OUTPUTS.size == 2)
        ))
    }

    sigmaProp(validSpend || (refund && buyerPK))
}
