package dinamica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscaneadorMetodosTest {

    // Copia la clase de prueba a una carpeta temporal para simular un proyecto real
    private void copiarClaseAPruebas(Path destinoRaiz, Class<?> clase) throws Exception {
        String recurso = clase.getName().replace('.', '/') + ".class";
        try (InputStream in = clase.getClassLoader().getResourceAsStream(recurso)) {
            assertNotNull(in, "No se encontró el archivo .class de prueba");
            Path destino = destinoRaiz.resolve(recurso);
            Files.createDirectories(destino.getParent());
            Files.copy(in, destino);
        }
    }

    @Test
    void escanearEncuentraMetodosSinParametrosEnUnaSolaCarpeta(@TempDir Path tempDir) throws Exception {
        copiarClaseAPruebas(tempDir, ClaseFixturePrueba.class);

        EscaneadorMetodos escaner = new EscaneadorMetodos();
        List<EscaneadorMetodos.MetodoObjetivo> catalogo = escaner.escanear(tempDir.toString());

        List<String> textos = catalogo.stream().map(EscaneadorMetodos.MetodoObjetivo::comoTexto).toList();
        
        // Verifica que encontró el método válido y descartó el que tenía parámetros
        assertTrue(textos.contains("dinamica.ClaseFixturePrueba#metodoValido"));
        assertFalse(textos.stream().anyMatch(t -> t.contains("metodoConParametro")));
        assertEquals(1, escaner.clasesEncontradas);
        assertEquals(0, escaner.clasesDescartadas);
    }

    @Test
    void escanearCombinaVariasCarpetasSeparadasPorPathSeparator(@TempDir Path tempDir) throws Exception {
        // Simula un proyecto dividido en dos carpetas diferentes
        Path modulo1 = Files.createDirectory(tempDir.resolve("modulo1"));
        Path modulo2 = Files.createDirectory(tempDir.resolve("modulo2"));
        copiarClaseAPruebas(modulo1, ClaseFixturePrueba.class);
        copiarClaseAPruebas(modulo2, ClaseFixturePrueba.class);

        // Une las rutas usando el separador del sistema (':' en Linux, ';' en Windows)
        String rutaCombinada = modulo1 + File.pathSeparator + modulo2;

        EscaneadorMetodos escaner = new EscaneadorMetodos();
        List<EscaneadorMetodos.MetodoObjetivo> catalogo = escaner.escanear(rutaCombinada);

        // Debe haber encontrado la clase en las dos rutas
        assertEquals(2, escaner.clasesEncontradas);
        long encontrados = catalogo.stream()
                .filter(m -> m.comoTexto().equals("dinamica.ClaseFixturePrueba#metodoValido"))
                .count();
        assertEquals(2, encontrados);
    }

    @Test
    void escanearLanzaExcepcionSiTodasLasRutasEstanVacias() {
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        // Si mandas puros espacios en blanco, debe lanzar error
        assertThrows(IllegalArgumentException.class,
                () -> escaner.escanear("   " + File.pathSeparator + "   "));
    }

    @Test
    void guardarCatalogoEscribeLasLineasEsperadas(@TempDir Path tempDir) throws Exception {
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        List<EscaneadorMetodos.MetodoObjetivo> catalogo = List.of(
                new EscaneadorMetodos.MetodoObjetivo("dinamica.Foo", "bar"),
                new EscaneadorMetodos.MetodoObjetivo("dinamica.Baz", "qux")
        );

        Path archivo = tempDir.resolve("catalogo_prueba.txt");
        escaner.guardarCatalogo(catalogo, archivo.toString());

        // Comprueba que el archivo guardado contenga las líneas correctas
        List<String> lineas = Files.readAllLines(archivo);
        assertEquals(List.of("dinamica.Foo#bar", "dinamica.Baz#qux"), lineas);
    }

    @Test
    void guardarResumenEscribeLosContadores(@TempDir Path tempDir) throws Exception {
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        escaner.clasesEncontradas = 10;
        escaner.clasesDescartadas = 2;
        escaner.metodosValidos = 7;
        escaner.metodosDescartados = 3;

        Path archivo = tempDir.resolve("resumen.txt");
        escaner.guardarResumen(archivo.toString());

        // Comprueba que el texto de resumen guarde todos los contadores
        List<String> lineas = Files.readAllLines(archivo);
        assertTrue(lineas.contains("clasesEncontradas=10"));
        assertTrue(lineas.contains("clasesDescartadas=2"));
        assertTrue(lineas.contains("metodosValidos=7"));
        assertTrue(lineas.contains("metodosDescartados=3"));
    }
}