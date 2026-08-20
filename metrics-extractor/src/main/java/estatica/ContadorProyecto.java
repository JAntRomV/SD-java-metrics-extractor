package estatica;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//-----> Contador general del proyecto
public class ContadorProyecto {
    public int clasesTotales = 0; //-----> Total clases
    public int metodosTotalesProyecto = 0; //-----> Total metodos
    
    public List<String> reporteMetodosPorClase = new ArrayList<>(); //-----> Desglose
    public List<ArchivoPesado> registroPesos = new ArrayList<>(); //-----> Pesos en KB

    //-----> Estructura de peso de archivos
    public static class ArchivoPesado {
        public String nombre;
        public long tamanoBytes;

        public ArchivoPesado(String nombre, long tamanoBytes) {
            this.nombre = nombre;
            this.tamanoBytes = tamanoBytes;
        }

        public double getTamanoKB() {
            return tamanoBytes / 1024.0; //-----> Convierte a KB
        }
    }

    //-----> Guarda el tamaño del archivo
    public void registrarPesoArchivo(File archivo) {
        if (archivo != null && archivo.exists()) {
            registroPesos.add(new ArchivoPesado(archivo.getName(), archivo.length()));
        }
    }

    //-----> Muestra resumen final en consola
    public void mostrarReporteTerminal(String nombreProyecto) {
        System.out.println("\n========================================");
        System.out.println(" REPORTES FINALES: " + nombreProyecto);
        System.out.println("========================================");
        System.out.println("-----> Clases totales del proyecto: " + clasesTotales);
        System.out.println("-----> Total de métodos del proyecto: " + metodosTotalesProyecto);
        System.out.println("----------------------------------------");
        System.out.println("-----> DESGLOSE DE MÉTODOS POR CLASE:");
        
        for (String desgloseClase : reporteMetodosPorClase) {
            System.out.println(desgloseClase);
        }
        System.out.println("----------------------------------------");
        
        imprimirTop10ClasesPesadas();
        
        System.out.println("========================================\n");
    }

    //-----> Muestra los 10 archivos mas grandes
    private void imprimirTop10ClasesPesadas() {
        if (registroPesos.isEmpty()) return;

        //-----> Ordena de mayor a menor
        Collections.sort(registroPesos, new Comparator<ArchivoPesado>() {
            @Override
            public int compare(ArchivoPesado a1, ArchivoPesado a2) {
                return Long.compare(a2.tamanoBytes, a1.tamanoBytes);
            }
        });

        System.out.println(" TOP 10: CLASES MÁS PESADAS (TAMAÑO EN DISCO)");
        
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