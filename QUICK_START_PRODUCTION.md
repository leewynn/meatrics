# Quick Start - Production Deployment

## Build for Production

```bash
mvn clean package -Pproduction
```

## Run in Production Mode

### Simple (Environment Variables)
```bash
export VAADIN_PRODUCTION_MODE=true
export VAADIN_LAUNCH_BROWSER=false
export LOG_PATH=/var/log/meatrics
java -jar target/meatrics-1.0.0.jar
```

### Complete (All Settings)
```bash
java -Xms512m -Xmx2g \
     -Dvaadin.productionMode=true \
     -Dvaadin.launch-browser=false \
     -DLOG_PATH=/var/log/meatrics \
     -DDB_HOST=your-db-host \
     -DDB_USERNAME=your-db-user \
     -DDB_PASSWORD=your-db-password \
     -jar target/meatrics-1.0.0.jar
```

## Key Differences: Development vs Production

| Setting | Development | Production |
|---------|-------------|------------|
| Build command | `mvn clean package` | `mvn clean package -Pproduction` |
| `vaadin.productionMode` | `false` | `true` |
| `vaadin.launch-browser` | `true` | `false` |
| Bundle size | Larger (includes dev tools) | Smaller (optimized) |
| Startup time | Faster | Slower (initial) |
| Runtime performance | Slower | **Much faster** |
| Frontend updates | Hot reload | Pre-built bundle |

## Verification

After starting, check:
1. **Console output** - Should NOT see "Running in development mode"
2. **Browser** - Should load fast, no debug info
3. **Logs** - Check `/var/log/meatrics/meatrics.log`

## Production Mode Flags

**Essential:**
- `-Pproduction` (Maven build)
- `-Dvaadin.productionMode=true` (runtime)

**Recommended:**
- `-Xms512m -Xmx2g` (memory)
- `-DLOG_PATH=/var/log/meatrics` (logs)
- `-Dvaadin.launch-browser=false` (no browser)

See `PRODUCTION_DEPLOYMENT.md` for complete guide.
