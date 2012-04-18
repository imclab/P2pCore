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

object RelayEncrypt {
  def main(args:Array[String]): Unit = {
    new RelayEncrypt().start
  }
}

class RelayEncrypt extends RelayTrait {

  appName = "RelayEncrypt"
  matchSource = appName
  matchTarget = appName

  val sendDataString = "data"
  var privKey:String = null
  var pubKey:String = null

  override def start() :Int = {
    log("start appName=["+appName+"] matchSource="+matchSource+" matchTarget="+matchTarget)
    privKey = io.Source.fromFile("keys/key0").mkString
    pubKey = io.Source.fromFile("keys/key0.pub").mkString
    return super.start
  }

  override def connectedThread(connectString:String) {
    log("connectedThread start connectString='"+connectString+"' sendDataString='"+sendDataString+"'")
    send(RsaEncrypt.encrypt(pubKey, sendDataString))
  }

  override def receiveMsgHandler(str:String) {
    val decryptString = RsaDecrypt.decrypt(privKey, str)
    if(decryptString!=null && decryptString==sendDataString) {
      log("receiveMsgHandler '"+sendDataString+"'; set relayQuitFlag")
      relayQuitFlag = true
      return
    }
    log("receiveMsgHandler cryptString=["+str+"] decryptString=["+decryptString+"]")
  }
}

