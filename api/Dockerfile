FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 루트로 실행하지 않습니다 (K8s securityContext 와 맞춤)
RUN addgroup -S spring && adduser -S spring -G spring

COPY build/libs/api-0.0.1-SNAPSHOT.jar app.jar
RUN chown spring:spring /app/app.jar

USER spring
EXPOSE 8080

# 컨테이너 메모리 제한(limits.memory)을 JVM 힙 계산에 반영
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 $JAVA_OPTS -jar app.jar"]
