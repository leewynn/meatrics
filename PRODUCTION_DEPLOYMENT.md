# Production Deployment Guide

This guide explains how to build and run the meatrics application in production mode.

## Building for Production

### Step 1: Build with Production Profile

The application must be built with the `production` Maven profile to enable Vaadin production mode:

```bash
mvn clean package -Pproduction
```

**What this does:**
- Compiles and optimizes frontend resources
- Minifies JavaScript and CSS
- Creates optimized Vaadin bundles
- Disables Vaadin development server
- Packages everything into a single JAR file

**Output:** `target/meatrics-1.0.0.jar` (production-ready)

### Step 2: Verify Production Build

Check that the JAR was built correctly:
```bash
ls -lh target/*.jar
```

You should see a JAR file around 100-150MB (includes all dependencies).

## Running in Production Mode

### Option 1: Using Environment Variable (Recommended)

```bash
export VAADIN_PRODUCTION_MODE=true
export VAADIN_LAUNCH_BROWSER=false
export LOG_PATH=/var/log/meatrics
export DB_HOST=your-db-host
export DB_USERNAME=your-db-user
export DB_PASSWORD=your-db-password

java -jar meatrics-1.0.0.jar
```

### Option 2: Using System Properties

```bash
java -Dvaadin.productionMode=true \
     -Dvaadin.launch-browser=false \
     -DLOG_PATH=/var/log/meatrics \
     -DDB_HOST=your-db-host \
     -DDB_USERNAME=your-db-user \
     -DDB_PASSWORD=your-db-password \
     -jar meatrics-1.0.0.jar
```

### Option 3: Using Spring Profile

Create `application-prod.properties`:
```properties
vaadin.productionMode=true
vaadin.launch-browser=false
vaadin.closeIdleSessions=true
logging.level.root=WARN
logging.level.com.meatrics=INFO
```

Run with:
```bash
java -Dspring.profiles.active=prod -jar meatrics-1.0.0.jar
```

## Production Mode Checklist

When running in production, ensure:

- ✅ Built with `-Pproduction` Maven profile
- ✅ `vaadin.productionMode=true`
- ✅ `vaadin.launch-browser=false`
- ✅ Database credentials configured
- ✅ Log path configured
- ✅ Sufficient memory allocated (see JVM settings below)
- ✅ Firewall allows port 8080 (or custom port)

## JVM Settings for Production

### Recommended JVM Flags

```bash
java -Xms512m \
     -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -Dvaadin.productionMode=true \
     -DLOG_PATH=/var/log/meatrics \
     -jar meatrics-1.0.0.jar
```

**Explanation:**
- `-Xms512m` - Initial heap size (512MB)
- `-Xmx2g` - Maximum heap size (2GB)
- `-XX:+UseG1GC` - Use G1 garbage collector (good for server apps)
- `-XX:MaxGCPauseMillis=200` - Target max GC pause time

### Memory Requirements

| Users | Recommended Heap | Recommended Total RAM |
|-------|------------------|----------------------|
| 1-10  | 512MB-1GB       | 2GB                  |
| 10-50 | 1GB-2GB         | 4GB                  |
| 50+   | 2GB-4GB         | 8GB                  |

## Running as a Service

### Linux (systemd)

Create `/etc/systemd/system/meatrics.service`:

```ini
[Unit]
Description=Meatrics Pricing Application
After=network.target postgresql.service

[Service]
Type=simple
User=meatrics
WorkingDirectory=/opt/meatrics
Environment="VAADIN_PRODUCTION_MODE=true"
Environment="VAADIN_LAUNCH_BROWSER=false"
Environment="LOG_PATH=/var/log/meatrics"
Environment="DB_HOST=localhost"
Environment="DB_USERNAME=meatrics"
Environment="DB_PASSWORD=your-password"
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/meatrics/meatrics-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable meatrics
sudo systemctl start meatrics
sudo systemctl status meatrics
```

View logs:
```bash
sudo journalctl -u meatrics -f
```

### Windows Service

