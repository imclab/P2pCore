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

object RelayEncryptStress {
  def main(args:Array[String]): Unit = {
    if(args.length<2) {
      println("need two args: encryptKeyFile decryptKeyFile")
      return
    }

    new RelayEncryptStress(args(0),args(1),args(2)).start
  }
}

class RelayEncryptStress(encryptKeyFile:String, decryptKeyFile:String, decryptPublicKeyFile:String) extends RelayTrait {

  var incomingDataCounter = 0
  var outgoingDataCounter = 0
  var localPrivateKey:String = null
  var remotePubKey:String = null
  var sendDataCount = 1000
  var sendDataString = "data"
  var sendEndString = "last"

  appName = "RelayEncryptStress"
  // prepare org.bouncycastle.crypto.encodings.PKCS1Encoding for RsaEncrypt/RsaDecrypt
  Security.addProvider(new ext.org.bouncycastle.jce.provider.BouncyCastleProvider())

  override def start() :Int = {
    remotePubKey = io.Source.fromFile(encryptKeyFile).mkString
    localPrivateKey = io.Source.fromFile(decryptKeyFile).mkString

    // using match strings based on fingerprints of the two public keys
    val localPubKey = io.Source.fromFile(decryptPublicKeyFile).mkString

    val messageDigest = MessageDigest.getInstance("SHA-1")
    messageDigest.update(Base64.decode(localPubKey))
    matchSource = RsaEncrypt.getHexString(messageDigest.digest).substring(0,20)

    messageDigest.reset
    messageDigest.update(Base64.decode(remotePubKey))
    matchTarget = RsaEncrypt.getHexString(messageDigest.digest).substring(0,20)

    log("init appName=["+appName+"] matchSource="+matchSource+" matchTarget="+matchTarget)
    return super.start
  }

  override def connectedThread(connectString:String) {
    log("connectedThread connectString='"+connectString+"' sendDataString='"+sendDataString+"'")
    if(remotePubKey==null)
      return

    while(!relayQuitFlag && outgoingDataCounter<sendDataCount) {
      send(RsaEncrypt.encrypt(remotePubKey, "text="+sendDataString))
      outgoingDataCounter+=1
    }

    log("finished sending; relayQuitFlag="+relayQuitFlag)
    send(RsaEncrypt.encrypt(remotePubKey, "text="+sendEndString))
    if(incomingDataCounter==sendDataCount)
      relayQuitFlag = true
  }

  override def receiveMsgHandler(str:String) {
    if(localPrivateKey==null)
      return

    val decryptString = RsaDecrypt.decrypt(localPrivateKey, str)
    if(decryptString!=null) {
      if(decryptString.startsWith("text=")) {
        val textMsg = decryptString.substring(5)
        if(textMsg==sendDataString) {
          incomingDataCounter+=1
        } else if(textMsg==sendEndString) {
          log("receiveMsgHandler received last; relayQuitFlag="+relayQuitFlag)
          if(outgoingDataCounter==sendDataCount)
            relayQuitFlag = true
        } else {
          log("receiveMsgHandler unknown textMsg=["+textMsg+"] incomingDataCounter="+incomingDataCounter)
        }
      } else {
        log("receiveMsgHandler unknown decryptString=["+decryptString+"] incomingDataCounter="+incomingDataCounter)
      }
    }
  }

  override def relayExit() {
    log("relayExit outgoingDataCounter="+outgoingDataCounter+" incomingDataCounter="+incomingDataCounter)
  }
}

