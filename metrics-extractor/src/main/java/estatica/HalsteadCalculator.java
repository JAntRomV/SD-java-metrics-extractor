package estatica;
//________________________/Calcula Halstead\_______________
public class HalsteadCalculator {

//----->Calcular el Vocabulario
    public static int calculateVocabulary(int n1, int n2) {
        return n1 + n2;
    }

//----->Calcular la Longitud
    public static int calculateLength(int N1, int N2) {
        return N1 + N2;
    }

//----->Calcular el Volumen
    public static double calculateVolume(int length, int vocabulary) {
        if (vocabulary <= 0) return 0.0;
//-- En Java no hay log2 directo, por eso dividimos el logaritmo natural entre logaritmo de 2
        double log2n = Math.log(vocabulary) / Math.log(2);
        return length * log2n;
    }

//----->Calcular la Dificultad
    public static double calculateDifficulty(int n1, int n2, int N2) {
        if (n2 == 0) return 0.0;
        return ((double) n1 / 2.0) * ((double) N2 / (double) n2);
    }

//----->Calcular el Esfuerzo (Dificultad * Volumen)
    public static double calculateEffort(double difficulty, double volume) {
        return difficulty * volume;
    }

//----->Calcular el Tiempo estimado
    public static double calculateTime(double effort) {
        return effort / 18.0;
    }

//----->Calcular los Bugs estimados 
    public static double calculateBugs(double volume) {
        return volume / 3000.0;
    }
}