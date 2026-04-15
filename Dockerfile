FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=census-rm-caseprocessor*.jar
CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/census-rm-caseprocessor.jar"]
COPY healthcheck.sh /opt/healthcheck.sh
RUN addgroup --gid 1000 caseprocessor && \
    adduser --system --uid 1000 caseprocessor caseprocessor
USER caseprocessor

COPY target/$JAR_FILE /opt/census-rm-caseprocessor.jar
