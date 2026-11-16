# -------- Stage 1: Build --------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy build config and wrapper
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source
COPY src ./src

# Build executable jar
RUN ./gradlew bootJar --no-daemon -x test

# -------- Stage 2: Runtime --------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Ownership and user
RUN chown -R appuser:appgroup /app
USER appuser:appgroup

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
