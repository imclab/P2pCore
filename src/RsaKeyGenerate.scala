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

import java.security.{ KeyPairGenerator, KeyPair, MessageDigest }

object RsaKeyGenerate {
  val keySize = 1536

  def main(args:Array[String]): Unit = {
    val keyPair = rsaKeyGenerate
    Tools.writeToFile("key.pub", Base64.encode(keyPair.getPublic.getEncoded))
    Tools.writeToFile("key", Base64.encode(keyPair.getPrivate.getEncoded))

    // print digest's for the public and private key
    val messageDigest = MessageDigest.getInstance("SHA-1")
    //println("messageDigest.getProvider.getInfo="+messageDigest.getProvider.getInfo)
    messageDigest.update(keyPair.getPublic.getEncoded)
    println(getBytesAsFormattedString(messageDigest.digest)+" len="+messageDigest.digest.length)
    messageDigest.reset
    messageDigest.update(keyPair.getPrivate.getEncoded)
    println(getBytesAsFormattedString(messageDigest.digest)+" len="+messageDigest.digest.length)
  }

  def rsaKeyGenerate() :KeyPair = {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(keySize)
    return keyPairGenerator.genKeyPair
  }

  private def getBytesAsFormattedString(byteArray:Array[Byte]) :String = {
    val stringBuilder = new StringBuilder()
    if(byteArray!=null) {
      var count=0
      for(byte <- byteArray) {
        if(count>0 && count%4==0)
          stringBuilder append "\n"
        else
        if(stringBuilder.length>0)
          stringBuilder append " "
        val hexString = "%02X" format (byte & 0xff)
        stringBuilder append hexString
        count += 1
      }
    }
    return stringBuilder.toString
  }
}

