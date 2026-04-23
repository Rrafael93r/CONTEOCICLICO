# ============================================
# ETAPA 1: BUILD
# ============================================
FROM docker.io/amazoncorretto:21-alpine AS builder

WORKDIR /app

# Instalar Maven (solo en etapa de build)
RUN apk add --no-cache maven

# Copiar solo pom.xml primero para aprovechar cache de dependencias
COPY pom.xml .

# Descargar dependencias (cacheada si pom.xml no cambia)
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar y empaquetar (sin tests)
RUN mvn clean package -DskipTests -B

# ============================================
# ETAPA 2: RUNTIME
# ============================================
FROM docker.io/amazoncorretto:21-alpine AS runtime

# Metadata
LABEL maintainer="Equipo de Desarrollo"
LABEL description="API Triton - Spring Boot"

WORKDIR /app

# Crear usuario no-root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar solo el JAR desde la etapa de build
COPY --from=builder /app/target/*.jar app.jar

# Cambiar permisos
RUN chown spring:spring app.jar

# Usar usuario no-root
USER spring:spring

# Puerto expuesto
EXPOSE 8080

# Healthcheck para verificar que la app está funcionando
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider https://apiciclico.pharmaser.com.co/actuator/health 2>/dev/null || exit 1

# Variables de entorno para JVM (optimizadas para contenedores)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true \
    -Dfile.encoding=UTF-8"

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
