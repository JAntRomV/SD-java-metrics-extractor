package estatica;

// GUarda los resultados de un metodo especifico-----------------------
public class MethodMetrics {

    //---------------------------DE donde viene el metodo---------------------------------------------
    private final String fileName;    
    private final String className;   
    private final String methodName;  

    //---------------------------------LOC------------------------------------------------ 
    private final int loc;

    //------------------------Los 4 valores bases de Halstead------------------------------------------
    private final int n1;   
    private final int n2;   
    private final int N1;   
    private final int N2;   

    //-------------------------Halstead (derivados, calculados en el constructor)--------------------
    private final int    vocabulary;   
    private final int    length;       
    private final double volume;       
    private final double difficulty;   
    private final double effort;       
    private final double time;         
    private final double bugs;         

    //-----------------------Complejidad Ciclomática----------------------------
    private final int cyclomaticComplexity;

    //-----------------------------CFG-------------------------------------------
    private final int cfgNodes;
    private final int cfgEdges;
    private final int cfgUnconnectedNodes;

    //----------------------- recibe todos los primitivos y calcula las derivadas---------------------------
    public MethodMetrics(
            String fileName,
            String className,
            String methodName,
            int loc,
            int n1, int n2, int N1, int N2,
            int cyclomaticComplexity,
            int cfgNodes, int cfgEdges, int cfgUnconnectedNodes) {

        this.fileName   = fileName;
        this.className  = className;
        this.methodName = methodName;
        this.loc        = loc;

        this.n1 = n1;
        this.n2 = n2;
        this.N1 = N1;
        this.N2 = N2;

        //--------------------- Derivadas de Halstead ------------------------------------
        this.vocabulary  = n1 + n2;
        this.length      = N1 + N2;

        double log2vocab = (vocabulary > 0)
                ? Math.log(vocabulary) / Math.log(2)
                : 0.0;

        this.volume     = length * log2vocab;
        this.difficulty = (n2 > 0)
                ? (n1 / 2.0) * ((double) N2 / n2)
                : 0.0;
        this.effort     = difficulty * volume;
        this.time       = effort / 18.0;
        this.bugs       = volume / 3000.0;

        this.cyclomaticComplexity = cyclomaticComplexity;
        this.cfgNodes             = cfgNodes;
        this.cfgEdges             = cfgEdges;
        this.cfgUnconnectedNodes  = cfgUnconnectedNodes;
    }

    //--------------------------Getters------------------------------------- 
    public String getFileName()             { return fileName; }
    public String getClassName()            { return className; }
    public String getMethodName()           { return methodName; }
    public int    getLoc()                  { return loc; }

    
    public int    getN1()                   { return n1; }
    public int    getN2()                   { return n2; }
    public int    getN1Total()              { return N1; }
    public int    getN2Total()              { return N2; }

    
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