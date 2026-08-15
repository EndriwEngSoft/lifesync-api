FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY --from=build /app/target/lifesync-api.jar app.jar
ENTRYPOINT ["java", "-Xmx400m", "-jar", "/app.jar"]
