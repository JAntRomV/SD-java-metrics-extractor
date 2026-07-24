package dinamica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeLoggerTest {

    @Test
    void toCSVNoEscribeNadaSiNoHayRegistros(@TempDir Path tempDir) throws Exception {
        // Si no se registraron datos, no debe crear el archivo CSV en vano
        TimeLogger logger = new TimeLogger();
        logger.logTime("INSTR-1");

        Path archivo = tempDir.resolve("vacio.csv");
        logger.toCSV(archivo.toString());

        assertFalse(Files.exists(archivo), "No debe crear el archivo si no hay marcas registradas");
    }

    @Test
    void logTimeAgregaFilasConClaseYParamCorrectos(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("dinamica.FixtureX#metodoY", 0);
        logger.logTime("INSTR-1", true);
        logger.logTime("INSTR-2", false);

        Path archivo = tempDir.resolve("salida.csv");
        logger.toCSV(archivo.toString());

        List<String> lineas = Files.readAllLines(archivo);
        
        // Verifica que tenga el encabezado y las 2 marcas guardadas
        assertEquals(3, lineas.size());
        assertEquals("IDLog,Iteracion,Clase,ParamN,Etiqueta,TiempoNanos,FechaHora,DuracionNanos,DuracionNanosTime", lineas.get(0));

        // Revisa los datos de la primera marca
        String[] fila1 = lineas.get(1).split(",");
        assertEquals("1", fila1[0]); // IDLog
        assertEquals("1", fila1[1]); // Iteración
        assertEquals("dinamica.FixtureX#metodoY", fila1[2]);
        assertEquals("0", fila1[3]);
        assertEquals("INSTR-1", fila1[4]);

        // Revisa los datos de la segunda marca
        String[] fila2 = lineas.get(2).split(",");
        assertEquals("2", fila2[0]); // IDLog incrementado
        assertEquals("1", fila2[1]); // Sigue en la iteración 1
        assertEquals("INSTR-2", fila2[4]);
    }

    @Test
    void logTimeIncrementaIteracionSoloCuandoSeIndica(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("Clase#metodo", 0);
        logger.logTime("A", true);   // Marca inicio de Iteración 1
        logger.logTime("B", false);  // Sigue en Iteración 1
        logger.logTime("C", true);   // Marca inicio de Iteración 2

        Path archivo = tempDir.resolve("iteraciones.csv");
        logger.toCSV(archivo.toString());
        List<String> lineas = Files.readAllLines(archivo);

        // Verifica que los números de iteración cambien de forma adecuada
        assertEquals("1", lineas.get(1).split(",")[1]);
        assertEquals("1", lineas.get(2).split(",")[1]);
        assertEquals("2", lineas.get(3).split(",")[1]);
    }

    @Test
    void logTimeSinIsNewIterationEquivaleAFalse(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("Clase#metodo", 0);
        logger.logTime("A"); // Por defecto no arranca una nueva iteración

        Path archivo = tempDir.resolve("default.csv");
        logger.toCSV(archivo.toString());
        List<String> lineas = Files.readAllLines(archivo);

        // Se queda en la iteración 0 por defecto
        assertEquals("0", lineas.get(1).split(",")[1], "La iteración debe mantenerse en 0");
    }
}