package integracion.api;

import almacenamiento.AlmacenMetricasMongo;
import almacenamiento.ConfiguracionMongo;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

//-----> Al arrancar la aplicacion, revisa si quedaron repos a medias por un
//-----> reinicio inesperado del servidor (ej. el contenedor se quedo sin
//-----> memoria durante un analisis) y los marca como fallidos, para que el
//-----> frontend no siga mostrando informacion desactualizada como si el
//-----> analisis siguiera corriendo o hubiera terminado con exito.
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

            for (Document repo : huerfanos) {
                String idRepo = repo.getString("_id");
                System.out.println("-----> [RecuperacionInicio] Repo huerfano detectado, marcando como fallido: " + idRepo);
                almacen.marcarComoFallidoPorReinicio(idRepo, mensaje);
            }

            System.out.println("-----> [RecuperacionInicio] " + huerfanos.size() + " repo(s) huerfano(s) marcado(s) como fallidos.");

        } catch (Exception e) {
            System.err.println("-----> [RecuperacionInicio] No se pudo revisar repos huerfanos: " + e.getMessage());
        }
    }
}