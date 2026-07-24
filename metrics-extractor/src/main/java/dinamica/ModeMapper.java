package dinamica;

//-----> Traductor de opciones de terminal a modos de ejecución del programa
public class ModeMapper {

    // Lista de las 3 formas en las que puede trabajar el programa
    public enum ModoEjecucion {
        BENCHMARK_GENERAL,      // Solo corre la Fase 1 (Tiempos generales y memoria con JMH)
        CAMINOS_INSTRUMENTADOS, // Solo corre la Fase 2 (Cronómetro línea por línea)
        COMPLETO                // Corre las dos fases una tras otra
    }

    // Convierte el texto que escribe el usuario en la terminal a un valor del ENUM
    public static ModoEjecucion obtenerModo(String modo) {
        // Si no se especifica modo, por defecto hace el proceso COMPLETO
        if (modo == null) return ModoEjecucion.COMPLETO;
        
        switch (modo.toLowerCase()) {
            case "fase1":
            case "benchmark":
                return ModoEjecucion.BENCHMARK_GENERAL;
            case "fase2":
            case "caminos":
                return ModoEjecucion.CAMINOS_INSTRUMENTADOS;
            default:
                return ModoEjecucion.COMPLETO;
        }
    }
}