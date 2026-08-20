# ---- Etapa 1: Compilación de la aplicación ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# ----> Copia configuración de dependencias
COPY pom.xml .
COPY src ./src

# ----> Compila el proyecto omitiendo pruebas unitarias
RUN mvn -B -DskipTests package

# ---- Etapa 2: Imagen base para ejecución ----
FROM eclipse-temurin:21-jdk
WORKDIR /app

# ----> Instala herramientas del sistema requeridas
RUN apt-get update \
    && apt-get install -y --no-install-recommends git maven \
    && rm -rf /var/lib/apt/lists/*

# ----> Ajusta límites de memoria RAM
ENV MAVEN_OPTS="-Xmx256m -Xms64m"

# ----> Copia el ejecutable compilado
COPY --from=build /app/target/metrics-api.jar app.jar

# ----> Expone el puerto de la API
EXPOSE 8080

# ----> Comando para iniciar la aplicación
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseSerialGC", "-jar", "app.jar"]