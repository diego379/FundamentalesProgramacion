import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class Producto {
    String id, nombre;
    double precio;
    int cantidadVendida = 0;

    Producto(String id, String n, double p) {
        this.id = id;
        this.nombre = n;
        this.precio = p;
    }

    public String getId() {
        return id;
    }
}

class Vendedor {
    String tipoDoc, numDoc, nombres, apellidos;
    double ventasTotales = 0.0;

    Vendedor(String td, String nd, String n, String a) {
        this.tipoDoc = td;
        this.numDoc = nd;
        this.nombres = n;
        this.apellidos = a;
    }

    public String getNumDoc() {
        return numDoc;
    }
}

public class Main {

    public static void main(String[] args) {
        try {
            System.out.println("Iniciando...");

            Map<String, Producto> mapaProductos = cargarDatos(
                    "productos.csv",
                    linea -> {
                        String[] d = linea.split(";");
                        return new Producto(d[0].trim(), d[1].trim(), Double.parseDouble(d[2].trim()));
                    },
                    Producto::getId
            );

            Map<String, Vendedor> mapaVendedores = cargarDatos(
                    "vendedores.csv",
                    linea -> {
                        String[] d = linea.split(";");
                        return new Vendedor(d[0].trim(), d[1].trim(), d[2].trim(), d[3].trim());
                    },
                    Vendedor::getNumDoc
            );

           
            try (Stream<Path> paths = Files.walk(Paths.get("."))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith("vendedor_"))
                        .forEach(path -> procesarArchivoVenta(path, mapaProductos, mapaVendedores));
            }

            generarReportes(mapaVendedores, mapaProductos);

            System.out.println("¡Reportes generados!");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private static <T> Map<String, T> cargarDatos(String archivo, Function<String, T> constructor, Function<T, String> getKey) throws IOException {
        Path p = Paths.get(archivo);
        if (!Files.exists(p)) throw new IOException("Archivo no encontrado: " + archivo);

        try (Stream<String> lines = Files.lines(p)) {
            return lines
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(constructor)
                    .collect(Collectors.toMap(getKey, item -> item));
        }
    }

    private static void procesarArchivoVenta(Path archivo, Map<String, Producto> prods, Map<String, Vendedor> vends) {
        try {
            List<String> lineas = Files.readAllLines(archivo);
            if (lineas.isEmpty()) {
                System.err.println("ADVERTENCIA: Archivo vacío - " + archivo.getFileName());
                return;
            }

            String[] header = lineas.get(0).split(";");
            if (header.length < 2) {
                System.err.println("ADVERTENCIA: Encabezado inválido - " + archivo.getFileName());
                return;
            }

            String idVendedor = header[1].trim();
            Vendedor vendedor = vends.get(idVendedor);
            if (vendedor == null) {
                System.err.println("ADVERTENCIA: Vendedor no encontrado - " + idVendedor);
                return;
            }

            for (int i = 1; i < lineas.size(); i++) {
                String[] datos = lineas.get(i).split(";");
                if (datos.length < 2) continue;

                Producto producto = prods.get(datos[0].trim());
                if (producto == null) continue;

                int cantidad;
                try {
                    cantidad = Integer.parseInt(datos[1].trim());
                } catch (NumberFormatException e) {
                    System.err.println("ADVERTENCIA: Cantidad inválida en archivo " + archivo.getFileName() + " línea " + (i + 1));
                    continue;
                }

                if (cantidad < 0) {
                    System.err.println("ADVERTENCIA: Cantidad negativa en archivo " + archivo.getFileName() + " línea " + (i + 1));
                    continue;
                }

                if (producto.precio < 0) {
                    System.err.println("ADVERTENCIA: Precio negativo para producto " + producto.nombre + " en archivo " + archivo.getFileName());
                    continue;
                }

                vendedor.ventasTotales += producto.precio * cantidad;
                producto.cantidadVendida += cantidad;
            }
        } catch (Exception e) {
            System.err.println("ADVERTENCIA: Error procesando archivo " + archivo.getFileName() + " - " + e.getMessage());
        }
    }

    private static void generarReportes(Map<String, Vendedor> mapaVendedores, Map<String, Producto> mapaProductos) throws IOException {
        List<Vendedor> vendedoresOrdenados = mapaVendedores.values().stream()
                .sorted(Comparator.comparingDouble(v -> -v.ventasTotales))
                .collect(Collectors.toList());

        try (PrintWriter writer = new PrintWriter("reporte_vendedores.csv")) {
            for (Vendedor v : vendedoresOrdenados) {
                writer.printf("%s %s;%.2f\n", v.nombres, v.apellidos, v.ventasTotales);
            }
        }

        List<Producto> productosOrdenados = mapaProductos.values().stream()
                .sorted(Comparator.comparingInt(p -> -p.cantidadVendida))
                .collect(Collectors.toList());

        try (PrintWriter writer = new PrintWriter("reporte_productos.csv")) {
            for (Producto p : productosOrdenados) {
                writer.printf("%s;%.2f\n", p.nombre, p.precio);
            }
        }
    }
}
