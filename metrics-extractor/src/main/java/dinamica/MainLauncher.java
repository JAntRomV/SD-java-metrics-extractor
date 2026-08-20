package dinamica;

import estatica.ArbolCaminoExtractor;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//-----> Lanzador principal de la Fase 2 (Caminos)
public class MainLauncher {

    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);

        String rutaProyecto = params.get("proyecto");
        String rutaClases = params.get("clases");
        String carpetaSalida = params.getOrDefault("salida", "resultados_dinamicos");

        System.out.println("=====================================================");
        System.out.println("  FASE 2: CRONÓMETRO DE CAMINOS");
        System.out.println("=====================================================");

        DirFileTools.crearDirectorio(carpetaSalida);

        String classpathParaCompilar = params.getOrDefault("classpath", rutaClases);

        //-----> Compila código del proyecto si es solicitado
        if (rutaProyecto != null) {
            System.out.println("-----> Proyecto externo detectado: " + rutaProyecto);
            CompiladorProyecto.ResultadoCompilacion res = CompiladorProyecto.compilar(rutaProyecto, true);
            if (!res.exitoso) {
                System.err.println("-----> Error al compilar el proyecto: " + res.mensaje);
                return;
            }
            rutaClases = res.carpetaClases;
            classpathParaCompilar = res.classpathCompleto;
        }

        if (rutaClases == null) {
            System.err.println("Uso: java -cp ... dinamica.MainLauncher --proyecto:/ruta [--salida:Resultados]");
            return;
        }

        //-----> Filtra la lista de métodos válidos previa
        String rutaBenchmarkCsv = carpetaSalida + "/Benchmarks.csv";
        List<String> metodosAprobados = leerMetodosAprobados(rutaBenchmarkCsv);

        //-----> Reporta la ausencia de datos iniciales
        if (metodosAprobados.isEmpty()) {
            String razon = "No se encontraron métodos aprobados en: " + rutaBenchmarkCsv
                    + " (Benchmarks.csv no existe o no tiene filas de datos; la Fase 1 no genero metodos validos).";
            System.err.println("-----> " + razon);
            guardarResumenCaminosSinDatos(carpetaSalida, razon);
            return;
        }

        System.out.println("-----> Métodos aprobados por Benchmark a cronometrar: " + metodosAprobados.size());

        List<String> csvsGenerados = new ArrayList<>();
        List<String> noSeguibles = new ArrayList<>();
        ArbolCaminoExtractor extractorEstatico = new ArbolCaminoExtractor();

        //-----> Cronometra iterativamente cada método
        for (String entrada : metodosAprobados) {
            String[] partes = entrada.split("#", 2);
            if (partes.length < 2 || partes[0].isBlank() || partes[1].isBlank()) {
                noSeguibles.add(entrada + " -> Formato inválido");
                continue;
            }
            String claseCompleta = partes[0];
            String nombreMetodo = partes[1];

            String rutaJava = derivarRutaJava(rutaClases, claseCompleta);
            File archivoJava = new File(rutaJava);

            if (!archivoJava.exists()) {
                noSeguibles.add(entrada + " -> No se encontró el fuente .java en: " + rutaJava);
                continue;
            }

            try {
                //-----> Analiza el árbol sintáctico del código
                CompilationUnit cu = StaticJavaParser.parse(archivoJava);
                ArbolCaminoExtractor.ResultadoClase resultadoArbol = extractorEstatico.procesarClase(cu, archivoJava.getName());

                System.out.println("-----> Cronometrando método aprobado: " + resultadoArbol.nombreClase + " | " + nombreMetodo);

                String csv = EjecutorInstrumentado.medirCamino(rutaJava, nombreMetodo, carpetaSalida, classpathParaCompilar);
                if (csv != null) {
                    csvsGenerados.add(csv);
                }
            } catch (Throwable e) {
                noSeguibles.add(entrada + " -> Error: " + e.getMessage());
            }
        }

        //-----> Combina todos los reportes parciales en uno
        String rutaConsolidado = carpetaSalida + "/cronometro_caminos.csv";
        consolidarYLimpiarCSVs(csvsGenerados, rutaConsolidado);

        guardarResumenCaminos(carpetaSalida, metodosAprobados.size(), csvsGenerados.size(), noSeguibles.size());

        DirFileTools.borrarDirectorioRecursivo(carpetaSalida + "/_temp_instrumentado");

        guardarDetalleNoSeguibles(carpetaSalida, noSeguibles);

        System.out.println("=====================================================");
        System.out.println(" FASE 2 FINALIZADA CON ÉXITO");
        System.out.println(" Archivo consolidado de caminos: " + rutaConsolidado);
        if (!noSeguibles.isEmpty()) {
            System.out.println(" " + noSeguibles.size() + " metodo(s) no se pudieron cronometrar.");
            System.out.println(" Detalle completo en: " + carpetaSalida + "/_caminos_no_seguibles.log");
        }
        System.out.println("=====================================================");
    }

    //-----> Loguea métodos no procesados
    private static void guardarDetalleNoSeguibles(String carpetaSalida, List<String> noSeguibles) throws Exception {
        try (PrintWriter w = new PrintWriter(carpetaSalida + "/_caminos_no_seguibles.log", StandardCharsets.UTF_8)) {
            if (noSeguibles.isEmpty()) {
                w.println("Todos los metodos se lograron cronometrar, ninguno quedo pendiente.");
            } else {
                for (String linea : noSeguibles) {
                    w.println(linea);
                }
            }
        }
    }

    //-----> Almacena reporte cuando el catálogo está vacío
    private static void guardarResumenCaminosSinDatos(String carpetaSalida, String razon) throws Exception {
        try (PrintWriter w = new PrintWriter(carpetaSalida + "/_caminos_resumen.txt", StandardCharsets.UTF_8)) {
            w.println("metodosIntentados=0");
            w.println("metodosMedidos=0");
            w.println("metodosNoSeguibles=0");
            w.println("sinDatos=true");
            w.println("razonSinDatos=" + razon);
        }
    }

    //-----> Almacena datos generales de la ejecución
    private static void guardarResumenCaminos(String carpetaSalida, int intentados, int medidos, int noSeguibles) throws Exception {
        try (PrintWriter w = new PrintWriter(carpetaSalida + "/_caminos_resumen.txt", StandardCharsets.UTF_8)) {
            w.println("metodosIntentados=" + intentados);
            w.println("metodosMedidos=" + medidos);
            w.println("metodosNoSeguibles=" + noSeguibles);
        }
    }

    //-----> Filtra métodos desde el archivo de benchmarks
    private static List<String> leerMetodosAprobados(String rutaCsv) throws Exception {
        java.util.LinkedHashSet<String> metodosUnicos = new java.util.LinkedHashSet<>();
        File f = new File(rutaCsv);
        if (!f.exists()) return new ArrayList<>();

        List<String> lineas = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        if (lineas.isEmpty()) return new ArrayList<>();

        String[] encabezados = parsearLineaCSV(lineas.get(0));
        int idxMetodo = -1;
        for (int i = 0; i < encabezados.length; i++) {
            if (encabezados[i].trim().equalsIgnoreCase("Param: metodoObjetivo") ||
                encabezados[i].trim().equalsIgnoreCase("metodoObjetivo") ||
                encabezados[i].trim().equalsIgnoreCase("LlaveUnion")) {
                idxMetodo = i;
                break;
            }
        }
        if (idxMetodo == -1) return new ArrayList<>();

        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i).trim();
            if (linea.isEmpty()) continue;
            String[] partes = parsearLineaCSV(linea);
            if (partes.length > idxMetodo) {
                String valor = partes[idxMetodo].trim();
                if (valor.contains("#")) {
                    metodosUnicos.add(valor);
                }
            }
        }
        return new ArrayList<>(metodosUnicos);
    }

    //-----> Divide las columnas del archivo CSV
    private static String[] parsearLineaCSV(String linea) {
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

    //-----> Resuelve la ruta física del código fuente
    private static String derivarRutaJava(String rutaClases, String claseCompleta) {
        String rutaRelativa = claseCompleta.replace('.', '/') + ".java";
        String[] posiblesRaicesClases = rutaClases.split(java.util.regex.Pattern.quote(File.pathSeparator));

        String primeraCandidata = null;
        for (String rutaCarpetaClases : posiblesRaicesClases) {
            if (rutaCarpetaClases.isBlank()) continue;
            Path raizModulo = encontrarRaizProyecto(Paths.get(rutaCarpetaClases));
            Path candidata = raizModulo.resolve("src/main/java").resolve(rutaRelativa);

            if (primeraCandidata == null) primeraCandidata = candidata.toString();
            if (Files.exists(candidata)) {
                return candidata.toString();
            }
        }

        return primeraCandidata;
    }

    //-----> Localiza el directorio raíz del proyecto
    private static Path encontrarRaizProyecto(Path carpetaClases) {
        Path actual = carpetaClases.toAbsolutePath().normalize();
        while (actual != null) {
            if (Files.exists(actual.resolve("pom.xml")) || Files.exists(actual.resolve("build.gradle"))) {
                return actual;
            }
            actual = actual.getParent();
        }
        return carpetaClases.getParent().getParent();
    }

    //-----> Junta archivos CSV y borra parciales
    private static void consolidarYLimpiarCSVs(List<String> rutasCSV, String rutaSalida) throws Exception {
        try (PrintWriter w = new PrintWriter(rutaSalida, StandardCharsets.UTF_8)) {
            boolean primerArchivo = true;
            for (String ruta : rutasCSV) {
                File f = new File(ruta);
                if (!f.exists()) continue;
                List<String> lineas = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < lineas.size(); i++) {
                    if (i == 0 && !primerArchivo) continue;
                    w.println(lineas.get(i));
                }
                primerArchivo = false;
                f.delete();
            }
        }
    }

    //-----> Transforma argumentos a mapa
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split(":", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }
}