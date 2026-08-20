package almacenamiento;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

//-----> Lee credenciales y colecciones de MongoDB
public class ConfiguracionMongo {

    public final String uri;
    public final String baseDatos;
    public final String coleccion;
    public final String coleccionClases;
    public final String coleccionDinamicas;

    //-----> Constructor con las variables directas
    private ConfiguracionMongo(String uri, String baseDatos, String coleccion, String coleccionClases, String coleccionDinamicas) {
        this.uri = uri;
        this.baseDatos = baseDatos;
        this.coleccion = coleccion;
        this.coleccionClases = coleccionClases;
        this.coleccionDinamicas = coleccionDinamicas;
    }

    //-----> Carga variables desde el entorno o .env
    public static ConfiguracionMongo desdeVariablesDeEntorno() {
        Map<String, String> valores = combinarEntornoYArchivoEnv();

        String uri = obtenerObligatoria(valores, "MONGO_URI");
        String baseDatos = valores.getOrDefault("MONGO_DATABASE", "shared_catalog");
        String coleccion = valores.getOrDefault("MONGO_COLLECTION", "repo_catalog");

        String coleccionClases = valores.getOrDefault("MONGO_CLASS_COLLECTION", "repo_metrics_static");
        String coleccionDinamicas = valores.getOrDefault("MONGO_DYNAMIC_COLLECTION", "repo_metrics_dynamic");

        return new ConfiguracionMongo(uri, baseDatos, coleccion, coleccionClases, coleccionDinamicas);
    }

    //-----> Une variables del sistema con el .env
    private static Map<String, String> combinarEntornoYArchivoEnv() {
        Map<String, String> resultado = new HashMap<>(leerArchivoEnvSiExiste());
        resultado.putAll(System.getenv());
        return resultado;
    }

    //-----> Lee claves y valores del archivo .env
    private static Map<String, String> leerArchivoEnvSiExiste() {
        Map<String, String> valores = new HashMap<>();
        File archivoEnv = new File(".env");
        if (!archivoEnv.exists()) {
            return valores;
        }

        try {
            for (String linea : Files.readAllLines(archivoEnv.toPath())) {
                String limpia = linea.trim();
                if (limpia.isEmpty() || limpia.startsWith("#")) continue;

                int idx = limpia.indexOf('=');
                if (idx == -1) continue;

                String clave = limpia.substring(0, idx).trim();
                String valor = limpia.substring(idx + 1).trim();

                if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
                    valor = valor.substring(1, valor.length() - 1);
                }

                valores.put(clave, valor);
            }
        } catch (Exception e) {
            System.err.println("-----> No se pudo leer el archivo .env: " + e.getMessage());
        }

        return valores;
    }

    //-----> Revisa que la variable no este vacia
    private static String obtenerObligatoria(Map<String, String> valores, String nombreVariable) {
        String valor = valores.get(nombreVariable);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta " + nombreVariable
                    + ". Exportala como variable de entorno o ponla en un archivo .env en la raiz del proyecto.");
        }
        return valor;
    }

    //-----> Retorna la URI de conexion
    public String construirUri() {
        return uri;
    }
}