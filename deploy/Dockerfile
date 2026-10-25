# Сборка backend: multi-stage, итоговый образ без JDK и без root.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY server/ ./server/
WORKDIR /workspace/server
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home podgon
WORKDIR /app
COPY --from=build /workspace/server/build/libs/*.jar app.jar
USER 10001
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75"
EXPOSE 8080
# Проверки живости/готовности выполняет оркестратор через /actuator/health/liveness и /readiness
ENTRYPOINT ["java", "-jar", "app.jar"]
