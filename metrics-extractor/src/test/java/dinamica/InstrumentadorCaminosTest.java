package dinamica;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentadorCaminosTest {

    // Código Java de prueba simple en texto
    private static final String FIXTURE_SIMPLE = """
            package fixture;

            public class FixtureSimple {
                public FixtureSimple() {
                }

                public int metodoSimple() {
                    int a = 1;
                    int b = 2;
                    return a + b;
                }
            }
            """;

    // Código Java de prueba con decisiones (if / else)
    private static final String FIXTURE_CON_RAMA = """
            package fixture;

            public class FixtureConRama {
                public FixtureConRama() {
                }

                public int metodoConRama() {
                    int valor = 5;
                    if (valor > 0) {
                        valor = valor + 1;
                    } else {
                        valor = valor - 1;
                    }
                    return valor;
                }
            }
            """;

    // Cuenta cuántas marcas de medición de tiempo se inyectaron
    private int contarMarcas(String contenido) {
        Matcher m = Pattern.compile(Pattern.quote("RegistradorTiempos.marcar(")).matcher(contenido);
        int cuenta = 0;
        while (m.find()) cuenta++;
        return cuenta;
    }

    @Test
    void instrumentaUnMetodoSimpleConTresInstrucciones(@TempDir Path tempDir) throws Exception {
        Path original = tempDir.resolve("FixtureSimple.java");
        Files.writeString(original, FIXTURE_SIMPLE, StandardCharsets.UTF_8);

        Path carpetaSalida = tempDir.resolve("salida");

        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();
        String rutaResultado = instrumentador.instrumentar(original.toString(), "metodoSimple", carpetaSalida.toString());

        String contenido = Files.readString(Path.of(rutaResultado), StandardCharsets.UTF_8);

        // Como el método tiene 3 instrucciones, debe tener 3 marcas inyectadas
        assertEquals(3, contarMarcas(contenido));

        // Verifica que el resultado siga siendo un archivo Java válido y sin errores de sintaxis
        assertDoesNotThrow(() -> StaticJavaParser.parse(new File(rutaResultado)));
    }

    @Test
    void instrumentaTambienLasInstruccionesDentroDeRamasIfElse(@TempDir Path tempDir) throws Exception {
        Path original = tempDir.resolve("FixtureConRama.java");
        Files.writeString(original, FIXTURE_CON_RAMA, StandardCharsets.UTF_8);

        Path carpetaSalida = tempDir.resolve("salida");

        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();
        String rutaResultado = instrumentador.instrumentar(original.toString(), "metodoConRama", carpetaSalida.toString());

        String contenido = Files.readString(Path.of(rutaResultado), StandardCharsets.UTF_8);

        // Verifica que también se inyectaron marcas dentro del 'if' y del 'else' (total: 5 marcas)
        assertEquals(5, contarMarcas(contenido));
        assertTrue(contenido.contains("valor = valor + 1"), "No debe perder la instrucción de la rama 'then'");
        assertTrue(contenido.contains("valor = valor - 1"), "No debe perder la instrucción de la rama 'else'");

        assertDoesNotThrow(() -> StaticJavaParser.parse(new File(rutaResultado)));
    }

    @Test
    void lanzaExcepcionSiElMetodoObjetivoNoTieneCodigoFuente(@TempDir Path tempDir) throws Exception {
        Path original = tempDir.resolve("FixtureSimple.java");
        Files.writeString(original, FIXTURE_SIMPLE, StandardCharsets.UTF_8);

        Path carpetaSalida = tempDir.resolve("salida");
        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();

        // Debe dar error si intentamos instrumentar un método que no está escrito en el archivo .java
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> instrumentador.instrumentar(original.toString(), "metodoQueNoExisteEnElFuente", carpetaSalida.toString()));

        assertTrue(ex.getMessage().contains("no tiene codigo fuente explicito"));
    }

    @Test
    void noModificaElArchivoOriginal(@TempDir Path tempDir) throws Exception {
        Path original = tempDir.resolve("FixtureSimple.java");
        Files.writeString(original, FIXTURE_SIMPLE, StandardCharsets.UTF_8);

        Path carpetaSalida = tempDir.resolve("salida");
        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();
        instrumentador.instrumentar(original.toString(), "metodoSimple", carpetaSalida.toString());

        // Comprueba que el archivo original se mantuvo intacto sin modificaciones
        String contenidoOriginal = Files.readString(original, StandardCharsets.UTF_8);
        assertEquals(FIXTURE_SIMPLE, contenidoOriginal, "El archivo original no debe sufrir cambios");
    }
}