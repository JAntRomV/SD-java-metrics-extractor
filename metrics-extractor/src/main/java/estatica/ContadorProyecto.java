package estatica;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//-----> Clase para llevar el conteo de métricas por cada proyecto
public class ContadorProyecto {
    public int clasesTotales = 0;
    public int metodosTotalesProyecto = 0;
    
    //-----> Lista para guardar el reporte individual de cada clase del proyecto
    public List<String> reporteMetodosPorClase = new ArrayList<>();
    
    //----->Lista para almacenar las clases analizadas y sus tamaños en disco
    public List<ArchivoPesado> registroPesos = new ArrayList<>();

    //-----> Para modelar el peso de cada archivo
    public static class ArchivoPesado {
        public String nombre;
        public long tamanoBytes;

        public ArchivoPesado(String nombre, long tamanoBytes) {
            this.nombre = nombre;
            this.tamanoBytes = tamanoBytes;
        }

        public double getTamanoKB() {
            return tamanoBytes / 1024.0;
        }
    }

    // ----->Para registrar los datos del archivo y su peso en disco
    public void registrarPesoArchivo(File archivo) {
        if (archivo != null && archivo.exists()) {
            registroPesos.add(new ArchivoPesado(archivo.getName(), archivo.length()));
        }
    }

    //-----> Imprime el reporte final en la terminal con todos los datos concentrados
    public void mostrarReporteTerminal(String nombreProyecto) {
        System.out.println("\n========================================");
        System.out.println(" REPORTES FINALES: " + nombreProyecto);
        System.out.println("========================================");
        System.out.println("-----> Clases totales del proyecto: " + clasesTotales);
        System.out.println("-----> Total de métodos del proyecto: " + metodosTotalesProyecto);
        System.out.println("----------------------------------------");
        System.out.println("-----> DESGLOSE DE MÉTODOS POR CLASE:");
        
        //-----> Imprime el total de métodos que tuvo cada clase guardada
        for (String desgloseClase : reporteMetodosPorClase) {
            System.out.println(desgloseClase);
        }
        System.out.println("----------------------------------------");
        
        //----->Llamamos a la función para imprimir el Top 10
        imprimirTop10ClasesPesadas();
        
        System.out.println("========================================\n");
    }

    //----->Ordena e imprime el formato de los 10 archivos más pesados
    private void imprimirTop10ClasesPesadas() {
        if (registroPesos.isEmpty()) return;

        // Ordenamos de mayor a menor tamaño
        Collections.sort(registroPesos, new Comparator<ArchivoPesado>() {
            @Override
            public int compare(ArchivoPesado a1, ArchivoPesado a2) {
                return Long.compare(a2.tamanoBytes, a1.tamanoBytes);
            }
        });

        System.out.println(" TOP 10: CLASES MÁS PESADAS (TAMAÑO EN DISCO)");
        
        // Tomamos 10 o el total de archivos si hay menos
        int limite = Math.min(10, registroPesos.size());
        for (int i = 0; i < limite; i++) {
            ArchivoPesado archivo = registroPesos.get(i);
            System.out.printf("  %d. %-30s -> %.2f KB\n", 
                    (i + 1), 
                    archivo.nombre, 
                    archivo.getTamanoKB());
        }
    }
}