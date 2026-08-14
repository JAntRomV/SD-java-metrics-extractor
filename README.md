# Módulo de Almacenamiento (Metrics Storage System)

### ¿Qué hace este proyecto?
Este módulo es el **puente entre lo que generan los análisis estático y dinámico, y MongoDB Atlas**. Hasta antes de este módulo, `ProcesadorMetricas` y `EjecutorCompleto` dejaban sus resultados sueltos en el disco: archivos JSON por clase, CSV de benchmarks, CSV de caminos cronometrados. Esa información no servía de nada si se quedaba solo en la laptop de quien la generó — el equipo necesita un catálogo compartido y consultable.

`almacenamiento` resuelve eso: toma los repos pendientes del catálogo compartido en Mongo, coordina que se les corra el análisis (vía `AnalizadorUnificado`), lee todo lo que ese análisis dejó en disco, y lo sube a MongoDB Atlas en la estructura correcta — separando métricas estáticas de dinámicas, fragmentando lo que sea demasiado grande para no chocar con el límite de 16MB por documento de Mongo, y dejando registrado el estado (`pending`, `metrics_in_progress`, `metrics_complete`, `metrics_failed`) de cada repo para que el equipo sepa en todo momento qué falta.

Es el encargado de que el trabajo de análisis, que hasta ahora vivía solo en archivos locales, se vuelva un catálogo real y compartido.

---

### Herramientas que utiliza (Tecnologías)
* **Java 17 y Maven:** la base del proyecto, igual que los otros módulos.
* **MongoDB Atlas (driver `mongodb-driver-sync`):** base de datos en la nube donde vive el catálogo compartido de repos y sus métricas.
* **`integracion.AnalizadorUnificado`:** este módulo lo invoca para correr el análisis estático + dinámico de cada repo antes de subir resultados; no repite esa lógica.
* **Variables de entorno / archivo `.env`:** para no exponer la cadena de conexión (`MONGO_URI`) en el código ni en el repositorio.

---

### ¿Cómo funciona?

#### 1. `ConfiguracionMongo` (La llave de acceso)
Carga la configuración de conexión leyendo primero un archivo `.env` local (si existe) y después las variables de entorno del sistema, que tienen prioridad. Exige `MONGO_URI` como obligatoria — si falta, el programa se detiene con un mensaje claro en vez de tronar con un error críptico de conexión. También trae valores por defecto para los nombres de la base de datos y las tres colecciones que usa el sistema (`repo_catalog`, `repo_metrics_static`, `repo_metrics_dynamic`), configurables por variable de entorno si hace falta.

#### 2. `AlmacenMetricasMongo` (El encargado de leer y escribir)
Es el corazón del módulo: administra las tres colecciones de Mongo.
* **`repo_catalog`:** un documento por repositorio, con su estado y un resumen de sus métricas.
* **`repo_metrics_static`:** un documento por clase analizada (Halstead, ciclomática, CFG).
* **`repo_metrics_dynamic`:** documentos fragmentados por clase y "parte" (benchmarks JMH + cronómetro de caminos).

Expone operaciones como `obtenerRepositoriosPendientes()` (trae los repos con status `pending` o `metrics_in_progress`, ordenados por ranking de minería), `inicializarMetricasVacias()` (limpia cualquier residuo de una corrida anterior antes de empezar), `agregarClaseAMetricas()` / `agregarDinamicoAMetricas()` (suben documentos con upsert, uno a la vez, para no acumular todo en memoria), y `actualizarEstadoParcial()` / `finalizarMetricas()` para ir marcando el progreso (`static: complete`, `dynamic: complete`, etc.) conforme cada fase termina.

#### 3. `LectorResultados` (El traductor de disco a Mongo)
Lee lo que `AnalizadorUnificado` dejó en el disco y lo convierte en documentos BSON listos para subir:
* Recorre los JSON de métricas estáticas (`<Clase>Metricas.json` y `<Clase>_caminos.json`) y arma un documento por clase.
* Recorre los CSV de métricas dinámicas (`Benchmarks.csv` y `cronometro_caminos.csv`), los agrupa por clase, y **los fragmenta en "partes" de máximo 15 filas cada una** — esto es lo que evita que una clase con miles de filas de benchmark genere un documento que rebase el límite de 16MB de MongoDB.

Ambos métodos procesan **uno a uno**, entregando cada documento a un consumidor (`OrquestadorRepos` lo sube inmediatamente), en vez de cargar todo el repo en memoria antes de subir nada.

