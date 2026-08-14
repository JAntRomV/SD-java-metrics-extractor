package dinamica;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.results.format.ResultFormatType;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//-----> Encargado de configurar y ejecutar las pruebas de rendimiento (Fase 1) usando JMH
public class EjecutorDinamico {

    private static final String INCLUDE_METODO_BENCHMARK =
            "^" + Pattern.quote(MetodoBenchmark.class.getName()) + "\\.";

    public static void main(String[] args) throws Exception {

        Map<String, String> params = getParams(args);

        String rutaClases = params.getOrDefault("clases", params.get("ruta"));
        String rutaProyecto = params.get("proyecto");

        String classpathParaCargar = params.getOrDefault("classpath", rutaClases);

        //-----> Compila el proyecto en caso de pasarse la ruta original del codigo
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

        //-----> Lectura de parametros de configuracion de lotes y memoria para JMH
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

        //-----> Obtiene la lista de metodos a evaluar a partir del catalogo
        List<String> catalogo = obtenerCatalogo(rutaClases, classpathParaCargar, rutaCatalogo, carpetaResultados);

        int totalLotes = (int) Math.ceil(catalogo.size() / (double) batchSize);
        System.out.println("-----> Catalogo total: " + catalogo.size() + " metodos, en " + totalLotes + " lotes de " + batchSize);

        if (batchIndex >= totalLotes) {
            System.out.println("-----> El lote " + batchIndex + " no existe, el maximo es " + (totalLotes - 1));
            return;
        }

        //-----> Extrae unicamente el subconjunto de metodos correspondiente al lote actual
        int inicio = batchIndex * batchSize;
        int fin = Math.min(inicio + batchSize, catalogo.size());
        List<String> loteActual = catalogo.subList(inicio, fin);

        System.out.println("-----> Corriendo ejecucion de la Fase 1 (" + loteActual.size() + " metodos, del " + inicio + " al " + (fin - 1) + ")");

        String resultCSV = carpetaResultados + "/Benchmarks.csv";

        //-----> Construccion de la configuracion de pruebas dinamicas en JMH
        Options opt = new OptionsBuilder()
            .include(INCLUDE_METODO_BENCHMARK)
            .param("rutaClases", classpathParaCargar)
            .param("metodoObjetivo", loteActual.toArray(new String[0]))
            .param("carpetaSalida", carpetaResultados)
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
            .mode(org.openjdk.jmh.annotations.Mode.SampleTime)
            .timeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
            .build();

        //-----> Ejecuta el motor Runner de JMH
        new Runner(opt).run();

        //-----> Anexa las metricas detalladas de tiempo por iteracion en el CSV general
        fusionarTiemposIteracionEnBenchmarks(carpetaResultados, resultCSV);

        System.out.println("-----> Fase 1 terminada con exito. Resultados en: " + resultCSV);
    }

    //-----> Combina logs temporales de tiempo con las metricas globales recopiladas por JMH
    private static void fusionarTiemposIteracionEnBenchmarks(String carpetaResultados, String rutaBenchmarksCsv) throws Exception {
        File carpetaTemp = new File(carpetaResultados, "_temp_inicios_iteracion");
        Map<String, List<Long>> duracionesPorMetodo = leerDuracionesPorMetodo(carpetaTemp);

        File archivoBenchmarks = new File(rutaBenchmarksCsv);
        if (!archivoBenchmarks.exists()) {
            DirFileTools.borrarDirectorioRecursivo(carpetaTemp.getAbsolutePath());
            return;
        }

        List<String> lineas = Files.readAllLines(archivoBenchmarks.toPath(), StandardCharsets.UTF_8);
        if (lineas.isEmpty()) {
            DirFileTools.borrarDirectorioRecursivo(carpetaTemp.getAbsolutePath());
            return;
        }

        String[] encabezados = parsearLineaCsv(lineas.get(0));
        int idxMetodo = indiceDeColumna(encabezados, "Param: metodoObjetivo");

        if (idxMetodo == -1) {
            System.err.println("-----> No se encontro la columna 'Param: metodoObjetivo' en Benchmarks.csv, no se pudieron fusionar los tiempos de iteracion.");
            DirFileTools.borrarDirectorioRecursivo(carpetaTemp.getAbsolutePath());
            return;
        }

        List<String> nuevasLineas = new ArrayList<>();
        nuevasLineas.add(lineas.get(0) + ",\"tiemposIteracionNanos\",\"tiempoPromedioIteracionNanos\"");

        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank()) continue;

