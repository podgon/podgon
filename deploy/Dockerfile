# Сборка backend: multi-stage, итоговый образ без JDK и без root.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradle/ ./gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared-contract/ ./shared-contract/
COPY server/ ./server/
COPY api/ ./api/
# Загрузки зависимостей иногда обрываются по TLS: кэш Gradle сохраняется между попытками, шаг повторяется
RUN --mount=type=cache,target=/root/.gradle \
    for i in 1 2 3 4 5 6; do ./gradlew --no-daemon :server:bootJar -x test && exit 0; echo "Попытка $i не удалась, повтор"; sleep 5; done; exit 1

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home podgon
WORKDIR /app
COPY --from=build /workspace/server/build/libs/*.jar app.jar
USER 10001
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75"
EXPOSE 8080
# Проверки живости/готовности выполняет оркестратор через /actuator/health/liveness и /readiness
ENTRYPOINT ["java", "-jar", "app.jar"]
