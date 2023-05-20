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

    val succesorBox = OUTPUTS(0)

    val validSpend = {
        val validSpender = succesorBox.propositionBytes == _mintContractPropBytes
        val validFee = succesorBox.value >= SELF.value - _minerFee

        allOf(Coll(
            validSpender,
            validFee
        ))
    }

    val refund = {
        val refundBoxOut: Boolean = OUTPUTS(0)
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

    sigmaProp(validSpend || refund)
}