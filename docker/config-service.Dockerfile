FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY CARING-Back-Config CARING-Back-Config

RUN chmod +x gradlew \
    && ./gradlew :CARING-Back-Config:bootJar -x test

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /workspace/CARING-Back-Config/build/libs/*.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 8888 9888

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
