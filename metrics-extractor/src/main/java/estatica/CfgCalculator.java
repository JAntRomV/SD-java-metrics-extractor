package estatica;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;

public class CfgCalculator {

//----->Estructura interna para agrupar los 4 datos del Grafo y poder devolverlos juntos
    public static class CfgResult {
        public final int nodes;
        public final int edges;
        public final int cyclomaticComplexity;
        public final int unconnectedNodes;

        public CfgResult(int nodes, int edges, int cyclomaticComplexity, int unconnectedNodes) {
            this.nodes = nodes;
            this.edges = edges;
            this.cyclomaticComplexity = cyclomaticComplexity;
            this.unconnectedNodes = unconnectedNodes;
        }
    }
//----->Calcula los componentes estimados del CFG
     
    public static CfgResult estimateCfg(MethodDeclaration md, int decisions) {
//----->Fórmulas teóricas basadas en métricas de complejidad de McCabe
        int nodes = 2 + decisions;
        int edges = nodes + decisions;
        int cc    = edges - nodes + 2;
        
//----->Calcular salidas tempranas (nodos que rompen el flujo normal)
        int earlyExits = md.findAll(ReturnStmt.class).size()
                       + md.findAll(ThrowStmt.class).size();
                       
//----->Estimación de nodos inconexos
        int unconnectedNodes = Math.max(0, earlyExits - 1);

        return new CfgResult(nodes, edges, cc, unconnectedNodes);
    }
}
