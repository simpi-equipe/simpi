FROM eclipse-temurin:21.0.13.1_2-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN bash ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21.0.13.1_2-jre-jammy

WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
