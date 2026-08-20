package dinamica;

//-----> Guarda datos de tiempo y memoria de un método
public class ResultadoDinamico {

    private final String fileName;
    private final String className;
    private final String methodName;

    private final double tiempoScore;
    private final double tiempoError;
    private final String unidadTiempo;

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

    //-----> Genera identificador formato Clase#Metodo
    public String getLlaveUnion() {
        return className + "#" + methodName;
    }

    public String getFileName()             { return fileName; }
    public String getClassName()            { return className; }
    public String getMethodName()           { return methodName; }
    public double getTiempoScore()          { return tiempoScore; }
    public double getTiempoError()          { return tiempoError; }
    public String getUnidadTiempo()         { return unidadTiempo; }
    public double getMemoriaAsignadaBytes() { return memoriaAsignadaBytes; }
}