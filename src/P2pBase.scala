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

  var p2pSocket = new DatagramSocket()
  var udpEchoPort = 18775
  @volatile var udpConnectConfirmed = false
  @volatile var udpConnectIpAddr:String = null
  @volatile var udpConnectPortInt = -1
  @volatile var p2pQuitFlag = false
  @volatile var udpPunchAttempts = 0
  @volatile var udpPunchFaults = 0
  var publicUdpAddrString:String = null
  var relayBasedP2pCommunication = false
  var waitingRelayThread:Thread = null

  override def start() :Int = {
    log("start appName=["+appName+"] matchSource="+matchSource+" matchTarget="+matchTarget+
        " receiveBufferSize="+p2pSocket.getReceiveBufferSize+
        " sendBufferSize="+p2pSocket.getSendBufferSize)
    val ret = super.start
    // in case the relay connection was closed but the udp connection is still in use, we keep the parent alive
    if(udpConnectIpAddr!=null && !p2pQuitFlag) {
      log("P2pBase keep running until p2pQuitFlag")
      waitingRelayThread = Thread.currentThread
      while(!p2pQuitFlag) {
        try { Thread.sleep(300000); } catch { case ex:Exception => p2pQuitFlag=true }
      }
      waitingRelayThread = null
    }
    log("P2pBase finished")
    p2pExit
    return ret
  }

  def p2pSend(sendString:String, host:String, port:Int) :Unit = synchronized {
    if(sendString==null) {
      log("p2pSend sendString==null to="+host+":"+port)
      return
    } 

    //log("p2pSend '"+sendString+"' len="+sendString.length+" to="+host+":"+port)
    // note: max 532 bytes per udp; otherwise fragmentation
    if(host==relayServer) {
      val sendByteBuf = sendString.getBytes
      val sendDatagram = new DatagramPacket(sendByteBuf, sendByteBuf.length, new InetSocketAddress(host, port))
      p2pSocket.send(sendDatagram)

    } else {
      val p2pCoreMsg = P2pCore.Message.newBuilder
                              .setCommand("string")
                              .setMsgLength(sendString.length)
                              .setMsgString(sendString)
                              .build
      val size = p2pCoreMsg.getSerializedSize
      if(size>0) {
        val byteData = p2pCoreMsg.toByteArray
        //log("p2pSend '"+sendString+"' len="+sendString.length+" to="+host+":"+port+" size="+size+" "+byteData.length)
        if(relayBasedP2pCommunication) {
          send(Base64.encode(byteData))

        } else if(host!=null && port!=0) {
          val sendDatagram = new DatagramPacket(byteData, size, new InetSocketAddress(host, port))
          p2pSocket.send(sendDatagram)
        }
      }
    }
  }

  override def connectedThread(connectString:String) {
    // we are now tcp connected via relay server

    /*
      // this code could be used for TCP hole punching (but we don't know how to do this yet)
      // parse connectString="username|null|92.201.15.122|42147" (username|county|ip|port)
      //log("connectedThread connectString='"+connectString+"'")
      val tokenArrayOfStrings = connectString split '|'
      otherPublicIpAddr = tokenArrayOfStrings(2)
      val otherPublicPort = new java.lang.Integer(tokenArrayOfStrings(3)).intValue
      val myPublicIpAddr = tokenArrayOfStrings(4)
      val myPublicPort = new java.lang.Integer(tokenArrayOfStrings(5)).intValue
      log("connectedThread otherPublicIpAddr='"+otherPublicIpAddr+"' otherPublicPort="+otherPublicPort)
      log("connectedThread    myPublicIpAddr='"+myPublicIpAddr+"' myPublicPort="+myPublicPort)
    */

    // first get our own public/external udp-port from relay server's udp echo-service
    p2pSend("hello", relayServer, udpEchoPort)
    // todo: does this get through in vf network?
    // compare with http://en.wikipedia.org/wiki/STUN#Classic_STUN_NAT_characterization_algorithm

    // start receiving datagram's 
    // the first packet received will be our "publicUdpAddress:port" response from relay server's udp echo-service
    // we will use our tcp-relay connection to send this info to our peer
    val byteBuf = new Array[Byte](2*1024)
    // todo: size not smaller than RsaKeyGenerate.keySize ???
    val datagram = new DatagramPacket(byteBuf, 2*1024)
    while(!p2pQuitFlag) {
      try {
        datagram.setData(byteBuf)
        datagram.setLength(1024)
        //log("p2pSocket="+p2pSocket)
        p2pSocket.receive(datagram)
        if(datagram.getLength>0) {
          val receivedString = new String(byteBuf,0,datagram.getLength)
          //log("datagram.getLength="+datagram.getLength+" receivedString='"+receivedString+"' from="+datagram.getAddress.getHostAddress)

          if(datagram.getAddress.getHostAddress==relayServer) {
            // response from relay server's udp echo-service
            publicUdpAddrString = receivedString
            var combinedUdpAddrString = "udpAddress="+publicUdpAddrString
            if(p2pSocket!=null)
              combinedUdpAddrString += "|"+relaySocket.getLocalAddress.getHostAddress+":"+p2pSocket.getLocalPort
            log("combinedUdpAddrString this peer "+combinedUdpAddrString)
            send(combinedUdpAddrString)

          } else {
            val msgBuf = new Array[Byte](datagram.getLength)
            for(i <- 0 until datagram.getLength) 
              msgBuf(i) = byteBuf(i)
            val protoMultiplex = P2pCore.Message.parseFrom(msgBuf)
            val command = protoMultiplex.getCommand
            if(command=="string") {
              udpConnectIpAddr = datagram.getAddress.getHostAddress
              udpConnectPortInt = datagram.getPort

              val len = protoMultiplex.getMsgLength.asInstanceOf[Int]
              val receivedString = protoMultiplex.getMsgString
              //val id = protoMultiplex.getMsgId

              if(receivedString=="quit") {
                p2pQuitFlag = true
                p2pDisconnect

              } else if(receivedString=="check") {
                p2pSend("ack", datagram.getAddress.getHostAddress, datagram.getPort)

              } else if(receivedString=="ack") {
                udpConnectConfirmed = true

              } else {
                p2pReceiveHandler(receivedString, datagram.getAddress.getHostAddress, datagram.getPort)
              }
            }
          }
        }
      } catch {
        case ex:java.lang.Exception =>
          log("connectedThread ex="+ex.getMessage+" ########")
          ex.printStackTrace
          p2pQuitFlag=true
      }
    }
  }

  override def receiveMsgHandler(str:String) {
    // will be called every time we receive from the other client: TCP data via relay server

    udpPunchAttempts=0
    udpPunchFaults=0
    if(str.startsWith("udpAddress=")) {
      val combindedUdpAddress = str.substring(11)
      log("receiveMsgHandler other peer combindedUdpAddress=["+combindedUdpAddress+"]")
      val tokenArrayOfStrings = combindedUdpAddress.trim split '|'
      val udpAddressString = tokenArrayOfStrings(0)
      if(udpAddressString.length>0) {
        // try to udp-communicate with the other parties external ip:port
        // todo: implement direct-p2p connect-timeout starting now

        udpPunchAttempts +=1
        new Thread("datagramSendPublic") { override def run() {
          val tokenArrayOfStrings2 = udpAddressString split ':'
          datagramSendThread(tokenArrayOfStrings2(0),new java.lang.Integer(tokenArrayOfStrings2(1)).intValue)
        } }.start

        val localIpAddressString = tokenArrayOfStrings(1)
        if(localIpAddressString.length>0) {
          // try to udp-communicate with the other parties local ip:port
          udpPunchAttempts +=1
          new Thread("datagramSendLocal") { override def run() { 
            val tokenArrayOfStrings3 = localIpAddressString split ':'
            datagramSendThread(tokenArrayOfStrings3(0),new java.lang.Integer(tokenArrayOfStrings3(1)).intValue)
          } }.start
        }
      }
      return
    }

    relayReceiveHandler(str)
  }

  def datagramSendThread(udpIpAddr:String, udpPortInt:Int) {
    log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt)

    // punch udp hole
    val startTime = System.currentTimeMillis
    while(!udpConnectConfirmed && System.currentTimeMillis-startTime<5000 && udpPortInt>0) {
      p2pSend("check", udpIpAddr, udpPortInt)
      try { Thread.sleep(1000); } catch { case ex:Exception => }
    }
    if(udpConnectIpAddr!=udpIpAddr || udpConnectPortInt!=udpPortInt) {
      udpPunchFaults +=1
      log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt+" abort udpPunchFaults="+udpPunchFaults+" udpPunchAttempts="+udpPunchAttempts)

      if(udpPunchAttempts==udpPunchFaults) {
        // all datagramSendThread's have failed
        // todo: this still appears twice (the following if tries to prevent this, but not ideal)
        if(!relayBasedP2pCommunication) {
          p2pReset
          p2pFault(udpPunchAttempts)
          relayBasedP2pCommunication = true
          p2pSendThread(udpConnectIpAddr,udpConnectPortInt)
        }
      }
      return
    }

    // tata - udp hole has been punched
    log("datagramSendThread udpIpAddr=["+udpIpAddr+"] udpPortInt="+udpPortInt+" connected")
    p2pSendThread(udpConnectIpAddr,udpConnectPortInt)
  }

  def p2pFault(attempts:Int) {
    log("p2pFault failed to establish direct p2p connection over "+attempts+" separate pathes")
  }

  def p2pReset() {
    p2pSocket = null
    udpConnectIpAddr = null
    udpConnectPortInt = 0
  }

  def p2pQuit() {
    log("p2pQuit")
    if(waitingRelayThread!=null)
      waitingRelayThread.interrupt
    try {
      p2pSend("quit", udpConnectIpAddr,udpConnectPortInt)
    } catch {
      case ex:Exception =>
        log("p2pSend quit ex="+ex.getMessage)
    }
    p2pDisconnect
    p2pQuitFlag = true
    p2pReset
    //log("p2pQuit -> relayQuit")
    relayQuit
  }

  def p2pExit() { }

  def relayReceiveHandler(str:String) {
    log("relayReceiveHandler str='"+str+"'")
  }

  def p2pSendThread(udpIpAddr:String, udpPortInt:Int) {
    for(i <- 0 until 3) {
      p2pSend("hello "+i, udpIpAddr ,udpPortInt)
      try { Thread.sleep(1000); } catch { case ex:Exception => }
    }
    p2pQuit
  }

  def p2pReceiveHandler(str:String, host:String, port:Int) {
    // will be called every time we receive a direct (UDP) message from the other client
    log("p2pReceiveHandler str='"+str+"'")
  }
  
  def p2pDisconnect() { }
}

