FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app
COPY gradlew gradle/ build.gradle settings.gradle ./
COPY src ./src

RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/local-storage

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=dev
ENV FILE_STORAGE_ROOT=/app/local-storage
ENV FILE_PUBLIC_BASE_URL=http://localhost:8080/files
ENV MEDIA_FFMPEG_PATH=/usr/bin/ffmpeg
ENV MEDIA_FFPROBE_PATH=/usr/bin/ffprobe

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
