package almacenamiento;

import org.bson.Document;

import java.util.List;
import java.util.Map;

//-----> Programa de prueba rapida para verificar que la conexion a MongoDB funciona bien y se leen los datos.
public class PruebaConexionMongo {

    //-----> Prueba de lectura e impresion
    public static void main(String[] args) {
        //-----> Carga la configuracion leyendo el archivo .env o las variables de entorno.
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();

        //-----> Abre la conexion a MongoDB en un bloque try-with-resources (cierra la conexion automaticamente al finalizar).
        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {

            //-----> 1. Cuenta y muestra en la consola el numero total de documentos guardados en el catalogo.
            long total = almacen.contarDocumentos();
            System.out.println("-----> Documentos en el catalogo: " + total);

            //-----> Muestra conteo por status
            System.out.println("-----> Valores encontrados en el campo 'status':");
            Map<Object, Long> conteoStatus = almacen.contarPorValorDeStatus();
            for (Map.Entry<Object, Long> entrada : conteoStatus.entrySet()) {
                System.out.println("   \"" + entrada.getKey() + "\" -> " + entrada.getValue() + " documento(s)");
            }

            //-----> 2. Consulta y muestra la cantidad de repositorios que faltan por procesar.
            List<Document> pendientes = almacen.obtenerRepositoriosPendientes();
            System.out.println("-----> Repos con status 'pending': " + pendientes.size());

            System.out.println("\n----------------------------------------------------------");
            System.out.println(" LISTA DE REPOS PENDIENTES");
            System.out.println("----------------------------------------------------------");
            
            //-----> 3. Recorre la lista de repositorios pendientes e imprime sus datos principales en la pantalla.
            for (Document repo : pendientes) {
                //-----> Obtiene el identificador del repo (por ejemplo: nombre del usuario/repositorio).
                String id = repo.getString("_id");
                //-----> Obtiene la URL de GitHub para poder clonarlo.
                String url = repo.getString("htmlUrl");
                //-----> Obtiene la posicion del ranking dentro de los datos anidados de "mining.score.rank".
                Object rank = repo.getEmbedded(List.of("mining", "score", "rank"), Object.class);
                
                //-----> Imprime el ranking, la ID y la URL del repositorio.
                System.out.println("  [" + rank + "] " + id + " -> " + url);
            }

        } catch (Exception e) {
            //-----> Si la contraseña es incorrecta o no hay internet, muestra el mensaje de error aqui.
            System.err.println("-----> Error de conexion: " + e.getMessage());
        }
    }
}