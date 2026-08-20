package almacenamiento;

import org.bson.Document;

import java.util.List;
import java.util.Map;

//-----> Prueba la conexion y la lectura de datos
public class PruebaConexionMongo {

    //-----> Punto de entrada de la prueba
    public static void main(String[] args) {
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();

        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {

            long total = almacen.contarDocumentos();
            System.out.println("-----> Documentos en el catalogo: " + total);

            System.out.println("-----> Valores encontrados en el campo 'status':");
            Map<Object, Long> conteoStatus = almacen.contarPorValorDeStatus();
            for (Map.Entry<Object, Long> entrada : conteoStatus.entrySet()) {
                System.out.println("   \"" + entrada.getKey() + "\" -> " + entrada.getValue() + " documento(s)");
            }

            List<Document> pendientes = almacen.obtenerRepositoriosPendientes();
            System.out.println("-----> Repos con status 'pending': " + pendientes.size());

            System.out.println("\n----------------------------------------------------------");
            System.out.println(" LISTA DE REPOS PENDIENTES");
            System.out.println("----------------------------------------------------------");
            
            for (Document repo : pendientes) {
                String id = repo.getString("_id");
                String url = repo.getString("htmlUrl");
                Object rank = repo.getEmbedded(List.of("mining", "score", "rank"), Object.class);
                
                System.out.println("  [" + rank + "] " + id + " -> " + url);
            }

        } catch (Exception e) {
            System.err.println("-----> Error de conexion: " + e.getMessage());
        }
    }
}