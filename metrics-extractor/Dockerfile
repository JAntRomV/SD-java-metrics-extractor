# ---- Etapa 1: build con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# -DskipTests: evita que corran los tests de JUnit durante el build en Render.
RUN mvn -B -DskipTests package

# ---- Etapa 2: imagen final liviana ----
FROM eclipse-temurin:21-jdk
WORKDIR /app

# git y maven necesarios para la mineria y la compilacion dinamica
RUN apt-get update \
    && apt-get install -y --no-install-recommends git maven \
    && rm -rf /var/lib/apt/lists/*

# Limita la memoria RAM de los procesos secundarios de Maven (compilacion dinamica)
# para evitar colapsar los 512MB de Render con el error "Java heap space"
ENV MAVEN_OPTS="-Xmx256m -Xms64m"

# Copia SOLO el jar de la API
COPY --from=build /app/target/metrics-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseSerialGC", "-jar", "app.jar"]