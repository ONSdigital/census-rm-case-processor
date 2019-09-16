FROM openjdk:11-jdk-slim

ARG JAR_FILE=census-rm-case-processor*.jar
COPY target/$JAR_FILE /opt/census-rm-case-processor.jar

COPY healthcheck.sh /opt/healthcheck.sh
RUN chmod +x /opt/healthcheck.sh

CMD exec /usr/local/openjdk-11/bin/java $JAVA_OPTS -jar /opt/census-rm-case-processor.jar
