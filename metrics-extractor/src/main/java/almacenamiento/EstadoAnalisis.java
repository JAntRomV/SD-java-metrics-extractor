package almacenamiento;

import java.util.LinkedHashMap;
import java.util.Map;

//-----> Guarda en memoria el progreso del analisis que esta corriendo,
//-----> para que MetricsController lo pueda leer desde /api/metrics/status
public class EstadoAnalisis {

    public enum EstadoFase {
        PENDIENTE, EN_PROGRESO, COMPLETADA, FALLIDA, OMITIDA
    }

    private static final String[] ORDEN_FASES = {"estatica", "benchmarks", "caminos"};

    private static volatile String repoActual = null;
    private static final Map<String, EstadoFase> fases = new LinkedHashMap<>();

    public static synchronized void iniciarRepo(String idRepo) {
        repoActual = idRepo;
        fases.clear();
        for (String nombreFase : ORDEN_FASES) {
            fases.put(nombreFase, EstadoFase.PENDIENTE);
        }
    }

    public static synchronized void marcarFase(String nombreFase, EstadoFase estado) {
        fases.put(nombreFase, estado);
    }

    public static synchronized void omitirRestantesDesdeDe(String nombreFase) {
        boolean yaPaso = false;
        for (String f : ORDEN_FASES) {
            if (f.equals(nombreFase)) {
                yaPaso = true;
                continue;
            }
            if (yaPaso && fases.getOrDefault(f, EstadoFase.PENDIENTE) == EstadoFase.PENDIENTE) {
                fases.put(f, EstadoFase.OMITIDA);
            }
        }
    }

    public static synchronized void marcarFallaGeneral() {
        boolean encontroEnProgreso = false;
        for (String f : ORDEN_FASES) {
            EstadoFase actual = fases.getOrDefault(f, EstadoFase.PENDIENTE);
            if (actual == EstadoFase.EN_PROGRESO) {
                fases.put(f, EstadoFase.FALLIDA);
                encontroEnProgreso = true;
            } else if (encontroEnProgreso && actual == EstadoFase.PENDIENTE) {
                fases.put(f, EstadoFase.OMITIDA);
            }
        }
    }

    //-----> AGREGADO: reconstruye el progreso visible despues de un reinicio
    //-----> inesperado del servidor. Como EstadoAnalisis se borro al reiniciar,
    //-----> RecuperacionInicio usa esto para "recordarle" al frontend, en base
    //-----> a lo que SI quedo guardado en Mongo, cual fase alcanzo a completarse
    //-----> antes del crash y cuales quedaron marcadas como fallidas/omitidas.
    public static synchronized void restaurarTrasReinicio(String idRepo, Map<String, EstadoFase> fasesConocidas) {
        repoActual = idRepo;
        fases.clear();
        for (String nombreFase : ORDEN_FASES) {
            fases.put(nombreFase, fasesConocidas.getOrDefault(nombreFase, EstadoFase.PENDIENTE));
        }
    }

    public static synchronized String getRepoActual() {
        return repoActual;
    }

    public static synchronized Map<String, EstadoFase> getFases() {
        return new LinkedHashMap<>(fases);
    }
}