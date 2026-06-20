package estatica;

//--------------------------Solo guarda resultados y calcula automaticamente halstead-----------------------------
public class MethodMetrics {

    
    private final String fileName;
    private final String className;
    private final String methodName;

//------------ Se usa solamente para exportar para que lo lea python--------
    private final String sourceCode;

//-------------------LOC-------------------------------
    private final int loc;

//------------------Halstead Variables---------------------------
    private final int n1;  // Operadores distintos
    private final int n2;  // Operandos distintos
    private final int N1;  // Total de operadores
    private final int N2;  // Total de operandos

//--------------------Halstead Recultado-------------------------------
    private final int    vocabulary;  
    private final int    length;      
    private final double volume;      
    private final double difficulty;  
    private final double effort;      
    private final double time;        
    private final double bugs;        

//-----------------------CC y CFG----------------------------------------
    private final int cyclomaticComplexity;
    private final int cfgNodes;
    private final int cfgEdges;
    private final int cfgUnconnectedNodes;

//----------------------Calculos-----------------------------------------
    public MethodMetrics(
            String fileName, String className, String methodName,
            String sourceCode,
            int loc,
            int n1, int n2, int N1, int N2,
            int cyclomaticComplexity,
            int cfgNodes, int cfgEdges, int cfgUnconnectedNodes) {

        this.fileName   = fileName;
        this.className  = className;
        this.methodName = methodName;
        this.sourceCode = sourceCode;
        this.loc        = loc;
        this.n1 = n1; this.n2 = n2; this.N1 = N1; this.N2 = N2;

        this.vocabulary = n1 + n2;
        this.length     = N1 + N2;
        double log2n    = (vocabulary > 0) ? Math.log(vocabulary) / Math.log(2) : 0.0;
        this.volume     = length * log2n;
        this.difficulty = (n2 > 0) ? (n1 / 2.0) * ((double) N2 / n2) : 0.0;
        this.effort     = difficulty * volume;
        this.time       = effort / 18.0;
        this.bugs       = volume / 3000.0;

        this.cyclomaticComplexity = cyclomaticComplexity;
        this.cfgNodes             = cfgNodes;
        this.cfgEdges             = cfgEdges;
        this.cfgUnconnectedNodes  = cfgUnconnectedNodes;
    }

//--------------------------Getters------------------------------------------------------
    public String getFileName()             { return fileName; }
    public String getClassName()            { return className; }
    public String getMethodName()           { return methodName; }
    public String getSourceCode()           { return sourceCode; }
    public int    getLoc()                  { return loc; }
    public int    getVocabulary()           { return vocabulary; }
    public int    getLength()               { return length; }
    public double getVolume()               { return volume; }
    public double getDifficulty()           { return difficulty; }
    public double getEffort()               { return effort; }
    public double getTime()                 { return time; }
    public double getBugs()                 { return bugs; }
    public int    getCyclomaticComplexity() { return cyclomaticComplexity; }
    public int    getCfgNodes()             { return cfgNodes; }
    public int    getCfgEdges()             { return cfgEdges; }
    public int    getCfgUnconnectedNodes()  { return cfgUnconnectedNodes; }
}