# Use Eclipse Temurin 17 as the base image
FROM imabkanand/ytdownload-service:latest

# Set the working directory inside the container
WORKDIR /app

# Install required tools

# Copy the built JAR file into the container
COPY target/*.jar app.jar

# Expose the port your Spring Boot app runs on (default is 8080)
EXPOSE 8080

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]