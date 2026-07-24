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
        //-----> El constructor vacio no agrega el encabezado (_prevTimes queda null),
        //-----> asi que LOGS permanece vacio y logTime() no hace nada; toCSV no
        //-----> deberia crear ningun archivo.
        TimeLogger logger = new TimeLogger();
        logger.logTime("INSTR-1");

        Path archivo = tempDir.resolve("vacio.csv");
        logger.toCSV(archivo.toString());

        assertFalse(Files.exists(archivo), "No deberia crear el archivo si no hay registros");
    }

    @Test
    void logTimeAgregaFilasConClaseYParamCorrectos(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("dinamica.FixtureX#metodoY", 0);
        logger.logTime("INSTR-1", true);
        logger.logTime("INSTR-2", false);

        Path archivo = tempDir.resolve("salida.csv");
        logger.toCSV(archivo.toString());

        List<String> lineas = Files.readAllLines(archivo);
        //-----> Linea 0 = encabezado, 1 y 2 = las dos marcas registradas
        assertEquals(3, lineas.size());
        assertEquals("IDLog,Iteracion,Clase,ParamN,Etiqueta,TiempoNanos,FechaHora,DuracionNanos,DuracionNanosTime", lineas.get(0));

        String[] fila1 = lineas.get(1).split(",");
        assertEquals("1", fila1[0]);                          // IDLog
        assertEquals("1", fila1[1]);                          // Iteracion (la primera marca fue "nueva iteracion")
        assertEquals("dinamica.FixtureX#metodoY", fila1[2]);
        assertEquals("0", fila1[3]);
        assertEquals("INSTR-1", fila1[4]);

        String[] fila2 = lineas.get(2).split(",");
        assertEquals("2", fila2[0]);                          // IDLog sigue incrementando
        assertEquals("1", fila2[1]);                          // Iteracion NO cambia (isNewIteration=false)
        assertEquals("INSTR-2", fila2[4]);
    }

    @Test
    void logTimeIncrementaIteracionSoloCuandoSeIndica(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("Clase#metodo", 0);
        logger.logTime("A", true);   // iteracion 1
        logger.logTime("B", false);  // sigue en 1
        logger.logTime("C", true);   // iteracion 2

        Path archivo = tempDir.resolve("iteraciones.csv");
        logger.toCSV(archivo.toString());
        List<String> lineas = Files.readAllLines(archivo);

        assertEquals("1", lineas.get(1).split(",")[1]);
        assertEquals("1", lineas.get(2).split(",")[1]);
        assertEquals("2", lineas.get(3).split(",")[1]);
    }

    @Test
    void logTimeSinIsNewIterationEquivaleAFalse(@TempDir Path tempDir) throws Exception {
        TimeLogger logger = new TimeLogger("Clase#metodo", 0);
        logger.logTime("A"); // sobrecarga de un solo argumento -> isNewIteration=false

        Path archivo = tempDir.resolve("default.csv");
        logger.toCSV(archivo.toString());
        List<String> lineas = Files.readAllLines(archivo);

        assertEquals("0", lineas.get(1).split(",")[1], "Sin marcar nueva iteracion, el contador debe quedarse en 0");
    }
}
