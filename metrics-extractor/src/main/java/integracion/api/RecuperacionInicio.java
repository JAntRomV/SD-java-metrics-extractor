package integracion.api;

import almacenamiento.AlmacenMetricasMongo;
import almacenamiento.ConfiguracionMongo;
import almacenamiento.EstadoAnalisis;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//-----> Al arrancar la aplicacion, revisa si quedaron repos a medias por un
//-----> reinicio inesperado del servidor (ej. el contenedor se quedo sin
//-----> memoria durante un analisis), los marca como fallidos en Mongo, y
//-----> reconstruye su progreso en EstadoAnalisis -que se borro con el
//-----> reinicio- para que el frontend pueda mostrar el X correcto en la
//-----> fase que fallo, en vez de quedarse sin nada que mostrar.
@Component
public class RecuperacionInicio implements CommandLineRunner {

    @Override
    public void run(String... args) {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();

        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {
            List<Document> huerfanos = almacen.obtenerRepositoriosEnProgreso();

            if (huerfanos.isEmpty()) {
                System.out.println("-----> [RecuperacionInicio] No hay repos huerfanos de una ejecucion anterior.");
                return;
            }

            String mensaje = "El servidor se reinicio de forma inesperada durante el analisis "
                    + "(posible falta de memoria en el contenedor). El repo quedo a medias y debe reintentarse.";

            //-----> Solo se muestra en pantalla el ultimo huerfano encontrado
            //-----> -es el que probablemente estaba viendo el usuario-, pero
            //-----> TODOS se marcan como fallidos en Mongo.
            Document ultimoHuerfano = null;

            for (Document repo : huerfanos) {
                String idRepo = repo.getString("_id");
                System.out.println("-----> [RecuperacionInicio] Repo huerfano detectado, marcando como fallido: " + idRepo);
                almacen.marcarComoFallidoPorReinicio(idRepo, mensaje);
                ultimoHuerfano = repo;
            }

            if (ultimoHuerfano != null) {
                EstadoAnalisis.restaurarTrasReinicio(ultimoHuerfano.getString("_id"), inferirFasesDesdeMongo(ultimoHuerfano));
            }

            System.out.println("-----> [RecuperacionInicio] " + huerfanos.size() + " repo(s) huerfano(s) marcado(s) como fallidos.");

        } catch (Exception e) {
            System.err.println("-----> [RecuperacionInicio] No se pudo revisar repos huerfanos: " + e.getMessage());
        }
    }

    //-----> AGREGADO: usa "metricsStatus" (lo unico que SI persiste en Mongo,
    //-----> actualizado por OrquestadorRepos.actualizarEstadoParcial) para
    //-----> adivinar hasta donde llego el repo antes del crash. No distingue
    //-----> "benchmarks" de "caminos" por separado -Mongo solo guarda un
    //-----> status combinado "dynamic"- asi que si la dinamica no llego a
    //-----> "complete", se asume que fallo en "benchmarks" (el paso mas
    //-----> comunmente responsable del OOM) y "caminos" se marca como omitida.
    private Map<String, EstadoAnalisis.EstadoFase> inferirFasesDesdeMongo(Document repo) {
        Map<String, EstadoAnalisis.EstadoFase> resultado = new LinkedHashMap<>();

        Document metricsStatus = repo.get("metricsStatus", Document.class);
        String estaticaStatus = metricsStatus != null ? metricsStatus.getString("static") : null;
        String dinamicaStatus = metricsStatus != null ? metricsStatus.getString("dynamic") : null;

        boolean estaticaCompleta = "complete".equals(estaticaStatus);

        if (!estaticaCompleta) {
            resultado.put("estatica", EstadoAnalisis.EstadoFase.FALLIDA);
            resultado.put("benchmarks", EstadoAnalisis.EstadoFase.OMITIDA);
            resultado.put("caminos", EstadoAnalisis.EstadoFase.OMITIDA);
            return resultado;
        }

        resultado.put("estatica", EstadoAnalisis.EstadoFase.COMPLETADA);

        boolean dinamicaCompleta = "complete".equals(dinamicaStatus);
        if (dinamicaCompleta) {
            //-----> Caso raro: dinamica ya habia terminado pero el crash paso
            //-----> justo antes de guardar el status final del repo
            resultado.put("benchmarks", EstadoAnalisis.EstadoFase.COMPLETADA);
            resultado.put("caminos", EstadoAnalisis.EstadoFase.COMPLETADA);
        } else {
            resultado.put("benchmarks", EstadoAnalisis.EstadoFase.FALLIDA);
            resultado.put("caminos", EstadoAnalisis.EstadoFase.OMITIDA);
        }

        return resultado;
    }
}