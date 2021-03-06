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

import java.net._

object P2pBase {
  def main(args:Array[String]): Unit = {
    new P2pBase().start
  }
}

class P2pBase extends RelayTrait {

  val p2pSocket = new DatagramSocket()
  val udpEchoPort = 18775
  @volatile var udpConnectConfirmed = false
  @volatile var udpConnectIpAddr:String = null
  @volatile var udpConnectPortInt = -1
  @volatile var p2pQuitFlag = false
  @volatile var udpPunchAttempts = 0
  @volatile var udpPunchFaults = 0
  @volatile var relayBasedP2pCommunication = false
  @volatile var waitingRelayThread:Thread = null
  @volatile var publicUdpAddrString:String = null
  @volatile var otherUdpAddrString:String = null
  @volatile var msgIdSend = 0l
  @volatile var msgMsSend = 0l
  @volatile var msgMsRcv = 0l


  override def start() :Int = {
    //log("start appName=["+appName+"] matchSource="+matchSource+" matchTarget="+matchTarget+
    //    " receiveBufferSize="+p2pSocket.getReceiveBufferSize+
    //    " sendBufferSize="+p2pSocket.getSendBufferSize)
    val ret = super.start
    // relay connection is now finished
    if(udpConnectIpAddr!=null && !p2pQuitFlag) {
      // we keep this thread (being the parent of the udp thread) alive, until the udp connection is also finished
      log("P2pBase keep running until p2pQuitFlag...")
      waitingRelayThread = Thread.currentThread
      waitingRelayThread synchronized { try { waitingRelayThread.wait } catch { case ex:Exception => } }
      waitingRelayThread = null
    }
    try { Thread.sleep(100); } catch { case ex:Exception => }
    p2pExit(ret)
    return ret
  }

  def p2pSend(sendString:String, 
              host:String=udpConnectIpAddr, 
              port:Int=udpConnectPortInt, 
              cmd:String="string") :Unit = synchronized {

    if(sendString==null) {
      log("p2pSend sendString==null to="+host+":"+port)
      return
    } 

    if(relayBasedP2pCommunication) {
      send(sendString)
      return
    }

    //log("p2pSend '"+sendString+"' len="+sendString.length+" to="+host+":"+port)
    if(host==relayServer) {
      // special case to send udp data to the relay server udp echo service
      val sendByteBuf = sendString.getBytes
      val sendDatagram = new DatagramPacket(sendByteBuf, sendByteBuf.length, new InetSocketAddress(host, port))
      p2pSocket.send(sendDatagram)

    } else {
      // sending data to the other peer
      msgIdSend += 1
      msgMsSend = System.currentTimeMillis
      val p2pCoreMsg = P2pCore.Message.newBuilder
                              .setCommand(cmd)
                              .setMsgLength(sendString.length)
                              .setMsgString(sendString)
                              .setMsgId(msgIdSend)
                              .build
      val size = p2pCoreMsg.getSerializedSize
      //log("p2pSend p2pCoreMsg.getSerializedSize="+size)
      if(size>0) {
        val byteData = p2pCoreMsg.toByteArray
        // if relayBasedP2pCommunication is set, use send(); else use p2pSocket.send()
        if(host!=null && port!=0) {
          //log("p2pSend udp '"+sendString+"' len="+sendString.length+" to="+host+":"+port+" size="+size+" "+byteData.length)
          val sendDatagram = new DatagramPacket(byteData, size, new InetSocketAddress(host, port))
          try {
            p2pSocket.send(sendDatagram)
            // todo: "java.io.IOException: Operation not permitted" ???
          } catch {
            case ex:java.io.IOException =>
              // for instance: "ENETUNREACH (Network is unreachable)"
              // retry...
              log("p2pSend ex="+ex+" retry...")
              try { Thread.sleep(700); } catch { case ex:Exception => }
              // todo: retry may also throw
              try {
                p2pSocket.send(sendDatagram)
              } catch {
                case ex:java.io.IOException =>
                  log("p2pSend retry failed ex="+ex)
                  // todo: how to cope? 
                  p2pQuit(false)
              }
          }
        }
      }
    }
  }