#### 4. `OrquestadorRepos` (El que corre el lote completo)
Es el punto de entrada principal del módulo. Por cada repo pendiente en el catálogo:
1. Lo clona con `git clone --depth 1` (reusa el clon si ya existe y está sano; lo borra y reclona si está roto).
2. Le corre `AnalizadorUnificado` (estático + dinámico).
3. Sube sus métricas estáticas y dinámicas a Mongo con `LectorResultados` + `AlmacenMetricasMongo`.
4. Marca el repo como `metrics_complete` o `metrics_failed` (guardando el motivo del fallo directamente en el documento del repo).
5. Borra el clon local para liberar espacio antes de pasar al siguiente repo.

Su cuerpo vive en el método `ejecutarLote(Map<String,String> params)`, invocable tanto desde `main()` (línea de comandos) como desde la API REST del módulo de integración.

#### 5. `ReprocesarDinamico` (El bisturí para un solo repo)
Herramienta puntual para cuando **solo** el análisis dinámico de un repo específico falló o quedó incompleto (por ejemplo, por un timeout de `git clone` o porque el JAR aún no compilaba bien). En vez de volver a correr todo el pipeline completo para ese repo, reclona solo ese uno, vuelve a correr `EjecutorCompleto` (fases 1 y 2), **borra únicamente las métricas dinámicas previas** de Mongo (para no mezclar la numeración vieja de "parte" con la nueva) y resube. No toca las métricas estáticas, que ya estaban bien.

#### 6. `DiagnosticoAlmacenamiento` (El chequeo de salud del catálogo)
Herramienta de auditoría, independiente del flujo de subida. Por repo, compara cuántas clases/partes se **esperaban** contra cuántas se **encontraron de verdad** en Mongo (detecta subidas incompletas), estima el espacio en MB que ocupa cada repo, y alerta si algún documento individual se está acercando al límite de 16MB. También da un resumen general del catálogo completo: espacio usado por cada colección y conteo de repos por status (`pending`, `metrics_in_progress`, `metrics_complete`, `metrics_failed`).

#### 7. `PruebaConexionMongo` (El "hola mundo" de conexión)
Script mínimo para verificar rápido, sin correr nada pesado, que la conexión a Mongo funciona: cuenta documentos totales, muestra el conteo por status, y lista los repos pendientes con su ranking y URL. Útil como primer chequeo antes de lanzar un lote completo.

---

### Cómo se ejecuta el programa

1. **Configurar la conexión.** Crea un archivo `.env` en la raíz del proyecto (o exporta la variable en el sistema):
```bash
   MONGO_URI="mongodb+srv://usuario:password@cluster.mongodb.net"
```

2. **Empaquetar el proyecto completo:**
```bash
   mvn clean package
```

3. **Probar la conexión** (opcional pero recomendado antes de un lote grande):
```bash
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       almacenamiento.PruebaConexionMongo
```

4. **Correr el lote completo de repos pendientes:**
```bash
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       almacenamiento.OrquestadorRepos \
       --clones:repos_clonados --salida:resultados
```
   Parámetros opcionales: `--limite:5` para procesar solo los primeros N repos pendientes.

5. **Reprocesar solo el análisis dinámico de un repo puntual:**
```bash
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       almacenamiento.ReprocesarDinamico \
       --repo:owner/nombre-del-repo
```

6. **Diagnosticar el estado del catálogo:**
```bash
   # Resumen general de todo el catálogo
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       almacenamiento.DiagnosticoAlmacenamiento

   # Diagnóstico de un repo específico
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       almacenamiento.DiagnosticoAlmacenamiento --repo:owner/nombre-del-repo
```

Al terminar un lote, cada repo queda en Mongo con esta forma:

```
repo_catalog (1 documento por repo)
├── _id: "owner/nombre-del-repo"
├── status: "metrics_complete" | "metrics_failed" | "pending" | "metrics_in_progress"
├── metricsStatus: { static: "complete", dynamic: "complete" }
└── metrics: { estaticas: { totalClases }, dinamicas: {...} }

repo_metrics_static (1 documento por clase)
└── { repoId, clase, metricasJson, caminos }

repo_metrics_dynamic (documentos fragmentados por clase/parte)
└── { repoId, clase, parte, totalPartes, benchmarks, cronometroCaminos }
```