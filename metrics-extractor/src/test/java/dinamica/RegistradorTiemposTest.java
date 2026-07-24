package dinamica;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegistradorTiemposTest {

    //-----> LOGGER_ACTUAL es un ThreadLocal estatico compartido entre pruebas que
    //-----> corren en el mismo hilo; se limpia despues de cada prueba para que no
    //-----> se contaminen entre si.
    @AfterEach
    void limpiar() {
        RegistradorTiempos.desactivarLogger();
    }

    @Test
    void obtenerLoggerActualEsNuloSiNoSeHaIniciado() {
        assertNull(RegistradorTiempos.obtenerLoggerActual());
    }

    @Test
    void iniciarLoggerCreaUnoNuevoYQuedaDisponible() {
        RegistradorTiempos.iniciarLogger("dinamica.Foo#bar");
        assertNotNull(RegistradorTiempos.obtenerLoggerActual());
    }

    @Test
    void desactivarLoggerLoQuitaDelHilo() {
        RegistradorTiempos.iniciarLogger("dinamica.Foo#bar");
        RegistradorTiempos.desactivarLogger();
        assertNull(RegistradorTiempos.obtenerLoggerActual());
    }

    @Test
    void marcarNoLanzaExcepcionSiNoHayLoggerActivo() {
        //-----> Sin logger iniciado, marcar() debe ser un no-op seguro
        assertDoesNotThrow(() -> RegistradorTiempos.marcar("INSTR-1", true));
    }

    @Test
    void marcarRegistraEnElLoggerActivo(@TempDir Path tempDir) throws Exception {
        RegistradorTiempos.iniciarLogger("dinamica.Foo#bar");
        RegistradorTiempos.marcar("INSTR-1", true);
        RegistradorTiempos.marcar("INSTR-2", false);

        Path archivo = tempDir.resolve("marcado.csv");
        RegistradorTiempos.escribirCSV(archivo.toString());

        List<String> lineas = Files.readAllLines(archivo);
        assertEquals(3, lineas.size()); // encabezado + 2 marcas
    }

    @Test
    void escribirCSVNoHaceNadaSinLoggerActivo(@TempDir Path tempDir) throws Exception {
        Path archivo = tempDir.resolve("nada.csv");
        RegistradorTiempos.escribirCSV(archivo.toString());
        assertFalse(Files.exists(archivo));
    }

    @Test
    void asignarLoggerUsaLaInstanciaDadaEnVezDeCrearUnaNueva() {
        TimeLogger propio = new TimeLogger("dinamica.Foo#bar", 3);
        RegistradorTiempos.asignarLogger(propio);
        assertSame(propio, RegistradorTiempos.obtenerLoggerActual());
    }
}
