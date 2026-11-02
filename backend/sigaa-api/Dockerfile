FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# The PostgreSQL image provides a pg_dump client matching the database image.
# Java is copied in so the backup feature and the API live in one runtime image.
FROM eclipse-temurin:21-jre-jammy AS jre

FROM postgres:18-bookworm
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig libreoffice-core libreoffice-writer \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 sigaa \
    && useradd --uid 10001 --gid 10001 --create-home --shell /usr/sbin/nologin sigaa \
    && install -d -m 0750 -o sigaa -g sigaa /app/uploads /var/lib/sigaa/backups /var/lib/sigaa/backups-external \
    && install -d -m 0755 /usr/local/share/fonts/sigaa
WORKDIR /app
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:${PATH} \
    SPRING_PROFILES_ACTIVE=prod \
    BACKUP_PG_DUMP=/usr/local/bin/pg_dump \
    BACKUP_DIR=/var/lib/sigaa/backups \
    LIBREOFFICE_COMMAND=/usr/bin/soffice \
    HOME=/home/sigaa \
    XDG_CONFIG_HOME=/tmp/sigaa-config \
    XDG_CACHE_HOME=/tmp/sigaa-cache
COPY --from=build /build/target/sigaa-api-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080 8081
USER 10001:10001
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
