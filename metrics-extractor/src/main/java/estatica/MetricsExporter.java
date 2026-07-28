package estatica;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

// -----> Guarda y exporta los resultados del analisis en archivos JSON y CSV.
public class MetricsExporter {

    // -----> Exporta todas las metricas calculadas a carpetas y archivos.
    public static void export(List<ProjectMetrics> reports, String outputFolder) {

        // -----> Crea la carpeta principal de resultados si no existe.
        File baseFolder = new File(outputFolder);
        if (!baseFolder.exists()) baseFolder.mkdirs();

        // -----> Contador de archivos creados.
        int totalArchivos = 0;

        // -----> Recorre cada proyecto analizado.
        for (ProjectMetrics project : reports) {

            // -----> Crea una carpeta para el proyecto actual.
            File projectFolder = new File(baseFolder, sanitize(project.getProjectName()));
            if (!projectFolder.exists()) projectFolder.mkdirs();

            // -----> Recorre las clases de cada proyecto.
            for (String className : project.getClassNames()) {
                List<MethodMetrics> methods = project.getMethodsOf(className);
                String fileName = project.getFileNameOf(className);

                // -----> Si la clase no tiene metodos, la ignora.
                if (methods.isEmpty()) continue;

                // -----> Genera los archivos JSON y CSV de la clase.
                writeJson(className, fileName, methods, projectFolder);
                writeCsv(className, fileName, methods, projectFolder);
                writeCode2SeqJson(className, fileName, methods, projectFolder);
                totalArchivos++;
            }
        }    

        // -----> Mensaje final con el total de archivos creados.
        System.out.println("Se genero: " + totalArchivos
                + " archivo(s) con exito en: " + baseFolder.getAbsolutePath());
    }

    // -----> Escribe el archivo JSON con las metricas estaticas de la clase.
    private static void writeJson(String className, String fileName, List<MethodMetrics> methods, File folder) {
        File out = new File(folder, sanitize(className) + "Metricas.json");
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {

            w.println("{");
            w.println("  \"Nombre del archivo\": \"" + esc(fileName)  + "\",");
            w.println("  \"clase\": \""               + esc(className) + "\",");
            w.println("  \"metodos\": [");

            for (int i = 0; i < methods.size(); i++) {
                MethodMetrics m   = methods.get(i);
                boolean       last = (i == methods.size() - 1);

                w.println("    {");
                w.println("      \"metodo\": \""                          + esc(m.getMethodName()) + "\",");
                w.println("      \"halstead\": {");
                w.println("        \"vocabulario\": "                     + m.getVocabulary()             + ",");
                w.println("        \"longitud\": "                        + m.getLength()                 + ",");
                w.println("        \"volumen\": "                         + r2(m.getVolume())             + ",");
                w.println("        \"dificultad\": "                      + r2(m.getDifficulty())         + ",");
                w.println("        \"esfuerzo de implementacion\": "      + r2(m.getEffort())             + ",");
                w.println("        \"tiempo estimado de desarrollo\": "   + r2(m.getTime())               + ",");
                w.println("        \"estimacion de bug\": "               + r4(m.getBugs())               + ",");
                w.println("        \"numero ciclomatico\": "              + m.getCyclomaticComplexity());
                w.println("      },");
                w.println("      \"grafo de flujo de control\": {");
                w.println("        \"loc\": "                             + m.getLoc()                    + ",");
                w.println("        \"nodes\": "                           + m.getCfgNodes()               + ",");
                w.println("        \"edges\": "                           + m.getCfgEdges()               + ",");
                w.println("        \"unconnected nodos\": "               + m.getCfgUnconnectedNodes());
                w.println("      }");
                w.println("    }" + (last ? "" : ","));
            }

            w.println("  ]");
            w.println("}");

            System.out.println("  [MetricasJSON] " + out.getName() + " (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error MetricasJSON " + className + ": " + e.getMessage());
        }
    }

    // -----> Escribe el archivo CSV con los datos y metricas en filas.
    private static void writeCsv(String className, String fileName, List<MethodMetrics> methods, File folder) {
        File out = new File(folder, sanitize(className) + "Metricas.csv");
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {

            // -----> Encabezado del CSV.
            w.println("Nombre del archivo,clase,metodo,"
                    + "n1_operadores_distintos,n2_operandos_distintos,N1_total_operadores,N2_total_operandos,"
                    + "vocabulario,longitud,volumen,dificultad,"
                    + "esfuerzo de implementacion,tiempo estimado de desarrollo,"
                    + "estimacion de bug,numero ciclomatico,"
                    + "loc,nodes,edges,unconnected nodos");

            // -----> Escribe las filas con los valores de cada metodo.
            for (MethodMetrics m : methods) {
                w.println(
                    csv(fileName)               + "," +
                    csv(className)              + "," +
                    csv(m.getMethodName())      + "," +
                    m.getN1()                   + "," + 
                    m.getN2()                   + "," + 
                    m.getN1Total()              + "," + 
                    m.getN2Total()              + "," + 
                    m.getVocabulary()           + "," +
                    m.getLength()               + "," +
                    r2(m.getVolume())           + "," +
                    r2(m.getDifficulty())       + "," +
                    r2(m.getEffort())           + "," +
                    r2(m.getTime())             + "," +
                    r4(m.getBugs())             + "," +
                    m.getCyclomaticComplexity() + "," +
                    m.getLoc()                  + "," +
                    m.getCfgNodes()             + "," +
                    m.getCfgEdges()             + "," +
                    m.getCfgUnconnectedNodes()
                );
            }

            System.out.println("  [MetricasCSV]  " + out.getName() + " (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error MetricasCSV " + className + ": " + e.getMessage());
        }
    }

