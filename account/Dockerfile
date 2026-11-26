# syntax=docker/dockerfile:1
# account-service. The build context is the ROOT of this repository (the parent POM, the platform module and contracts/ are needed),
# so the Dockerfile is selected with ACCOUNT_DOCKERFILE=account/Dockerfile (the Makefile sets it).
# Temurin 25.0.5.1+2; Renovate keeps the tags current.
FROM eclipse-temurin:25.0.5.1_2-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY platform/pom.xml platform/pom.xml
COPY account/pom.xml account/pom.xml
COPY contracts/i18n contracts/i18n
COPY platform/src platform/src
COPY account/src account/src
# The cache mount keeps Maven's repository between builds; the wrapper downloads Maven itself (distributionUrl in .mvn/wrapper).
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q -pl account -am -DskipTests package

FROM eclipse-temurin:25.0.5.1_2-jre-noble
# No HEALTHCHECK and no Compose healthcheck: the template waits from the host (scripts/wait-ready.sh polls /health/ready on the management port).
COPY --from=build /src/account/target/account.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["/app/entrypoint.sh"]
