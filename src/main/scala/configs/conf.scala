package configs

import com.google.gson.{Gson, GsonBuilder, JsonElement}

import java.io.{FileWriter, Writer}
import scala.io.Source

case class ContractsConfig(
    Contracts: Config
)

case class Config(
    mintContract: MintContract,
    proxyContract: ProxyContract,
)
case class MintContract(
    contract: String,
    singleton: String,
    initialTx: String,
)
case class ProxyContract(
    contract: String
)

class conf(
    mintContract: String,
    singleton: String,
    initialTx: String,
    proxyContract: String
) {
  private val mintContractInstance: MintContract =
    MintContract(mintContract, singleton, initialTx)
  private val proxyContractInstance: ProxyContract = ProxyContract(proxyContract)

  val conf: Config = Config(
    mintContractInstance,
    proxyContractInstance
  )
  private val newConfig: ContractsConfig = ContractsConfig(conf)
  private val gson = new GsonBuilder().setPrettyPrinting().create()

  def write(filePath: String): Unit = {
    val writer: Writer = new FileWriter(filePath)
    writer.write(this.gson.toJson(this.newConfig))
    writer.close()
  }

  def read(filePath: String): ContractsConfig = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[ContractsConfig])
  }

}

object conf {
  private val gson = new GsonBuilder().setPrettyPrinting().create()

  def read(filePath: String): ContractsConfig = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[ContractsConfig])
  }

  def write(filePath: String, newConfig: ContractsConfig): Unit = {
    val writer: Writer = new FileWriter(filePath)
    writer.write(this.gson.toJson(newConfig))
    writer.close()
  }

}

case class ServiceOwnerConfig(
    txOperatorMnemonic: String,
    txOperatorMnemonicPw: String,
    nodeUrl: String,
    apiUrl: String
)

object serviceOwnerConf {
  private val gson = new GsonBuilder().setPrettyPrinting().create()

  def read(filePath: String): ServiceOwnerConfig = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[ServiceOwnerConfig])
  }
}
