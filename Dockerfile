# Этап 1: Сборка приложения
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests


# Этап 2: Запуск приложения
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar /app/*.jar

ENTRYPOINT ["java", "-jar", "/app/*.jar"]