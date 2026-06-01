# Stage 1: Build
FROM gradle:8.7-jdk17 AS build

WORKDIR /app

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle

# Copy module build files
COPY diameter-codec/build.gradle diameter-codec/build.gradle
COPY gateway/build.gradle gateway/build.gradle
COPY simulator/build.gradle simulator/build.gradle
COPY load-test/build.gradle load-test/build.gradle

# Copy version catalog
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# Copy source code
COPY diameter-codec/src/ diameter-codec/src/
COPY simulator/src/ simulator/src/

# Build the simulator distribution
RUN chmod +x gradlew && ./gradlew :simulator:installDist --no-daemon -x test

# Stage 2: Run
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the distribution from the build stage
COPY --from=build /app/simulator/build/install/simulator/ /app/

EXPOSE 3868

ENTRYPOINT ["/app/bin/simulator"]
