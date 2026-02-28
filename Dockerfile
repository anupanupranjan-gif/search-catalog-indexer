# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S indexer && adduser -S indexer -G indexer

# Create dirs for dataset cache and DJL model cache
RUN mkdir -p /tmp/dataset /root/.djl.ai && \
    chown -R indexer:indexer /tmp/dataset

COPY --from=builder /build/target/catalog-indexer-*.jar app.jar

USER indexer

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

