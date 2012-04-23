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

import java.net.{Socket, SocketAddress, InetSocketAddress, InetAddress }
import java.io.{BufferedWriter, BufferedReader, OutputStreamWriter, InputStreamReader }
import java.text.SimpleDateFormat
import java.util.Calendar

trait RelayTrait {

  var relayServer = "109.74.203.226"
  var relayPort = 18771

  var appName = this.getClass.getName
  if(appName.lastIndexOf('.') > 0)
    appName = appName.substring(appName.lastIndexOf('.')+1)
  if(appName.lastIndexOf('$') > 0)
    appName = appName.substring(appName.lastIndexOf('$')+1)
  var matchSource = appName  // we can be searched by this
  var matchTarget = appName  // what we are searching for

  var relaySocket = new Socket()
  var socketOutWriter:BufferedWriter = null
  var socketInReader:BufferedReader = null
  var hostPubKey:String = null
  @volatile var relayQuitFlag = false
  var randomId = 0
  var clientId = ""

  def start() :Int = {
    initHostPubKey
    relaySocket.setReuseAddress(true)

    log("relaySocket.getLocalPort="+relaySocket.getLocalPort+" relayServer="+relayServer+" relayPort="+relayPort)
    relaySocket.connect(new InetSocketAddress(relayServer,relayPort))
    // may throw java.net.ConnectException: Connection refused

    socketOutWriter = new BufferedWriter(new OutputStreamWriter(relaySocket.getOutputStream))
    socketInReader = new BufferedReader(new InputStreamReader(relaySocket.getInputStream))
    send("hello")

    var throwEx:Exception = null
    while(!relayQuitFlag && socketInReader!=null) {
      try {
        val msgString = socketInReader.readLine
        // may throw java.net.ConnectException: Connection refused
        // may throw java.net.SocketException: recvfrom failed: ETIMEDOUT (Connection timed out)

        if(msgString==null) {
          log(appName+" socketInReader msgString==null")
          relayQuitFlag = true
        }
        else
        if(msgString.length>0) {
          receiveHandler(msgString)
        }

      } catch {
        case ex:Exception =>
          // if not manualy disconencted, this is not an error
          if(!relayQuitFlag) {
            throwEx = ex
            logEx("relay connection disconnected")
            ex.printStackTrace
            relayQuitFlag = true
          }
      }
    }

    try {
      socketInReader.close
      socketOutWriter.close
      relayQuit

    } catch {
      case ex:Exception =>
        logEx("relayQuit "+ex)
    }

    relayExit
    if(throwEx!=null)
      throw throwEx
    return 0
  }


  def log(str:String) {
    val dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance.getTime)
    println(dateTime+" "+appName+" "+str)
  }

  def logEx(str:String) {
    //val dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance.getTime)
    //println(dateTime+" "+appName+" exception "+str+" ######")
    log("exception "+str+" ######")
  }

  def send(str:String) {
    if(!relayQuitFlag) {
      socketOutWriter synchronized {
        socketOutWriter.write(str+"\n")
        socketOutWriter.flush
      }
    } else {
      log("send skip sending ["+str+"] due to relayQuitFlag")
    }
  }

  def receiveHandler(msgString:String) {
    // relay server startup connection protocol
    // 1. wait for "commserver:id=nnnnnn"
    // 2. send: "id=nnnnnn|core={appName},{localBtMac},{remoteBtMac},{directlyTargetable},{findRandomPlayer},{matchScore},
    //           {userName},{appType},{version}"  (rsa encrypted)
    // 3. wait for
    //    "commserver:wait"  (only if this is the 1st of two clients)
    //    "commserver:connect={otherClientUserName}|{otherClientCountry}"
    // 4. start sending & receiving stream data...
    //log("receiveHandler msgString=["+msgString+"]")

    if(msgString.startsWith("commserver:")) {
      val commserverString = msgString.substring(11)
      //log("receiveHandler commserver:["+commserverString+"]")
      if(commserverString.startsWith("id=")) {
        val id = commserverString.substring(3) 
        randomId = util.Random.nextInt(100000000)
        // consider encrypt string max-size RsaKeyGenerate.keySize; see also: RsaEncrypt.scala
        val initialMsg = "id="+id+"|core="+appName+","+matchSource+","+matchTarget+",true,false,false,-,direct,null,"+randomId
        log("receiveHandler send encrypted initialMsg='"+initialMsg+"' initialMsg.length="+initialMsg.length)
        val encrypted = RsaEncrypt.encrypt(hostPubKey, initialMsg)
        //log("receiveHandler send encrypted=["+encrypted+"]")
        send(encrypted)

      } else if(commserverString.startsWith("clientId=")) {
        clientId = commserverString.substring(9)
        verifyRelay

      } else if(commserverString.startsWith("check")) {
        verifyRelay
        send("ack")

      } else if(commserverString.startsWith("wait")) {
        verifyRelay
        // now wait for other client

      } else if(commserverString.startsWith("connect=")) {    // may be better named "connected=..."
        val connectString = commserverString.substring(8) 
        verifyRelay
        new Thread("senddata") { override def run() { connectedThread(connectString) } }.start

      } else if(commserverString.startsWith("disconnect")) {
        log("receiveHandler 'disconnect' -> set relayQuitFlag")
        relayQuitFlag = true
      }

    } else {
      receiveMsgHandler(msgString)
    }  
  }

  def verifyRelay() {
    if(clientId==null || clientId.length==0 || clientId!=""+randomId) {
      log("randomId="+randomId+" clientId="+clientId+" are not the same #########")
      // relayserver not trustworthy?
      relayQuit
    }
  }

  def relayQuit() {
    // bring the relay connection down
    if(relaySocket!=null) {
      relayQuitFlag = true
      relaySocket.close
      relaySocket=null
    }
  }

  def initHostPubKey() {
    hostPubKey = io.Source.fromFile("relaykey.pub").mkString
  }

  def receiveMsgHandler(str:String) {}

  def connectedThread(connectString:String) {}

  def relayExit() {}
}

