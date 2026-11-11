# ============================================================
# Backend Dockerfile — Multi-stage build (modulaire)
# Spring Boot 4 / Java 21 (Temurin)
# ============================================================

# ---- Stage 1 : Dependencies (cache Maven multi-modules) ----
FROM maven:3.9-eclipse-temurin-21-alpine AS deps

WORKDIR /workspace

# Copier uniquement les descripteurs Maven pour maximiser le cache
COPY pom.xml ./pom.xml
COPY shared-kernel/pom.xml ./shared-kernel/pom.xml
# Contextes refaits sur place (ADR 0024/0025) : un module Maven par contexte, déclaré dans le pom racine
COPY contexts/shared-kernel/pom.xml ./contexts/shared-kernel/pom.xml
COPY contexts/gmao/pom.xml ./contexts/gmao/pom.xml
COPY contexts/comptabilite/pom.xml ./contexts/comptabilite/pom.xml
COPY contexts/facturation/pom.xml ./contexts/facturation/pom.xml
COPY contexts/absences-patients/pom.xml ./contexts/absences-patients/pom.xml
COPY contexts/stock/pom.xml ./contexts/stock/pom.xml
COPY contexts/planification/pom.xml ./contexts/planification/pom.xml
COPY contexts/organisation/pom.xml ./contexts/organisation/pom.xml
COPY contexts/patient-couverture/pom.xml ./contexts/patient-couverture/pom.xml
COPY contexts/dossier-medical/pom.xml ./contexts/dossier-medical/pom.xml
COPY contexts/seance/pom.xml ./contexts/seance/pom.xml
COPY contexts/pilotage-direction/pom.xml ./contexts/pilotage-direction/pom.xml
COPY platform/pom.xml ./platform/pom.xml

RUN mvn -B -q -DskipTests dependency:go-offline

# ---- Stage 2 : Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

COPY --from=deps /root/.m2 /root/.m2
COPY . .

# Compile uniquement le module exécutable + ses dépendances inter-modules
RUN mvn -B -q -DskipTests -pl platform -am clean package

# ---- Stage 3 : Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="hemodialyse-team"
LABEL version="1.1"
LABEL description="Hemodialyse Backend (modulaire) - Spring Boot 4"

# Utilisateur non-root pour la sécurité
RUN addgroup -S hemo && adduser -S hemo -G hemo

WORKDIR /app

# Jar exécutable du module platform
COPY --from=builder /workspace/platform/target/platform-*.jar /app/app.jar

# Répertoires runtime
RUN mkdir -p /app/reports /app/data && chown -R hemo:hemo /app

USER hemo

# Render injecte PORT, fallback local à 8090
ENV PORT=8090
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT}"]
