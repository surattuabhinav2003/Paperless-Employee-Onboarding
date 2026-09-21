# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies resolve from pom.xml alone, so this layer is cached and a code-only
# change rebuilds in seconds instead of re-downloading the world. It has to come
# before the source is copied for that to be true.
COPY pom.xml .
RUN mvn -q -B -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.rto=60000 dependency:go-offline

COPY src ./src
# Tests are skipped on purpose: they run in development and in CI, where a failure
# can be read and acted on. Running them here would mean a slow server-side build
# that fails at deploy time with nobody watching the output.
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# Fonts, for the offer letter.
#
# The letter is rendered to PDF through docx4j and PDFBox, and both go through
# java.awt to measure and draw text. A JRE on alpine ships with no fonts and no
# fontconfig at all, so that call throws and the letter fails to generate - on the
# server only, which is the worst place to find out. Two small packages, and the
# rendering matches what a developer sees locally.
RUN apk add --no-cache fontconfig ttf-dejavu

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# MaxRAMPercentage, not -Xmx: the JVM otherwise sizes the heap from the host's
# total memory and ignores the container limit, then gets killed by the OOM
# reaper when it grows into it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
