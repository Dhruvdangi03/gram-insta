# Builds the exact same jar `./mvnw package` produces locally, via the same wrapper — not a
# separately maintained build definition that could drift from local dev.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline || true
COPY src/ src/
RUN ./mvnw -B package -DskipTests

# Alpine JRE, not the full JDK, for the runtime image — plus FFmpeg, which the media transcode
# worker (com.instaclone.media) shells out to at runtime; nothing else in the app needs it.
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app
COPY --from=build /workspace/target/insta-clone-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
