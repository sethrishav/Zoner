# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source and build package
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S zoner && adduser -S zoner -G zoner
USER zoner

# Copy compiled jar
COPY --from=build /app/target/*.jar app.jar

# JVM memory configuration tuned for Render Free Tier (512 MB hard cap)
ENV JAVA_OPTS="-Xmx350m -Xms150m -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080

# Graceful process execution binding to Render dynamic PORT
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
