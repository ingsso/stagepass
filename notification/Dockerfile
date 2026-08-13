FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/notification-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
