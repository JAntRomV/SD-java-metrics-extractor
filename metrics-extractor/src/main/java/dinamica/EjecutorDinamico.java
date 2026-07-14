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

public class EjecutorDinamico {

    public static void main(String[] args) throws Exception {

        //-----> Convierte los argumentos de la terminal en una lista facil de leer
        Map<String, String> params = getParams(args);

        //-----> Obtiene la ruta de los archivos compilados
        String rutaClases = params.get("ruta");

        //-----> Si le diste la carpeta raiz, manda a llamar al CompiladorProyecto de forma automatica
        String rutaProyecto = params.get("proyecto");
        if (rutaProyecto != null) {
            System.out.println("-----> Se recibio --proyecto, intentando compilar automaticamente...");
            CompiladorProyecto.ResultadoCompilacion resultado = CompiladorProyecto.compilar(rutaProyecto);

            //-----> Si la compilacion falla, detiene el programa por completo
            if (!resultado.exitoso) {
                System.err.println("-----> No se pudo compilar: " + resultado.mensaje);
                return;
            }

            System.out.println("-----> Compilacion exitosa, clases en: " + resultado.carpetaClases);
            rutaClases = resultado.carpetaClases;
        }

        //-----> Valida que tengamos una ruta para analizar, de lo contrario se detiene
        if (rutaClases == null) {
            System.err.println("Falta el parametro --ruta:/ruta/a/target/classes o --proyecto:/ruta/a/la/raiz");
            return;
        }

        //-----> Define el nombre del archivo de texto donde se guardara el catalogo de metodos[cite: 2]
        String rutaCatalogo = params.getOrDefault("catalogo", "catalogo_metodos.txt");

        //-----> Define cuantos metodos se procesan por grupo y cual grupo se va a correr ahorita
        int batchSize = Integer.parseInt(params.getOrDefault("batchSize", "50"));
        int batchIndex = Integer.parseInt(params.getOrDefault("batchIndex", "0"));

        //-----> Configura los tiempos y repeticiones para las mediciones del reloj de JMH[cite: 2]
        int iterations = Integer.parseInt(params.getOrDefault("I", "10"));
        int warmupIterations = Integer.parseInt(params.getOrDefault("WI", "2"));
        int forks = Integer.parseInt(params.getOrDefault("F", "1"));
        int minHeap = Integer.parseInt(params.getOrDefault("MINH", "2048"));
        int maxHeap = Integer.parseInt(params.getOrDefault("MAXH", "2048"));

        //-----> Carga la lista de metodos (la lee del archivo si ya existe, o escanea el proyecto)
        List<String> catalogo = obtenerCatalogo(rutaClases, rutaCatalogo);

        //-----> Calcula cuantos lotes o grupos totales salieron en base al tamaño elegido
        int totalLotes = (int) Math.ceil(catalogo.size() / (double) batchSize);
        System.out.println("-----> Catalogo total: " + catalogo.size() + " metodos, en " + totalLotes + " lotes de " + batchSize);

        //-----> Proteccion por si pides correr un lote que no existe
        if (batchIndex >= totalLotes) {
            System.out.println("-----> El lote " + batchIndex + " no existe, el maximo es " + (totalLotes - 1));
            return;
        }

        //-----> Corta el catalogo gigante para quedarse unicamente con los metodos del lote actual
        int inicio = batchIndex * batchSize;
        int fin = Math.min(inicio + batchSize, catalogo.size());
        List<String> loteActual = catalogo.subList(inicio, fin);

        System.out.println("-----> Corriendo lote " + batchIndex + " (" + loteActual.size() + " metodos, del " + inicio + " al " + (fin - 1) + ")");

        //-----> Crea las carpetas donde se van a guardar los archivos de resultados
        String carpetaResultados = "resultados_dinamicos/lote_" + batchIndex;
        Files.createDirectories(Paths.get(carpetaResultados));
        String resultCSV = carpetaResultados + "/lote_" + batchIndex + "_JMH.csv";

        //-----> Configura el motor de JMH con los archivos, la memoria RAM y el medidor de memoria (GCProfiler)
        Options opt = new OptionsBuilder()
            .include(MetodoBenchmark.class.getSimpleName())
            .param("rutaClases", rutaClases)
            .param("metodoObjetivo", loteActual.toArray(new String[0]))
            .addProfiler(GCProfiler.class)
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

        //-----> Arranca de forma oficial la ejecucion del experimento[cite: 2]
        new Runner(opt).run();

        System.out.println("-----> Lote " + batchIndex + " terminado. Resultados en: " + resultCSV);
        System.out.println("-----> Cada fila del CSV trae el parametro 'metodoObjetivo' (Clase#metodo),");
    }

    //-----> Revisa si ya guardamos la lista de metodos antes, si no, activa el Escaneador
    private static List<String> obtenerCatalogo(String rutaClases, String rutaCatalogo) throws Exception {
        File archivoCatalogo = new File(rutaCatalogo);

        //-----> Si el archivo existe, ahorra tiempo leyendolo directamente
        if (archivoCatalogo.exists()) {
            System.out.println("-----> Usando catalogo existente: " + rutaCatalogo);
            return Files.readAllLines(archivoCatalogo.toPath(), StandardCharsets.UTF_8);
        }

        //-----> Si no existe, llama al escaner para revisar carpeta por carpeta
        System.out.println("-----> No existe el catalogo, escaneando: " + rutaClases);
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        List<EscaneadorMetodos.MetodoObjetivo> metodos = escaner.escanear(rutaClases);
        escaner.mostrarResumen();

        //-----> Convierte los objetos de los metodos a texto simple
        List<String> catalogo = new ArrayList<>();
        for (EscaneadorMetodos.MetodoObjetivo m : metodos) {
            catalogo.add(m.comoTexto());
        }

        //-----> Guarda la lista en un archivo de texto para la proxima vez
        escaner.guardarCatalogo(metodos, rutaCatalogo);
        return catalogo;
    }

    //-----> Utilidad que separa los comandos de la terminal que tengan la estructura --clave:valor
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