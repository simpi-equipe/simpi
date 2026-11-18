FROM eclipse-temurin:21.0.13_9-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Mitigação temporária da CVE-2026-114938 até a publicação das imagens corrigidas.
ENV JAVA_TOOL_OPTIONS="-XX:+UnlockDiagnosticVMOptions -XX:-UseBASE64Intrinsics"
RUN bash ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21.0.13_9-jre-jammy

WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UnlockDiagnosticVMOptions", "-XX:-UseBASE64Intrinsics", "-jar", "/app/app.jar"]
