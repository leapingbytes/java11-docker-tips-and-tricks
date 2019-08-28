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

