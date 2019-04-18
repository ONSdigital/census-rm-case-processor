FROM openjdk:11-slim

ARG JAR_FILE=census-rm-case-processor*.jar
COPY target/$JAR_FILE /opt/census-rm-case-processor.jar

CMD exec /usr/bin/java $JAVA_OPTS -jar /opt/census-rm-case-processor.jar
