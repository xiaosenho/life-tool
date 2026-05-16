FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .mvn/ ./
COPY .mvn .mvn

RUN mvn dependency:go-offline -B -q

COPY src ./src

RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S lifetool && adduser -S lifetool -G lifetool

COPY --from=builder /app/target/*.jar app.jar

USER lifetool

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "app.jar"]
