FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml /build/
COPY trio-base-dependencies/pom.xml /build/trio-base-dependencies/
COPY trio-base-common/pom.xml /build/trio-base-common/
COPY trio-base-common/common-core/pom.xml /build/trio-base-common/common-core/
COPY trio-base-common/common-dto/pom.xml /build/trio-base-common/common-dto/
COPY trio-base-common/common-temporal/pom.xml /build/trio-base-common/common-temporal/
COPY trio-base-common/common-archunit/pom.xml /build/trio-base-common/common-archunit/
COPY trio-base-services/pom.xml /build/trio-base-services/
COPY trio-base-services/service-ops/pom.xml /build/trio-base-services/service-ops/
RUN mvn dependency:go-offline -pl trio-base-services/service-ops -am -q
COPY . /build
RUN mvn package -pl trio-base-services/service-ops -am -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/trio-base-services/service-ops/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
