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
  var pubKeyLocalFingerprint:String = null
  var pubKeyRemote:String = null
  var remoteKeyName = setRemoteKeyName

  /**
   *
   */
  override def start() :Int = {
    init
    val ret = readkeys
    if(ret!=0)
      return ret
    return super.start
  }

  /**
   * prepare org.bouncycastle.crypto.encodings.PKCS1Encoding in RsaEncrypt/RsaDecrypt
   */
  def init() {
    Security.addProvider(new ext.org.bouncycastle.jce.provider.BouncyCastleProvider())
  }

  /**
   * readkeys will 
   * try to load privKeyLocal and pubKeyLocal from keyFolderPath
   * or: generate a new key pair and set privKeyLocal and pubKeyLocal
   * @return 0 if there was no error
   */
  def readkeys() :Int = {
    if(remoteKeyName.length<=0)
      return -1

    try {
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

  /**
   * buildMatchStrings will define matchSource and matchTarget 
   * based on: rendezvous string - or -
   * based on: pubKeyLocalFingerprint and pubKeyRemoteFingerprint
   */
  def buildMatchStrings() :Int = {
    try {
      // create pubKeyLocalFingerprint based on pubKeyLocal
      val messageDigest = MessageDigest.getInstance("SHA-1")
      messageDigest.update(Base64.decode(pubKeyLocal))
      pubKeyLocalFingerprint = RsaEncrypt.getHexString(messageDigest.digest)
    } catch {
      case ex:Exception =>
        logEx("fingerprint setup error ex="+ex)
        return -1
    }

    if(rendezvous!=null && rendezvous.length>0) {
      // build match strings based on the rendezvous string
      matchSource = rendezvous
      matchTarget = rendezvous
      log("matching clients with rendezvous string '"+rendezvous+"'")
      return 0
    } 
    
    try {
      // create match strings based on fingerprints of the two public keys
      val fullRemoteKeyName = keyFolderPath +"/" +remoteKeyName+".pub"
      log("fullRemoteKeyName="+fullRemoteKeyName+" used for fingerprint matching")
      pubKeyRemote = io.Source.fromFile(fullRemoteKeyName).mkString
      val messageDigest = MessageDigest.getInstance("SHA-1")
      messageDigest.update(Base64.decode(pubKeyRemote))
      val pubKeyRemoteFingerprint = RsaEncrypt.getHexString(messageDigest.digest)

      matchSource = pubKeyLocalFingerprint.substring(0,20)
      matchTarget = pubKeyRemoteFingerprint.substring(0,20)

    } catch {
      case ex:Exception =>
        logEx("fingerprint setup error ex="+ex)
        return -2
    }
    return 0
  }

  /**
   * p2pSendThread is called when a p2p connection was established
   * if relayBasedP2pCommunication is set, p2p is relayed; else it is direct
   * if pubKeyRemote is not known, a key fingerprint will be requested using "requestPubKeyFingerprint"
   * processing will then be forward to p2pEncryptedCommunication
   */
  override def p2pSendThread() {
    if(pubKeyRemote!=null) {
      // remote public key is known
      log("p2pSendThread -> p2pEncryptedCommunication...")
      p2pEncryptedCommunication

    } else {
      // remote public key is NOT known, request public key fingerprint, so we can check if we have the key stored already
      log("p2pSendThread requestPubKeyFingerprint...")
      p2pSend("requestPubKeyFingerprint", udpConnectIpAddr,udpConnectPortInt)    // unencrypted request for pubkey fingerprint
      // p2pEncryptedCommunication will be called, as soon as we receive "pubKeyFingerprint=..." in p2pReceiveHandler     
    }
  }

  /**
   * p2pReceiveMultiplexHandler will be called with an encoded P2pCore.Message
   * will decrypt MsgString (if nesessary) and 
   * if there was no error, hand over the decrypted string from the other client to p2pReceivePreHandler
   */
  override def p2pReceiveMultiplexHandler(protoMultiplex:P2pCore.Message) {
    val command = protoMultiplex.getCommand
    if(command=="string") {
      super.p2pReceiveMultiplexHandler(protoMultiplex)

    } else if(command=="rsastr") {     
      val len = protoMultiplex.getMsgLength.asInstanceOf[Int]
      val receivedString = protoMultiplex.getMsgString
      //val id = protoMultiplex.getMsgId

      try {
        // possible exception: ext.org.bouncycastle.crypto.InvalidCipherTextException: unknown block type
        // possible exception: ext.org.bouncycastle.crypto.DataLengthException: input too large for RSA cipher
        //log("p2pReceiveMultiplexHandler: crypted="+receivedString+" len="+receivedString.length)
        val decryptString = RsaDecrypt.decrypt(privKeyLocal, receivedString)
        p2pReceivePreHandler(decryptString) // -> p2pReceiveHandler

      } catch {
        case ex:Exception =>
          logEx("p2pReceiveMultiplexHandler "+ex.getMessage)
          ex.printStackTrace
      }
    }
  }

  /**
   * p2pReceivePreHandler is called as soon as p2p data was encrypted
   * will process special commands, such as "requestPubKeyFingerprint", "pubKeyFingerprint=...", "check", "ack", "quit"
   */
  override def p2pReceivePreHandler(str:String) {
    if(str=="requestPubKeyFingerprint") {
      log("p2pReceivePreHandler: sending fingerprint of our pubkey on request="+pubKeyLocalFingerprint)
      p2pSend("pubKeyFingerprint="+pubKeyLocalFingerprint, udpConnectIpAddr, udpConnectPortInt)

    } else if(str.startsWith("pubKeyFingerprint=")) {
      val remoteKeyFingerprint = str.substring(18)
      log("p2pReceivePreHandler: remoteKeyFingerprint="+remoteKeyFingerprint)

      // search all stored pub keys for a match to remoteKeyFingerprint
      pubKeyRemote = null
      val fileArray = new java.io.File(keyFolderPath).listFiles
      for(file <- fileArray.iterator.toList) {
        if(pubKeyRemote==null) {
          val fileName = file.getName.trim
          if(fileName.length>4 && fileName.endsWith(".pub") && fileName!="key.pub") {
            val key = io.Source.fromFile(keyFolderPath+"/"+fileName).mkString
            val messageDigest = MessageDigest.getInstance("SHA-1")
            messageDigest.update(Base64.decode(key))
            val fingerprint = RsaEncrypt.getHexString(messageDigest.digest)
            if(fingerprint==remoteKeyFingerprint) {
              log("p2pReceivePreHandler: found stored pubKeyRemote in file "+fileName)
              pubKeyRemote = key
            }
          }
        }
      }

      if(pubKeyRemote==null) {
        log("p2pReceivePreHandler: not found stored pubKeyRemote - abort session")
        p2pQuitFlag = true
        p2pQuit(true)
        relayReceiveEncryptionFailed(remoteKeyFingerprint)
        return
      }

      log("p2pReceivePreHandler -> p2pEncryptedCommunication...")
      new Thread("datagramSendPublic") { override def run() {
        p2pEncryptedCommunication
      } }.start

    } else {
      super.p2pReceivePreHandler(str) // -> p2pReceiveHandler()
    }
  }
  
  /**
   * storeRemotePublicKey todo ?
   */
  def storeRemotePublicKey(keyName:String, keystring:String) {
    Tools.writeToFile(keyFolderPath+"/"+keyName+".pub", keystring)
  }

  /**
   * relayReceiveEncryptionFailed will be called after the received fingerprint has been evaluated
   * and not matching public key was not found in the local filesystem 
   */
  def relayReceiveEncryptionFailed(remoteKeyFingerprint:String) {
    log("relayReceiveEncryptionFailed failed to load key for remote key fingerprint='"+remoteKeyFingerprint+"'")
  }

  /**
   * p2pReceiveHandler is called for receiving and processing of decrypted data strings from the other client
   * data that was sent directly per UDP - or relayed per TCP (relayBasedP2pCommunication=true)
   * if relayBasedP2pCommunication is NOT set, we may use this to disconnect from the relay connection
   */
  override def p2pReceiveHandler(str:String, host:String, port:Int) {
    log("p2pReceiveHandler decryptString='"+str+"'")
  }

  /**
   * relayReceiveHandler is NOT used in p2p mode; instead all received data goes to p2pReceiveHandler
   * exception: if the relay link is being used as a udp-fallback (relayBasedP2pCommunication=true)
   * a base64 encoded P2pCore.Message will be received here and will be forwarded to p2pReceiveMultiplexHandler
   */
  override def relayReceiveHandler(str:String) {
    if(relayBasedP2pCommunication) {
      val p2pCoreMessage = Base64.decode(str)
      val protoMultiplex = P2pCore.Message.parseFrom(p2pCoreMessage)
      p2pReceiveMultiplexHandler(protoMultiplex)  // -> p2pReceivePreHandler() -> p2pEncryptedCommunication()
      return
    }

    log("relayReceiveHandler !relayBasedP2pComm str='"+str+"' pubKeyRemote="+pubKeyRemote+" UNEXPECTED IN P2P APP ###########")
  }

  /**
   * p2pEncryptedCommunication is being called when p2p connection has been 
   * established and encryption has been enabled 
   */ 
  def p2pEncryptedCommunication() {
    log("p2pEncryptedCommunication...")
    for(i <- 0 until 3) {
      val unencryptedMessage = "hello "+i  // maxSize of unencrypted string ~128 bytes (?)
      val encryptedMessage = RsaEncrypt.encrypt(pubKeyRemote, unencryptedMessage)
      p2pSend(encryptedMessage, udpConnectIpAddr, udpConnectPortInt, "rsastr")
      try { Thread.sleep(1000); } catch { case ex:Exception => }
    }
    p2pQuit(true)
  }
}

