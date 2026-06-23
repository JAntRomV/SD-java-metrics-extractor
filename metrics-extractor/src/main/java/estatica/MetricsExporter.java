package estatica;
//Genera un archivo por cada clase y decide si json csv 20 metodos
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MetricsExporter {

//------>Si tiene menos de 20 metodos se hace JSON 
    private static final int JSON_THRESHOLD = 20;
//____________________________________________________________
    public static void export(List<ProjectMetrics> reports, String outputFolder) {

//------>Crea la carpeta principal  de resultados si es que no existe        
        File baseFolder = new File(outputFolder);
        if (!baseFolder.exists()) baseFolder.mkdirs();

//------>Contador para saber cuantos archivos creamos en total
        int totalArchivos = 0;

//------>Empieza a recorrer cada proyecto que se analizo 
        for (ProjectMetrics project : reports) {

//------>Crea una subcarpeta exclusiva para el proyecto actual
            File projectFolder = new File(baseFolder, sanitize(project.getProjectName()));
            if (!projectFolder.exists()) projectFolder.mkdirs();


//------>Recorre cada una de las clases que tiene el proyecto
            for (String className : project.getClassNames()) {
                List<MethodMetrics> methods = project.getMethodsOf(className);
                String fileName = project.getFileNameOf(className);

//------>Si la clase no tiene ningun metodo se salta y continua 
                if (methods.isEmpty()) continue;

//------>Se revisa si tiene menos de 20 metodos para crear un json o un csv
                if (methods.size() < JSON_THRESHOLD) {
                    writeJson(className, fileName, methods, projectFolder);
                } else {
                    writeCsv(className, fileName, methods, projectFolder);
                }
                totalArchivos++;
            }
        }    


//------>Avisa que  termino el proceso con exito 
        System.out.println("Se genero: " + totalArchivos
                + " archivo(s) con exito en: " + baseFolder.getAbsolutePath());
    }

//_____________________________________________________________________________________

    private static void writeJson(String className, String fileName,List<MethodMetrics> methods, File folder) {
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
    private static String escCode(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

//___________________________________________________________________________________
    private static void writeCsv(String className, String fileName,List<MethodMetrics> methods, File folder) {
//------>DEfine el nombre del archivo.csv        
        File out = new File(folder, sanitize(className) + ".csv");
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {

//Se escribe  la primera linea del archivo 
            w.println("Nombre del archivo,clase,metodo,"
                    + "n1_operadores_distintos,n2_operandos_distintos,N1_total_operadores,N2_total_operandos,"
                    + "vocabulario,longitud,volumen,dificultad,"
                    + "esfuerzo de implementacion,tiempo estimado de desarrollo,"
                    + "estimacion de bug,numero ciclomatico,"
                    + "loc,nodes,edges,unconnected nodos");

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
//------>Muestra un mensaje de que se finalizo 
            System.out.println("  [CSV]  " + out.getName() + " (" + methods.size() + " método(s))");

        } catch (Exception e) {
            System.err.println("Error CSV " + className + ": " + e.getMessage());
        }
    }
   //------>LImpia los nombres de archivos, carpetas, comillas, barras  
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
// redondea numeros  decimales
    private static double r2(double v) { return Math.round(v * 100.0)   / 100.0; }
    private static double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}