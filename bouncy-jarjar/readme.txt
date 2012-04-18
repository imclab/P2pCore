run "ant" on the provided "build.xml"

jarjar is being used to change the package namespace of the original bcprov-jdk15on-147.jar
from org.bouncycastle.* to ext.org.bouncycastle.*

afterwards remove the .DSA and .SF entries from /META-INF/ inside the new bcprov-jdk15on-147-ext.jar

the copy bcprov-jdk15on-147-ext.jar to ../lib/

see: http://stackoverflow.com/questions/999489/invalid-signature-file-when-attempting-to-run-a-jar
see: http://www.unwesen.de/2011/06/12/encryption-on-android-bouncycastle/
