package dinamica;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

//-----> Compila en memoria la clase instrumentada y la ejecuta controlando que no caiga en bucles infinitos
public class EjecutorInstrumentado {

    private static final java.util.concurrent.ExecutorService EJECUTOR_TIMEOUT =
            java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
                Thread hilo = new Thread(runnable);
                hilo.setDaemon(true);
                return hilo;
            });

    // Invoca el método con un límite máximo de tiempo (5 segundos) para abortar si hay un loop infinito
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

    // Coordina la instrumentación, compilación temporal y ejecución del método a medir
    public static String medirCamino(
            String rutaArchivoOriginal,
            String nombreMetodo,
            String carpetaSalida,
            String classpathExtra) throws Exception {

        String carpetaTrabajo = carpetaSalida + "/_temp_instrumentado";
        new File(carpetaTrabajo).mkdirs();

        // 1. Inyecta los marcadores
        InstrumentadorCaminos instrumentador = new InstrumentadorCaminos();
        String rutaInstrumentado = instrumentador.instrumentar(rutaArchivoOriginal, nombreMetodo, carpetaTrabajo);

        // 2. Compila el nuevo código instrumentado
        JavaCompiler compilador = ToolProvider.getSystemJavaCompiler();
        if (compilador == null) {
            throw new IllegalStateException("No hay compilador disponible en el entorno JDK.");
        }

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

        // 3. Carga y ejecuta la clase temporal instrumentada
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), EjecutorInstrumentado.class.getClassLoader());
        try {
            Class<?> clazz = loader.loadClass(claseCompleta);
            Object instancia = clazz.getDeclaredConstructor().newInstance();
            Method metodo = clazz.getDeclaredMethod(nombreMetodo);
            metodo.setAccessible(true);

            String claveMetodo = claseCompleta + "#" + nombreMetodo;
            String rutaCSV = carpetaSalida + "/" + clazz.getSimpleName() + "_" + nombreMetodo + "_caminos.csv";

            RegistradorTiempos.iniciarLogger(claveMetodo);
            invocarConTimeout(metodo, instancia, 5);
            RegistradorTiempos.escribirCSV(rutaCSV);

            System.out.println("-----> Metodo procesado: " + claveMetodo);
            return rutaCSV;
        } finally {
            loader.close(); // Cierra el classloader para evitar consumo innecesario de recursos
        }
    }

    private static String obtenerClaseCompleta(String rutaArchivoJava) throws Exception {
        com.github.javaparser.ast.CompilationUnit cu =
                com.github.javaparser.StaticJavaParser.parse(new File(rutaArchivoJava));
        String nombreClase = cu.getPrimaryTypeName().orElseThrow();
        String paquete = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return paquete.isEmpty() ? nombreClase : paquete + "." + nombreClase;
    }
}