    // -----> Limpia textos para usarlos como nombres de archivo sin caracteres raros.
    private static String sanitize(String s) {
        return (s == null || s.isEmpty()) ? "sin_nombre"
                : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // -----> Escapa comillas y barras para formatos JSON.
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // -----> Prepara un texto para agregarse a un archivo CSV.
    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // -----> Redondea numeros decimales a 2 posiciones.
    private static double r2(double v) { return Math.round(v * 100.0)   / 100.0; }

    // -----> Redondea numeros decimales a 4 posiciones.
    private static double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    // -----> Escribe el JSON de Code2Seq con los caminos agrupados por metodo.
    private static void writeCode2SeqJson(String className, String fileName, List<MethodMetrics> methods, File folder) {
        File out = new File(folder, sanitize(className) + "_code2seq.json");
        try (PrintWriter w = new PrintWriter(out, java.nio.charset.StandardCharsets.UTF_8)) {

            w.println("{");
            w.println("  \"Nombre del archivo\": \"" + esc(fileName) + "\",");
            w.println("  \"clase\": \"" + esc(className) + "\",");
            w.println("  \"metodos\": [");

            for (int i = 0; i < methods.size(); i++) {
                MethodMetrics m = methods.get(i);
                boolean lastMethod = (i == methods.size() - 1);

                w.println("    {");
                w.println("      \"nombre_metodo\": \"" + esc(m.getMethodName()) + "\",");
                w.println("      \"caminos_code2seq\": [");

                List<String> caminos = m.getCaminosCode2Seq();
                for (int j = 0; j < caminos.size(); j++) {
                    boolean lastCamino = (j == caminos.size() - 1);
                    w.println("        \"" + esc(caminos.get(j)) + "\"" + (lastCamino ? "" : ","));
                }

                w.println("      ]");
                w.println("    }" + (lastMethod ? "" : ","));
            }

            w.println("  ]");
            w.println("}");
            
            System.out.println("  [Code2Seq JSON] " + out.getName() + " generado con éxito.");

        } catch (Exception e) {
            System.err.println("Error generando Code2Seq JSON para " + className + ": " + e.getMessage());
        }
    }

    // -----> Escribe el JSON con los caminos del arbol ordenados por metodo.
    public static void writeArbolCaminosJson(String nombreClase, String nombreProyecto, String outputFolder, ArbolCaminoExtractor.ResultadoClase resultadoArbol) {
        File baseFolder = new File(outputFolder);
        File projectFolder = new File(baseFolder, sanitize(nombreProyecto));
        if (!projectFolder.exists()) projectFolder.mkdirs();

        File archivoJsonCaminos = new File(projectFolder, nombreClase.replace(".java", "_caminos.json"));

        try (PrintWriter writer = new PrintWriter(archivoJsonCaminos, StandardCharsets.UTF_8.name())) {
            writer.println("{");
            writer.println("  \"clase\": \"" + esc(resultadoArbol.nombreClase) + "\",");
            writer.println("  \"metodos\": [");

            List<ArbolCaminoExtractor.ResultadoMetodo> metodos = resultadoArbol.metodos;
            for (int i = 0; i < metodos.size(); i++) {
                ArbolCaminoExtractor.ResultadoMetodo resultadoMetodo = metodos.get(i);
                boolean esUltimoMetodo = (i == metodos.size() - 1);

                writer.println("    {");
                writer.println("      \"metodo\": \"" + esc(resultadoMetodo.nombreMetodo) + "\",");
                writer.println("      \"caminos\": [");

                for (int j = 0; j < resultadoMetodo.vectorTexto.size(); j++) {
                    boolean esUltimoCamino = (j == resultadoMetodo.vectorTexto.size() - 1);
                    writer.println("        {");
                    writer.println("          \"camino_id\": " + (j + 1) + ",");
                    String textoLimpio = esc(resultadoMetodo.vectorTexto.get(j));
                    writer.println("          \"texto\": \"" + textoLimpio + "\",");
                    writer.println("          \"serie_numerica\": " + resultadoMetodo.vectorNumerico.get(j).toString());
                    writer.println("        }" + (esUltimoCamino ? "" : ","));
                }

                writer.println("      ]");
                writer.println("    }" + (esUltimoMetodo ? "" : ","));
            }

            writer.println("  ]");
            writer.println("}");
            System.out.println(" [CAMINO JSON] Generado por el Exporter para: " + nombreClase);
        } catch (Exception e) {
            System.err.println(" Error al escribir el JSON del camino: " + e.getMessage());
        }
    }
}