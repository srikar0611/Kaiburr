# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the JAR file into the container
COPY target/backend-1.0.0.jar app.jar

# Expose port 9090
EXPOSE 9090

# Run the application
CMD ["java", "-jar", "app.jar"]
