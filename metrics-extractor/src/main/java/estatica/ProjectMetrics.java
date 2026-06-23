package estatica;
//___________________________/organiza y guarda todas las metricas de un proyecto entero\________________________
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectMetrics {
//----->Guarda el nombre del proyecto
    private final String projectName;

//----->Relaciona el nombre de la clase con la lista de todos los metodos
    private final Map<String, List<MethodMetrics>> classesByName = new LinkedHashMap<>();

//----->Recuerda de que archivo .java va cada clase
    private final Map<String, String> fileNameByClass = new LinkedHashMap<>();

//----->CRea un archivero y le da un nombre
    public ProjectMetrics(String projectName) {
        this.projectName = projectName;
    }

//______________/VA metiendo los metodos dentro del archivero\_____________

//Si es la primera vez con la clase se crea una lista vacia donde se iran colocando
// los metodos de esa clase y se guarda el nombre del archivo 
    public void addMethod(String className, String fileName, MethodMetrics method) {

        classesByName.computeIfAbsent(className, k -> new ArrayList<>());
        fileNameByClass.putIfAbsent(className, fileName);
        classesByName.get(className).add(method);
    }
//----->Devuelve el nombre del proyecto actual
    public String getProjectName() {
        return projectName;
    }
    
//----->Devuelve una lista con los nombres de todas las clases que se encontraron 
    public Iterable<String> getClassNames() {
        return classesByName.keySet();
    }

//----->Devuelve la lista de métodos de una clase específica.

    public List<MethodMetrics> getMethodsOf(String className) {
        return classesByName.getOrDefault(className, new ArrayList<>());
    }

//----->Devuelve la ruta del archivo .java de una clase
    public String getFileNameOf(String className) {
        return fileNameByClass.getOrDefault(className, "Unknown");
    }
}