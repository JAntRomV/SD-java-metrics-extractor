package estatica;
//------------------------ ENcuentra proyectos, recorre archivos------------------------------s
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App {

    
    private static final String RUTA_RAIZ_PROYECTOS = "."; 
    private static final String RUTA_SALIDA_REPORTES = ".";

    public static void main(String[] args) {
        System.out.println("=== Iniciando Extractor de Métricas Estáticas ===");

//------------------------Verifica que el archivo exista----------------------------        
        File rootDir = new File(RUTA_RAIZ_PROYECTOS);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("Error: La ruta raíz de proyectos no existe o no es válida: " + RUTA_RAIZ_PROYECTOS);
            return;
        }

        List<ProjectMetrics> totalReports = new ArrayList<>();
        List<File> targetProjects = findProjectsWithSrc(rootDir);

        System.out.println("Proyectos encontrados para análisis: " + targetProjects.size());
//---------------------------recorre cada proyecto encontrado---------------------------------
        for (File projectDir : targetProjects) {
            String projectName = projectDir.getName();
            System.out.println("Analizando microservicio: [" + projectName + "]");

//-----------------------------crea un contenedor vacio------------------------------------------
            ProjectMetrics projectAccumulator = new ProjectMetrics(projectName);
            List<File> javaFiles = new ArrayList<>();

//------------------Entra a la carpeta src del proyecto y busca los archivo .java de forma recursiva-------------------------------         
            findJavaFilesRecursively(new File(projectDir, "src"), javaFiles);

            for (File javaFile : javaFiles) {
                try {
//---------------------------Lee el archivo y lo convierte en arbol------------------------------------------                     
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    MetricsAnalyzer analyzer = new MetricsAnalyzer(projectAccumulator);
                    cu.accept(analyzer, null);
                } catch (Exception e) {
                    System.err.println(" No se pudo procesar el archivo: " + javaFile.getName() + " -> " + e.getMessage());
                }
            }

            totalReports.add(projectAccumulator);
        }

// --------------------------------Crea una subcarpeta por proyecto---------------------------------------
        MetricsExporter.export(totalReports, RUTA_SALIDA_REPORTES);
        System.out.println("=== Proceso finalizado con éxito ===");
    }
//--------------------------------Recorre las subcarpetas----------------------------------------------
    private static List<File> findProjectsWithSrc(File root) {
        List<File> projects = new ArrayList<>();
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    File srcFolder = new File(f, "src");
                    if (srcFolder.exists() && srcFolder.isDirectory()) {
                        projects.add(f);
                    } else {
                        // Búsqueda en un segundo nivel de profundidad si es necesario
                        List<File> subProjects = findProjectsWithSrc(f);
                        projects.addAll(subProjects);
                    }
                }
            }
        }
        return projects;
    }
//-------------------------recorre la carpeta dada y si escuentra una carpeta .java lo agrega a una lista-------------------------------
    private static void findJavaFilesRecursively(File folder, List<File> res) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    findJavaFilesRecursively(f, res);
                } else if (f.getName().endsWith(".java")) {
                    res.add(f);
                }
            }
        }
    }
}