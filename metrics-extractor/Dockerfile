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
ENTRYPOINT ["java", "-jar", "app.jar"]