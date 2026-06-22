FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system leasetrack && useradd --system --gid leasetrack --home-dir /app leasetrack \
    && mkdir -p /app/data/evidence-documents \
    && chown -R leasetrack:leasetrack /app
COPY --from=build /app/target/lease-track-0.0.1-SNAPSHOT.jar app.jar
RUN chown leasetrack:leasetrack app.jar
USER leasetrack
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
