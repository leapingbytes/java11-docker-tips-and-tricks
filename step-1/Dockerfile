FROM adoptopenjdk/openjdk11:jre-11.0.2.9-alpine

COPY build/libs/step-1-all.jar ${DOCKER_HOME}/app.jar

EXPOSE 8080

CMD java -jar app.jar
