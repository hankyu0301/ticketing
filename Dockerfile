FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

ENV GRADLE_USER_HOME=/workspace/.gradle

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/workspace/.gradle/caches \
    --mount=type=cache,target=/workspace/.gradle/wrapper \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/workspace/.gradle/caches \
    --mount=type=cache,target=/workspace/.gradle/wrapper \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin spring

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

USER spring

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]