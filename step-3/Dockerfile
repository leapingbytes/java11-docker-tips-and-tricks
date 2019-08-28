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
