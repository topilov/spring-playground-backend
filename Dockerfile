# build
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# run
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

CMD ["java", "-jar", "app.jar"]