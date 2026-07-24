package dinamica;

import java.io.IOException;

//-----> Puente accesible globalmente (ThreadLocal) para registrar marcas de tiempo durante la ejecución
public class RegistradorTiempos {

    // Guarda una instancia independiente de TimeLogger por cada hilo de ejecución
    private static final ThreadLocal<TimeLogger> LOGGER_ACTUAL = new ThreadLocal<>();

    public static TimeLogger obtenerLoggerActual() {
        return LOGGER_ACTUAL.get();
    }

    // Inicia un nuevo registrador para un método específico
    public static void iniciarLogger(String claveMetodo) {
        LOGGER_ACTUAL.set(new TimeLogger(claveMetodo, 0));
    }

    public static void asignarLogger(TimeLogger logger) {
        LOGGER_ACTUAL.set(logger);
    }

    public static void desactivarLogger() {
        LOGGER_ACTUAL.remove();
    }

    // Es invocado por las llamadas inyectadas dinamica.RegistradorTiempos.marcar(...)
    public static void marcar(String etiqueta, boolean esNuevaIteracion) {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.logTime(etiqueta, esNuevaIteracion);
        }
    }

    // Guarda el informe de tiempos hacia el CSV al terminar
    public static void escribirCSV(String rutaArchivo) throws IOException {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.toCSV(rutaArchivo);
        }
    }
}