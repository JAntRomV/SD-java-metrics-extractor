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

//-----> Clase plantilla que usa la herramienta JMH para medir con alta precisión científica el tiempo y consumo de memoria
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MetodoBenchmark {

    // JMH inyecta aquí las rutas de las clases y la firma del método
    @Param({""})
    public String rutaClases;

    @Param({""})
    public String metodoObjetivo;

    private Object instancia;
    private Method metodo;
    private URLClassLoader loader;

    // Se ejecuta antes de iniciar las pruebas para preparar la clase y método mediante reflexión
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
    }

    // Se ejecuta al terminar la prueba para liberar memoria y recursos
    @TearDown(Level.Trial)
    public void limpiar() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    // Este es el método que JMH invoca miles de veces para calcular los promedios de tiempo
    @Benchmark
    public void medirMetodo(Blackhole bh) throws Exception {
        Object resultado = metodo.invoke(instancia);

        if (resultado != null) {
            bh.consume(resultado); // Evita que la JVM elimine llamadas no usadas por optimizaciones
        }
    }
}