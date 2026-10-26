# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# tests need a live DB/Redis, so they are skipped inside the image build
RUN mvn -q clean package -DskipTests

# ---- Stage 2: small runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser \
    && mkdir -p /app/logs /app/secrets \
    && chown -R appuser /app
COPY --from=build --chown=appuser /build/target/nhcdc-chatbot-0.0.1-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
