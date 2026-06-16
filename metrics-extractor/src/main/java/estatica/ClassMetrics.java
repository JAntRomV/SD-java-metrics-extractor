package estatica;

import java.util.ArrayList;
import java.util.List;

//--------------------------------- Contenedor de métricas para UNA sola clase.-------------------------------------------
 /*
 * Guarda:
 *  - El nombre del archivo .java donde vive la clase (ruta relativa al proyecto)
 *  - El nombre de la clase
 *  - La lista de métodos de esa clase, cada uno con sus métricas individuales
 */
public class ClassMetrics {

    private final String fileName;    
    private final String className;   

    private final List<MethodMetrics> methods = new ArrayList<>();

    public ClassMetrics(String fileName, String className) {
        this.fileName  = fileName;
        this.className = className;
    }

//------------------------------------Agrega las métricas de un método a esta clase.------------------------------------

    public void addMethod(MethodMetrics method) {
        methods.add(method);
    }

//-----------------------------------Getters-----------------------------------------------------------
    public String              getFileName()  { return fileName; }
    public String              getClassName() { return className; }
    public List<MethodMetrics> getMethods()   { return methods; }
}