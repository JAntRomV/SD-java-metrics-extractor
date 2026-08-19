# ---- Etapa 1: build con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# -DskipTests: evita que corran los tests de JUnit durante el build en Render.
# El "package" NO dispara el exec-maven-plugin (esta atado a la fase "verify",
# que va DESPUES de "package" en el ciclo de vida de Maven), asi que no se
# ejecuta App.main() automaticamente durante el build.
RUN mvn -B -DskipTests package

# ---- Etapa 2: imagen final liviana ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# git es obligatorio: OrquestadorRepos hace "git clone" de cada repo del
# catalogo. La imagen base de JRE no lo trae instalado.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

# Copia SOLO el jar de la API (generado por la segunda ejecucion del
# shade-plugin en el pom.xml), no el de benchmarks ni el de jar-with-dependencies.
COPY --from=build /app/target/metrics-api.jar app.jar

EXPOSE 8080

# 🔌 NUEVO: flags explicitos de memoria para la JVM. Sin esto, Java 17 usa
# por defecto ~25% de la RAM del contenedor como heap (MaxRAMPercentage=25.0
# por defecto) -en un contenedor de 512MB eso son solo ~128MB de heap-, muy
# poco para analizar clases grandes con JavaParser (AST completo en memoria)
# + generacion de caminos (hasta 500 por metodo) en repos grandes tipo
# conductor-oss/conductor. Se sube el porcentaje de heap disponible y se usa
# SerialGC en vez del G1 por defecto: G1 reserva estructuras internas mas
# pesadas (utiles en heaps grandes, contraproducentes en heaps chicos como
# este); SerialGC tiene mucho menos overhead propio y es lo recomendado por
# la mayoria de plataformas cloud para contenedores con poca memoria.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseSerialGC", "-jar", "app.jar"]