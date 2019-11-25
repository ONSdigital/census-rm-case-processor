FROM openjdk:11-jdk-slim

ARG JAR_FILE=census-rm-case-processor*.jar

CMD ["/usr/local/openjdk-11/bin/java", "-jar", "/opt/census-rm-case-processor.jar"]
COPY healthcheck.sh /opt/healthcheck.sh
RUN chmod +x /opt/healthcheck.sh
RUN groupadd --gid 999 caseprocessor && \
    useradd --create-home --system --uid 999 --gid caseprocessor caseprocessor
USER caseprocessor

COPY target/$JAR_FILE /opt/census-rm-case-processor.jar