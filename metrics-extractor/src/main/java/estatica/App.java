package estatica;

//-----> Clase principal de entrada
public class App {
    public static void main(String[] args) {
        System.out.println("===  Iniciando Extracción de Métricas en Lote ===");

        //-----> Ruta de entrada: argumento 0 o ruta por defecto
        String carpetaCodigoFuente = (args.length > 0) ? args[0] : "/home/tania/Documentos/ejemplojava";
        //-----> Ruta de salida: argumento 1 o ruta por defecto
        String carpetaResultados   = (args.length > 1) ? args[1] : "/home/tania/Escritorio/SD-java-metrics-extractor/metrics-extractor/Resultados";

        //-----> Crea procesador e inicia analisis
        ProcesadorMetricas procesador = new ProcesadorMetricas();
        procesador.iniciarAnalisis(carpetaCodigoFuente, carpetaResultados);

        System.out.println("\n=== ¡Análisis de todos los proyectos finalizado con éxito! ===");
    }
}