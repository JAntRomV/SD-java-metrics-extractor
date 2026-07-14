package dinamica;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

//-----> Configura JMH para medir todas las variables posibles y mostrar los resultados en nanosegundos
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MetodoBenchmark {

    //-----> Recibe la ruta de las clases desde el Ejecutor
    @Param({""})
    public String rutaClases;

    //-----> Recibe el metodo que toca medir en esta iteracion (con formato Clase#metodo
    @Param({""})
    public String metodoObjetivo;

    private Object instancia;
    private Method metodo;

    //-----> Se ejecuta un momento antes de iniciar la prueba para preparar el metodo en memoria
    @Setup(Level.Trial)
    public void prepararMetodo() throws Exception {
        //-----> Separa la llave por el caracter '#' para obtener el nombre de la clase y el metodo por separado
        String[] partes = metodoObjetivo.split("#");
        String nombreClase = partes[0];
        String nombreMetodo = partes[1];

        //-----> Carga la clase de forma dinamica en la prueba actual
        URL url = new java.io.File(rutaClases).toURI().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[]{url}, this.getClass().getClassLoader());

        //-----> Crea un clon u objeto real de la clase y localiza el metodo interno
        Class<?> clazz = loader.loadClass(nombreClase);
        this.instancia = clazz.getDeclaredConstructor().newInstance();
        this.metodo = clazz.getDeclaredMethod(nombreMetodo);
        this.metodo.setAccessible(true);
    }

    //-----> Esta es la prueba central que se ejecuta repetidamente para promediar la velocidad
    @Benchmark
    public void medirMetodo(Blackhole bh) throws Exception {
        //-----> Activa el metodo en caliente[cite: 4]
        Object resultado = metodo.invoke(instancia);
        
        //-----> Si el metodo devuelve algun dato, se lo tira al Blackhole para obligar a la computadora a procesarlo de verdad
        if (resultado != null) {
            bh.consume(resultado);
        }
    }
}