FROM openjdk:11-slim

ARG JAR_FILE=census-rm-case-processor*.jar
COPY target/$JAR_FILE /opt/census-rm-case-processor.jar

COPY healthcheck.sh /opt/healthcheck.sh
RUN chmod +x /opt/healthcheck.sh

CMD exec /usr/bin/java $JAVA_OPTS -jar /opt/census-rm-case-processor.jar
