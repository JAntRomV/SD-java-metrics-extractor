package estatica;
//-------------------------------------------------------------Calcula las métricas del grafo de flujo de control (CFG) ----------------------------------
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.ConditionalExpr;

public class CfgBuilder {

    public static class CfgResult {
        public int nodes;
        public int edges;
        public int CC;
        public int unconnectedNodes;

        public CfgResult(int nodes, int edges, int CC, int unconnectedNodes) {
            this.nodes = nodes;
            this.edges = edges;
            this.CC = CC;
            this.unconnectedNodes = unconnectedNodes;
        }
    }

    public CfgResult analyze(MethodDeclaration md, int decisionPoints) {
        
        int nodes = 2 + decisionPoints; 
        int edges = nodes + decisionPoints; 
        int cc = edges - nodes + 2;

        
        int earlyTerminations = md.findAll(ReturnStmt.class).size() + md.findAll(ThrowStmt.class).size();
        int unconnectedNodes = Math.max(0, earlyTerminations - 1);

        return new CfgResult(nodes, edges, cc, unconnectedNodes);
    }
}