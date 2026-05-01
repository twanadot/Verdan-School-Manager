# Stage 1: Build the application using Maven Wrapper
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven Wrapper and project files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src ./src

# Make wrapper executable and package
RUN chmod +x mvnw && ./mvnw clean package -B -DskipTests

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
EXPOSE 8080

# Database connection is configured via environment variables (DB_HOST, DB_USER, DB_PASS)

# Run the API server directly (the standalone main class)
CMD ["java", "-cp", "/app/app.jar:/app/lib/*", "no.example.verdan.api.ApiServer"]
