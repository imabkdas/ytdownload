# Use Eclipse Temurin 17 JRE (lightweight, ~600MB vs 1.44GB for JDK)
FROM eclipse-temurin:17-jre

# Set the working directory inside the container
WORKDIR /app

# Install required tools (yt-dlp, ffmpeg, curl for health checks)
# Minimal dependencies only
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    python3-minimal \
    python3-pip \
    ffmpeg \
    curl \
    && pip3 install yt-dlp --break-system-packages \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/*

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR file into the container
COPY target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose the port your Spring Boot app runs on (default is 8080)
EXPOSE 8080

# Health check - uses correct endpoint (/health instead of /api/test to avoid conflicts)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]