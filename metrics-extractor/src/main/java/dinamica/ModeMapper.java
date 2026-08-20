package dinamica;

//-----> Convierte opciones de entrada a enum de modos
public class ModeMapper {

    //-----> Opciones de ejecución del programa
    public enum ModoEjecucion {
        BENCHMARK_GENERAL,
        CAMINOS_INSTRUMENTADOS,
        COMPLETO
    }

    //-----> Mapea el texto ingresado al valor Enum
    public static ModoEjecucion obtenerModo(String modo) {
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