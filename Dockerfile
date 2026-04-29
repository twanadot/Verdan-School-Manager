# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Package the application (skipping tests since they were run locally)
RUN mvn clean package -DskipTests

# Stage 2: Create a lightweight runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create data directory
RUN mkdir -p /app/data

# Copy the built jar file from the build stage - assuming it's an uber-jar or we use dependency plugin
# In this Maven project, the dependencies are copied to target/lib via maven-dependency-plugin
COPY --from=build /app/target/verdan-0.1.0.jar /app/app.jar
COPY --from=build /app/target/lib /app/lib

# Expose the Javalin port
EXPOSE 7070

# Database connection is configured via environment variables (DB_HOST, DB_USER, DB_PASS)

# Run the API server directly (the standalone main class)
CMD ["java", "-cp", "/app/app.jar:/app/lib/*", "no.example.verdan.api.ApiServer"]
