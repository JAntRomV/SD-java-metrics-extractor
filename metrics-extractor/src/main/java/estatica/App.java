package estatica;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App {

    public static void main(String[] args) {
        System.out.println("=== Iniciando Extractor de Métricas Estáticas ===");

        
        String rutaRaiz;
        String rutaSalida;

        if (args.length >= 2) {

            rutaRaiz = args[0];
            rutaSalida = args[1];
        } else {
//---------------- Rutas por defecto-----------------
            rutaRaiz = "/home/tania/Documentos/ejemplo java/Cacahuate";
            rutaSalida = "/home/tania/Escritorio/Resultados";
        }

        System.out.println("Ruta de búsqueda: " + rutaRaiz);
        System.out.println("Ruta de destino: " + rutaSalida);

        File rootDir = new File(rutaRaiz);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("Error: La ruta raíz de proyectos no existe o no es válida: " + rutaRaiz);
            return;
        }

        List<ProjectMetrics> totalReports = new ArrayList<>();
        List<File> targetProjects = findProjectsWithSrc(rootDir);

        System.out.println("Proyectos encontrados para análisis: " + targetProjects.size());

//------------------ recorre cada proyecto encontrado------------
        for (File projectDir : targetProjects) {
            String projectName = projectDir.getName();
            System.out.println("Analizando microservicio: [" + projectName + "]");

//------------------- crea un contenedor vacio---------------------
            ProjectMetrics projectAccumulator = new ProjectMetrics(projectName);
            List<File> javaFiles = new ArrayList<>();

//-------------------- Entra a la carpeta src del proyecto y busca los archivos .java-------------------
            findJavaFilesRecursively(new File(projectDir, "src"), javaFiles);

            for (File javaFile : javaFiles) {
                try {
//-------------------------- Lee el archivo y lo convierte en arbol---------------------------------
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    MetricsAnalyzer analyzer = new MetricsAnalyzer(projectAccumulator);
                    cu.accept(analyzer, null);
                } catch (Exception e) {
                    System.err.println(" No se pudo procesar el archivo: " + javaFile.getName() + " -> " + e.getMessage());
                }
            }

            totalReports.add(projectAccumulator);
        }

//------------------------ Crea una subcarpeta por proyecto usando la ruta dinámica-------------------------
        MetricsExporter.export(totalReports, rutaSalida);
        System.out.println("=== Proceso finalizado con éxito ===");
    }

    
    private static List<File> findProjectsWithSrc(File root) {
        List<File> projects = new ArrayList<>();
        
        File directSrc = new File(root, "src");
        if (directSrc.exists() && directSrc.isDirectory()) {
            projects.add(root);
            return projects;
        }

        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    File srcFolder = new File(f, "src");
                    if (srcFolder.exists() && srcFolder.isDirectory()) {
                        projects.add(f);
                    } else {
                        List<File> subProjects = findProjectsWithSrc(f);
                        projects.addAll(subProjects);
                    }
                }
            }
        }
        return projects;
    }

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