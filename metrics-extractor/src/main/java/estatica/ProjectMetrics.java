package estatica;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//-----> Contenedor general de metricas por proyecto
public class ProjectMetrics {
    private final String projectName; //-----> Nombre del proyecto
    
    private final Map<String, List<MethodMetrics>> classesByName = new LinkedHashMap<>(); //-----> Mapa de clases
    
    private final Map<String, String> fileNameByClass = new LinkedHashMap<>(); //-----> Mapa de archivos

    public ProjectMetrics(String projectName) {
        this.projectName = projectName;
    }

    //-----> Agrega metodos extraidos
    public void addMethod(String className, String fileName, MethodMetrics method) {
        classesByName.computeIfAbsent(className, k -> new ArrayList<>());
        fileNameByClass.putIfAbsent(className, fileName);
        classesByName.get(className).add(method);
    }

    public String getProjectName() {
        return projectName;
    }

    public Iterable<String> getClassNames() {
        return classesByName.keySet();
    }

    public List<MethodMetrics> getMethodsOf(String className) {
        return classesByName.getOrDefault(className, new ArrayList<>());
    }

    public String getFileNameOf(String className) {
        return fileNameByClass.getOrDefault(className, "Unknown");
    }
}