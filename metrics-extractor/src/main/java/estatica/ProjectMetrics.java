package estatica;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ----> Esta clase actúa como un contenedor global que agrupa todas las clases y métodos analizados dentro de un mismo proyecto
public class ProjectMetrics {
    private final String projectName;
    
    // ----> Mapea o relaciona cada nombre de clase con la lista de métricas de sus métodos
    private final Map<String, List<MethodMetrics>> classesByName = new LinkedHashMap<>();
    
    // ----> Asocia el nombre de la clase con el nombre original del archivo físico
    private final Map<String, String> fileNameByClass = new LinkedHashMap<>();

    // ----> Constructor: crea el contenedor para el proyecto especificado
    public ProjectMetrics(String projectName) {
        this.projectName = projectName;
    }

    // ----> Agrega las métricas de un método a su clase correspondiente en el mapa
    public void addMethod(String className, String fileName, MethodMetrics method) {
        classesByName.computeIfAbsent(className, k -> new ArrayList<>());
        fileNameByClass.putIfAbsent(className, fileName);
        classesByName.get(className).add(method);
    }

    // ----> Obtiene el nombre del proyecto
    public String getProjectName() {
        return projectName;
    }

    // ----> Regresa la lista con los nombres de todas las clases procesadas
    public Iterable<String> getClassNames() {
        return classesByName.keySet();
    }

    // ----> Devuelve todos los métodos procesados pertenecientes a una clase en específico
    public List<MethodMetrics> getMethodsOf(String className) {
        return classesByName.getOrDefault(className, new ArrayList<>());
    }

    // ----> Devuelve el nombre del archivo original asociado a la clase
    public String getFileNameOf(String className) {
        return fileNameByClass.getOrDefault(className, "Unknown");
    }
}