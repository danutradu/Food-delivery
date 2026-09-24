FROM eclipse-temurin:21-jre

ARG JAR_FILE
ARG PORT

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser

WORKDIR /app

COPY ${JAR_FILE} app.jar

EXPOSE ${PORT}

USER appuser

# Avro 1.12 validates generated specific-record classes during JSON and Kafka
# serialization. All application events live under the fd package.
ENV JAVA_TOOL_OPTIONS="-Dorg.apache.avro.SERIALIZABLE_PACKAGES=fd"

ENTRYPOINT ["java", "-jar", "app.jar"]
