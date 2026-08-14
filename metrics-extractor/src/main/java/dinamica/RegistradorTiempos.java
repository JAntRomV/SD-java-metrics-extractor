package dinamica;

import java.io.IOException;

//-----> Administrador global thread-safe para acoplar la captura de tiempos usando ThreadLocal
public class RegistradorTiempos {

    private static final ThreadLocal<TimeLogger> LOGGER_ACTUAL = new ThreadLocal<>();

    //-----> Obtiene la instancia del TimeLogger asociada al hilo actual
    public static TimeLogger obtenerLoggerActual() {
        return LOGGER_ACTUAL.get();
    }

    //-----> Inicia un nuevo registrador de tiempos para la ejecucion del metodo
    public static void iniciarLogger(String claveMetodo) {
        LOGGER_ACTUAL.set(new TimeLogger(claveMetodo, 0));
    }

    //-----> Enlaza una instancia de logger existente al hilo en ejecucion
    public static void asignarLogger(TimeLogger logger) {
        LOGGER_ACTUAL.set(logger);
    }

    //-----> Desvincula y limpia el logger del hilo actual al finalizar la ejecucion
    public static void desactivarLogger() {
        LOGGER_ACTUAL.remove();
    }

    //-----> Captura un evento temporal intermedio durante la instruccion instrumentada
    public static void marcar(String etiqueta, boolean esNuevaIteracion) {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.logTime(etiqueta, esNuevaIteracion);
        }
    }

    //-----> Vuelca todos los puntos de medicion almacenados en el logger local hacia un CSV
    public static void escribirCSV(String rutaArchivo) throws IOException {
        TimeLogger logger = LOGGER_ACTUAL.get();
        if (logger != null) {
            logger.toCSV(rutaArchivo);
        }
    }
}