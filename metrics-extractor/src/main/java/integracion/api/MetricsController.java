package integracion.api;

import almacenamiento.AlmacenMetricasMongo;
import almacenamiento.ConfiguracionMongo;
import almacenamiento.DiagnosticoAlmacenamiento;
import almacenamiento.EstadoAnalisis;
import almacenamiento.OrquestadorRepos;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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

        //-----> Evita ejecuciones simultaneas
        if (!corriendo.compareAndSet(false, true)) {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("iniciado", false);
            cuerpo.put("mensaje", "Ya hay un proceso corriendo, espera a que termine.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
        }

        //-----> Registra inicio de ejecucion
        ultimoInicio = Instant.now().toString();
        ultimoResultado = "en progreso";

        //-----> Crea y dispara hilo de trabajo
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

        //-----> Respuesta confirmando el arranque
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

        //-----> Arma la lista de fases (nombre + estado) para el frontend
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
}