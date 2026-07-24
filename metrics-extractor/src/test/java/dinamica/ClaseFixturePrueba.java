package dinamica;

// Clase "conejillo de indias" para probar el escaneador de métodos
public class ClaseFixturePrueba {

    // Constructor sin parámetros
    public ClaseFixturePrueba() {
    }

    // Método válido: NO recibe parámetros, así que SÍ debe ser escaneado
    public int metodoValido() {
        return 42;
    }

    // Método inválido: SÍ recibe parámetros, así que NO debe ser escaneado
    public void metodoConParametro(int x) {
        // Vacío a propósito
    }
}