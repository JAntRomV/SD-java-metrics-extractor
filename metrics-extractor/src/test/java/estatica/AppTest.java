package estatica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class AppTest {

    // SERIE DE PRUEBAS 1: FÓRMULAS DE HALSTEAD

    @Test
    public void testFormulasBasicasHalstead() {
//-----> ESCENARIO (Datos controlados en papel)
        int n1 = 4;  // 4 operadores distintos
        int n2 = 4;  // 4 operandos distintos
        int N1 = 8;  // 8 operadores totales
        int N2 = 8;  // 8 operandos totales

//----->COMPROBACIONES
    // Vocabulario: n1 + n2 = 4 + 4 = 8
        assertEquals(8, HalsteadCalculator.calculateVocabulary(n1, n2), "El vocabulario debería ser 8");

    // Longitud: N1 + N2 = 8 + 8 = 16
        assertEquals(16, HalsteadCalculator.calculateLength(N1, N2), "La longitud debería ser 16");
    }

    @Test
    public void testFormulasDecimalesHalstead() {
//----->Escenario teórico para probar Volumen, Dificultad y Esfuerzo
        int longitud = 16;
        int vocabulario = 8;
        int n1 = 4;
        int n2 = 4;
        int N2 = 8;

        // Cálculos esperados:
        // Volumen = 16 * log2(8) = 16 * 3 = 48.0
        // Dificultad = (4 / 2) * (8 / 4) = 4.0
        // Esfuerzo = 48.0 * 4.0 = 192.0

        // Usamos 0.001 como margen de tolerancia por los decimales (double)
        assertEquals(48.0, HalsteadCalculator.calculateVolume(longitud, vocabulario), 0.001, "El volumen debería ser 48.0");
        assertEquals(4.0, HalsteadCalculator.calculateDifficulty(n1, n2, N2), 0.001, "La dificultad debería ser 4.0");
        assertEquals(192.0, HalsteadCalculator.calculateEffort(48.0, 4.0), 0.001, "El esfuerzo debería ser 192.0");
    }

    // SERIE DE PRUEBAS 2: CÁLCULOS DEL GRAFO (CFG) Y COMPLEJIDAD CICLOMÁTICA

    @Test
    public void testCalculoGrafoYComplejidad() {
        // --- Escenario simulado para un método que tiene 3 decisiones (por ejemplo, 3 "if") ---
        int decisionesEncontradas = 3;

        // Le pedimos a tu clase experta CfgCalculator que estime el resultado
        // (Le pasamos 'null' en el MethodDeclaration porque solo queremos evaluar las matemáticas internas)
        CfgCalculator.CfgResult resultado = CfgCalculator.estimateCfg(null, decisionesEncontradas);

        // --- COMPROBACIONES ---
        // Nodos esperados = decisiones + 2 -> 3 + 2 = 5
        assertEquals(5, resultado.nodes, "Debería tener 5 nodos");

        // Aristas (Edges) esperadas = decisiones + 2 -> 3 + 2 = 5
        assertEquals(5, resultado.edges, "Debería tener 5 aristas");

        // Complejidad Ciclomática (CC) esperada = decisiones + 1 -> 3 + 1 = 4
        assertEquals(4, resultado.cyclomaticComplexity, "La complejidad ciclomática debería ser 4");
        
        // Nodos inconexos (sueltos) siempre esperamos 0
        assertEquals(0, resultado.unconnectedNodes, "Los nodos inconexos deberían ser 0");
    }
}