package estatica;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("===  Iniciando Extracción de Métricas ===");

        //-----> Se Definen las rutas
        String carpetaCodigoFuente = "/home/tania/Documentos/ejemplojava/ProyectoPrueba"; 
        String carpetaResultados   = "/home/tania/Escritorio/SD-java-metrics-extractor/metrics-extractor/Resultados";         

        //Se extrar el nombre del proyecto
        File directorioProyecto = new File(carpetaCodigoFuente);
        String nombreDelProyectoFound = directorioProyecto.getName();
        //-----> Aqui se guardará todo el reporte
        ProjectMetrics reporteGlobal = new ProjectMetrics(nombreDelProyectoFound);

        //-----> Se Crea nuestro analizador
        MetricsAnalyzer analizador = new MetricsAnalyzer(reporteGlobal);

        try {
            File carpeta = new File(carpetaCodigoFuente);
            if (!carpeta.exists()) {
                System.err.println("Error: La carpeta de código fuente no existe: " + carpeta.getAbsolutePath());
                return;
            }

            // ----> NUEVO: Buscamos archivos .java en la carpeta y en todas sus subcarpetas
            List<File> archivosJava = new ArrayList<>();
            buscarArchivosJava(carpeta, archivosJava);

            if (archivosJava.isEmpty()) {
                System.out.println("No se encontraron archivos .java para analizar en ninguna carpeta.");
                return;
            }

            //-----> JavaParser pasa por cada archivo encontrado
            for (File archivo : archivosJava) {
                System.out.println("-> Analizando archivo: " + archivo.getName());
                
                //-----> Le avisamos al analizador qué archivo estamos leyendo
                analizador.setCurrentFileName(archivo.getName());

                //-----> JavaParser lee el archivo y genera el árbol
                CompilationUnit cu = StaticJavaParser.parse(archivo);

                //-----> El analizador recorre el árbol extrayendo Halstead y el Grafo (CFG)
                analizador.visit(cu, null);
            }

            //-----> Se colocan en una lista 
            List<ProjectMetrics> listaReportes = new ArrayList<>();
            listaReportes.add(reporteGlobal);

            //-----> Se manda la lista ya sea JSON o CSV
            MetricsExporter.export(listaReportes, carpetaResultados);

            System.out.println("===  Proceso terminado con éxito ===");

        } catch (Exception e) {
            System.err.println("Ocurrió un error general durante el análisis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----> NUEVO MÉTODO RECURSIVO: Entra a todas las carpetas buscando archivos .java
    private static void buscarArchivosJava(File directorio, List<File> listaArchivos) {
        File[] archivosYCarpetas = directorio.listFiles();
        if (archivosYCarpetas != null) {
            for (File elemento : archivosYCarpetas) {
                if (elemento.isDirectory()) {
                    // Si es una carpeta, entramos en ella
                    buscarArchivosJava(elemento, listaArchivos);
                } else if (elemento.getName().endsWith(".java")) {
                    // Si es un archivo .java, lo guardamos
                    listaArchivos.add(elemento);
                }
            }
        }
    }
}