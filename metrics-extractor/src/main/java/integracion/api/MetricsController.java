package integracion.api;

import almacenamiento.ConfiguracionMongo;
import almacenamiento.DiagnosticoAlmacenamiento;
import almacenamiento.OrquestadorRepos;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

//-----> Endpoints REST para ejecutar y consultar metricas
@RestController
public class MetricsController {

    //-----> Estado y control de ejecucion en segundo plano
    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private volatile String ultimoInicio = null;
    private volatile String ultimoResultado = "sin ejecuciones todavia";

    //-----> Inicia el proceso de analisis en segundo plano
    @PostMapping("/api/metrics/run")
    public ResponseEntity<Map<String, Object>> ejecutar() {
        if (!corriendo.compareAndSet(false, true)) {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("iniciado", false);
            cuerpo.put("mensaje", "Ya hay un proceso corriendo, espera a que termine.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
        }

        ultimoInicio = Instant.now().toString();
        ultimoResultado = "en progreso";

        //-----> Hilo secundario para no bloquear la respuesta HTTP
        Thread hiloAnalisis = new Thread(() -> {
            try {
                OrquestadorRepos.ejecutarLote(new HashMap<>());
                ultimoResultado = "completado sin errores en " + Instant.now();
            } catch (Throwable e) {
                //-----> Captura fallos del analisis
                ultimoResultado = "fallo: " + e.getMessage();
            } finally {
                corriendo.set(false);
            }
        }, "metrics-run-thread");
        hiloAnalisis.start();

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("iniciado", true);
        cuerpo.put("mensaje", "Analisis iniciado en segundo plano");
        return ResponseEntity.accepted().body(cuerpo);
    }

    //-----> Devuelve el estado actual de la ejecucion
    @GetMapping("/api/metrics/status")
    public Map<String, Object> status() {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("corriendo", corriendo.get());
        cuerpo.put("ultimoInicio", ultimoInicio);
        cuerpo.put("ultimoResultado", ultimoResultado);
        return cuerpo;
    }

    //-----> Obtiene el resumen de almacenamiento Mongo
    @GetMapping("/api/metrics/summary")
    public ResponseEntity<?> summary() {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        try (DiagnosticoAlmacenamiento diagnostico = new DiagnosticoAlmacenamiento(config)) {
            //-----> Convierte el Document de BSON a JSON
            Document resumen = diagnostico.resumenGeneral();
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudo leer el resumen de Mongo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //-----> Endpoint de monitoreo de salud del servidor
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}