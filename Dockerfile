FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/payment-orchestrator-*.jar app.jar
EXPOSE 8080
# preview APIs (structured concurrency, scoped values) need the flag at runtime too
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
