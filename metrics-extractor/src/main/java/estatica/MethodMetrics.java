package estatica;

import java.util.ArrayList;
import java.util.List;

//_____________________/Solo guarda resultados\______________
public class MethodMetrics {

    private final String fileName;
    private final String className;
    private final String methodName;
    
//----->Le pertenece a code2sep
    private List<String> caminosCode2Seq = new ArrayList<>();

//------>LOC
    private final int loc;

//------>Halstead Variables
    private final int n1; 
    private final int n2; 
    private final int N1;  
    private final int N2;  

//------>Resultados Calculados de Halstead
    private final int    vocabulary;  
    private final int    length;      
    private final double volume;      
    private final double difficulty;  
    private final double effort;      
    private final double time;        
    private final double bugs;        

//------>CC y CFG
    private final int cyclomaticComplexity;
    private final int cfgNodes;
    private final int cfgEdges;
    private final int cfgUnconnectedNodes;

//______________________________________________________________________
    public MethodMetrics(
            String fileName, String className, String methodName,
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

        this.vocabulary = HalsteadCalculator.calculateVocabulary(n1, n2);
        this.length     = HalsteadCalculator.calculateLength(N1, N2);
        this.volume     = HalsteadCalculator.calculateVolume(this.length, this.vocabulary);
        this.difficulty = HalsteadCalculator.calculateDifficulty(n1, n2, N2);
        this.effort     = HalsteadCalculator.calculateEffort(this.difficulty, this.volume);
        this.time       = HalsteadCalculator.calculateTime(this.effort);
        this.bugs       = HalsteadCalculator.calculateBugs(this.volume);

        this.cyclomaticComplexity = cyclomaticComplexity;
        this.cfgNodes             = cfgNodes;
        this.cfgEdges             = cfgEdges;
        this.cfgUnconnectedNodes  = cfgUnconnectedNodes;

    }

//------>Getters
    public String getFileName()             { return fileName; }
    public String getClassName()            { return className; }
    public String getMethodName()           { return methodName; }
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

    public int getN1() { return n1; }
    public int getN2() { return n2; }
    public int getN1Total() { return N1; } 
    public int getN2Total() { return N2; } 

    //le pertenece a code2seq
    public void setCaminosCode2Seq(List<String> caminos) { this.caminosCode2Seq = caminos; }
public List<String> getCaminosCode2Seq() { return this.caminosCode2Seq; }

}