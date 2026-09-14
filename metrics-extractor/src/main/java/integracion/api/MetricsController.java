package integracion.api;

import almacenamiento.AlmacenMetricasMongo;
import almacenamiento.ConfiguracionMongo;
import almacenamiento.DiagnosticoAlmacenamiento;
import almacenamiento.EstadoAnalisis;
import almacenamiento.OrquestadorRepos;
import com.mongodb.client.FindIterable;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//-----> Controlador principal de endpoints REST
@RestController
public class MetricsController {

    //-----> Estado y control del proceso activo
    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private volatile String ultimoInicio = null;
    private volatile String ultimoResultado = "sin ejecuciones todavia";

    //-----> Inicia el analisis en segundo plano
    @PostMapping("/api/metrics/run")
    public ResponseEntity<Map<String, Object>> ejecutar(
            @RequestParam(name = "repo", required = false) String repo) {

        if (!corriendo.compareAndSet(false, true)) {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("iniciado", false);
            cuerpo.put("mensaje", "Ya hay un proceso corriendo, espera a que termine.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
        }

        ultimoInicio = Instant.now().toString();
        ultimoResultado = "en progreso";

        Thread hiloAnalisis = new Thread(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                if (repo != null && !repo.isBlank()) {
                    params.put("repo", repo);
                }
                OrquestadorRepos.ejecutarLote(params);
                ultimoResultado = "completado sin errores en " + Instant.now();
            } catch (Throwable e) {
                ultimoResultado = "fallo: " + e.getMessage();
            } finally {
                corriendo.set(false);
            }
        }, "metrics-run-thread");
        hiloAnalisis.start();

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("iniciado", true);
        cuerpo.put("mensaje", (repo != null && !repo.isBlank())
                ? "Analisis del repo '" + repo + "' iniciado en segundo plano"
                : "Analisis iniciado en segundo plano");
        return ResponseEntity.accepted().body(cuerpo);
    }

    //-----> Consulta el estado actual de la ejecucion
    @GetMapping("/api/metrics/status")
    public Map<String, Object> status() {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("corriendo", corriendo.get());
        cuerpo.put("ultimoInicio", ultimoInicio);
        cuerpo.put("ultimoResultado", ultimoResultado);
        cuerpo.put("repoActual", EstadoAnalisis.getRepoActual());

        List<Map<String, String>> fases = new ArrayList<>();
        for (Map.Entry<String, EstadoAnalisis.EstadoFase> entrada : EstadoAnalisis.getFases().entrySet()) {
            Map<String, String> fase = new HashMap<>();
            fase.put("nombre", entrada.getKey());
            fase.put("estado", entrada.getValue().name().toLowerCase());
            fases.add(fase);
        }
        cuerpo.put("fases", fases);

        return cuerpo;
    }

    //-----> Consulta el resumen general guardado en Mongo
    @GetMapping("/api/metrics/summary")
    public ResponseEntity<?> summary() {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (DiagnosticoAlmacenamiento diagnostico = new DiagnosticoAlmacenamiento(config)) {
            Document resumen = diagnostico.resumenGeneral();
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudo leer el resumen de Mongo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //-----> Devuelve la lista completa de repositorios
    @GetMapping("/api/metrics/repos")
    public ResponseEntity<?> listarRepos() {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {
            List<Document> repos = almacen.obtenerTodosLosRepositorios();
            return ResponseEntity.ok(repos);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudo leer el catalogo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //-----> Devuelve los datos de un repositorio por su ID
    @GetMapping("/api/metrics/repo")
    public ResponseEntity<?> obtenerRepo(@RequestParam(name = "id") String id) {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {
            Document repoEncontrado = almacen.obtenerRepositorioPorId(id);
            if (repoEncontrado == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No se encontro el repo: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            return ResponseEntity.ok(repoEncontrado);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudo leer el repo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //-----> Verifica la disponibilidad del servicio
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    //-----> Exporta un ZIP con 4 CSV -metricas estaticas, caminos estaticos,
    //-----> benchmarks dinamicos y cronometro de caminos dinamico- de TODO el
    //-----> catalogo. Escribe directo al stream de la respuesta usando
    //-----> cursores de Mongo -nunca carga todos los documentos en una lista
    //-----> en memoria-, para evitar el mismo problema de OOM que ya se
    //-----> resolvio en otras partes del proyecto.
    @GetMapping("/api/metrics/export/metricas")
    public void exportarMetricasZip(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"metricas_export.zip\"");

        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config);
             ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {

            escribirMetricasEstaticasCsv(almacen, zip);
            escribirCaminosEstaticosCsv(almacen, zip);
            escribirBenchmarksDinamicosCsv(almacen, zip);
            escribirCronometroCaminosDinamicoCsv(almacen, zip);

            zip.finish();
        }
    }

    //-----> repo_metrics_static (documentos con metricasJson) -> 1 fila por metodo
    private void escribirMetricasEstaticasCsv(AlmacenMetricasMongo almacen, ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("metricas_estaticas.csv"));

        escribirLinea(zip, "repo,clase,metodo,loc,vocabulario,longitud,volumen,dificultad,"
                + "esfuerzo,tiempoEstimado,bugsEstimados,complejidadCiclomatica,"
                + "cfgNodes,cfgEdges,cfgUnconnectedNodes");

        FindIterable<Document> cursor = almacen.obtenerCursorClasesConMetricas();
        for (Document docClase : cursor) {
            String repoId = docClase.getString("repoId");
            String clase = docClase.getString("clase");
            Document metricasJson = docClase.get("metricasJson", Document.class);
            if (metricasJson == null) continue;

            List<Document> metodos = metricasJson.getList("metodos", Document.class);
            if (metodos == null) continue;

            for (Document metodo : metodos) {
                String nombreMetodo = metodo.getString("metodo");
                Document halstead = metodo.get("halstead", Document.class);
                Document cfg = metodo.get("grafo de flujo de control", Document.class);

                StringBuilder fila = new StringBuilder();
                fila.append(csvCampo(repoId)).append(",")
                    .append(csvCampo(clase)).append(",")
                    .append(csvCampo(nombreMetodo)).append(",")
                    .append(valorONulo(cfg, "loc")).append(",")
                    .append(valorONulo(halstead, "vocabulario")).append(",")
                    .append(valorONulo(halstead, "longitud")).append(",")
                    .append(valorONulo(halstead, "volumen")).append(",")
                    .append(valorONulo(halstead, "dificultad")).append(",")
                    .append(valorONulo(halstead, "esfuerzo de implementacion")).append(",")
                    .append(valorONulo(halstead, "tiempo estimado de desarrollo")).append(",")
                    .append(valorONulo(halstead, "estimacion de bug")).append(",")
                    .append(valorONulo(halstead, "numero ciclomatico")).append(",")
                    .append(valorONulo(cfg, "nodes")).append(",")
                    .append(valorONulo(cfg, "edges")).append(",")
                    .append(valorONulo(cfg, "unconnected nodos"));

                escribirLinea(zip, fila.toString());
            }
        }

        zip.closeEntry();
    }

    //-----> repo_metrics_static (documentos con "caminos") -> 1 fila por camino
    private void escribirCaminosEstaticosCsv(AlmacenMetricasMongo almacen, ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("caminos_estaticos.csv"));

        escribirLinea(zip, "repo,clase,metodo,caminoId,texto,serieNumerica");

        FindIterable<Document> cursor = almacen.obtenerCursorClasesConCaminos();
        for (Document docClase : cursor) {
            String repoId = docClase.getString("repoId");
            String clase = docClase.getString("clase");
            List<Document> caminos = docClase.getList("caminos", Document.class);
            if (caminos == null) continue;

            for (Document camino : caminos) {
                String metodo = camino.getString("metodo");
                Object caminoId = camino.get("camino_id");
                String texto = camino.getString("texto");
                Object serieNumerica = camino.get("serie_numerica");

                StringBuilder fila = new StringBuilder();
                fila.append(csvCampo(repoId)).append(",")
                    .append(csvCampo(clase)).append(",")
                    .append(csvCampo(metodo)).append(",")
                    .append(caminoId == null ? "" : caminoId.toString()).append(",")
                    .append(csvCampo(texto)).append(",")
                    .append(csvCampo(serieNumerica == null ? "" : serieNumerica.toString()));

                escribirLinea(zip, fila.toString());
            }
        }

        zip.closeEntry();
    }

    //-----> repo_metrics_dynamic (array "benchmarks") -> 1 fila por metodo medido.
    //-----> Las columnas de JMH varian segun la corrida, asi que se recolectan
    //-----> en una primera pasada -solo los NOMBRES de columna, no las filas-
    //-----> y luego se escribe en una segunda pasada. Esto mantiene el uso de
    //-----> memoria bajo: lo unico que se acumula es un set de textos cortos.
    private void escribirBenchmarksDinamicosCsv(AlmacenMetricasMongo almacen, ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("benchmarks_dinamicos.csv"));

        Set<String> columnas = new LinkedHashSet<>();
        for (Document docDinamico : almacen.obtenerCursorDinamicas()) {
            List<Document> benchmarks = docDinamico.getList("benchmarks", Document.class);
            if (benchmarks == null) continue;
            for (Document fila : benchmarks) {
                columnas.addAll(fila.keySet());
            }
        }
        //-----> la columna cruda de JMH ya no se repite por separado, se
        //-----> extrae su valor en la nueva columna "metodo"
        columnas.remove("Param: metodoObjetivo");

        List<String> columnasOrdenadas = new ArrayList<>(columnas);

        StringBuilder encabezado = new StringBuilder("repo,clase,metodo");
        for (String col : columnasOrdenadas) {
            encabezado.append(",").append(csvCampo(col));
        }
        escribirLinea(zip, encabezado.toString());

        for (Document docDinamico : almacen.obtenerCursorDinamicas()) {
            String repoId = docDinamico.getString("repoId");
            String clase = docDinamico.getString("clase");
            List<Document> benchmarks = docDinamico.getList("benchmarks", Document.class);
            if (benchmarks == null) continue;

            for (Document fila : benchmarks) {
                //-----> separa "Clase#metodo" en el nombre del metodo solo
                String claveMetodo = fila.getString("Param: metodoObjetivo");
                String metodo = extraerMetodo(claveMetodo);

                StringBuilder linea = new StringBuilder();
                linea.append(csvCampo(repoId)).append(",").append(csvCampo(clase)).append(",").append(csvCampo(metodo));
                for (String col : columnasOrdenadas) {
                    Object valor = fila.get(col);
                    linea.append(",").append(csvCampo(valor == null ? "" : valor.toString()));
                }
                escribirLinea(zip, linea.toString());
            }
        }

        zip.closeEntry();
    }

    //-----> repo_metrics_dynamic (array "cronometroCaminos") -> 1 fila por instruccion medida
    private void escribirCronometroCaminosDinamicoCsv(AlmacenMetricasMongo almacen, ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("cronometro_caminos_dinamico.csv"));

        //-----> se quita "Clase" de las columnas crudas porque en realidad
        //-----> contiene "Clase#metodo" completo -se separa abajo en las
        //-----> columnas "clase" (ya la trae el documento padre) y "metodo"
        String[] columnasFijas = {"IDLog", "Iteracion", "ParamN", "Etiqueta",
                "TiempoNanos", "FechaHora", "DuracionNanos", "DuracionNanosTime"};

        StringBuilder encabezado = new StringBuilder("repo,clase,metodo");
        for (String col : columnasFijas) {
            encabezado.append(",").append(col);
        }
        escribirLinea(zip, encabezado.toString());

        for (Document docDinamico : almacen.obtenerCursorDinamicas()) {
            String repoId = docDinamico.getString("repoId");
            String clase = docDinamico.getString("clase");
            List<Document> cronometroCaminos = docDinamico.getList("cronometroCaminos", Document.class);
            if (cronometroCaminos == null) continue;

            for (Document fila : cronometroCaminos) {
                //-----> la columna "Clase" del CSV original trae
                //-----> "claseCompleta#metodo" -se extrae solo el metodo
                String metodo = extraerMetodo(fila.getString("Clase"));

                StringBuilder linea = new StringBuilder();
                linea.append(csvCampo(repoId)).append(",").append(csvCampo(clase)).append(",").append(csvCampo(metodo));
                for (String col : columnasFijas) {
                    Object valor = fila.get(col);
                    linea.append(",").append(csvCampo(valor == null ? "" : valor.toString()));
                }
                escribirLinea(zip, linea.toString());
            }
        }

        zip.closeEntry();
    }

    //-----> Exporta CSV directo (sin ZIP) con los repos que tuvieron alguna
    //-----> incidencia -fallidos por completo, o solo estaticos-
    @GetMapping("/api/metrics/export/incidencias")
    public ResponseEntity<byte[]> exportarCsvIncidencias() {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {
            List<Document> repos = almacen.obtenerRepositoriosConIncidencias();

            StringBuilder csv = new StringBuilder();
            csv.append("repo,status,razon\n");

            for (Document repo : repos) {
                String repoId = repo.getString("_id");
                String statusOriginal = repo.getString("status");
                Document metrics = repo.get("metrics", Document.class);

                String statusLegible;
                String razon = "";

                if ("metrics_failed".equals(statusOriginal)) {
                    statusLegible = "fallido";
                    if (metrics != null) razon = metrics.getString("error");
                } else {
                    statusLegible = "solo_estatico";
                    if (metrics != null) {
                        Document dinamicas = metrics.get("dinamicas", Document.class);
                        if (dinamicas != null) razon = dinamicas.getString("razonSinDatos");
                    }
                }

                csv.append(csvCampo(repoId)).append(",")
                   .append(csvCampo(statusLegible)).append(",")
                   .append(csvCampo(razon)).append("\n");
            }

            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"incidencias.csv\"")
                    .body(bytes);

        } catch (Exception e) {
            String mensajeError = "No se pudo generar el CSV de incidencias: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mensajeError.getBytes(StandardCharsets.UTF_8));
        }
    }

    //-----> Exporta CSV con repos atorados por posible falta de memoria
    //-----> -para correrlos manualmente via el jar-
    @GetMapping("/api/metrics/export/atorados")
    public ResponseEntity<byte[]> exportarCsvAtorados() {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {
            List<Document> repos = almacen.obtenerRepositoriosAtorados();

            StringBuilder csv = new StringBuilder();
            csv.append("repo,link,comandoSugerido\n");

            for (Document repo : repos) {
                String repoId = repo.getString("_id");
                String link = repo.getString("htmlUrl");
                String comando = "java -cp app.jar almacenamiento.OrquestadorRepos --repo:" + repoId;

                csv.append(csvCampo(repoId)).append(",")
                   .append(csvCampo(link)).append(",")
                   .append(csvCampo(comando)).append("\n");
            }

            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"repos_atorados.csv\"")
                    .body(bytes);

        } catch (Exception e) {
            String mensajeError = "No se pudo generar el CSV de repos atorados: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mensajeError.getBytes(StandardCharsets.UTF_8));
        }
    }

    //-----> Escribe una linea de texto + salto de linea directo al ZIP
    private void escribirLinea(ZipOutputStream zip, String linea) throws IOException {
        zip.write((linea + "\n").getBytes(StandardCharsets.UTF_8));
    }

    //-----> Saca un valor anidado de un Document, o cadena vacia
    private String valorONulo(Document doc, String campo) {
        if (doc == null || !doc.containsKey(campo)) return "";
        Object valor = doc.get(campo);
        return valor == null ? "" : valor.toString();
    }

    //-----> Escapa comillas/comas para que el CSV no se rompa
    private String csvCampo(String valor) {
        if (valor == null) return "";
        String limpio = valor.replace("\"", "\"\"");
        return "\"" + limpio + "\"";
    }

    //-----> Extrae el nombre del metodo de un valor "Clase#metodo"
    private String extraerMetodo(String claveCompleta) {
        if (claveCompleta == null) return "";
        int idx = claveCompleta.indexOf('#');
        return idx >= 0 ? claveCompleta.substring(idx + 1) : "";
    }
}