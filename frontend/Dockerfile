# Multi-stage build
# Stage 1: Build React app
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies with frozen lockfile for reproducible builds
RUN npm install --frozen-lockfile

# Copy source code
COPY . .

# Build React app for production
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S app && \
    adduser -u 1001 -S app -G app

# Copy built app from builder stage
COPY --from=builder /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create necessary directories and change ownership to non-root user
RUN mkdir -p /var/run/nginx && \
    chown -R app:app /usr/share/nginx/html \
    /var/cache/nginx \
    /var/log/nginx \
    /var/run/nginx

# Switch to non-root user
USER app

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:80/ || exit 1

# Run Nginx
CMD ["nginx", "-g", "daemon off;"]