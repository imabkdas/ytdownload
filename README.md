# YouTube Downloader

A full-stack application for downloading YouTube videos and audio with quality selection and progress tracking.

## Features

- **Video Download**: Download YouTube videos in MP4 format (HQ, 720p, 480p)
- **Audio Download**: Extract audio and convert to MP3
- **Quality Selection**: Choose video quality based on your needs
- **Progress Tracking**: Real-time download progress updates
- **Rate Limiting**: 10 requests per 60 seconds per IP (prevents abuse)
- **Error Handling**: Comprehensive error handling and cleanup
- **Production Ready**: Optimized for AWS ECS deployment

## Tech Stack

### Backend
- Spring Boot 3.3.2
- Java 17
- yt-dlp (video downloading)
- ffmpeg (audio conversion)
- Resilience4j (rate limiting)

### Frontend
- React 18.3.1
- React Router 6
- Axios (HTTP client)
- Nginx (production server)

## Project Structure

```
ytdownload/
├── backend/
│   ├── Dockerfile           # Multi-stage build
│   ├── pom.xml             # Maven dependencies
│   ├── mvnw                # Maven wrapper
│   └── src/                # Java source code
│
├── frontend/
│   ├── Dockerfile          # Multi-stage build (~85MB final)
│   ├── nginx.conf          # Nginx configuration
│   ├── package.json        # Node dependencies
│   └── src/                # React components
│
└── docker-compose.yml      # Local testing
```

## Quick Start (Local Development)

### Prerequisites
- Docker & Docker Compose
- OR: Java 17, Node.js 20, yt-dlp, ffmpeg

### Option 1: Docker Compose (Recommended)
```bash
docker-compose up -d
# Frontend: http://localhost:3000
# Backend: http://localhost:8080
```

### Option 2: Local Development
```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend (in another terminal)
cd frontend
npm install
npm start
```

## API Endpoints

### Audio Download
```
POST /api/audio/download
Parameters:
  - url: YouTube URL
  - format: "mp3"

Example:
curl -X POST "http://localhost:8080/api/audio/download?url=https://www.youtube.com/watch?v=..." \
  -o audio.mp3
```

### Video Download
```
POST /api/video/download
Parameters:
  - url: YouTube URL
  - format: "mp4"
  - quality: "HQ", "720", or "480"

Example:
curl -X POST "http://localhost:8080/api/video/download?url=...&format=mp4&quality=720" \
  -o video.mp4
```

### Health Check
```
GET /health

Response:
{"status":"UP"}
```

## Deployment

### AWS ECS Fargate
See `DEPLOYMENT_GUIDE.md` for step-by-step instructions.

**Estimated Setup Time**: 30-60 minutes  
**Estimated Monthly Cost**: $50-100

### Docker Build
```bash
# Backend
docker build -f backend/Dockerfile -t ytdownload-backend:latest .

# Frontend
docker build -f frontend/Dockerfile -t ytdownload-frontend:latest .
```

### Push to Registry
```bash
docker tag ytdownload-backend:latest your-registry/ytdownload-backend:latest
docker tag ytdownload-frontend:latest your-registry/ytdownload-frontend:latest

docker push your-registry/ytdownload-backend:latest
docker push your-registry/ytdownload-frontend:latest
```

## Configuration

### Backend Environment Variables
```
SERVER_PORT=8080
LOG_LEVEL=INFO
PROCESS_TIMEOUT_MINUTES=30
RATE_LIMIT_REQUESTS=10
RATE_LIMIT_PERIOD=60
```

### Frontend Configuration
- Automatically connects to backend at configured URL
- No environment variables required (uses relative paths)

## Security

- ✅ Rate limiting (10 req/60s per IP)
- ✅ Input validation
- ✅ Security headers (X-Frame-Options, X-Content-Type-Options, etc.)
- ✅ CORS configured
- ✅ Non-root Docker users
- ✅ No hardcoded secrets

## Monitoring

### Health Checks
- Backend: `GET /health`
- Frontend: `GET /` (returns index.html)

### Logs
- All operations logged to stdout (CloudWatch compatible)
- SLF4J with Logback configuration
- Error stack traces included for debugging

### Metrics
Monitor in CloudWatch:
- Request count
- Error rate
- Response time
- Disk usage (temp files)

## Development

### Adding Features
1. Backend: Add service in `src/main/java/com/backend/ytdownload/service/`
2. Frontend: Add component in `frontend/src/components/`
3. Test locally with Docker Compose
4. Rebuild Docker images
5. Deploy to staging first

### Testing
- Backend: Unit tests in `backend/src/test/`
- Frontend: Component tests (add as needed)
- Integration: Use Docker Compose locally

## Documentation

- **DEPLOYMENT_GUIDE.md** - AWS ECS deployment instructions
- **DEPLOYMENT_STATUS.md** - Current deployment status
- **PRODUCTION_READINESS.md** - Pre-deployment checklist
- **DOCKERFILE_ARCHITECTURE.md** - Docker optimization details

## Troubleshooting

### Backend won't start
```bash
# Check if port 8080 is in use
lsof -i :8080

# Check logs
docker logs ytdownload-backend
```

### Frontend won't load
```bash
# Check if port 80/3000 is in use
lsof -i :80
lsof -i :3000

# Check logs
docker logs ytdownload-frontend
```

### Download fails
- Check URL is valid YouTube video
- Check internet connection
- Check rate limiting (10 req/60s per IP)
- Check logs for specific error messages

## License

This project is provided as-is for educational and personal use.

## Support

For issues or questions, refer to:
1. Logs in Docker containers or local terminal
2. DEPLOYMENT_GUIDE.md for deployment issues
3. PRODUCTION_READINESS.md for pre-deployment checklist

---

**Status**: ✅ Production Ready  
**Last Updated**: December 9, 2025
