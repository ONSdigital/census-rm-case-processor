FROM openjdk:11-slim

ARG JAR_FILE=census-rm-casesvc-v2*.jar
COPY target/$JAR_FILE /opt/census-rm-casesvc-v2.jar

CMD exec /usr/bin/java $JAVA_OPTS -jar /opt/census-rm-casesvc-v2.jar
