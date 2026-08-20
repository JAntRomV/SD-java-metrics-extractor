package dinamica;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

//-----> Utilidades prácticas para manipular carpetas y archivos en el disco duro
public class DirFileTools {

    //-----> Crea una carpeta si no existe
    public static boolean crearDirectorio(String rutaCarpeta) {
        File directorio = new File(rutaCarpeta);
        if (!directorio.exists()) {
            return directorio.mkdirs();
        }
        return true;
    }

    //-----> Une rutas para obtener dirección absoluta
    public static String resolverRutaRelativa(String raiz, String subruta) {
        return new File(raiz, subruta).getAbsolutePath();
    }

    //-----> Revisa existencia de un archivo
    public static boolean existeArchivo(String rutaArchivo) {
        return new File(rutaArchivo).exists();
    }

    //-----> Elimina un directorio y todo su contenido
    public static void borrarDirectorioRecursivo(String rutaCarpeta) {
        Path raiz = new File(rutaCarpeta).toPath();
        if (!Files.exists(raiz)) return;

        try (Stream<Path> flujo = Files.walk(raiz)) {
            flujo.sorted(Comparator.reverseOrder())
                 .forEach(p -> {
                     try {
                         Files.delete(p);
                     } catch (IOException e) {
                         System.err.println("-----> No se pudo borrar: " + p + " (" + e.getMessage() + ")");
                     }
                 });
        } catch (IOException e) {
            System.err.println("-----> Error limpiando la carpeta " + rutaCarpeta + ": " + e.getMessage());
        }
    }
}