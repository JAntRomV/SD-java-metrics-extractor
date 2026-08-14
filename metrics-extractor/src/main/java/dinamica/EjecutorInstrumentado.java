package dinamica;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

//-----> Carga, compila dinamicamente y ejecuta metodos instrumentados por reflexiion con timeout
public class EjecutorInstrumentado {

    //-----> Pool de hilos daemon para controlar la duracion máxima de ejecucion de metodos
    private static final java.util.concurrent.ExecutorService EJECUTOR_TIMEOUT =
            java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
                Thread hilo = new Thread(runnable);
                hilo.setDaemon(true);
                return hilo;
            });

    //-----> Invoca el metodo por reflexiion deteniendo la ejecucion si excede el tiempo límite
    private static void invocarConTimeout(Method metodo, Object instancia, long segundosLimite) throws Exception {
        TimeLogger loggerDelHiloLlamador = RegistradorTiempos.obtenerLoggerActual();

        java.util.concurrent.Future<?> tarea = EJECUTOR_TIMEOUT.submit(() -> {
            try {
                RegistradorTiempos.asignarLogger(loggerDelHiloLlamador);
                metodo.invoke(instancia);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        try {
            tarea.get(segundosLimite, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            tarea.cancel(true);
            throw new IllegalStateException("El metodo tardo mas de " + segundosLimite + "s, posible bucle infinito.");
        }
    }

    //-----> Instrumenta el codigo, lo compila sobre la marcha y registra los tiempos de execucion por camino
    public static String medirCamino(
            String rutaArchivoOriginal,
            String nombreMetodo,
            String carpetaSalida,
            String classpathExtra) throws Exception {

        String carpetaTrabajo = carpetaSalida + "/_temp_instrumentado";
        new File(carpetaTrabajo).mkdirs();

        //-----> Inserta llamadas de medicion en el codigo fuente original
        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();
        String rutaInstrumentado = instrumentador.instrumentar(rutaArchivoOriginal, nombreMetodo, carpetaTrabajo);

        JavaCompiler compilador = ToolProvider.getSystemJavaCompiler();
        if (compilador == null) {
            throw new IllegalStateException("No hay compilador disponible en el entorno JDK.");
        }

        //-----> Compila el archivo .java modificado en el directorio temporal
        String classpathCompleto = System.getProperty("java.class.path") + File.pathSeparator + classpathExtra;
        int codigoSalida = compilador.run(null, null, null,
                "-d", carpetaTrabajo, "-cp", classpathCompleto, rutaInstrumentado);
        if (codigoSalida != 0) {
            throw new IllegalStateException("La compilacion del archivo instrumentado fallo.");
        }

        String claseCompleta = obtenerClaseCompleta(rutaInstrumentado);

        List<URL> urls = new ArrayList<>();
        urls.add(new File(carpetaTrabajo).toURI().toURL());
        for (String ruta : classpathExtra.split(File.pathSeparator)) {
            if (!ruta.isBlank()) {
                urls.add(new File(ruta).toURI().toURL());
            }
        }

        //-----> Carga la clase recien compilada en un aislador de clases aislado (ClassLoader)
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), EjecutorInstrumentado.class.getClassLoader());
        try {
            Class<?> clazz = loader.loadClass(claseCompleta);
            Object instancia = clazz.getDeclaredConstructor().newInstance();
            Method metodo = clazz.getDeclaredMethod(nombreMetodo);
            metodo.setAccessible(true);

            String claveMetodo = claseCompleta + "#" + nombreMetodo;
            String rutaCSV = carpetaSalida + "/" + clazz.getSimpleName() + "_" + nombreMetodo + "_caminos.csv";

            //-----> Captura los eventos temporales del flujo de ejecucion
            RegistradorTiempos.iniciarLogger(claveMetodo);
            try {
                invocarConTimeout(metodo, instancia, 5);
                RegistradorTiempos.escribirCSV(rutaCSV);
            } finally {
                RegistradorTiempos.desactivarLogger();
            }

            System.out.println("-----> Metodo procesado: " + claveMetodo);
            return rutaCSV;
        } finally {
            loader.close();
        }
    }

    //-----> Analiza con JavaParser la clase para obtener su paquete y nombre calificado
    private static String obtenerClaseCompleta(String rutaArchivoJava) throws Exception {
        com.github.javaparser.ast.CompilationUnit cu =
                com.github.javaparser.StaticJavaParser.parse(new File(rutaArchivoJava));
        String nombreClase = cu.getPrimaryTypeName().orElseThrow();
        String paquete = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return paquete.isEmpty() ? nombreClase : paquete + "." + nombreClase;
    }
}