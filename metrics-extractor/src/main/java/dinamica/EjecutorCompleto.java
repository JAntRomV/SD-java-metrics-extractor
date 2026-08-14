package dinamica;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//-----> Orquestador general que coordina y ejecuta la Fase 1 y la Fase 2 de manera secuencial
public class EjecutorCompleto {

    public static void main(String[] args) throws Exception {
        //-----> Parsea los argumentos de linea de comandos (--clave:valor)
        Map<String, String> params = parseArgs(args);

        String rutaProyecto = params.get("proyecto");
        String rutaClases = params.get("clases");
        String carpetaSalida = params.getOrDefault("salida", "resultados_dinamicos");
        String classpathCompleto = null;

        //-----> Determina el modo de ejecucion (completo, fase1 o fase2)
        ModeMapper.ModoEjecucion modo = ModeMapper.obtenerModo(params.get("modo"));

        System.out.println("==========================================================");
        System.out.println(" INICIANDO METRICAS DINAMICAS ");
        System.out.println("==========================================================");

        //-----> Crea la carpeta principal donde se guardaran todos los resultados
        DirFileTools.crearDirectorio(carpetaSalida);

        //-----> Compila el proyecto externo si se especifico la ruta
        if (rutaProyecto != null) {
            System.out.println("-----> Paso 1: Compilando proyecto externo...");
            CompiladorProyecto.ResultadoCompilacion res = CompiladorProyecto.compilar(rutaProyecto, true);
            if (!res.exitoso) {
                System.err.println("-----> Error al compilar el proyecto: " + res.mensaje);
                return;
            }
            rutaClases = res.carpetaClases;
            classpathCompleto = res.classpathCompleto;
        }

        if (rutaClases == null) {
            System.err.println("Uso: java -cp ... dinamica.EjecutorCompleto --proyecto:/ruta [--salida:resultados_dinamicos] [--modo:completo|fase1|fase2]");
            return;
        }

        //-----> Define la ruta unica del catalogo dentro de la carpeta de salida para evitar solapamientos entre repositorios
        String rutaCatalogo = params.getOrDefault("catalogo", carpetaSalida + "/catalogo_metodos.txt");

        //-----> Prepara los argumentos comunes para ser reutilizados en las Fases 1 y 2
        List<String> argsComunes = new ArrayList<>();
        argsComunes.add("--clases:" + rutaClases);
        argsComunes.add("--salida:" + carpetaSalida);
        argsComunes.add("--catalogo:" + rutaCatalogo);
        if (classpathCompleto != null) {
            argsComunes.add("--classpath:" + classpathCompleto);
        }
        String[] argsFaseComun = argsComunes.toArray(new String[0]);

        String rutaBenchmarksCsv = carpetaSalida + "/Benchmarks.csv";
        String rutaCaminosCsv = carpetaSalida + "/cronometro_caminos.csv";

        boolean corrioFase1 = false;
        boolean corrioFase2 = false;

        //-----> Ejecuta la FASE 1: Obtencion de benchmarks generales si el modo lo permite
        if (modo != ModeMapper.ModoEjecucion.CAMINOS_INSTRUMENTADOS) {
            System.out.println("\n----------------------------------------------------------");
            System.out.println("-----> FASE 1: BENCHMARKS");
            System.out.println("----------------------------------------------------------");

            EjecutorDinamico.main(argsFaseComun);
            corrioFase1 = true;

            if (!DirFileTools.existeArchivo(rutaBenchmarksCsv)) {
                System.err.println("-----> Error: La Fase 1 finalizo pero no genero el archivo: " + rutaBenchmarksCsv);
                return;
            }
        }

        //-----> Ejecuta la FASE 2: Medicion por caminos instrumentados
        if (modo != ModeMapper.ModoEjecucion.BENCHMARK_GENERAL) {
            if (!DirFileTools.existeArchivo(rutaBenchmarksCsv)) {
                System.err.println("-----> No se puede correr la Fase 2: no existe " + rutaBenchmarksCsv
                        + ". Corre primero la Fase 1 (--modo:fase1 o --modo:completo).");
                return;
            }

            System.out.println("\n----------------------------------------------------------");
            System.out.println("-----> FASE 2: CRONÓMETRO DE CAMINOS");
            System.out.println("----------------------------------------------------------");

            MainLauncher.main(argsFaseComun);
            corrioFase2 = true;
        }

        //-----> Imprime resumen de ejecucion de la Fase 1
        if (corrioFase1) {
            int metodosProcesados = contarMetodosUnicos(rutaBenchmarksCsv);
            Map<String, String> resumenEscaneo = leerResumen(carpetaSalida + "/_escaneo_resumen.txt");

            System.out.println("\n==========================================================");
            System.out.println(" RESUMEN DEL BENCHMARK");
            System.out.println("==========================================================");
            if (resumenEscaneo.isEmpty()) {
                System.out.println("  (Se uso un catalogo ya existente, no hubo escaneo nuevo esta vez)");
            } else {
                System.out.println("  Clases encontradas   : " + resumenEscaneo.getOrDefault("clasesEncontradas", "?"));
                System.out.println("  Clases descartadas   : " + resumenEscaneo.getOrDefault("clasesDescartadas", "?"));
                System.out.println("  Metodos validos      : " + resumenEscaneo.getOrDefault("metodosValidos", "?"));
                System.out.println("  Metodos descartados  : " + resumenEscaneo.getOrDefault("metodosDescartados", "?"));
            }
            System.out.println("  Metodos transferidos a Fase 2 : " + metodosProcesados);
        }

        //-----> Imprime resumen de ejecucion de la Fase 2
        if (corrioFase2) {
            int caminosGenerados = contarCaminosGenerados(rutaCaminosCsv);
            Map<String, String> resumenCaminos = leerResumen(carpetaSalida + "/_caminos_resumen.txt");

            System.out.println("\n==========================================================");
            System.out.println(" RESUMEN CRONOMETROS CAMINOS");
            System.out.println("==========================================================");
            System.out.println("  Caminos generados          : " + caminosGenerados);
            if (!resumenCaminos.isEmpty()) {
                String noSeguibles = resumenCaminos.getOrDefault("metodosNoSeguibles", "0");
                System.out.println("  Metodos NO cronometrados   : " + noSeguibles
                        + (noSeguibles.equals("0") ? " (todos se lograron medir)" : ""));
            }
        }

        System.out.println("\n==========================================================");
        System.out.println(" SE FINALIZO CON ÉXITO");
        System.out.println("==========================================================");
        System.out.println(" Archivos consolidados en '" + carpetaSalida + "':");
        if (corrioFase1) System.out.println("   1. " + rutaBenchmarksCsv);
        if (corrioFase2) System.out.println("   2. " + rutaCaminosCsv);
        System.out.println("==========================================================");
    }