  def p2pSend(msg:String, cmd:String) {
    p2pSend(msg, udpConnectIpAddr, udpConnectPortInt, cmd)
  }

  def p2pSend(msg:String) {
    p2pSend(msg, udpConnectIpAddr, udpConnectPortInt)
  }

  /** we are now p2p-connected via relay server (tcp) */
  override def connectedThread(connectString:String) {

    //log("connectedThread connectString='"+connectString+"'")

    // first get our own public/external udp-port from relay server's udp echo-service
    p2pSend("hello", relayServer, udpEchoPort)
    // todo: does this UDP delivery get through in vf network?
    // compare: http://en.wikipedia.org/wiki/STUN#Classic_STUN_NAT_characterization_algorithm

    // start receiving datagram's
    // the first packet received will be our "publicUdpAddress:port" response from relay server's udp echo-service
    // we will use our tcp-relay connection to send this info to our peer
    val arraySize = 2*1024
    val byteBuf = new Array[Byte](arraySize)
    // array size not smaller than RsaKeyGenerate.keySize!
    val datagram = new DatagramPacket(byteBuf, arraySize)
    while(!p2pQuitFlag) {
      try {
        datagram.setData(byteBuf)
        datagram.setLength(arraySize)
        //log("p2pSocket="+p2pSocket)
        p2pSocket.receive(datagram)
        if(datagram.getLength>0) {
          val receivedString = new String(byteBuf,0,datagram.getLength)
          //log("datagram len="+datagram.getLength+" str='"+receivedString+"' from="+datagram.getAddress.getHostAddress)

          if(datagram.getAddress.getHostAddress==relayServer) {
            // special case: response from relay server's udp echo-service
            publicUdpAddrString = receivedString
            var combinedUdpAddrString = "udpAddress="+publicUdpAddrString
            if(p2pSocket!=null)
              combinedUdpAddrString += "|"+relaySocket.getLocalAddress.getHostAddress+":"+p2pSocket.getLocalPort
            log("combinedUdpAddrString this peer "+combinedUdpAddrString)
            send(combinedUdpAddrString)

          } else {
            // this DatagramPacket is coming from the other client
            udpConnectIpAddr = datagram.getAddress.getHostAddress
            udpConnectPortInt = datagram.getPort
            val msgBuf = new Array[Byte](datagram.getLength)
            for(i <- 0 until datagram.getLength) 
              msgBuf(i) = byteBuf(i)
            p2pReceiveMultiplexHandler(P2pCore.Message.parseFrom(msgBuf))
          }
        }
      } catch {
        case ex:java.lang.Exception =>
          logEx("connectedThread "+ex.getMessage)
          ex.printStackTrace
          p2pQuitFlag=true
      }
    }
    //log("connectedThread done")
  }
  
  def p2pReceiveMultiplexHandler(protoMultiplex:P2pCore.Message) {
    val command = protoMultiplex.getCommand
    if(command=="string") {
      val len = protoMultiplex.getMsgLength.asInstanceOf[Int]
      val receivedString = protoMultiplex.getMsgString
      //val id = protoMultiplex.getMsgId
      p2pReceivePreHandler(receivedString)
    }
  }

  def p2pReceivePreHandler(str:String) {
    msgMsRcv = System.currentTimeMillis
    //log("p2pReceivePreHandler P2pBase reset msgMsRcv; receiving=["+str+"]")
    if(str=="//quit") {
      log("p2pReceivePreHandler received 'quit'")
      //p2pQuitFlag = true
      p2pQuit(false)

    } else if(str=="//check") {
      p2pSend("//ack", udpConnectIpAddr, udpConnectPortInt)

    } else if(str=="//ack") {
      udpConnectConfirmed = true

    } else if(str.startsWith("//")) {
      log("p2pReceivePreHandler skip '"+str+"' ####")    

    } else {
      p2pReceiveHandler(str, udpConnectIpAddr, udpConnectPortInt)
    }
  }

