FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
RUN ./gradlew --version

COPY src src
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
