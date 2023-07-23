package utils

import AVL.ErgoName.{ErgoName, RegistrationInfo}
import org.ergoplatform.sdk.ErgoId

import java.sql.DriverManager

object DatabaseUtils {

  val databasePath: String =
    "jdbc:postgresql://localhost:5432/ergonames?user=ergonames&password=ergonames"

  def readRegistryInsertion(mintTransactionId: String): RegistrationInfo = {
    val connection = DriverManager.getConnection(databasePath)
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery(
      s"SELECT * FROM confirmed_registry_insertions WHERE mint_transaction_id = '$mintTransactionId'"
    )
    if (resultSet.next()) {
      val registrationInfo = RegistrationInfo(
        resultSet.getString("mint_transaction_id"),
        resultSet.getString("spend_transaction_id"),
        ErgoName(resultSet.getString("ergoname_registered")).toErgoNameHash,
        ErgoId.create(resultSet.getString("ergoname_token_id"))
      )
      connection.close()
      registrationInfo
    } else {
      connection.close()
      null
    }
  }

  def getMostRecentMintTransactionId: String = {
    val connection = DriverManager.getConnection(databasePath)
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery(
      "SELECT * FROM confirmed_registry_insertions WHERE spend_transaction_id IS NULL"
    )
    if (resultSet.next()) {
      val registrationInfo = RegistrationInfo(
        resultSet.getString("mint_transaction_id"),
        resultSet.getString("spend_transaction_id"),
        ErgoName(resultSet.getString("ergoname_registered")).toErgoNameHash,
        ErgoId.create(resultSet.getString("ergoname_token_id"))
      )
      connection.close()
      registrationInfo.mintTransactionId
    } else {
      connection.close()
      null
    }
  }

}