  /** receiving p2p-data indirectly via relay server */
  override def receiveMsgHandler(str:String) {
    udpPunchAttempts=0
    udpPunchFaults=0

    if(str=="relayBasedP2p=true") {
      // other client prefers relayed communication
      log("receiveMsgHandler 'relayBasedP2p=true' relayBasedP2pCommunication="+relayBasedP2pCommunication+" ####")
      if(relayBasedP2pCommunication==false) {
        publicUdpAddrString = myPublicIpAddr+":"+myPublicPort
        otherUdpAddrString = otherPublicIpAddr+":"+otherPublicPort
        log("receiveMsgHandler publicUdpAddrString="+publicUdpAddrString+" otherUdpAddrString="+otherUdpAddrString)
        relayBasedP2pCommunication = true
        p2pSendThread
      }
      return
    }

    if(relayBasedP2pCommunication) {
      //log("receiveMsgHandler relayBasedP2pCommunication str="+str+" ####")
      // forward all receiveMsgHandler(str) to p2pReceivePreHandler(str)
      if(str.startsWith("udpAddress=")) {
        // ignore
      } else {
        p2pReceivePreHandler(str)  // -> p2pReceiveHandler()
      }
      return
    }

    if(str.startsWith("udpAddress=")) {
      // "udpAddress=..." is the only data we always receive from the other client via relay (unencoded/non-multiplexed format)
      // because NO direct p2p-connection was established yet
      val combindedUdpAddress = str.substring(11)
      log("receiveMsgHandler other peer combindedUdpAddress=["+combindedUdpAddress+"]")
      val tokenArrayOfStrings = combindedUdpAddress.trim split '|'
      otherUdpAddrString = tokenArrayOfStrings(0)
      if(otherUdpAddrString.length>0) {
        // trying to udp-communicate with the other parties external ip:port
        // todo: implement direct-p2p connect-timeout starting here and now

        udpPunchAttempts +=1
        new Thread("datagramSendPublic") { override def run() {
          val tokenArrayOfStrings2 = otherUdpAddrString split ':'
          datagramSendThread(tokenArrayOfStrings2(0),new java.lang.Integer(tokenArrayOfStrings2(1)).intValue)
          // if the udp hole was punched, call p2pSendThread
        } }.start

        val localIpAddressString = tokenArrayOfStrings(1)
        if(localIpAddressString.length>0) {
          // try to udp-communicate with the other parties local ip:port
          udpPunchAttempts +=1
          new Thread("datagramSendLocal") { override def run() { 
            val tokenArrayOfStrings3 = localIpAddressString split ':'
            datagramSendThread(tokenArrayOfStrings3(0),new java.lang.Integer(tokenArrayOfStrings3(1)).intValue)
            // if the udp hole was punched, call p2pSendThread
          } }.start
        }       
      }
    }

    // in p2p mode, this is not being used: all data goes to p2pReceiveHandler (even if relayed as a fallback)
    relayReceiveHandler(str)
  }

