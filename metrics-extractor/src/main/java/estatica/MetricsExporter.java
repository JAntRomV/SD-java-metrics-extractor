package estatica;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

//--------------------------------------------Exportador de métricas — genera UN archivo por cada clase analizada-------------------------

//--------------Métodos en la clase < 20  →  JSON-----
//--------------Métodos en la clase >= 20 →  CSV------

public class MetricsExporter {

//-----------------------------clases con menos de 20 métodos se exportan como JSON-----------------------------------
    private static final int JSON_THRESHOLD = 20;

    public static void export(List<ProjectMetrics> reports, String outputFolder) {
        File baseFolder = new File(outputFolder);
        if (!baseFolder.exists()) baseFolder.mkdirs();

        int totalArchivos = 0;

        for (ProjectMetrics project : reports) {

//----------------------------- Crear subcarpeta para este proyecto------------------------------------
            File projectFolder = new File(baseFolder, sanitizeName(project.getProjectName()));
            if (!projectFolder.exists()) projectFolder.mkdirs();

            for (ClassMetrics classMetrics : project.getClasses()) {

//--------------------Si la clase no tiene metodos no genera archivo----------------------------
                if (classMetrics.getMethods().isEmpty()) continue;

//------------------- Decidir formato según número de métodos-----------------------------------
                if (classMetrics.getMethods().size() < JSON_THRESHOLD) {
                    exportClassToJSON(classMetrics, projectFolder);
                } else {
                    exportClassToCSV(classMetrics, projectFolder);
                }
                totalArchivos++;
            }
        }

        System.out.println("Exportación finalizada: " + totalArchivos
                + " archivo(s) generado(s) en: " + baseFolder.getAbsolutePath());
    }

//----------------------Exportar una clase a JSON-----------------------------------------
    private static void exportClassToJSON(ClassMetrics classMetrics, File projectFolder) {
//-----------------------El nombre del archivo va a hacer el nombre de la clase
        File outputFile = new File(projectFolder, sanitizeName(classMetrics.getClassName()) + ".json");

        try (PrintWriter w = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {

            w.println("{");
            w.println("  \"Nombre del archivo\": \"" + esc(classMetrics.getFileName()) + "\",");
            w.println("  \"clase\": \""               + esc(classMetrics.getClassName()) + "\",");
            w.println("  \"metodos\": [");

            List<MethodMetrics> methods = classMetrics.getMethods();
            for (int i = 0; i < methods.size(); i++) {
                MethodMetrics m = methods.get(i);
                boolean isLast = (i == methods.size() - 1);

                w.println("    {");
                w.println("      \"metodo\": \""                      + esc(m.getMethodName()) + "\",");
                w.println("      \"halstead\": {");
                w.println("        \"vocabulario\": "                 + m.getVocabulary()              + ",");
                w.println("        \"longitud\": "                    + m.getLength()                  + ",");
                w.println("        \"volumen\": "                     + round(m.getVolume())            + ",");
                w.println("        \"dificultad\": "                  + round(m.getDifficulty())        + ",");
                w.println("        \"esfuerzo de implementacion\": "  + round(m.getEffort())            + ",");
                w.println("        \"tiempo estimado de desarrollo\": "+ round(m.getTime())             + ",");
                w.println("        \"estimacion de bug\": "           + round4(m.getBugs())            + ",");
                w.println("        \"numero ciclomatico\": "          + m.getCyclomaticComplexity());
                w.println("      },");
                w.println("      \"grafo de flujo de control\": {");
                w.println("        \"loc\": "                         + m.getLoc()                     + ",");
                w.println("        \"nodes\": "                       + m.getCfgNodes()                + ",");
                w.println("        \"edges\": "                       + m.getCfgEdges()                + ",");
                w.println("        \"unconnected nodos\": "           + m.getCfgUnconnectedNodes());
                w.println("      }");
                w.println("    }" + (isLast ? "" : ","));
            }

            w.println("  ]");
            w.println("}");

            System.out.println("  [JSON] " + outputFile.getName()
                    + "  (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error exportando JSON para "
                    + classMetrics.getClassName() + ": " + e.getMessage());
        }
    }

//----------------------------------Exportar una clase a CSV------------------------------------------
    private static void exportClassToCSV(ClassMetrics classMetrics, File projectFolder) {
//---------------------------El nombre del archivo  es el nombre de la clase-------------------------- 
        File outputFile = new File(projectFolder, sanitizeName(classMetrics.getClassName()) + ".csv");

        try (PrintWriter w = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {

//----------------------------------- campos requeridos-----------------------------------------------
            w.println("Nombre del archivo,clase,metodo,"
                    + "vocabulario,longitud,volumen,dificultad,"
                    + "esfuerzo de implementacion,tiempo estimado de desarrollo,"
                    + "estimacion de bug,numero ciclomatico,"
                    + "loc,nodes,edges,unconnected nodos");

           
            for (MethodMetrics m : classMetrics.getMethods()) {
                w.println(
                    csv(m.getFileName())        + "," +
                    csv(m.getClassName())        + "," +
                    csv(m.getMethodName())       + "," +
                    m.getVocabulary()            + "," +
                    m.getLength()                + "," +
                    round(m.getVolume())         + "," +
                    round(m.getDifficulty())     + "," +
                    round(m.getEffort())         + "," +
                    round(m.getTime())           + "," +
                    round4(m.getBugs())          + "," +
                    m.getCyclomaticComplexity()  + "," +
                    m.getLoc()                   + "," +
                    m.getCfgNodes()              + "," +
                    m.getCfgEdges()              + "," +
                    m.getCfgUnconnectedNodes()
                );
            }

            System.out.println("  [CSV]  " + outputFile.getName()
                    + "  (" + classMetrics.getMethods().size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error exportando CSV para "
                    + classMetrics.getClassName() + ": " + e.getMessage());
        }
    }

    
    private static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) return "sin_nombre";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
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

    private static double round(double v)  { return Math.round(v * 100.0)   / 100.0; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}