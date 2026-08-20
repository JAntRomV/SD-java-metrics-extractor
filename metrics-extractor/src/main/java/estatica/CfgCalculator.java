package estatica;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;

//-----> Calculadora del Grafo de Flujo (CFG)
public class CfgCalculator {

    //-----> Resultados del grafo
    public static class CfgResult {
        public final int nodes; //-----> Cantidad de nodos
        public final int edges; //-----> Cantidad de aristas
        public final int cyclomaticComplexity; //-----> Complejidad ciclomática
        public final int unconnectedNodes; //-----> Nodos desconectados

        public CfgResult(int nodes, int edges, int cyclomaticComplexity, int unconnectedNodes) {
            this.nodes = nodes;
            this.edges = edges;
            this.cyclomaticComplexity = cyclomaticComplexity;
            this.unconnectedNodes = unconnectedNodes;
        }
    }

    //-----> Estima metricas del CFG
    public static CfgResult estimateCfg(MethodDeclaration md, int decisions) {
        //-----> Formulas de McCabe
        int nodes = 2 + decisions;
        int edges = nodes + decisions;
        int cc    = edges - nodes + 2;
        
        //-----> Conteo de salidas anticipadas
        int earlyExits = md.findAll(ReturnStmt.class).size()
                       + md.findAll(ThrowStmt.class).size();
                       
        //-----> Conteo de nodos aislados
        int unconnectedNodes = Math.max(0, earlyExits - 1);

        return new CfgResult(nodes, edges, cc, unconnectedNodes);
    }
}