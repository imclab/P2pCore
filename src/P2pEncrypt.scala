/*
 * This file is part of P2pCore.
 *
 * Copyright (C) 2012 Timur Mehrvarz, timur.mehrvarz(at)gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation <http://www.gnu.org/licenses/>, either 
 * version 3 of the License, or (at your option) any later version.
 */

package timur.p2pCore

import java.security.{ Security, MessageDigest }

object P2pEncrypt {
  def main(args:Array[String]): Unit = {
    if(args.length<2) {
      println("arg1: keyFolderPath")
      println("arg2: remotePublicKeyName (san .pub)")
      println("arg3: rendezvous-string (optional)")
      return
    }
    new P2pEncrypt(args(0), args(1), if(args.length>2) args(2) else null).start
  }
}

class P2pEncrypt(keyFolderPath:String, setRemoteKeyName:String, rendezvous:String) extends P2pBase {

  var privKeyLocal:String = null
  var pubKeyLocal:String = null
  var pubKeyRemote:String = null
  var remoteKeyName = setRemoteKeyName

  override def start() :Int = {
    init
    val ret = readkeys
    if(ret!=0)
      return ret
    return super.start
  }

  def init() {
    // prepare org.bouncycastle.crypto.encodings.PKCS1Encoding in RsaEncrypt/RsaDecrypt
    Security.addProvider(new ext.org.bouncycastle.jce.provider.BouncyCastleProvider())
  }

  def readkeys() :Int = {
    if(remoteKeyName.length<=0)
      return -1

    try {
      // load local key pair
      privKeyLocal = io.Source.fromFile(keyFolderPath+"/key").mkString

      val fullLocalKeyName = keyFolderPath+"/key.pub"
      log("fullLocalKeyName="+fullLocalKeyName+" used for fingerprint matching")
      pubKeyLocal = io.Source.fromFile(fullLocalKeyName).mkString

    } catch {
      case ex:Exception =>
        // generate local key pair
        val keyPair = RsaKeyGenerate.rsaKeyGenerate

        new java.io.File(keyFolderPath).mkdir

        pubKeyLocal = Base64.encode(keyPair.getPublic.getEncoded)
        Tools.writeToFile(keyFolderPath+"/key.pub", pubKeyLocal)

        privKeyLocal = Base64.encode(keyPair.getPrivate.getEncoded)
        Tools.writeToFile(keyFolderPath+"/key", privKeyLocal)
    }

    if(buildMatchStrings!=0)
      return -2
      
    return 0
  }

  def buildMatchStrings() :Int = {
    if(rendezvous!=null && rendezvous.length>0) {
      // build match strings based on the rendezvous string
      matchSource = rendezvous
      matchTarget = rendezvous
      log("matching with rendezvous string '"+rendezvous+"'")
      // remoteKeyName will be used as name of remotePublicKey to be stored
      return 0
    } 
    
    // build match strings based on public key fingerprints
    try {
      val fullRemoteKeyName = keyFolderPath +"/" +remoteKeyName+".pub"
      log("fullRemoteKeyName="+fullRemoteKeyName+" used for fingerprint matching")
      pubKeyRemote = io.Source.fromFile(fullRemoteKeyName).mkString
      // create match strings based on fingerprints of the two public keys
      val messageDigest = MessageDigest.getInstance("SHA-1")
      messageDigest.update(Base64.decode(pubKeyLocal))
      matchSource = RsaEncrypt.getHexString(messageDigest.digest).substring(0,20)
      messageDigest.reset
      messageDigest.update(Base64.decode(pubKeyRemote))
      matchTarget = RsaEncrypt.getHexString(messageDigest.digest).substring(0,20)
      //return 0

    } catch {
      case ex:Exception =>
        logEx("fingerprint setup error ex="+ex)
        return -1
    }
    return 0
  }

  override def p2pSendThread(udpIpAddr:String, udpPortInt:Int) {
    if(pubKeyRemote!=null) {
      p2pEncryptedCommunication
    } else {
      log("requestPubKeyViaRelay...")
      send("requestPubKeyViaRelay")

      // later
      // todo: instead check if we got the pubKeyRemote already stored
      //       request the fingerprint of pubKeyRemote
      //       log("requestPubKeyFingerprint...")
      //       send("requestPubKeyFingerprint")
    }
  }

