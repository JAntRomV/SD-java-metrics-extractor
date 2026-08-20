package estatica;

//-----> Calculadora de metricas Halstead
public class HalsteadCalculator {

    //-----> Vocabulario Halstead (n1 + n2)
    public static int calculateVocabulary(int n1, int n2) {
        return n1 + n2;
    }

    //-----> Longitud Halstead (N1 + N2)
    public static int calculateLength(int N1, int N2) {
        return N1 + N2;
    }

    //-----> Volumen Halstead
    public static double calculateVolume(int length, int vocabulary) {
        if (vocabulary <= 0) return 0.0;
        double log2n = Math.log(vocabulary) / Math.log(2);
        return length * log2n;
    }

    //-----> Dificultad Halstead
    public static double calculateDifficulty(int n1, int n2, int N2) {
        if (n2 == 0) return 0.0;
        return ((double) n1 / 2.0) * ((double) N2 / (double) n2);
    }

    //-----> Esfuerzo de implementacion
    public static double calculateEffort(double difficulty, double volume) {
        return difficulty * volume;
    }

    //-----> Tiempo estimado en segundos
    public static double calculateTime(double effort) {
        return effort / 18.0;
    }

    //-----> Estimacion de errores/bugs
    public static double calculateBugs(double volume) {
        return volume / 3000.0;
    }
}