  def datagramSendThread(udpIpAddr:String, udpPortInt:Int) {
    //log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt)

    // punch udp hole
    val startTime = System.currentTimeMillis
    while(!udpConnectConfirmed && System.currentTimeMillis-startTime<4000 && udpIpAddr!=null && udpPortInt>0) {
      p2pSend("//check", udpIpAddr, udpPortInt)
      try { Thread.sleep(700); } catch { case ex:Exception => }
    }
    if(udpConnectIpAddr!=udpIpAddr || udpConnectPortInt!=udpPortInt) {
      udpPunchFaults +=1
      log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt+" try other")

      if(udpPunchAttempts==udpPunchFaults) {
        log("datagramSendThread all datagramSendThread's have failed; relayBasedP2pCommunication="+relayBasedP2pCommunication)
        if(!relayBasedP2pCommunication) {
          p2pReset
          p2pFault(udpPunchAttempts)

          log("datagramSendThread p2p via relay server")
          relayBasedP2pCommunication = true
          p2pSendThread
        }
      }
    } else {
      // udp hole is punched
      log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt+" connected")
      p2pSendThread
    }

    log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt+" done")
  }

  def p2pFault(attempts:Int) {
    log("p2pFault failed to establish direct p2p connection over "+attempts+" separate pathes")
  }

  def p2pReset() {
    log("p2pReset")
    udpConnectIpAddr = null
    udpConnectPortInt = 0
  }

  /** bring the p2p connection down (and the relay connection too) */
  def p2pQuit(sendQuit:Boolean=false) = synchronized {
    if(udpConnectIpAddr!=null) {
      log("p2pQuit p2pQuitFlag="+p2pQuitFlag+" sendQuit="+sendQuit+" udpConnectIpAddr="+udpConnectIpAddr)
      if(sendQuit) {
        try {
          // say bye bye to remote client
          p2pSend("//quit", udpConnectIpAddr,udpConnectPortInt)
        } catch {
          case ex:Exception =>
            logEx("p2pQuit ex="+ex.getMessage)
        }
        //log("p2pQuit sendQuit done")
      }
    }
    relayBasedP2pCommunication = false
    p2pQuitFlag = true
    relayQuit
    if(waitingRelayThread!=null)
      waitingRelayThread synchronized { waitingRelayThread.interrupt }
    //log("p2pQuit done")
  }

  /** we now receive data from the other client (via UDP) */
  def p2pReceiveHandler(str:String, host:String, port:Int) {
    log("p2pReceiveHandler str='"+str+"'")

    // disconnect our relay connection (stay connected via direct p2p)
    if(relaySocket!=null && !relayBasedP2pCommunication) {
      log("relaySocket.close")
      relayQuitFlag=true
      try { relaySocket.close } catch { case ex:Exception => }
      relaySocket=null
    }
  }

  /** we receive data via (or from) the relay server */
  def relayReceiveHandler(str:String) {
    if(str.startsWith("start otr/smp")) {
      log("relayReceiveHandler other client trying relayBasedP2pCommunication -> receiveMsgHandler")
      // set publicUdpAddrString and otherUdpAddrString AS IF we are UDP connected
      publicUdpAddrString = myPublicIpAddr+":"+myPublicPort
      otherUdpAddrString = otherPublicIpAddr+":"+otherPublicPort
      relayBasedP2pCommunication = true
      receiveMsgHandler(str)
      return
    }

    log("relayReceiveHandler str='"+str+"' UNEXPECTED IN P2P MODE ###########")
  }

  /** will check remote client availability */
  def p2pWatchdog() {
    new Thread("p2pWatchdog") { override def run() {
      udpConnectConfirmed = true
      while(!p2pQuitFlag && !relayBasedP2pCommunication) {
        try { Thread.sleep(1000); } catch { case ex:Exception => }
        // if we didn't receive any p2p data since 5 seconds, send 'check' to other side, waiting for 'ack'
        if(msgMsRcv+5000<System.currentTimeMillis) {
          if(!udpConnectConfirmed) {
            // todo: maybe don't give up so quickly
            if(msgMsRcv+10000<System.currentTimeMillis) {
              p2pTimeout
            }
          } else {
            //log("p2pWatchdog TIMEOUT send check ###########")
            udpConnectConfirmed = false
            p2pSend("//check", udpConnectIpAddr, udpConnectPortInt)
          }
        }
      }
      log("p2pWatchdog exit")
    } }.start
  }

  /** remote client does not respond */
  def p2pTimeout() {
    log("p2pWatchdog TIMEOUT give up ####")
    p2pQuit(true)
  }

  /** the p2p connection has now ended */
  def p2pExit(ret:Int) { 
    log("p2pExit ret="+ret)
  }

  /** we are now p2p connected (if relayBasedP2pCommunication is set, p2p is relayed; else it is direct) */
  def p2pSendThread() {
    p2pWatchdog
    // test code
    for(i <- 0 until 3) {
      p2pSend("hello "+i, udpConnectIpAddr, udpConnectPortInt)
      try { Thread.sleep(1000); } catch { case ex:Exception => }
    }
    p2pQuit(true)
  }
}

