package estatica;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("===  Iniciando Extracción de Métricas en Lote ===");

        //-----> Se Definen las rutas
        String carpetaCodigoFuente = "/home/tania/Documentos/ejemplojava"; 
        String carpetaResultados   = "/home/tania/Escritorio/SD-java-metrics-extractor/metrics-extractor/Resultados";         

        //Se extraer el nombre del proyecto padre
        File directorioProyecto = new File(carpetaCodigoFuente);
        
        if (!directorioProyecto.exists() || !directorioProyecto.isDirectory()) {
            System.err.println("Error: La carpeta contenedora no existe: " + directorioProyecto.getAbsolutePath());
            return;
        }

        //----->  Listamos todas las subcarpetas (cada una es un proyecto individual)
        File[] proyectos = directorioProyecto.listFiles(File::isDirectory);
        if (proyectos == null || proyectos.length == 0) {
            System.out.println("No se encontraron subcarpetas de proyectos en: " + carpetaCodigoFuente);
            return;
        }

        //----->Se procesa proyecto por proyecto
        for (File proyectoActual : proyectos) {
            String nombreDelProyectoFound = proyectoActual.getName();
            
            System.out.println("\n-> Analizando proyecto: " + nombreDelProyectoFound.toUpperCase());

            //-----> Aqui se guardará todo el reporte (Limpio para cada proyecto)
            ProjectMetrics reporteGlobal = new ProjectMetrics(nombreDelProyectoFound);

            //-----> Se Crea nuestro analizador
            MetricsAnalyzer analizador = new MetricsAnalyzer(reporteGlobal);

            try {
                // ---->Buscamos archivos .java en la carpeta y en todas sus subcarpetas de ESTE proyecto
                List<File> archivosJava = new ArrayList<>();
                buscarArchivosJava(proyectoActual, archivosJava);

                if (archivosJava.isEmpty()) {
                    System.out.println("   [Aviso] No hay archivos .java en: " + nombreDelProyectoFound);
                    continue;
                }

                //-----> Se recorre la lista de los archivos encontrados para analizarlos uno por uno
                for (File archivo : archivosJava) {
                    
                    //-----> Le pasamos el nombre del archivo al analizador
                    analizador.setCurrentFileName(archivo.getName());

                    //-----> Con el StaticJavaParser se lee el archivo y se genera el árbol
                    CompilationUnit cu = StaticJavaParser.parse(archivo);

                    //-----> El analizador recorre el árbol extrayendo Halstead y el Grafo (CFG)
                    analizador.visit(cu, null);
                }

                //-----> Se colocan en una lista 
                List<ProjectMetrics> listaReportes = new ArrayList<>();
                listaReportes.add(reporteGlobal);

                //-----> Se manda la lista ya sea JSON o CSV
                MetricsExporter.export(listaReportes, carpetaResultados);

                System.out.println("===  Proceso del proyecto " + nombreDelProyectoFound + " terminado con éxito ===");

            } catch (Exception e) {
                System.err.println("Ocurrió un error durante el análisis del proyecto " + nombreDelProyectoFound + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n=== ¡Análisis de todos los proyectos finalizado con éxito! ===");
    }

    // ----> NUEVO lo de code2seq: Entra a todas las carpetas buscando archivos .java
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