package almacenamiento;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionMongoTest {

    // -----> obtenerObligatoria es privado y estatico, pero es la unica parte
    // -----> de esta clase que es pura (no toca System.getenv() ni el cwd),
    // -----> asi que es la unica que se puede testear de forma confiable.
    private String invocarObtenerObligatoria(Map<String, String> valores, String nombreVariable) throws Exception {
        Method m = ConfiguracionMongo.class.getDeclaredMethod("obtenerObligatoria", Map.class, String.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(null, valores, nombreVariable);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    // -----> Instancia directamente via el constructor privado, sin pasar por
    // -----> desdeVariablesDeEntorno() (que exige variables de entorno reales).
    private ConfiguracionMongo crearInstanciaDirecta(String uri, String baseDatos, String coleccion,
                                                       String coleccionClases, String coleccionDinamicas) throws Exception {
        Constructor<ConfiguracionMongo> c = ConfiguracionMongo.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class);
        c.setAccessible(true);
        return c.newInstance(uri, baseDatos, coleccion, coleccionClases, coleccionDinamicas);
    }

    @Test
    void obtenerObligatoriaConValorPresenteLoRegresaTalCual() throws Exception {
        Map<String, String> valores = new HashMap<>();
        valores.put("MONGO_URI", "mongodb://localhost:27017");

        assertEquals("mongodb://localhost:27017", invocarObtenerObligatoria(valores, "MONGO_URI"));
    }

    @Test
    void obtenerObligatoriaConValorNuloLanzaExcepcion() {
        Map<String, String> valores = new HashMap<>();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invocarObtenerObligatoria(valores, "MONGO_URI"));
        assertTrue(ex.getMessage().contains("Falta MONGO_URI"));
    }

    @Test
    void obtenerObligatoriaConValorEnBlancoLanzaExcepcion() {
        Map<String, String> valores = new HashMap<>();
        valores.put("MONGO_URI", "   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invocarObtenerObligatoria(valores, "MONGO_URI"));
        assertTrue(ex.getMessage().contains("Falta MONGO_URI"));
    }

    @Test
    void construirUriDevuelveLaUriAsignada() throws Exception {
        ConfiguracionMongo config = crearInstanciaDirecta(
                "mongodb+srv://user:pass@cluster/db", "miBaseDatos", "miColeccion",
                "miColeccionClases", "miColeccionDinamicas");

        assertEquals("mongodb+srv://user:pass@cluster/db", config.construirUri());
    }

    @Test
    void losCamposPublicosQuedanAsignadosCorrectamente() throws Exception {
        ConfiguracionMongo config = crearInstanciaDirecta(
                "mongodb://localhost", "baseDatos1", "coleccion1", "clases1", "dinamicas1");

        assertEquals("mongodb://localhost", config.uri);
        assertEquals("baseDatos1", config.baseDatos);
        assertEquals("coleccion1", config.coleccion);
        assertEquals("clases1", config.coleccionClases);
        assertEquals("dinamicas1", config.coleccionDinamicas);
    }
}