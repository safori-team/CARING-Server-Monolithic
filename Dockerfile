FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 8080 9082

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
