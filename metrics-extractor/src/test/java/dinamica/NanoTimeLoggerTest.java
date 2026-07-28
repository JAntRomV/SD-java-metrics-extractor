package dinamica;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NanoTimeLoggerTest {

    @Test
    void constructorYGettersDevuelvenLoAsignado() {
        LocalDateTime fecha = LocalDateTime.of(2026, 1, 1, 12, 0);
        NanoTimeLogger logger = new NanoTimeLogger(1000L, fecha, 5);

        // Confirma que los datos asignados al inicio se puedan leer bien
        assertEquals(1000L, logger.getPrevNanos());
        assertEquals(fecha, logger.getPrevFechaHora());
        assertEquals(5, logger.getIDLog());
    }

    @Test
    void settersActualizanLosValores() {
        NanoTimeLogger logger = new NanoTimeLogger(0L, LocalDateTime.now(), 0);

        LocalDateTime nuevaFecha = LocalDateTime.of(2030, 5, 20, 8, 30);
        logger.setPrevNanos(999L);
        logger.setPrevFechaHora(nuevaFecha);
        logger.setIDLog(42);

        // Confirma que los datos modificados se actualicen bien
        assertEquals(999L, logger.getPrevNanos());
        assertEquals(nuevaFecha, logger.getPrevFechaHora());
        assertEquals(42, logger.getIDLog());
    }
}