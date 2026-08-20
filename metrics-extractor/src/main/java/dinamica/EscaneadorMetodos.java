package dinamica;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

//-----> Busca métodos ejecutables sin argumentos
public class EscaneadorMetodos {

    //-----> Modelo de representación de método
    public static class MetodoObjetivo {
        public final String claseCompleta;
        public final String nombreMetodo;

        //-----> Asigna propiedades del método
        public MetodoObjetivo(String claseCompleta, String nombreMetodo) {
            this.claseCompleta = claseCompleta;
            this.nombreMetodo = nombreMetodo;
        }

        //-----> Retorna formato legible
        public String comoTexto() {
            return claseCompleta + "#" + nombreMetodo;
        }
    }

    //-----> Métricas generales del escaneo
    public int clasesEncontradas = 0;
    public int clasesDescartadas = 0;
    public int metodosValidos = 0;
    public int metodosDescartados = 0;

    //-----> Registro de fallas al cargar clases
    private final List<String> detalleClasesDescartadas = new ArrayList<>();

    //-----> Escanea usando ruta base
    public List<MetodoObjetivo> escanear(String rutaCarpetaClases) throws Exception {
        return escanear(rutaCarpetaClases, rutaCarpetaClases);
    }

    //-----> Escanea clases y filtra métodos por reflexión
    public List<MetodoObjetivo> escanear(String rutaCarpetaClases, String classpathParaCargar) throws Exception {
        List<MetodoObjetivo> catalogo = new ArrayList<>();

        String[] rutas = rutaCarpetaClases.split(java.util.regex.Pattern.quote(File.pathSeparator));

        List<File> directoriosBase = new ArrayList<>();
        for (String ruta : rutas) {
            if (ruta.isBlank()) continue;
            File dir = new File(ruta);
            if (!dir.exists()) {
                throw new IllegalArgumentException("No existe la carpeta: " + ruta);
            }
            directoriosBase.add(dir);
        }

        if (directoriosBase.isEmpty()) {
            throw new IllegalArgumentException("No se recibio ninguna carpeta de clases valida: " + rutaCarpetaClases);
        }

        List<URL> urls = new ArrayList<>();
        for (String ruta : classpathParaCargar.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!ruta.isBlank()) {
                urls.add(new File(ruta).toURI().toURL());
            }
        }

        //-----> Instancia clases externas sin argumentos
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader())) {
            for (File directorioBase : directoriosBase) {
                List<String> nombresDeClases = new ArrayList<>();
                buscarClasesRecursivo(directorioBase, "", nombresDeClases);
                clasesEncontradas += nombresDeClases.size();

                for (String nombreClase : nombresDeClases) {
                    try {
                        Class<?> clazz = loader.loadClass(nombreClase);
                        clazz.getDeclaredConstructor().newInstance();

                        for (Method metodo : clazz.getDeclaredMethods()) {
                            //-----> Identifica métodos compatibles
                            if (metodo.getParameterCount() == 0 && !metodo.getName().contains("$")) {
                                catalogo.add(new MetodoObjetivo(nombreClase, metodo.getName()));
                                metodosValidos++;
                            } else {
                                metodosDescartados++;
                            }
                        }
                    } catch (Throwable e) {
                        clasesDescartadas++;
                        Throwable causaReal = e.getCause() != null ? e.getCause() : e;
                        detalleClasesDescartadas.add(nombreClase + " -> "
                                + causaReal.getClass().getSimpleName() + ": " + causaReal.getMessage());
                    }
                }
            }
        }

        return catalogo;
    }

    //-----> Guarda métodos escaneados en archivo
    public void guardarCatalogo(List<MetodoObjetivo> catalogo, String rutaArchivo) throws Exception {
        try (PrintWriter w = new PrintWriter(rutaArchivo, StandardCharsets.UTF_8)) {
            for (MetodoObjetivo m : catalogo) {
                w.println(m.comoTexto());
            }
        }
    }

    //-----> Escribe datos del resumen
    public void guardarResumen(String rutaArchivo) throws Exception {
        try (PrintWriter w = new PrintWriter(rutaArchivo, StandardCharsets.UTF_8)) {
            w.println("clasesEncontradas=" + clasesEncontradas);
            w.println("clasesDescartadas=" + clasesDescartadas);
            w.println("metodosValidos=" + metodosValidos);
            w.println("metodosDescartados=" + metodosDescartados);
        }
    }

    //-----> Guarda registro de clases omitidas
    public void guardarClasesDescartadas(String rutaArchivo) throws Exception {
        try (PrintWriter w = new PrintWriter(rutaArchivo, StandardCharsets.UTF_8)) {
            if (detalleClasesDescartadas.isEmpty()) {
                w.println("Ninguna clase fue descartada.");
            } else {
                for (String linea : detalleClasesDescartadas) {
                    w.println(linea);
                }
            }
        }
    }

    //-----> Muestra conteos en la terminal
    public void mostrarResumen() {
        System.out.println("-----> Clases encontradas: " + clasesEncontradas);
        System.out.println("-----> Clases descartadas (sin constructor vacio / error): " + clasesDescartadas);
        System.out.println("-----> Metodos validos para medir: " + metodosValidos);
        System.out.println("-----> Metodos descartados (con parametros / internos): " + metodosDescartados);
    }

    //-----> Búsqueda recursiva de archivos de clases
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