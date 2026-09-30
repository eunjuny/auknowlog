FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system auknowlog && useradd --system --gid auknowlog --home /app auknowlog
COPY --from=build --chown=auknowlog:auknowlog /workspace/build/libs/*.jar /app/auknowlog.jar
USER auknowlog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/auknowlog.jar"]
