package dinamica;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EscaneadorMetodos {

    //-----> Guarda los datos de un metodo encontrado: su clase y su nombre
    public static class MetodoObjetivo {
        public final String claseCompleta;
        public final String nombreMetodo;

        public MetodoObjetivo(String claseCompleta, String nombreMetodo) {
            this.claseCompleta = claseCompleta;
            this.nombreMetodo = nombreMetodo;
        }

        //-----> Une los datos con un gato "Clase#metodo" para que sea nuestra llave de union
        public String comoTexto() {
            return claseCompleta + "#" + nombreMetodo;
        }
    }

    //-----> Marcadores para saber cuantos archivos procesamos con exito y cuantos fallaron
    public int clasesEncontradas = 0;
    public int clasesDescartadas = 0;
    public int metodosValidos = 0;
    public int metodosDescartados = 0;

    //-----> Revisa la carpeta de clases y genera la lista de metodos listos para medir
    public List<MetodoObjetivo> escanear(String rutaCarpetaClases) throws Exception {
        List<MetodoObjetivo> catalogo = new ArrayList<>();

        File directorioBase = new File(rutaCarpetaClases);
        if (!directorioBase.exists()) {
            throw new IllegalArgumentException("No existe la carpeta: " + rutaCarpetaClases);
        }

        //-----> Herramienta especial para poder leer e inyectar las clases externas a la memoria
        URL url = directorioBase.toURI().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[]{url}, this.getClass().getClassLoader());

        List<String> nombresDeClases = new ArrayList<>();
        buscarClasesRecursivo(directorioBase, "", nombresDeClases);
        clasesEncontradas = nombresDeClases.size();

        //-----> Analiza cada clase encontrada una por una
        for (String nombreClase : nombresDeClases) {
            try {
                Class<?> clazz = loader.loadClass(nombreClase);

                //-----> Filtro/Escudo: Intentamos crear una copia basica. Si no tiene constructor vacio, da error y se salta la clase
                clazz.getDeclaredConstructor().newInstance();

                //-----> Revisa todos los metodos que tiene la clase adentro
                for (Method metodo : clazz.getDeclaredMethods()) {
                    //-----> Filtro/Escudo: Solo acepta metodos sin parametros y que no sean internos del sistema (que no tengan $)
                    if (metodo.getParameterCount() == 0 && !metodo.getName().contains("$")) {
                        catalogo.add(new MetodoObjetivo(nombreClase, metodo.getName()));
                        metodosValidos++;
                    } else {
                        metodosDescartados++;
                    }
                }
            } catch (Throwable e) {
                //-----> Si la clase pide configuraciones extrañas (como Sprin)
                clasesDescartadas++;
            }
        }

        return catalogo;
    }

    //-----> Escribe la lista final de metodos en el archivo de texto
    public void guardarCatalogo(List<MetodoObjetivo> catalogo, String rutaArchivo) throws Exception {
        try (PrintWriter w = new PrintWriter(rutaArchivo, StandardCharsets.UTF_8)) {
            for (MetodoObjetivo m : catalogo) {
                w.println(m.comoTexto());
            }
        }
    }

    //-----> Muestra en la pantalla el reporte final con los contadores
    public void mostrarResumen() {
        System.out.println("-----> Clases encontradas: " + clasesEncontradas);
        System.out.println("-----> Clases descartadas (sin constructor vacio / error): " + clasesDescartadas);
        System.out.println("-----> Metodos validos para medir: " + metodosValidos);
        System.out.println("-----> Metodos descartados (con parametros / internos): " + metodosDescartados);
    }

    //-----> Se mete carpeta por carpeta buscando archivos que terminen en .class de forma automatica
    private void buscarClasesRecursivo(File carpetaActual, String paqueteActual, List<String> listaClases) {
        File[] archivos = carpetaActual.listFiles();
        if (archivos == null) return;

        for (File archivo : archivos) {
            if (archivo.isDirectory()) {
                String nuevoPaquete = paqueteActual.isEmpty() ? archivo.getName() : paqueteActual + "." + archivo.getName();
                buscarClasesRecursivo(archivo, nuevoPaquete, listaClases);
            } else if (archivo.getName().endsWith(".class") && !archivo.getName().contains("$")) {
                String nombreClase = archivo.getName().substring(0, archivo.getName().length() - 6);
                String claseCompleta = paqueteActual.isEmpty() ? nombreClase : paqueteActual + "." + nombreClase;
                listaClases.add(claseCompleta);
            }
        }
    }
}