package integracion.api;

import almacenamiento.AlmacenMetricasMongo;
import almacenamiento.ConfiguracionMongo;
import almacenamiento.DiagnosticoAlmacenamiento;
import almacenamiento.OrquestadorRepos;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

//-----> Expone tu framework de metricas (estatico + dinamico) como API REST
@RestController
public class MetricsController {

    //-----> Bandera en memoria: evita lanzar dos lotes de procesamiento al mismo tiempo
    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private volatile String ultimoInicio = null;
    private volatile String ultimoResultado = "sin ejecuciones todavia";

    //-----> Dispara el analisis en un hilo aparte y responde de inmediato.
    //-----> 🔌 MODIFICADO: ahora acepta un parametro opcional "repo". Si se
    //-----> manda (ej. POST /api/metrics/run?repo=owner/nombre), solo se
    //-----> procesa ESE repo. Si no se manda, se comporta igual que antes:
    //-----> procesa todos los repos pendientes del catalogo.
    //-----> 🔌 MODIFICADO: se agrega name="repo" explicito. Sin esto, Spring
    //-----> intenta averiguar el nombre del parametro leyendo el bytecode
    //-----> compilado, lo cual solo funciona si Maven compilo con la bandera
    //-----> -parameters. Como no era el caso aqui, CADA peticion a este
    //-----> endpoint truena con IllegalArgumentException antes de ejecutar
    //-----> una sola linea del metodo -ni siquiera llega a tocar Mongo-.
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

    //-----> Consulta rapida en memoria, no toca Mongo
    @GetMapping("/api/metrics/status")
    public Map<String, Object> status() {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("corriendo", corriendo.get());
        cuerpo.put("ultimoInicio", ultimoInicio);
        cuerpo.put("ultimoResultado", ultimoResultado);
        return cuerpo;
    }

    //-----> Reutiliza DiagnosticoAlmacenamiento.resumenGeneral() que ya tenias armado
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

    //-----> 🔌 NUEVO: lista completa del catalogo (para ver, ej., que repos
    //-----> estan "en progreso" y en que fase van: estatica/dinamica)
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

    //-----> 🔌 NUEVO: detalle puntual de un solo repo
    //-----> 🔌 MODIFICADO: mismo fix que en ejecutar() -- se agrega name="id"
    //-----> explicito para no depender de que Maven haya compilado con -parameters.
    //-----> Esto es lo que causaba el error 500 (Whitelabel Error Page) al abrir
    //-----> /api/metrics/repo?id=... directamente en el navegador.
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

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}