    //-----> Lee archivos de resumen clave=valor a un mapa
    private static Map<String, String> leerResumen(String rutaArchivo) {
        Map<String, String> datos = new HashMap<>();
        File archivo = new File(rutaArchivo);
        if (!archivo.exists()) return datos;

        try {
            for (String linea : Files.readAllLines(archivo.toPath())) {
                String[] partes = linea.split("=", 2);
                if (partes.length == 2) {
                    datos.put(partes[0].trim(), partes[1].trim());
                }
            }
        } catch (Exception e) {
            System.err.println("-----> No se pudo leer " + rutaArchivo + ": " + e.getMessage());
        }
        return datos;
    }

    //-----> Cuenta metodos unicos analizados dentro de un CSV ignorando vacios y encabezados
    private static int contarMetodosUnicos(String rutaCsv) {
        try {
            List<String> lineas = Files.readAllLines(Paths.get(rutaCsv));
            Set<String> metodosUnicos = new HashSet<>();

            for (int i = 1; i < lineas.size(); i++) {
                String linea = lineas.get(i).trim();
                if (linea.isEmpty()) continue;

                String[] columnas = linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                for (String col : columnas) {
                    String valor = col.replace("\"", "").trim();
                    if (valor.contains("#") && !valor.startsWith("dinamica.")) {
                        metodosUnicos.add(valor);
                        break;
                    }
                }
            }
            return metodosUnicos.size();
        } catch (Exception e) {
            System.err.println("-----> Error contando métodos en CSV de Benchmarks: " + e.getMessage());
            return 0;
        }
    }

    //-----> Cuenta lineas con registros de caminos generados en el CSV
    private static int contarCaminosGenerados(String rutaCsv) {
        try {
            List<String> lineas = Files.readAllLines(Paths.get(rutaCsv));
            int caminos = 0;
            for (int i = 1; i < lineas.size(); i++) {
                if (!lineas.get(i).trim().isEmpty()) {
                    caminos++;
                }
            }
            return caminos;
        } catch (Exception e) {
            System.err.println("-----> Error contando caminos en CSV final: " + e.getMessage());
            return 0;
        }
    }

    //-----> Convierte el arreglo de parametros con prefijo '--' en un Map clave-valor
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