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

import java.security.Security

import ext.org.bouncycastle.crypto.AsymmetricBlockCipher
import ext.org.bouncycastle.crypto.engines.RSAEngine
import ext.org.bouncycastle.crypto.params.AsymmetricKeyParameter
import ext.org.bouncycastle.crypto.util.PublicKeyFactory
import ext.org.bouncycastle.crypto.encodings.PKCS1Encoding

object RsaEncrypt {

  // note: individual string messages cannot be longer than the used key (RsaKeyGenerate.keySize = 1536 bits = 192 bytes)
  def encrypt(key:String, inputData:String) :String = {
    try {
      val keyBase64 = Base64.decode(key)
      //println("encrypt() key="+key+" keyBase64="+keyBase64)
      val publicKey = PublicKeyFactory.createKey(keyBase64)
      val asymmetricBlockCipher = new RSAEngine()
      val asymmetricBlockCipher2 = new PKCS1Encoding(asymmetricBlockCipher)
      asymmetricBlockCipher2.init(true, publicKey)
      val messageBytes = inputData.getBytes // byte[]
      //println("encrypt() asymmetricBlockCipher2.processBlock() messageBytes.length="+messageBytes.length)
      val hexEncodedCipher = asymmetricBlockCipher2.processBlock(messageBytes, 0, messageBytes.length) // byte[]
      val hexString = getHexString(hexEncodedCipher)
      //println("encrypt() return hexString="+hexString)
      return hexString

    } catch {
      case ex:Exception =>
        ex.printStackTrace
    }
  
    return null
  }

  def getHexString(byteArray:Array[Byte]) :String = {
    val stringBuilder = new StringBuilder()
    if(byteArray!=null) {
      for(byte <- byteArray) {
        val hexString = "%02X" format (byte & 0xff)
        stringBuilder append hexString
      }
    }
    return stringBuilder.toString
  }
}

