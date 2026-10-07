# Build stage using Maven and Java 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8083
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8083}"]