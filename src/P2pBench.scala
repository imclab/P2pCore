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

object P2pBench {
  def main(args:Array[String]): Unit = {
    new P2pBench().start
  }
}

class P2pBench extends P2pBase {

  appName = "P2pBench"
  matchSource = appName  // we can be searched by this
  matchTarget = appName  // we are searching for this

  val dataString = "datadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadata"+
                   "datadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadatadata"
  val sendDataCount = 2000
  val endString = "last"
  val endConfirmString = "endConfirm"
  @volatile var incomingDataCounter = 0
  @volatile var outgoingDataCounter = 0
  var startTime:Long = 0
  var benchMS:Long = 0

  override def start() :Int = {
    // the proposed values are hints only
    // it may be necessary to run on the OS-level: sysctl -w net.core.rmem_max=1048576
    p2pSocket.setReceiveBufferSize(512*1024) 
    p2pSocket.setSendBufferSize(256*1024) 
    return super.start
  }

  override def p2pSendThread(udpIpAddr:String, udpPortInt:Int) {
    log("p2pSendThread start udpIpAddr='"+udpIpAddr+"' udpPortInt="+udpPortInt+" relayQuitFlag="+relayQuitFlag)
    startTime = System.currentTimeMillis
    while(!relayQuitFlag && outgoingDataCounter<sendDataCount && udpIpAddr!=null) {
      p2pSend(dataString, udpIpAddr, udpPortInt)
      outgoingDataCounter+=1

      // try not to send udp data too fast...
      if(outgoingDataCounter%5==0) {
        try { Thread.sleep(100) } catch { case ex:Exception => }
      }
    }

    send(endString)
  }

  override def p2pReceiveHandler(str:String, udpIpAddr:String, udpPortInt:Int) {
    if(str==dataString) {
      incomingDataCounter+=1
    } else {
      log("p2pReceiveHandler str=["+str+"] incomingDataCounter="+incomingDataCounter+" unexpected ###########")
    }
  }

  override def relayReceiveHandler(str:String) {
    if(str==endString) {
      var maxDelayBy100MS = 0
      while(outgoingDataCounter<sendDataCount && maxDelayBy100MS<20) {
        try { Thread.sleep(100); } catch { case ex:Exception => }
        maxDelayBy100MS +=1
      }
      while(outgoingDataCounter<sendDataCount && maxDelayBy100MS<30) {
        try { Thread.sleep(100); } catch { case ex:Exception => }
        maxDelayBy100MS +=1
      }
      send(endConfirmString)

    } else if(str==endConfirmString) {
      showStatistics(str)
      send(endConfirmString)
      try { Thread.sleep(500); } catch { case ex:Exception => }
      p2pQuit

    } else {
      log("relayReceiveHandler str=["+str+"] incomingDataCounter="+incomingDataCounter+" unexpected ###########")
    }
  }

  def showStatistics(stage:String) {
    benchMS = System.currentTimeMillis-startTime
    val bytesReceived = incomingDataCounter * dataString.length
    val bytesPerMs = if(benchMS>0) bytesReceived / benchMS else 0
    val missedPackets = sendDataCount - incomingDataCounter
    val missedPercentage = missedPackets.asInstanceOf[Float] / (sendDataCount/100)
    log("benchMS="+benchMS+" incomingDataCounter="+incomingDataCounter+" outgoingDataCounter="+outgoingDataCounter+" bytesReceived="+bytesReceived+" (="+incomingDataCounter+"*"+dataString.length+") "+bytesPerMs+" KB/s missedPercentage="+missedPercentage)
  }
}

