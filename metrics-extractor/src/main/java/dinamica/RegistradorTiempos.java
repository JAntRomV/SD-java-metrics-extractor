package dinamica;

import java.io.IOException;

//-----> Controla la captura de tiempos por hilo
public class RegistradorTiempos {

    private static final ThreadLocal<TimeLogger> LOGGER_ACTUAL = new ThreadLocal<>();

    //-----> Obtiene el logger del hilo actual
    public static TimeLogger obtenerLoggerActual() {
        return LOGGER_ACTUAL.get();
    }

    //-----> Inicia un nuevo medidor de tiempo
    public static void iniciarLogger(String claveMetodo) {
        LOGGER_ACTUAL.set(new TimeLogger(claveMetodo, 0));
    }

    //-----> Asigna un logger existente al hilo
    public static void asignarLogger(TimeLogger logger) {
        LOGGER_ACTUAL.set(logger);
    }

    //-----> Limpia el logger al terminar
    public static void desactivarLogger() {
        LOGGER_ACTUAL.remove();
    }

    //-----> Registra una marca de tiempo
    public static void marcar(String etiqueta, boolean esNuevaIteracion) {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.logTime(etiqueta, esNuevaIteracion);
        }
    }

    //-----> Guarda las mediciones en un CSV
    public static void escribirCSV(String rutaArchivo) throws IOException {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.toCSV(rutaArchivo);
        }
    }
}