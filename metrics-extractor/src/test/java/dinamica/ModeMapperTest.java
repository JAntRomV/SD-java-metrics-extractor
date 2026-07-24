package dinamica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static dinamica.ModeMapper.ModoEjecucion.*;

class ModeMapperTest {

    @Test
    void nuloDevuelveCompleto() {
        // Si no se especifica modo (null), usa el modo 'COMPLETO' por defecto
        assertEquals(COMPLETO, ModeMapper.obtenerModo(null));
    }

    @Test
    void fase1YBenchmarkDevuelvenBenchmarkGeneral() {
        // Traduce sin importar mayúsculas o minúsculas a BENCHMARK_GENERAL
        assertEquals(BENCHMARK_GENERAL, ModeMapper.obtenerModo("fase1"));
        assertEquals(BENCHMARK_GENERAL, ModeMapper.obtenerModo("benchmark"));
        assertEquals(BENCHMARK_GENERAL, ModeMapper.obtenerModo("BENCHMARK"));
    }

    @Test
    void fase2YCaminosDevuelvenCaminosInstrumentados() {
        // Traduce las palabras clave al modo CAMINOS_INSTRUMENTADOS
        assertEquals(CAMINOS_INSTRUMENTADOS, ModeMapper.obtenerModo("fase2"));
        assertEquals(CAMINOS_INSTRUMENTADOS, ModeMapper.obtenerModo("caminos"));
        assertEquals(CAMINOS_INSTRUMENTADOS, ModeMapper.obtenerModo("CAMINOS"));
    }

    @Test
    void valorDesconocidoODeVacioDevuelveCompleto() {
        // Si meten un texto inventado o vacío, vuelve a 'COMPLETO' de forma segura
        assertEquals(COMPLETO, ModeMapper.obtenerModo("algo-que-no-existe"));
        assertEquals(COMPLETO, ModeMapper.obtenerModo(""));
    }
}