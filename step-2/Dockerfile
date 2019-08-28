FROM azul/zulu-openjdk-alpine:11.0.3-jre

COPY build/libs/step-2-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
