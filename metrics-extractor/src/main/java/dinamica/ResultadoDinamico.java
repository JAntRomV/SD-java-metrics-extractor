package dinamica;

public class ResultadoDinamico {

    //-----> Identificadores basicos del metodo analizado
    private final String fileName;
    private final String className;
    private final String methodName;

    //-----> Almacena el tiempo promedio obtenido y su rango de variacion
    private final double tiempoScore;
    private final double tiempoError;
    private final String unidadTiempo;

    //-----> Almacena la cantidad de memoria RAM en bytes consumida por el metodo
    private final double memoriaAsignadaBytes;

    public ResultadoDinamico(
            String fileName, String className, String methodName,
            double tiempoScore, double tiempoError, String unidadTiempo,
            double memoriaAsignadaBytes) {

        this.fileName = fileName;
        this.className = className;
        this.methodName = methodName;
        this.tiempoScore = tiempoScore;
        this.tiempoError = tiempoError;
        this.unidadTiempo = unidadTiempo;
        this.memoriaAsignadaBytes = memoriaAsignadaBytes;
    }

    
    public String getLlaveUnion() {
        return className + "#" + methodName;
    }

    //-----> Metodos basicos para poder leer las variables guardadas desde otros archivos
    public String getFileName()             { return fileName; }
    public String getClassName()            { return className; }
    public String getMethodName()           { return methodName; }
    public double getTiempoScore()          { return tiempoScore; }
    public double getTiempoError()          { return tiempoError; }
    public String getUnidadTiempo()         { return unidadTiempo; }
    public double getMemoriaAsignadaBytes() { return memoriaAsignadaBytes; }
}