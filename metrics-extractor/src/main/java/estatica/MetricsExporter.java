package estatica;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

//-----> Generador de archivos de salida JSON
public class MetricsExporter {

    //-----> Exporta lote completo de proyectos
    public static void export(List<ProjectMetrics> reports, String outputFolder) {
        int totalArchivos = 0;
        for (ProjectMetrics project : reports) {
            totalArchivos += exportarProyecto(project, outputFolder);
        }

        System.out.println("Se genero: " + totalArchivos
                + " archivo(s) con exito en: " + new File(outputFolder).getAbsolutePath());
    }

    //-----> Exporta metricas de una sola clase
    public static int exportarProyecto(ProjectMetrics project, String outputFolder) {
        File baseFolder = new File(outputFolder);
        if (!baseFolder.exists()) baseFolder.mkdirs();

        File projectFolder = new File(baseFolder, sanitize(project.getProjectName()));
        if (!projectFolder.exists()) projectFolder.mkdirs();

        int archivosGenerados = 0;

        for (String className : project.getClassNames()) {
            List<MethodMetrics> methods = project.getMethodsOf(className);
            String fileName = project.getFileNameOf(className);

            if (methods.isEmpty()) continue;

            writeJson(className, fileName, methods, projectFolder);
            archivosGenerados++;
        }

        return archivosGenerados;
    }

    //-----> Escribe estructura JSON en disco
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

    //-----> Limpia nombre de archivo para SO
    private static String sanitize(String s) {
        return (s == null || s.isEmpty()) ? "sin_nombre"
                : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    //-----> Escapa caracteres especiales en cadenas
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    //-----> Redondeo a 2 decimales
    private static double r2(double v) { return Math.round(v * 100.0)   / 100.0; }
    //-----> Redondeo a 4 decimales
    private static double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    //-----> Escribe JSON con caminos del AST
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
                writer.println("      \"truncado\": " + resultadoMetodo.truncado + ",");
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