Use [WinSW](https://github.com/winsw/winsw) or [NSSM](https://nssm.cc/) to create a Windows service.

Example with NSSM:
```cmd
nssm install Meatrics "C:\Program Files\Java\jdk-17\bin\java.exe"
nssm set Meatrics AppParameters "-Dvaadin.productionMode=true -jar C:\meatrics\meatrics-1.0.0.jar"
nssm set Meatrics AppDirectory "C:\meatrics"
nssm set Meatrics AppEnvironmentExtra "VAADIN_PRODUCTION_MODE=true" "LOG_PATH=C:\logs\meatrics"
nssm start Meatrics
```

## Reverse Proxy Setup (Recommended)

### Nginx

```nginx
server {
    listen 80;
    server_name meatrics.example.com;

    # Redirect to HTTPS
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name meatrics.example.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support (for Vaadin Push)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### Apache

```apache
<VirtualHost *:80>
    ServerName meatrics.example.com
    Redirect permanent / https://meatrics.example.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName meatrics.example.com

    SSLEngine on
    SSLCertificateFile /path/to/cert.pem
    SSLCertificateKeyFile /path/to/key.pem

    ProxyPreserveHost On
    ProxyPass / http://localhost:8080/
    ProxyPassReverse / http://localhost:8080/

    # WebSocket support
    RewriteEngine on
    RewriteCond %{HTTP:Upgrade} websocket [NC]
    RewriteCond %{HTTP:Connection} upgrade [NC]
    RewriteRule ^/?(.*) "ws://localhost:8080/$1" [P,L]
</VirtualHost>
```

## Docker Deployment

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven
RUN mvn clean package -Pproduction -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create log directory
RUN mkdir -p /app/logs

# Copy the built jar
COPY --from=build /app/target/meatrics-*.jar app.jar

# Environment variables
ENV VAADIN_PRODUCTION_MODE=true
ENV VAADIN_LAUNCH_BROWSER=false
ENV LOG_PATH=/app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t meatrics:latest .
docker run -d \
  --name meatrics \
  -p 8080:8080 \
  -v /var/log/meatrics:/app/logs \
  -e DB_HOST=host.docker.internal \
  -e DB_USERNAME=meatrics \
  -e DB_PASSWORD=your-password \
  meatrics:latest
```

## Environment Variables Reference

| Variable | Description | Default | Production Value |
|----------|-------------|---------|------------------|
| `VAADIN_PRODUCTION_MODE` | Enable Vaadin production mode | `false` | `true` |
| `VAADIN_LAUNCH_BROWSER` | Auto-open browser on start | `true` | `false` |
| `LOG_PATH` | Log file directory | `./logs` | `/var/log/meatrics` |
| `DB_HOST` | Database host | `localhost` | Your DB host |
| `DB_PORT` | Database port | `5432` | `5432` |
| `DB_NAME` | Database name | `meatrics` | `meatrics` |
| `DB_USERNAME` | Database username | `postgres` | Your DB user |
| `DB_PASSWORD` | Database password | `password` | Your DB password |
| `PORT` | Application port | `8080` | Custom port |

## Monitoring Production

### Health Check Endpoint

Spring Boot Actuator (if enabled):
```bash
curl http://localhost:8080/actuator/health
```

### Check Application Logs

```bash
tail -f /var/log/meatrics/meatrics.log
```

### Check for Errors

```bash
grep ERROR /var/log/meatrics/meatrics-error.log
```

### Monitor Memory Usage

```bash
# Linux
ps aux | grep meatrics

# Get detailed JVM info
jps -v | grep meatrics
```

## Performance Optimization

### Enable HTTP/2
- Use reverse proxy (Nginx/Apache) with HTTP/2
- Improves loading of Vaadin resources

### Enable Compression
In `application.properties`:
```properties
server.compression.enabled=true
server.compression.mime-types=text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json
```

### Session Timeout
```properties
server.servlet.session.timeout=30m
vaadin.closeIdleSessions=true
```

## Troubleshooting Production Issues

### Application won't start

1. Check Java version: `java -version` (needs JDK 17+)
2. Check database connectivity: `psql -h DB_HOST -U DB_USER -d meatrics`
3. Check logs: `tail -f /var/log/meatrics/meatrics.log`
4. Verify port is not in use: `netstat -tuln | grep 8080`

### Vaadin shows "Development mode" warning

- Ensure built with `-Pproduction` profile
- Verify `vaadin.productionMode=true` is set
- Rebuild if necessary

### Out of Memory errors

- Increase `-Xmx` value
- Check for memory leaks in logs
- Monitor with `jstat -gc <pid> 1000`

### Slow performance

- Enable production mode (if not already)
- Use reverse proxy for static content caching
- Check database connection pool settings
- Review application logs for slow queries

## Security Checklist

- [ ] Change default database password
- [ ] Use HTTPS (SSL/TLS certificates)
- [ ] Configure firewall (only allow necessary ports)
- [ ] Disable unnecessary endpoints
- [ ] Regular security updates (OS, Java, dependencies)
- [ ] Use strong passwords for database
- [ ] Enable log rotation to prevent disk fill
- [ ] Backup database regularly

## Backup and Recovery

### Database Backup

```bash
pg_dump -h DB_HOST -U DB_USER meatrics > meatrics-backup-$(date +%Y%m%d).sql
```

### Restore Database

```bash
psql -h DB_HOST -U DB_USER meatrics < meatrics-backup-20251109.sql
```

### Application Backup

```bash
# Backup JAR, config, and logs
tar -czf meatrics-backup-$(date +%Y%m%d).tar.gz \
  meatrics-1.0.0.jar \
  application.properties \
  /var/log/meatrics
```

## Support and Maintenance

For production support:
1. Check logs first: `/var/log/meatrics/meatrics.log`
2. Review this documentation
3. Consult application documentation in `PROJECT_OVERVIEW.md`
4. Check GitHub issues if applicable
