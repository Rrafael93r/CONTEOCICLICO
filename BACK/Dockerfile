# Etapa 1: compilar con Maven usando Java 21
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: runtime con Java 21
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/conteociclico-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]