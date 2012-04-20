P2pCore - A portable peer-to-peer framework
===========================================

P2pCore is a protable peer-to-peer framework written in Scala. It enables back-to-back RSA encrypted and direct datagram socket communication between devices which are located behind firewalls. For this purpose, "UDP hole punching" as described in this document: [http://www.brynosaurus.com/pub/net/p2pnat/] is being implemented.
One major objective was to link two parties and enable communication without any user accounts being involved.
P2pCore is highly portable and can be used, for example, in Gnome/GTK and Android environments.
P2pCore uses binary message encoding.



System requirements
-------------------

To run (or build) this software the following 3rd party software needs to be installed Scala 2.9.x, JDK 1.6 and Ant.

On Debian you can install these with the following command:

    apt-get install scala ant


Building from source
--------------------

To build the project, run:

    ./make

The `make` script will run three steps:

  1. compile P2pCore.java generated by the protocol buffer compiler using Ant
  2. compile all src/*.scala classes using Scalac
  3. run ./makejar script using the jar tool to create p2pCore.jar


Running the code 
----------------

A `run` script is used to invoke provided functionality. `run` combines the required libraries (three 3rp party libraries listed below as well as this projects p2pCore.jar) to the classpath and uses the scala runtime to execute specified classes.

Two instances of the software need to run in parallel, ideally on separate machines. Running both instances on the same machine or on separate machines but inside the same network (and behind the same firewall) is possible, but that would defeat the purpose of the project. Ideally you should use two machines, on two different networks and behind different firewalls.

### RelayBase

    ./run timur.p2pCore.RelayBase
  
RelayBase can be executed without arguments. RelayBase will establish communication with another instance of RelayBase by routing all communication through a relay server. RelayBase implements two methods and has only a dozen lines of code. Method 1: `connectedThread()` is called when a p2p connection has been established. Here a single call to `send("data")` is placed. Method 2: `receiveMsgHandler(str:String)` is called when a string message arrives from the other client. If the string message is "data", the application terminates. This is what is being displayed in the console, when looking at one of the two instances:

    RelayBase relaySocket.getLocalPort=50582 relayServer=109.74.203.226 relayPort=18771y
    RelayBase receiveHandler send encrypted initialMsg='...'
    RelayBase connectedThread send='data'
    RelayBase connectedThread finished
    RelayBase receiveMsgHandler 'data'; setting relayQuitFlag
    
The two instances match by telling the relay server to connect each other with another instance by the same advertised name 'RelayBase'.

### RelayStress

    ./run timur.p2pCore.RelayStress

RelayStress works like RelayBase, but it sends "data" 5000 times before it sends "last" to signal end of communication. RelayStress has about 25 lines of code. RelayStress uses a relayed communication path like RelayBase. When you see this in the console, then all 5000 data elements have been send and also 5000 data elements from the other instance have been received:

    RelayStress relaySocket.getLocalPort=51626 relayServer=109.74.203.226 relayPort=18771
    RelayStress receiveHandler send encrypted initialMsg='...'
    RelayStress connectString finished sending; relayQuitFlag=false
    RelayStress receiveMsgHandler last; relayQuitFlag=false
    RelayStress relayExit outgoingDataCounter=5000 incomingDataCounter=5000

The two instances match by telling the relay server to connect each other with another instance by the same advertised name 'RelayStress'.

### P2pBase

    ./run timur.p2pCore.P2pBase

P2pBase works like RelayBase, but instead of using a relay server to route all communication, a direct p2p link will be established with another instance. The two instances get matched by asking the relay server to connect them with another instance with the same name 'P2pBase'. A relay server is only being used to match the two clients and to help them learn about their public adresses and port numbers. This info is required to get through any firewalls. All other communication between the clients (sending and receiving three hello strings in this case) is done client to client directly.

    P2pBase relaySocket.getLocalPort=48564 relayServer=109.74.203.226 relayPort=18771
    P2pBase receiveHandler send encrypted initialMsg='...'
    P2pBase combinedUdpAddrString this peer udpAddress=92.201.71.60:60177|192.168.1.135:60177
    P2pBase receiveMsgHandler other peer combindedUdpAddress=92.201.71.60:51556|192.168.1.135:51556
    P2pBase datagramSendThread udpIpAddr='92.201.71.60' udpPortInt=51556 connected
    P2pBase datagramSendThread udpIpAddr='192.168.1.135' udpPortInt=51556 abort
    P2pBase p2pReceiveHandler str='hello 0'
    P2pBase p2pReceiveHandler str='hello 1'
    P2pBase p2pReceiveHandler str='hello 2'


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


