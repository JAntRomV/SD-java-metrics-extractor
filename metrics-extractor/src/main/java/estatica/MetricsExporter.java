package estatica;
//Genera un archivo por cada clase y decide si json csv 20 metodos
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MetricsExporter {

    private static final int JSON_THRESHOLD = 20;

    public static void export(List<ProjectMetrics> reports, String outputFolder) {
//--------------------------crea la carpeta y todas las carpetas padre-------------------------       
        File baseFolder = new File(outputFolder);
        if (!baseFolder.exists()) baseFolder.mkdirs();

        int totalArchivos = 0;
//------------------------------recorre proyectos--------------------------------------------
        for (ProjectMetrics project : reports) {

//-------------------------- Subcarpeta por proyecto: outputFolder/nombre-proyecto/-------------------------
            File projectFolder = new File(baseFolder, sanitize(project.getProjectName()));
            if (!projectFolder.exists()) projectFolder.mkdirs();

//-----------------------Iterar sobre cada clase del proyecto-----------------------------------------
            for (String className : project.getClassNames()) {
                List<MethodMetrics> methods = project.getMethodsOf(className);
                String fileName = project.getFileNameOf(className);

                if (methods.isEmpty()) continue;

                if (methods.size() < JSON_THRESHOLD) {
                    writeJson(className, fileName, methods, projectFolder);
                } else {
                    writeCsv(className, fileName, methods, projectFolder);
                }
                totalArchivos++;
            }
        }

        System.out.println("Exportación finalizada: " + totalArchivos
                + " archivo(s) en: " + baseFolder.getAbsolutePath());
    }

//----------------------------------- JSON--------------------------------------------------------------

    private static void writeJson(String className, String fileName,
                                  List<MethodMetrics> methods, File folder) {
        File out = new File(folder, sanitize(className) + ".json");
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

            System.out.println("  [JSON] " + out.getName() + " (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error JSON " + className + ": " + e.getMessage());
        }
    }

//--------------------------------------CSV--------------------------------

    private static void writeCsv(String className, String fileName,
                                 List<MethodMetrics> methods, File folder) {
        File out = new File(folder, sanitize(className) + ".csv");
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {

            w.println("Nombre del archivo,clase,metodo,"
                    + "vocabulario,longitud,volumen,dificultad,"
                    + "esfuerzo de implementacion,tiempo estimado de desarrollo,"
                    + "estimacion de bug,numero ciclomatico,"
                    + "loc,nodes,edges,unconnected nodos");

            for (MethodMetrics m : methods) {
                w.println(
                    csv(fileName)               + "," +
                    csv(className)              + "," +
                    csv(m.getMethodName())      + "," +
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

            System.out.println("  [CSV]  " + out.getName() + " (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error CSV " + className + ": " + e.getMessage());
        }
    }

    private static String sanitize(String s) {
        return (s == null || s.isEmpty()) ? "sin_nombre"
                : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static double r2(double v) { return Math.round(v * 100.0)   / 100.0; }
    private static double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}