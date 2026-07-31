# ============================================================
# Backend Dockerfile — Multi-stage build
# Spring Boot 4 / Java 21 (Temurin)
# ============================================================

# ---- Stage 1 : Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Télécharger les dépendances en premier (layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copier les sources et compiler
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Stage 2 : Runtime ----
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="hemodialyse-team"
LABEL version="1.0"
LABEL description="Hemodialyse Backend — Spring Boot 4"

# Utilisateur non-root pour la sécurité
RUN addgroup -S hemo && adduser -S hemo -G hemo

WORKDIR /app

# Copier le jar depuis le stage builder
COPY --from=builder /app/target/backend-*.jar app.jar

# Répertoires pour les rapports JasperReports et les données
RUN mkdir -p /app/reports /app/data && chown -R hemo:hemo /app

USER hemo

EXPOSE 8090

# Options JVM optimisées pour les conteneurs
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