            String[] valores = parsearLineaCsv(linea);
            String metodoObjetivo = idxMetodo < valores.length ? valores[idxMetodo].trim() : "";

            List<Long> duraciones = duracionesPorMetodo.getOrDefault(metodoObjetivo, new ArrayList<>());
            String listaTiempos = duraciones.stream().map(String::valueOf).collect(Collectors.joining(";"));
            double promedio = duraciones.isEmpty() ? 0.0 : duraciones.stream().mapToLong(Long::longValue).average().orElse(0.0);

            nuevasLineas.add(linea + ",\"" + listaTiempos + "\"," + promedio);
        }

        Files.write(archivoBenchmarks.toPath(), nuevasLineas, StandardCharsets.UTF_8);
        DirFileTools.borrarDirectorioRecursivo(carpetaTemp.getAbsolutePath());
        System.out.println("-----> Tiempos por iteracion fusionados dentro de: " + rutaBenchmarksCsv);
    }

    //-----> Extrae los tiempos acumulados desde los archivos CSV temporales
    private static Map<String, List<Long>> leerDuracionesPorMetodo(File carpetaTemp) throws Exception {
        Map<String, List<Long>> resultado = new HashMap<>();
        if (!carpetaTemp.exists()) return resultado;

        File[] archivos = carpetaTemp.listFiles((dir, nombre) -> nombre.endsWith(".csv"));
        if (archivos == null) return resultado;

        for (File archivo : archivos) {
            List<String> lineas = Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);
            if (lineas.size() < 2) continue;

            String[] encabezado = parsearLineaCsv(lineas.get(0));
            int idxClase = indiceDeColumna(encabezado, "Clase");
            int idxEtiqueta = indiceDeColumna(encabezado, "Etiqueta");
            int idxDuracion = indiceDeColumna(encabezado, "DuracionNanos");
            if (idxClase == -1 || idxEtiqueta == -1 || idxDuracion == -1) continue;

            String metodoObjetivo = null;
            List<Long> duraciones = new ArrayList<>();

            for (int i = 1; i < lineas.size(); i++) {
                if (lineas.get(i).isBlank()) continue;
                String[] valores = parsearLineaCsv(lineas.get(i));
                int maxIdx = Math.max(idxClase, Math.max(idxEtiqueta, idxDuracion));
                if (valores.length <= maxIdx) continue;

                if (metodoObjetivo == null) metodoObjetivo = valores[idxClase].trim();

                if ("IF-START".equals(valores[idxEtiqueta].trim())) {
                    try {
                        duraciones.add(Long.parseLong(valores[idxDuracion].trim()));
                    } catch (NumberFormatException ignorado) {
                    }
                }
            }

            if (metodoObjetivo != null) {
                resultado.computeIfAbsent(metodoObjetivo, k -> new ArrayList<>()).addAll(duraciones);
            }
        }

        return resultado;
    }

    //-----> Busca la posicion de una columna específica dentro del arreglo de encabezados
    private static int indiceDeColumna(String[] encabezado, String nombre) {
        for (int i = 0; i < encabezado.length; i++) {
            if (encabezado[i].trim().equalsIgnoreCase(nombre)) return i;
        }
        return -1;
    }

    //-----> Parsea una linea CSV respetando campos entre comillas
    private static String[] parsearLineaCsv(String linea) {
        List<String> campos = new ArrayList<>();
        for (String parte : linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String limpio = parte.trim();
            if (limpio.length() >= 2 && limpio.startsWith("\"") && limpio.endsWith("\"")) {
                limpio = limpio.substring(1, limpio.length() - 1);
            }
            campos.add(limpio);
        }
        return campos.toArray(new String[0]);
    }

    //-----> Carga un catalogo existente o escanea los archivos .class para construir uno nuevo
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

    //-----> Extrae los argumentos ingresados por la terminal a un diccionario
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