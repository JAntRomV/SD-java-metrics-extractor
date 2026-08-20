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
# 🔌 MODIFICADO: de "-jre" a "-jdk". La fase dinamica (CompiladorProyecto)
# corre "mvn compile" sobre CADA repo que se clona, para generar los .class
# que luego carga por reflexion. "mvn compile" necesita javac -que una
# imagen JRE (Java Runtime, sin compilador) NO trae-, asi que aunque se
# instale Maven iba a seguir fallando por falta de compilador. Ademas, el
# paquete "maven" de apt de por si depende de tener un JDK instalado -sin
# esto tambien terminaria bajando su propio JDK por separado-, asi que usar
# el JDK de Temurin (el mismo vendor/version que la Etapa 1) es mas
# consistente que dejar que apt resuelva "cualquier JDK por defecto".
FROM eclipse-temurin:17-jdk
WORKDIR /app

# git es obligatorio: OrquestadorRepos hace "git clone" de cada repo del
# catalogo.
# 🔌 NUEVO: maven es obligatorio: CompiladorProyecto corre "mvn compile"
# sobre cada repo clonado para poder analizarlo dinamicamente (JMH +
# reflexion). Sin esto, CompiladorProyecto tronaba con "Cannot run program
# 'mvn': No such file or directory" y la fase dinamica nunca generaba nada,
# aunque la estatica si funcionara bien.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git maven \
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