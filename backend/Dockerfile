# ---- Stage 1: build with JDK 17 + Maven 3.9 (no local JDK/Maven required) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Stage 2: runtime JRE 17 only ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S webox && adduser -S webox -G webox
COPY --from=build /build/target/*.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R webox:webox /app
USER webox
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
