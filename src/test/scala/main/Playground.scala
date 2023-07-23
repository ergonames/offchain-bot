package main

import contracts.ErgoNamesContracts
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.Address
import scorex.crypto.hash

object Playground extends App {
  println("Hello World!")
}

object printContract extends App {
  val proxyContract = ErgoNamesContracts.ProxyContract.contractScript
  println(proxyContract)
}

object getFeeHash extends App{
  val minerAddress = Address.create("2iHkR7CWvD1R4j1yZg5bkeDRQavjAaVPeTDFGGLZduHyfWMuYpmhHocX8GJoaieTx78FntzJbCBVL6rf96ocJoZdmWBL2fci7NqWgAirppPQmZ7fN9V6z13Ay6brPriBKYqLp1bT2Fk4FkFLCfdPpe")
  val minerErgoTree = minerAddress.asP2S().scriptBytes
  val ergoTreeHex = minerAddress.asP2S().script.bytesHex
  val ergoTreeFromHex2 = new String(Hex.encode(minerErgoTree))
  println(ergoTreeHex)
  println(ergoTreeFromHex2)
  val minerErgoTreeHash = hash.Blake2b256(minerErgoTree)
  val minerErgoTreeHashHex = new String(Hex.encode(minerErgoTreeHash))
  println(minerErgoTreeHashHex)
}

object getP2PKHash extends App{
  val addressErgoTree = Address.create("9hU5VUSUAmhEsTehBKDGFaFQSJx574UPoCquKBq59Ushv5XYgAu").asP2PK().script.bytesHex

//  val ergoTreeFromHex2 = new String(Hex.encode(minerErgoTree))

  println(addressErgoTree)


//  val minerErgoTreeHash = hash.Blake2b256(minerErgoTree)
//  val minerErgoTreeHashHex = new String(Hex.encode(minerErgoTreeHash))
//  println(minerErgoTreeHashHex)
}
