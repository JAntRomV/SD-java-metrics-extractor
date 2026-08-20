package dinamica;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

//-----> Prepara y ejecuta la medición aislada
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MetodoBenchmark {

    @Param({""})
    public String rutaClases;

    @Param({""})
    public String metodoObjetivo;

    @Param({""})
    public String carpetaSalida;

    private Object instancia;
    private Method metodo;
    private URLClassLoader loader;

    private TimeLogger _timeLogger;

    //-----> Carga dinámicamente el método objetivo
    @Setup(Level.Trial)
    public void prepararMetodo() throws Exception {
        String[] partes = metodoObjetivo.split("#");
        String nombreClase = partes[0];
        String nombreMetodo = partes[1];

        List<URL> urls = new ArrayList<>();
        for (String ruta : rutaClases.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!ruta.isBlank()) {
                urls.add(new File(ruta).toURI().toURL());
            }
        }
        this.loader = new URLClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader());

        Class<?> clazz = loader.loadClass(nombreClase);
        this.instancia = clazz.getDeclaredConstructor().newInstance();
        this.metodo = clazz.getDeclaredMethod(nombreMetodo);
        this.metodo.setAccessible(true);

        this._timeLogger = new TimeLogger(metodoObjetivo, 0);
    }

    //-----> Guarda marca al iniciar cada iteración
    @Setup(Level.Iteration)
    public void marcarInicioIteracion() {
        if (_timeLogger != null) {
            _timeLogger.logTime("IF-START", true);
        }
    }

    //-----> Libera recursos y exporta datos temporales
    @TearDown(Level.Trial)
    public void limpiar() throws Exception {
        if (loader != null) {
            loader.close();
        }

        if (_timeLogger != null && carpetaSalida != null && !carpetaSalida.isBlank()) {
            File carpetaTemp = new File(carpetaSalida, "_temp_inicios_iteracion");
            carpetaTemp.mkdirs();
            String nombreSeguro = metodoObjetivo.replaceAll("[^a-zA-Z0-9_#.]", "_");
            File archivo = new File(carpetaTemp, nombreSeguro + "_" + System.nanoTime() + ".csv");
            _timeLogger.toCSV(archivo.getAbsolutePath());
        }
    }

    //-----> Invoca el método evitando optimización
    @Benchmark
    public void medirMetodo(Blackhole bh) throws Exception {
        Object resultado = metodo.invoke(instancia);

        if (resultado != null) {
            bh.consume(resultado);
        }
    }
}