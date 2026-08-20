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

# ----> 🔌 MODIFICADO: se quita el MAVEN_OPTS fijo de aqui. El codigo Java
# ----> (CompiladorProyecto.limitarMemoriaProcesoHijo) ahora fija MAVEN_OPTS/
# ----> GRADLE_OPTS/JAVA_OPTS de forma explicita en cada proceso hijo que
# ----> compila un repo -tenerlo tambien aqui, con un valor DISTINTO, solo
# ----> generaba confusion sobre cual valor aplicaba realmente (el que gana
# ----> es el que fija ProcessBuilder.environment() en el codigo).

# ----> Copia el ejecutable compilado
COPY --from=build /app/target/metrics-api.jar app.jar

# ----> Expone el puerto de la API
EXPOSE 8080

# ----> Comando para iniciar la aplicación
# ----> 🔌 MODIFICADO: se baja MaxRAMPercentage de 70.0 a 50.0 y se agrega
# ----> MaxMetaspaceSize. Con 70% (~358MB de 512MB) mas el heap del proceso
# ----> hijo de Maven/Gradle que compila cada repo (ver CompiladorProyecto,
# ----> hasta hace poco -Xmx256m), la SUMA de los dos techos de heap YA
# ----> superaba los 512MB del contenedor -sin contar metaspace, threads ni
# ----> memoria nativa de ninguna de las dos JVMs-. Esa suma, no un limite
# ----> faltante, era la causa real del OOM silencioso durante la fase
# ----> dinamica (compilacion). Con 50% (~256MB) para la app + el limite
# ----> mas chico del hijo (ver CompiladorProyecto: -Xmx160m), queda margen
# ----> real para que ambas JVMs convivan sin exceder el contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50.0", "-XX:InitialRAMPercentage=35.0", "-XX:MaxMetaspaceSize=150m", "-XX:+UseSerialGC", "-jar", "app.jar"]