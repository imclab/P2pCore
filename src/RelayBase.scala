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

object RelayBase {
  def main(args:Array[String]): Unit = {
    new RelayBase().start
  }
}

class RelayBase extends RelayTrait {

  override def connectedThread(connectString:String) {
    val msg = "data"
    log("connectedThread send='"+msg+"'")
    send(msg)
    log("connectedThread finished")
  }

  override def receiveMsgHandler(str:String) {
    if(str=="data") {
      log("receiveMsgHandler 'data'; setting relayQuitFlag")
      relayQuitFlag = true
      return
    }

    log("receiveMsgHandler str=["+str+"]")
  }
}

