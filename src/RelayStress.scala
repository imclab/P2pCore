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

object RelayStress {
  def main(args:Array[String]): Unit = {
    new RelayStress().start
  }
}

class RelayStress extends RelayTrait {

  var incomingDataCounter = 0
  var outgoingDataCounter = 0

  override def connectedThread(connectString:String) {
    log("connectedThread start connectString='"+connectString+"' relayQuitFlag="+relayQuitFlag)
    while(!relayQuitFlag && outgoingDataCounter<5000) {
      send("data")
      outgoingDataCounter += 1
    }

    log("connectString finished sending; relayQuitFlag="+relayQuitFlag)
    send("last")
  }

  override def receiveMsgHandler(str:String) {
    if(str=="data") {
      incomingDataCounter+=1
    } else if(str=="last") {
      log("receiveMsgHandler last; relayQuitFlag="+relayQuitFlag)
      relayQuitFlag = true
    } else {
      log("receiveMsgHandler data=["+str+"] incomingDataCounter="+incomingDataCounter)
    }
  }

  override def relayExit() {
    log("relayExit outgoingDataCounter="+outgoingDataCounter+" incomingDataCounter="+incomingDataCounter)
  }
}

