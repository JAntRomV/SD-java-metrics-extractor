package dinamica;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.results.format.ResultFormatType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

//-----> Lanzador de la Fase 1 (Benchmarks de tiempo general y memoria usando JMH)
public class EjecutorDinamico {

    private static final String INCLUDE_METODO_BENCHMARK =
            "^" + Pattern.quote(MetodoBenchmark.class.getName()) + "\\.";

    public static void main(String[] args) throws Exception {

        Map<String, String> params = getParams(args);

        String rutaClases = params.getOrDefault("clases", params.get("ruta"));
        String rutaProyecto = params.get("proyecto");

        String classpathParaCargar = params.getOrDefault("classpath", rutaClases);

        // Si se recibe el proyecto sin compilar, lo compila primero
        if (rutaProyecto != null) {
            System.out.println("-----> Se recibio --proyecto, intentando compilar automaticamente...");
            CompiladorProyecto.ResultadoCompilacion resultado = CompiladorProyecto.compilar(rutaProyecto, true);

            if (!resultado.exitoso) {
                System.err.println("-----> No se pudo compilar: " + resultado.mensaje);
                return;
            }

            System.out.println("-----> Compilacion exitosa, clases en: " + resultado.carpetaClases);
            rutaClases = resultado.carpetaClases;
            classpathParaCargar = resultado.classpathCompleto;
        }

        if (rutaClases == null) {
            System.err.println("Falta el parametro --clases:/ruta/a/target/classes o --proyecto:/ruta/a/la/raiz");
            return;
        }

        String rutaCatalogo = params.getOrDefault("catalogo", "catalogo_metodos.txt");

        int batchSize = Integer.parseInt(params.getOrDefault("batchSize", "50"));
        int batchIndex = Integer.parseInt(params.getOrDefault("batchIndex", "0"));

        int iterations = Integer.parseInt(params.getOrDefault("I", "10"));
        int warmupIterations = Integer.parseInt(params.getOrDefault("WI", "2"));
        int forks = Integer.parseInt(params.getOrDefault("F", "1"));
        int minHeap = Integer.parseInt(params.getOrDefault("MINH", "2048"));
        int maxHeap = Integer.parseInt(params.getOrDefault("MAXH", "2048"));

        String carpetaResultados = params.getOrDefault("salida", "resultados_dinamicos");
        Files.createDirectories(Paths.get(carpetaResultados));

        // Obtiene el catálogo de métodos mediante el escaneador o usando uno existente
        List<String> catalogo = obtenerCatalogo(rutaClases, classpathParaCargar, rutaCatalogo, carpetaResultados);

        int totalLotes = (int) Math.ceil(catalogo.size() / (double) batchSize);
        System.out.println("-----> Catalogo total: " + catalogo.size() + " metodos, en " + totalLotes + " lotes de " + batchSize);

        if (batchIndex >= totalLotes) {
            System.out.println("-----> El lote " + batchIndex + " no existe, el maximo es " + (totalLotes - 1));
            return;
        }

        int inicio = batchIndex * batchSize;
        int fin = Math.min(inicio + batchSize, catalogo.size());
        List<String> loteActual = catalogo.subList(inicio, fin);

        System.out.println("-----> Corriendo ejecucion de la Fase 1 (" + loteActual.size() + " metodos, del " + inicio + " al " + (fin - 1) + ")");

        String resultCSV = carpetaResultados + "/Benchmarks.csv";

        // Configuración y ejecución del Runner de JMH
        Options opt = new OptionsBuilder()
            .include(INCLUDE_METODO_BENCHMARK)
            .param("rutaClases", classpathParaCargar)
            .param("metodoObjetivo", loteActual.toArray(new String[0]))
            .addProfiler(GCProfiler.class) // Habilita la medición de uso de memoria/GC
            .jvmArgs("-Xms" + minHeap + "m", "-Xmx" + maxHeap + "m")
            .resultFormat(ResultFormatType.CSV)
            .result(resultCSV)
            .warmupIterations(warmupIterations)
            .warmupTime(TimeValue.milliseconds(500))
            .measurementIterations(iterations)
            .measurementTime(TimeValue.milliseconds(500))
            .forks(forks)
            .threads(1)
            .mode(org.openjdk.jmh.annotations.Mode.All)
            .timeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
            .build();

        new Runner(opt).run();

        System.out.println("-----> Fase 1 terminada con exito. Resultados en: " + resultCSV);
    }

    private static List<String> obtenerCatalogo(String rutaClases, String classpathParaCargar, String rutaCatalogo, String carpetaResultados) throws Exception {
        File archivoCatalogo = new File(rutaCatalogo);

        if (archivoCatalogo.exists()) {
            System.out.println("-----> Usando catalogo existente: " + rutaCatalogo);
            return Files.readAllLines(archivoCatalogo.toPath(), StandardCharsets.UTF_8);
        }

        System.out.println("-----> No existe el catalogo, escaneando: " + rutaClases);
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        List<EscaneadorMetodos.MetodoObjetivo> metodos = escaner.escanear(rutaClases, classpathParaCargar);
        escaner.guardarResumen(carpetaResultados + "/_escaneo_resumen.txt");

        List<String> catalogo = new ArrayList<>();
        for (EscaneadorMetodos.MetodoObjetivo m : metodos) {
            catalogo.add(m.comoTexto());
        }

        escaner.guardarCatalogo(metodos, rutaCatalogo);
        return catalogo;
    }

    private static Map<String, String> getParams(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split(":", 2);
                if (parts.length == 2) {
                    params.put(parts[0], parts[1]);
                }
            }
        }
        return params;
    }
}