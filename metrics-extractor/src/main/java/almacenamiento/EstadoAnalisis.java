package almacenamiento;

import java.util.LinkedHashMap;
import java.util.Map;

//-----> Guarda en memoria el progreso del analisis que esta corriendo,
//-----> para que MetricsController lo pueda leer desde /api/metrics/status
public class EstadoAnalisis {

    //-----> Estados posibles de cada fase
    public enum EstadoFase {
        PENDIENTE, EN_PROGRESO, COMPLETADA, FALLIDA, OMITIDA
    }

    //-----> Orden fijo de las fases que se muestran en pantalla
    private static final String[] ORDEN_FASES = {"estatica", "benchmarks", "caminos"};

    private static volatile String repoActual = null;
    private static final Map<String, EstadoFase> fases = new LinkedHashMap<>();

    //-----> Reinicia el progreso al arrancar un repo nuevo
    public static synchronized void iniciarRepo(String idRepo) {
        repoActual = idRepo;
        fases.clear();
        for (String nombreFase : ORDEN_FASES) {
            fases.put(nombreFase, EstadoFase.PENDIENTE);
        }
    }

    //-----> Cambia el estado de una fase especifica
    public static synchronized void marcarFase(String nombreFase, EstadoFase estado) {
        fases.put(nombreFase, estado);
    }

    //-----> Marca como OMITIDA cualquier fase pendiente posterior a la indicada
    //-----> (ej. si benchmarks falla, caminos nunca llega a correr)
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

    //-----> Cubre el caso de un crash inesperado: la fase que estaba
    //-----> en progreso pasa a fallida y las que faltan se omiten
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

    public static synchronized String getRepoActual() {
        return repoActual;
    }

    //-----> Devuelve una copia en el orden fijo de fases
    public static synchronized Map<String, EstadoFase> getFases() {
        return new LinkedHashMap<>(fases);
    }
}