package AVL.ErgoName

import org.ergoplatform.sdk.ErgoId

case class RegistrationInfo(
    mintTransactionId: String,
    spentTransactionId: String,
    ergoNameRegistered: ErgoNameHash,
    ergoNameTokenId: ErgoId
)
