package estatica;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class App {
//---------------------Ubicacion de los proyectos------------------------------------------
    private static final String RUTA_RAIZ_PROYECTOS  = "/home/tania/Documentos/Pruebas";
    private static final String RUTA_SALIDA_REPORTES = ".";

    public static void main(String[] args) {
        System.out.println("=== Iniciando Extractor de Métricas Estáticas ===");
//------------------------------Verifica que la carpeta exista--------------
        File rootDir = new File(RUTA_RAIZ_PROYECTOS);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("Error: ruta no válida: " + RUTA_RAIZ_PROYECTOS);
            return;
        }
        List<ProjectMetrics> totalReports = new ArrayList<>();
//-----------------Recorre las subcarpetas buscando proyecto con carpetas src-------------------
        List<File> targetProjects = findProjectsWithSrc(rootDir);

        System.out.println("Proyectos encontrados: " + targetProjects.size());
//--------------------Recorre cada proyecto encontrado-----------------------------------------
        for (File projectDir : targetProjects) {
            String projectName = projectDir.getName();
            System.out.println("Analizando: [" + projectName + "]");
//--------------------------Se guardadn los datos de cada metodo que se encuentre dentro del proyecto. se crea uno nuevo por cada proyecto.-----------------------------
            ProjectMetrics projectAccumulator = new ProjectMetrics(projectName);

//Entra a la carpeta src y busca todos los archivo .java en forma recursiva--------------------
            List<File> javaFiles = new ArrayList<>();
            findJavaFilesRecursively(new File(projectDir, "src"), javaFiles);
//--------------------Lee el archivo .java y lo convierte en arbol---------------------------
            for (File javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);

                    
                    String relativePath = projectDir.toURI()
                            .relativize(javaFile.toURI()).getPath();

                    
                    String className = detectClassName(cu, javaFile);

                    MetricsAnalyzer analyzer = new MetricsAnalyzer(projectAccumulator);
                    analyzer.setCurrentFileName(relativePath);
                    cu.accept(analyzer, null);

                } catch (Exception e) {
                    System.err.println(" No se pudo procesar: "
                            + javaFile.getName() + " -> " + e.getMessage());
                }
            }

            totalReports.add(projectAccumulator);
        }
//Cuando termine de analizar llama al metodo para que decida donde guardarlo ya sea un json
        MetricsExporter.export(totalReports, RUTA_SALIDA_REPORTES);
        System.out.println("=== Proceso finalizado con éxito ===");
    }

    
    private static String detectClassName(CompilationUnit cu, File file) {
        Optional<ClassOrInterfaceDeclaration> publicClass = cu
                .findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .filter(c -> c.isPublic() && !c.isInterface())
                .findFirst();

        if (publicClass.isPresent()) return publicClass.get().getNameAsString();

        Optional<ClassOrInterfaceDeclaration> anyClass =
                cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (anyClass.isPresent()) return anyClass.get().getNameAsString();

        return file.getName().replace(".java", "");
    }

    // -------------------------------Busca una carpeta que tenga una carpeta src/ -----------------------------
    private static List<File> findProjectsWithSrc(File root) {
        List<File> projects = new ArrayList<>();
        File[] files = root.listFiles();
        if (files == null) return projects;

        for (File f : files) {
            if (!f.isDirectory()) continue;
            File srcFolder = new File(f, "src");
            if (srcFolder.exists() && srcFolder.isDirectory()) {
                projects.add(f);
            } else {
                projects.addAll(findProjectsWithSrc(f));
            }
        }
        return projects;
    }

    // ------------------------------- Recorre recursivamente buscando entre carpetas .java ------------------------
    private static void findJavaFilesRecursively(File folder, List<File> res) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                findJavaFilesRecursively(f, res);
            } else if (f.getName().endsWith(".java")) {
                res.add(f);
            }
        }
    }
}