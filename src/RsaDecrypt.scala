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
import ext.org.bouncycastle.crypto.util.PrivateKeyFactory
import ext.org.bouncycastle.crypto.encodings.PKCS1Encoding

object RsaDecrypt {

  def decrypt(key:String, encryptedData:String) :String = {
    val privateKey = PrivateKeyFactory.createKey(Base64.decode(key)) // AsymmetricKeyParameter
    val asymmetricBlockCipher = new RSAEngine()
    val asymmetricBlockCipher2 = new PKCS1Encoding(asymmetricBlockCipher)
    asymmetricBlockCipher2.init(false, privateKey)
    val messageBytes = hexStringToByteArray(encryptedData) // byte[]
    val hexEncodedCipher = asymmetricBlockCipher2.processBlock(messageBytes, 0, messageBytes.length) // byte[]
    return new String(hexEncodedCipher)
  }

  def hexStringToByteArray(s:String) :Array[Byte] = {
    val len = s.length
    val data = new Array[Byte](len/2)
    for(i <- 0 until len/2)
      data(i) = ((Character.digit(s.charAt(i*2), 16) << 4) + Character.digit(s.charAt(i*2+1), 16)).asInstanceOf[Byte]
    return data
  }

}

