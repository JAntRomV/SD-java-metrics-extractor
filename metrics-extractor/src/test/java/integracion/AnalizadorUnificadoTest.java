package integracion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class AnalizadorUnificadoTest {

    // -----> Guarda las salidas de error en memoria para poder verificar mensajes sin ensuciar la consola.
    private final ByteArrayOutputStream salidaError = new ByteArrayOutputStream();
    private PrintStream errorOriginal;

    // -----> Se ejecuta ANTES de cada prueba para capturar las salidas de error (System.err).
    @BeforeEach
    void redirigirSystemErr() {
        errorOriginal = System.err;
        System.setErr(new PrintStream(salidaError));
    }

    // -----> Se ejecuta DESPUES de cada prueba para restaurar el System.err original a la normalidad.
    @AfterEach
    void restaurarSystemErr() {
        System.setErr(errorOriginal);
    }

    // -----> Helpers de reflexion para probar los metodos privados directamente,
    // -----> sin duplicar su logica ni tener que exponerlos como publicos solo por pruebas.
    @SuppressWarnings("unchecked")
    private Map<String, String> invocarParseArgs(String[] args) throws Exception {
        Method m = AnalizadorUnificado.class.getDeclaredMethod("parseArgs", String[].class);
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(null, (Object) args);
    }

    private String[] invocarConSalidaOverride(String[] args, String nuevaSalida) throws Exception {
        Method m = AnalizadorUnificado.class.getDeclaredMethod("conSalidaOverride", String[].class, String.class);
        m.setAccessible(true);
        return (String[]) m.invoke(null, (Object) args, nuevaSalida);
    }

    // ------------------------- parseArgs -------------------------

    // -----> Prueba que parseArgs extraiga correctamente las llaves y valores cuando vienen en formato --clave:valor.
    @Test
    void parseArgsDebeExtraerClaveYValorDeArgumentosValidos() throws Exception {
        String[] args = {"--proyecto:/ruta/proyecto", "--salida:resultados"};

        Map<String, String> params = invocarParseArgs(args);

        assertEquals("/ruta/proyecto", params.get("proyecto"));
        assertEquals("resultados", params.get("salida"));
        assertEquals(2, params.size());
    }

    // -----> Prueba que parseArgs ignore argumentos que no inicien con el doble guion "--".
    @Test
    void parseArgsDebeIgnorarArgumentosSinPrefijoDobleGuion() throws Exception {
        String[] args = {"proyecto:/ruta/proyecto", "--salida:resultados"};

        Map<String, String> params = invocarParseArgs(args);

        assertFalse(params.containsKey("proyecto"));
        assertEquals("resultados", params.get("salida"));
        assertEquals(1, params.size());
    }

    // -----> Prueba que parseArgs ignore opciones mal formadas que no tengan los dos puntos ":".
    @Test
    void parseArgsDebeIgnorarArgumentosSinDosPuntos() throws Exception {
        String[] args = {"--proyecto", "--salida:resultados"};

        Map<String, String> params = invocarParseArgs(args);

        assertFalse(params.containsKey("proyecto"));
        assertEquals("resultados", params.get("salida"));
        assertEquals(1, params.size());
    }

    // -----> Prueba que no se rompan las rutas o valores que contengan mas de un par de dos puntos ":".
    @Test
    void parseArgsDebePreservarDosPuntosAdicionalesDentroDelValor() throws Exception {
        // -----> Simula --classpath con varias rutas unidas por el separador de
        // -----> classpath del sistema (":" en Linux/Mac), que NO debe cortarse
        // -----> porque split(":", 2) limita el corte a solo la primera ocurrencia.
        String[] args = {"--classpath:/ruta/a:/ruta/b:/ruta/c"};

        Map<String, String> params = invocarParseArgs(args);

        assertEquals("/ruta/a:/ruta/b:/ruta/c", params.get("classpath"));
    }

    // -----> Prueba que parseArgs retorne un mapa vacio si se le pasa un arreglo de argumentos sin elementos.
    @Test
    void parseArgsConArregloVacioDebeRegresarMapaVacio() throws Exception {
        Map<String, String> params = invocarParseArgs(new String[0]);

        assertTrue(params.isEmpty());
    }

    // ------------------------- conSalidaOverride -------------------------

    // -----> Prueba que se reemplace el valor de --salida si este argumento ya venia en el arreglo original.
    @Test
    void conSalidaOverrideDebeReemplazarSalidaExistente() throws Exception {
        String[] original = {"--proyecto:/ruta/proyecto", "--salida:resultados", "--I:5"};

        String[] resultado = invocarConSalidaOverride(original, "resultados/resultados_dinamicos");

        assertArrayEquals(
                new String[]{"--proyecto:/ruta/proyecto", "--salida:resultados/resultados_dinamicos", "--I:5"},
                resultado
        );
    }

    // -----> Prueba que se agregue el argumento --salida al final si no venia definido originalmente.
    @Test
    void conSalidaOverrideDebeAgregarSalidaSiNoExistiaOriginalmente() throws Exception {
        String[] original = {"--proyecto:/ruta/proyecto", "--I:5"};

        String[] resultado = invocarConSalidaOverride(original, "resultados/resultados_dinamicos");

        assertArrayEquals(
                new String[]{"--proyecto:/ruta/proyecto", "--I:5", "--salida:resultados/resultados_dinamicos"},
                resultado
        );
    }

    // -----> Prueba que la funcion no altere ni pierda el resto de banderas o argumentos.
    @Test
    void conSalidaOverrideDebePreservarElRestoDeArgumentosIntactos() throws Exception {
        String[] original = {"--batchSize:50", "--batchIndex:0", "--classpath:/a:/b"};

        String[] resultado = invocarConSalidaOverride(original, "salida_nueva");

        assertEquals(4, resultado.length);
        assertEquals("--batchSize:50", resultado[0]);
        assertEquals("--batchIndex:0", resultado[1]);
        assertEquals("--classpath:/a:/b", resultado[2]);
        assertEquals("--salida:salida_nueva", resultado[3]);
    }

    // -----> Prueba que si el arreglo inicial esta vacio, solo agregue la bandera --salida.
    @Test
    void conSalidaOverrideConArregloVacioDebeSoloAgregarSalida() throws Exception {
        String[] resultado = invocarConSalidaOverride(new String[0], "resultados_dinamicos");

        assertArrayEquals(new String[]{"--salida:resultados_dinamicos"}, resultado);
    }

    // ------------------------- main() (camino de salida temprana) -------------------------

    // -----> Prueba que si falta el argumento obligatorio --proyecto, el programa no lance error fatal y muestre las instrucciones de uso.
    @Test
    void mainSinProyectoDebeImprimirUsoYNoLanzarExcepcion() {
        assertDoesNotThrow(() -> AnalizadorUnificado.main(new String[]{"--salida:resultados"}));

        String mensaje = salidaError.toString();
        assertTrue(mensaje.contains("Uso:"));
        assertTrue(mensaje.contains("integracion.AnalizadorUnificado"));
    }

    // -----> Prueba la salida temprana ejecutando el metodo main sin ningun parametro.
    @Test
    void mainSinArgumentosDebeComportarseIgualQueSinProyecto() {
        assertDoesNotThrow(() -> AnalizadorUnificado.main(new String[0]));

        String mensaje = salidaError.toString();
        assertTrue(mensaje.contains("--proyecto"));
    }
}