P2P-Core - Peer-to-peer framework
=================================

P2P-Core is a Peer-to-peer framework written in Scala. 
It enables back-to-back RSA encrypted, direct datagram socket communication between devices behind firewalls. 
One major objective was to allow for account-less usage.
P2P-Core framework is made for portability. 
It is, for example, possible to use it for Gnome/GTK and Android projects.
P2P-Core uses binary encoding, no XML.

--- WORK IN PROCESS --- PROJECT WILL BE RELEASED April 23 2012 ---


Building from source
--------------------

Scala 2.9.x, JDK 1.6 and Ant need to be installed:

    apt-get install scala ant

To build the project, run:

    ./make

The `make` script will run three steps:

  1. compile P2pCore.java generated by the protocol buffer compiler using Ant
  2. compile all src/*.scala classes using Scalac
  3. run ./makejar script using the jar tool to create p2pCore.jar

Running the code 
----------------

A `run` script is used to execture and test different functionality. It adds the newly built p2pCore.jar and the three 3rp party libraries mentioned below to the classpath and uses the scala runtime to execute the specified classes.

Two instances of the software need to be run in parallel, either on separate machines or in separate terminals. (Running both instances on the same machine does probably mean they will be running inside the same network and behind the same firewall. It is possible to run this software in this way, but it does defeat the purpose of the project. Ideally you will use two machines running in two different networks and behind different firewalls.)

### RelayBase

    ./run timur.p2pCore.RelayBase
  
RelayBase can be executed without arguments. It communicates with another instance through the relay server. RelayBase implements two methods and has only a dozen lines of code. Method 1: `connectedThread()` is called when a p2p connection via relay server has been established. Here we simply call `send("data")`. Method 2: `receiveMsgHandler(str:String)` is called when a string message arrives from the other client. If the string message is "data", we quit the application. This is what is being displayed in the console, when looking at one instance:

    RelayBase relaySocket.getLocalPort=50582 relayServer=109.74.203.226 relayPort=18771
    RelayBase receiveHandler send encrypted initialMsg='...'
    RelayBase connectedThread send='data'
    RelayBase connectedThread finished
    RelayBase receiveMsgHandler 'data'; setting relayQuitFlag
    
How do the two instance get matched? Both instances simply look for an instance with the advertised name 'RelayBase'.

### RelayStress

    ./run timur.p2pCore.RelayStress

RelayStress works just like RelayBase. But it sends 5000 "data" strings, instead of one. When you see this in the console, then all 5000 elements have been send and another 5000 elements have been received:

    RelayStress relaySocket.getLocalPort=51626 relayServer=109.74.203.226 relayPort=18771
    RelayStress receiveHandler send encrypted initialMsg='...'
    RelayStress connectString finished sending; relayQuitFlag=false
    RelayStress receiveMsgHandler last; relayQuitFlag=false
    RelayStress relayExit outgoingDataCounter=5000 incomingDataCounter=5000

### RelayEncrypt

    ./run timur.p2pCore.RelayEncrypted

RelayEncrypt works just like RelayBase. But it encrypts it's data using the other parties public RSA key.
(todo:) 


### P2pBase

    ./run timur.p2pCore.P2pBase

(todo:) 


### P2pBench

    ./run timur.p2pCore.P2pBench

(todo:) # sudo sysctl -w net.core.rmem_max=1048576 net.core.rmem_default=1048576


### P2pEncrypt (key fingerprint matching)

    ./run timur.p2pCore.P2pEncrypt keysAlice bob
    ./run timur.p2pCore.P2pEncrypt keysBob alice

(todo:) # key-folder-path remote-public-key-name


### P2pEncrypt (Rendesvouz string matching)

    ./run timur.p2pCore.P2pEncrypt keysAlice bob rendesvouz
    ./run timur.p2pCore.P2pEncrypt keysBob alice rendesvouz

(todo:) # key-folder-path remote-public-key-name rendevouz-string



More info
---------

- protobuf folder 
  (todo:) 

- bouncy-jarjar and bcprov-jdk15on-147.jar 
  (todo:) 

- keys* folders 
  (todo:) 

- getjars 
  (todo:) 

- relaykey.pub 
  (todo:) 

- the role of the relayserver 
  (todo:) 


License
-------

Source code is licensed under the GNU General Public License, Version 3

Copyright (C) 2012 timur.mehrvarz@gmail.com

3rd party libraries being used:

- Bouncycastele

- Google protobuf

- Apache commons-codec

- JarJar


Todo
----

- RelayEncrypt out-of-date needs fix; still using "keys/key0" "keys/key0.pub"
- ./run timur.p2pCore.RelayBench needs fix: "print duration time"


