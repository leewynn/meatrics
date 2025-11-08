# Logging Configuration Guide

This document explains how logging works in the meatrics application and how to configure it.

## Default Behavior

By default, the application creates logs in a `./logs` directory relative to where you run the jar file.

**Log files created:**
- `meatrics.log` - Main application log (all INFO level and above)
- `meatrics-error.log` - Error-only log (ERROR level only)
- `meatrics-YYYY-MM-DD.N.log` - Rolled/archived logs

## Running the Application

### Basic Run (logs in ./logs)
```bash
java -jar meatrics-1.0.0.jar
```
Creates logs in: `./logs/meatrics.log`

### Custom Log Location

**Option 1: Using environment variable**
```bash
export LOG_PATH=/var/log/meatrics
java -jar meatrics-1.0.0.jar
```

**Option 2: Using system property**
```bash
java -DLOG_PATH=/var/log/meatrics -jar meatrics-1.0.0.jar
```

**Option 3: Windows**
```cmd
set LOG_PATH=C:\logs\meatrics
java -jar meatrics-1.0.0.jar
```

## Log File Rotation

Logs automatically rotate based on:
- **Size**: Each log file max 10MB
- **Time**: Daily rollover (new file each day)
- **Retention**: 30 days of logs kept
- **Total size cap**: 1GB total for all logs

Old logs are automatically deleted when limits are reached.

## Log Levels

You can adjust log verbosity:

### More Detailed Logs (DEBUG level)
```bash
java -Dlogging.level.com.meatrics=DEBUG -jar meatrics-1.0.0.jar
```

### Less Verbose (WARN level)
```bash
java -Dlogging.level.root=WARN -jar meatrics-1.0.0.jar
```

### Database Query Logging (jOOQ)
```bash
java -Dlogging.level.org.jooq=DEBUG -jar meatrics-1.0.0.jar
```

## Log Format

Each log entry includes:
```
2025-11-09 14:30:45.123 [http-nio-8080-exec-1] INFO  com.meatrics.pricing.PricingImportService - File imported successfully! 150 records imported.
```

Format: `timestamp [thread] LEVEL logger - message`

## Production Deployment

For production, create a dedicated log directory:

```bash
# Create log directory
sudo mkdir -p /var/log/meatrics
sudo chown youruser:yourgroup /var/log/meatrics

# Run with custom log path
java -DLOG_PATH=/var/log/meatrics -jar meatrics-1.0.0.jar
```

## Troubleshooting

### No logs appearing?

1. **Check permissions**: Ensure the application can write to the log directory
   ```bash
   ls -la logs/
   ```

2. **Check if directory was created**: Look for `./logs` folder where you ran the jar

3. **Check console output**: Logs also appear in console/terminal

4. **Verify log path**: Add this to see where logs are going:
   ```bash
   java -Dlogging.level.root=DEBUG -jar meatrics-1.0.0.jar | grep "Logging"
   ```

### Logs filling up disk?

Adjust retention in `logback-spring.xml`:
- `<maxHistory>30</maxHistory>` - Change number of days
- `<totalSizeCap>1GB</totalSizeCap>` - Change total size limit

## Log File Locations by Deployment Type

| Deployment | Log Location | How to Set |
|------------|--------------|------------|
| Development (IDE) | `./logs` | Default |
| Packaged JAR (local) | `./logs` | Default |
| Linux Server | `/var/log/meatrics` | `-DLOG_PATH=/var/log/meatrics` |
| Windows Server | `C:\logs\meatrics` | `-DLOG_PATH=C:\logs\meatrics` |
| Docker Container | `/app/logs` | Volume mount + env var |

## Docker Example

```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/meatrics-1.0.0.jar app.jar

# Create log directory
RUN mkdir -p /app/logs

ENV LOG_PATH=/app/logs

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Docker run with volume for logs:
```bash
docker run -v /host/logs:/app/logs -p 8080:8080 meatrics:latest
```

## Monitoring Logs

### Tail logs in real-time
```bash
tail -f logs/meatrics.log
```

### Search for errors
```bash
grep ERROR logs/meatrics.log
```

### View last 100 lines
```bash
tail -n 100 logs/meatrics.log
```

### Filter by date/time
```bash
grep "2025-11-09 14:" logs/meatrics.log
```

## Important Log Events

The application logs these key events:
- Application startup/shutdown
- Database connection status
- File imports (success/failure)
- Duplicate detection
- Zero-amount items detected
- Report generation
- Error conditions

Look for these in the logs to track application health and troubleshoot issues.
