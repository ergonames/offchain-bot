package main

import contracts.ErgoNamesContracts

object init extends App {
  println("Hello World!")
}

object printContract extends App {
  val proxyContract = ErgoNamesContracts.ProxyContract.contractScript
  val mintContract = ErgoNamesContracts.MintContract.contractScript
  println(mintContract)
}
