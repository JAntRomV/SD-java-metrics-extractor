package estatica;

import java.util.ArrayList;
import java.util.List;

//------------------------Contenedor de métricas----------------------------------------


public class ProjectMetrics {

    private final String projectName;

    // Una entrada por cada clase encontrada dentro del proyecto
    private final List<ClassMetrics> classes = new ArrayList<>();

    public ProjectMetrics(String projectName) {
        this.projectName = projectName;
    }

   
    public void addClass(ClassMetrics classMetrics) {
        classes.add(classMetrics);
    }

    //---------------------------Getters---------------------------------------
    public String             getProjectName() { return projectName; }
    public List<ClassMetrics> getClasses()     { return classes; }
}