  override def p2pReceiveHandler(str:String, host:String, port:Int) {
    //log("p2pReceiveHandler str='"+str+"' ###########")
    if(str=="requestPubKeyFingerprint") {
      // generate fingerprint from pubKeyLocal
      val messageDigest = MessageDigest.getInstance("SHA-1")
      messageDigest.update(Base64.decode(pubKeyLocal))
      val pubKeyFingerprint = RsaEncrypt.getHexString(messageDigest.digest)
      log("p2pSend: pubKeyFingerprint="+pubKeyFingerprint)
      p2pSend("pubKeyFingerprint="+pubKeyFingerprint, host, port)

    } else if(str.startsWith("pubKeyFingerprint=")) {
      val remoteKeyFingerprint = str.substring(18)
      log("p2pReceiveHandler: remoteKeyFingerprint="+remoteKeyFingerprint)
      // todo: search all stored pub keys for remoteKeyFingerprint
      pubKeyRemote = "" // todo: fetch the found key
      log("found stored pubKeyRemote")
      p2pEncryptedCommunication

    } else {
      try {
        // possible exception: ext.org.bouncycastle.crypto.InvalidCipherTextException: unknown block type
        // possible exception: ext.org.bouncycastle.crypto.DataLengthException: input too large for RSA cipher
        val decryptString = RsaDecrypt.decrypt(privKeyLocal, str)
        if(decryptString!=null)
          p2pReceiveUserData(decryptString)
      } catch {
        case ex:Exception =>
          logEx("p2pReceiveHandler "+ex.getMessage)
          ex.printStackTrace
      }
          
    }
  }

  def p2pReceiveUserData(str:String) {
    log("p2pReceiveHandler decryptString='"+str+"'")
  }

  override def relayReceiveHandler(str:String) {
    //log("relayReceiveHandler str='"+str+"'")  // never log any real user communication
    if(str=="requestPubKeyViaRelay") {
      log("send: pubkey") //"="+pubKeyLocal)
      send("pubkey="+pubKeyLocal)

    } else if(str.startsWith("pubkey=")) {
      pubKeyRemote = str.substring(7)
      log("received pubKeyRemote")
      storeRemotePublicKey(remoteKeyName, pubKeyRemote)
      p2pEncryptedCommunication

    } else {
      //log("relayReceiveHandler str='"+str+"'")  // never log any real user communication
      val p2pCoreMessage = Base64.decode(str)
      val protoMultiplex = P2pCore.Message.parseFrom(p2pCoreMessage)
      val command = protoMultiplex.getCommand
      if(command=="string") {
        val len = protoMultiplex.getMsgLength.asInstanceOf[Int]
        val receivedString = protoMultiplex.getMsgString
        if(receivedString=="quit") {
          // relay-server based communication will be ended this way (with an unencrypted 'quit') 
          p2pQuitFlag = true
          p2pDisconnect

        } else {        
          //log("relayReceiveHandler receivedString='"+receivedString+"'")  // never log any real user communication
          var decryptString:String = null
          try {
            decryptString = RsaDecrypt.decrypt(privKeyLocal, receivedString)
          } catch {
            case invChioherTextEx:ext.org.bouncycastle.crypto.InvalidCipherTextException =>
              logEx("relayReceiveHandler invChioherTextEx="+invChioherTextEx)
          }

          if(decryptString!=null) {
            //log("relayReceiveHandler decryptString='"+decryptString+"'")  // never log any real user communication
            relayReceiveUserData(decryptString)
          }
        }
      }
    }
  }

  def relayReceiveUserData(str:String) {
    log("p2pReceiveHandler decryptString='"+str+"'")
  }

  def storeRemotePublicKey(keyName:String, keystring:String) {
    Tools.writeToFile(keyFolderPath+"/"+keyName+".pub", keystring)
  }

  def p2pEncryptedCommunication() {
    for(i <- 0 until 3) {
      val unencryptedMessage = "hello "+i  // maxSize of unencrypted string ~128 bytes (?)
      val encryptedMessage = RsaEncrypt.encrypt(pubKeyRemote, unencryptedMessage)
      p2pSend(encryptedMessage, udpConnectIpAddr, udpConnectPortInt)
      try { Thread.sleep(1000); } catch { case ex:Exception => }
    }
    p2pQuit
  }
}

