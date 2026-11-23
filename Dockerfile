
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw -B -DskipTests package


# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
