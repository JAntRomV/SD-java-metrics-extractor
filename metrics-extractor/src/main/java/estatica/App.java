package estatica;

public class App {
    public static void main(String[] args) {
        System.out.println("===  Iniciando Extracción de Métricas en Lote ===");

        //-----> Si llegan argumentos, se usan esas rutas
        //-----> Si no llega nada, se usan las rutas de siempre como respaldo
        String carpetaCodigoFuente = (args.length > 0) ? args[0] : "/home/tania/Documentos/ejemplojava";
        String carpetaResultados   = (args.length > 1) ? args[1] : "/home/tania/Escritorio/SD-java-metrics-extractor/metrics-extractor/Resultados";

        //-----> Se delega todo el trabajo pesado al procesador de negocio
        ProcesadorMetricas procesador = new ProcesadorMetricas();
        procesador.iniciarAnalisis(carpetaCodigoFuente, carpetaResultados);

        System.out.println("\n=== ¡Análisis de todos los proyectos finalizado con éxito! ===");
    }
}