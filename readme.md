# Dockerizing Java 11

As we move our java based services to Java 11 we run into a problem with size of docker images. 
This post documents progress of our quest for reasonably sized Java 11 docker image.

**NOTE** You can run `run.sh` script from the root of this repo to build and run all example images.

## Step 0 (openjdk:11-jre)

To be honest, this was **not** step 0 for us. We have heard about alpine. 
But based on googling, many try to do Java 11 docker this way

```dockerfile
FROM openjdk:11-jre

COPY build/libs/step-0-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
``` 
The problem, resulting image is wooping 267MB!

## Step 1 (openjdk11:jre-11.0.2.9-alpine)

Luckily for us, adoptopenjdk folks, have published [alpine based image](https://hub.docker.com/r/adoptopenjdk/openjdk11). 
And this is where we really started from

```dockerfile
FROM adoptopenjdk/openjdk11:jre-11.0.2.9-alpine

COPY build/libs/step-1-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
```

Result was much much better... but still at 137MB it was far from what we were aiming for.

## Step 2 (zulu-openjdk-alpine:11.0.3-jre)

We had a good fun with docker images [provided by azul crew](https://hub.docker.com/r/azul/zulu-openjdk/).
So we decided to give it a try:

```dockerfile
FROM azul/zulu-openjdk-alpine:11.0.3-jre

COPY build/libs/step-2-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
```  

It did result in slight improvement ( 130MB vs 137MB ), but still fall short of our expectations.

## Step 3 (JLink)

Clearly it was time to get serious... and try to use *secret weapon* of Java 9+ - [jlink](https://docs.oracle.com/javase/9/tools/jlink.htm)

```dockerfile
FROM azul/zulu-openjdk-alpine:11.0.3 AS jlink

ENV JAVA_MODULES="java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.rmi,java.naming,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.unsupported,java.instrument"

RUN jlink --compress=2 --no-header-files --no-man-pages --strip-debug \
    --module-path $JAVA_HOME/jmods \
    --add-modules ${JAVA_MODULES} \
    --output /jlinked

FROM alpine

COPY --from=jlink /jlinked /opt/jlinked/
ENV JAVA_HOME=/opt/jlinked
ENV PATH=$JAVA_HOME/bin:$PATH

COPY build/libs/step-3-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
```  

Results were quite dramatic! 57MB is very respectable docker image size... something we definitely can live with.

**NOTE** See [note below](#how-to-use-jdeps) about **JAVA_MODULES** and use of **jdeps**

## Step 4 (jvm only alpine)

Below 60MB is where we wanted to be, but as always I have tried to squeeze some more out of the process.
When I looked at alpine image I have noticed that the biggest file is `libcrypto.so.1.1` (it takes almost half of the image, 2.6MB out of ~5MB). 
I was pretty sure that Java does it's own crypto... so this file could be un-used. 

Result of application of this theory looks like this:

```dockerfile
FROM azul/zulu-openjdk-alpine:11.0.3 AS jlink

ENV JAVA_MODULES="java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.rmi,java.naming,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.unsupported,java.instrument"

RUN jlink --compress=2 --no-header-files --no-man-pages --strip-debug \
    --module-path $JAVA_HOME/jmods \
    --add-modules ${JAVA_MODULES} \
    --output /jlinked

FROM alpine:3.10 as jvm_only_alpine

RUN rm -f /lib/libcrypto.so.1.1 /lib/libssl.so.1.1
RUN touch /tmp/placeholder

#
# Create app docker image
#
FROM scratch

ENV DOCKER_HOME=/opt/customer
WORKDIR ${DOCKER_HOME}

COPY --from=jvm_only_alpine /lib/* /lib/
COPY --from=jvm_only_alpine /tmp/* /tmp/

COPY --from=jlink /jlinked /opt/jlinked/

COPY build/libs/step-4-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD [ "/opt/jlinked/bin/java", "-jar", "app.jar" ]
```

Using this trick, allowed us to shrink docker image to 52.8MB.

## Step 5 (have to have New Relic)

Steps 0 to 4 were all about shrinking the size of docker image... but to make this toy example to resemble 
*real* app, we need to add few things. The most important, we need to add NR agent.

```dockerfile
FROM azul/zulu-openjdk-alpine:11.0.3 AS jlink

RUN wget -q -O /tmp/newrelic-java.zip "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/5.3.0/newrelic-java.zip"
RUN cd /tmp && unzip /tmp/newrelic-java.zip

ENV JAVA_MODULES="java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.rmi,java.naming,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.unsupported,java.instrument"

RUN jlink --compress=2 --no-header-files --no-man-pages --strip-debug \
    --module-path $JAVA_HOME/jmods \
    --add-modules ${JAVA_MODULES} \
    --output /jlinked

FROM alpine:3.10 as jvm_only_alpine

RUN rm -f /lib/libcrypto.so.1.1 /lib/libssl.so.1.1
RUN touch /tmp/placeholder

#
# Create app docker image
#
FROM scratch

ENV DOCKER_HOME=/opt/customer
WORKDIR ${DOCKER_HOME}

COPY --from=jvm_only_alpine /lib/* /lib/
COPY --from=jvm_only_alpine /tmp/* /tmp/

COPY --from=jlink /tmp/newrelic/newrelic.jar /tmp/newrelic/newrelic.yml /opt/customer/newrelic/

COPY --from=jlink /jlinked /opt/jlinked/

COPY build/libs/step-5-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD [ "/opt/jlinked/bin/java", "-javaagent:/opt/customer/newrelic/newrelic.jar", "-jar", "app.jar" ]
```

It pains to see our image grow... but NR is a must. It "cost" ~12MB... NR enchanced image end up weighting 64.5MB

# Step 6 (HTTPS)

Most of the services would run happily in docker container build in Step 5... with one exception. 
If your service needs to open HTTPS connection, it would fail with message like this:

```java
Exception in thread "main" javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
	at java.base/sun.security.ssl.Alert.createSSLException(Unknown Source)
	at java.base/sun.security.ssl.Alert.createSSLException(Unknown Source)
	at java.base/sun.security.ssl.TransportContext.fatal(Unknown Source)
	at java.base/sun.security.ssl.Alert$AlertConsumer.consume(Unknown Source)
	at java.base/sun.security.ssl.TransportContext.dispatch(Unknown Source)
	at java.base/sun.security.ssl.SSLTransport.decode(Unknown Source)
	at java.base/sun.security.ssl.SSLSocketImpl.decode(Unknown Source)
	at java.base/sun.security.ssl.SSLSocketImpl.readHandshakeRecord(Unknown Source)
	at java.base/sun.security.ssl.SSLSocketImpl.startHandshake(Unknown Source)
	at java.base/sun.net.www.protocol.https.HttpsClient.afterConnect(Unknown Source)
	at java.base/sun.net.www.protocol.https.AbstractDelegateHttpsURLConnection.connect(Unknown Source)
	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream0(Unknown Source)
	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream(Unknown Source)
	at java.base/sun.net.www.protocol.https.HttpsURLConnectionImpl.getInputStream(Unknown Source)
	at java.base/java.net.URL.openStream(Unknown Source)
	at java11.docker.tips.and.tricks.Main.main(Main.java:18)
```

This is happening because most of Crypto related functionality is "dynamically linked". 
At runtime code looks up providers to use. Jlink needs to be told to perform this "look up" and package 
all discovered providers into result JVM. This could be done with option `--bind-services`. 
So final docker file looks like this:

```dockerfile
FROM azul/zulu-openjdk-alpine:11.0.3 AS jlink

RUN wget -q -O /tmp/newrelic-java.zip "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/5.3.0/newrelic-java.zip"
RUN cd /tmp && unzip /tmp/newrelic-java.zip

ENV JAVA_MODULES="java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.rmi,java.naming,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.unsupported,java.instrument"

RUN jlink --compress=2 --no-header-files --no-man-pages --strip-debug \
    --bind-services \
    --module-path $JAVA_HOME/jmods \
    --add-modules ${JAVA_MODULES} \
    --output /jlinked

FROM alpine:3.10 as jvm_only_alpine

RUN rm -f /lib/libcrypto.so.1.1 /lib/libssl.so.1.1
RUN touch /tmp/placeholder

#
# Create app docker image
#
FROM scratch

ENV DOCKER_HOME=/opt/customer
WORKDIR ${DOCKER_HOME}

COPY --from=jvm_only_alpine /lib/* /lib/
COPY --from=jvm_only_alpine /tmp/* /tmp/

COPY --from=jlink /tmp/newrelic/newrelic.jar /tmp/newrelic/newrelic.yml /opt/customer/newrelic/

COPY --from=jlink /jlinked /opt/jlinked/

COPY build/libs/step-6-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD [ "/opt/jlinked/bin/java", "-javaagent:/opt/customer/newrelic/newrelic.jar", "-jar", "app.jar" ]
```

The privelege to be able to use HTTPS come at serious cost: extra 20MB (final image size: 85.7MB)

## How to use jdeps

Dockerfile from Step 3 contains following line:

```dockerfile
ENV JAVA_MODULES="java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.rmi,java.naming,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.unsupported,java.instrument"
```

This is list of modules we want to include into out customer JVM. It is reasonable to ask where this list come from?
To buld this list we used another command line tool - [jdeps](https://docs.oracle.com/javase/9/tools/jdeps.htm)

Theoretically, it should be possible to run

```bash
jdeps --print-module-deps <path to jar file>
```

to get comma separated list of modules, ready to be used with `jlink`...

Unfortunatelly, it does not work very well...  more often than not, you end up with error like this one:

```dockerfile
jdeps --print-module-deps build/libs/winloss-rmp-0.1.0-local-all.jar
 
Exception in thread "main" java.lang.NullPointerException
	at jdk.jdeps/com.sun.tools.jdeps.ModuleGraphBuilder.requiresTransitive(ModuleGraphBuilder.java:124)
	at jdk.jdeps/com.sun.tools.jdeps.ModuleGraphBuilder.buildGraph(ModuleGraphBuilder.java:110)
	at jdk.jdeps/com.sun.tools.jdeps.ModuleGraphBuilder.reduced(ModuleGraphBuilder.java:65)
	at jdk.jdeps/com.sun.tools.jdeps.ModuleExportsAnalyzer.modules(ModuleExportsAnalyzer.java:124)
	at jdk.jdeps/com.sun.tools.jdeps.ModuleExportsAnalyzer.run(ModuleExportsAnalyzer.java:97)
	at jdk.jdeps/com.sun.tools.jdeps.JdepsTask$ListModuleDeps.run(JdepsTask.java:1023)
	at jdk.jdeps/com.sun.tools.jdeps.JdepsTask.run(JdepsTask.java:560)
	at jdk.jdeps/com.sun.tools.jdeps.JdepsTask.run(JdepsTask.java:519)
	at jdk.jdeps/com.sun.tools.jdeps.Main.main(Main.java:49)
```

So as an alternative, we can use different option `--list-deps` which produces something like this:

```dockerfile
jdeps --list-deps build/libs/winloss-rmp-0.1.0-local-all.jar 
   JDK removed internal API/sun.reflect
   java.base
   java.desktop
   java.logging
   java.management
   java.naming
   java.security.jgss
   java.security.sasl
   java.sql
   java.transaction.xa
   java.xml
   jdk.unsupported
```

and this is were we can get the list of modules from. 

**NOTE** even with `--list-deps` option jdeps not always produce correct result. In particular, it seems that 
it has difficulties with services which use mysql. If this is the case for your service... 
just use the list of modules we used in this repo. It should allow you to run Spark + Postgresql services.  

