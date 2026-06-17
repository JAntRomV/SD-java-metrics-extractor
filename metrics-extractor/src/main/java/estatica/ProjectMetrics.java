package estatica;
//-------------------------------------------------------------------Organiza y guarda datos--------------------------------------------------------
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectMetrics {

    private final String projectName;

    private final Map<String, List<MethodMetrics>> classesByName = new LinkedHashMap<>();

//----------------------------- Guarda el archivo .java corresponde a cada clase----------------------------------    
    private final Map<String, String> fileNameByClass = new LinkedHashMap<>();

    public ProjectMetrics(String projectName) {
        this.projectName = projectName;
    }

    public void addMethod(String className, String fileName, MethodMetrics method) {
//-------------------------------Si es la primera vez que vemos esta clase, inicializa su lista-----------------
        classesByName.computeIfAbsent(className, k -> new ArrayList<>());
//-------------------------------Guarda la ruta del archivo para esta clase         
        fileNameByClass.putIfAbsent(className, fileName);
//---------------------------- agrega el metodo a la lista de la clase---------------------------------------
        classesByName.get(className).add(method);
    }

    
    public String getProjectName() {
        return projectName;
    }
    
//----------------- Devuelve los nombres de todas las clases encontradas en el proyecto.------------------------
    public Iterable<String> getClassNames() {
        return classesByName.keySet();
    }

//--------------------Devuelve la lista de métodos de una clase específica.----------------------------

    public List<MethodMetrics> getMethodsOf(String className) {
        return classesByName.getOrDefault(className, new ArrayList<>());
    }

//------------------------Devuelve la ruta relativa del archivo .java de una clase.------------------------
    public String getFileNameOf(String className) {
        return fileNameByClass.getOrDefault(className, "Unknown");
    }
}