/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testreproductormusical;

import java.io.File;
import java.time.Duration;
import java.util.Scanner;
import reproductormusical.Disco;
import reproductormusical.FormatoMultimedia;
import reproductormusical.GeneroDisco;
import reproductormusical.ListaMultimedia;
import reproductormusical.Multimedia;
import reproductormusical.Pelicula;

/**
 *
 * @author matia
 */
public class TestMenu {

    private final Scanner leer;
    private final ListaMultimedia lista;

    public TestMenu() {

        this.leer = new Scanner(System.in);
        this.lista = new ListaMultimedia();

    }

    private void insertarDisco() {

        /**
         * Archivo
         */
        System.out.println("Ingrese direccion del archivo");
        File archivo = new File(leer.nextLine());

        while (!archivo.exists()) {

            System.out.println("Direccion inválida, vuelva a ingresar");

            archivo = new File(leer.nextLine());

        }

        /**
         * Genero del disco
         */
        System.out.println("Ingrese el genero del disco (Rock, Pop, Punk)");

        GeneroDisco genero = null;

        do {

            String nombreGenero = leer.nextLine().toUpperCase();

            try {

                genero = GeneroDisco.valueOf(nombreGenero);

            } catch (Exception e) {

                System.out.println("Genero inválido, las opciones son: Rock, Pop, Punk");

            }

        } while (genero == null);

        /**
         * Titulo del disco
         */
        System.out.println("Ingrese el titulo del disco");
        String titulo = leer.nextLine();

        /**
         * Autor del disco
         */
        System.out.println("Ingrese el nombre del autor");
        String autor = leer.nextLine();

        /**
         * Duracion del disco
         */
        System.out.println("Ingrese la duracion del disco (en formato ISO-8601)");
        Duration duracion = null;

        do {

            try {

                duracion = Duration.parse(leer.nextLine());

            } catch (Exception e) {

                System.out.println("Duracion inválida, pruebe con otro valor (en formato ISO-8601)");

            }

        } while (duracion == null);

        /**
         * Formato del disco
         */
        System.out.println("Ingrese el formato del disco: WAV, MP3, MIDI");

        FormatoMultimedia formato = null;

        do {

            String nombreFormato = leer.nextLine().toUpperCase();

            try {

                formato = FormatoMultimedia.valueOf(nombreFormato);

            } catch (Exception e) {

                System.out.println("Formato inválido, las opciones son: WAV, MP3, MIDI");

            }

        } while (formato == null);

        // Instanciar el disco
        Disco disco = new Disco(archivo, genero, titulo, autor, duracion, formato);

        // Insertar a lista multimedia
        this.lista.agregarMultimedia(disco);

    }

    private void insertarPelicula() {

        /**
         * Archivo
         */
        System.out.println("Ingrese direccion del archivo");
        File archivo = new File(leer.nextLine());

        while (!archivo.exists()) {

            System.out.println("Direccion inválida, vuelva a ingresar");

            archivo = new File(leer.nextLine());

        }

        /**
         * Actor principal
         */
        System.out.println("Ingrese el nombre del actor principal");
        String actorPrincipal = leer.nextLine();

        /**
         * Actriz principal
         */
        System.out.println("Ingrese el nombre de la actriz principal");
        String actrizPrincipal = leer.nextLine();

        while (actrizPrincipal.length() == 0 && actorPrincipal.length() == 0) {

            System.out.println("La actriz principal no puede ser nulo");
            actrizPrincipal = leer.nextLine();

        }

        /**
         * Titulo de la pelicula
         */
        System.out.println("Ingrese el título de la pelicula");
        String titulo = leer.nextLine();

        /**
         * Autor de la pelicula
         */
        System.out.println("Ingrese el nombre del autor");
        String autor = leer.nextLine();

        /**
         * Duracion de la pelicula
         */
        System.out.println("Ingrese la duracion de la pelicula (en formato ISO-8601)");
        Duration duracion = null;

        do {

            try {

                duracion = Duration.parse(leer.nextLine());

            } catch (Exception e) {

                System.out.println("Duracion inválida, pruebe con otro valor (en formato ISO-8601)");

            }

        } while (duracion == null);

        /**
         * Formato de la pelicula
         */
        System.out.println("Ingrese el formato de la pelicula: AVI, MOV, MPG");

        FormatoMultimedia formato = null;

        do {

            String nombreFormato = leer.nextLine().toUpperCase();

            try {

                formato = FormatoMultimedia.valueOf(nombreFormato);

            } catch (Exception e) {

                System.out.println("Formato inválido, las opciones son: AVI, MOV, MPG");

            }

        } while (formato == null);

        Pelicula pelicula = new Pelicula(
                archivo,
                actorPrincipal,
                actrizPrincipal,
                titulo,
                autor,
                duracion,
                formato
        );

        this.lista.agregarMultimedia(pelicula);

    }

    private void listarDiscos() {

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Disco) {

                System.out.println(m);

            }

        }

    }

    private void listarPeliculas() {

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Pelicula) {

                System.out.println(m);

            }

        }

    }

    private void buscarDisco() {

        System.out.println("Ingrese el titulo del disco");
        String titulo = leer.nextLine();

        Disco disco = null;

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Disco) {

                if (m.getTitulo().contains(titulo)) {

                    disco = (Disco) m;
                    break;

                }

            }

        }

        if (disco == null) {

            System.out.println("Disco no encontrado");

        } else {

            System.out.println(disco);

        }

    }

    private void buscarPelicula() {

        System.out.println("Ingrese el titulo de la pelicula");
        String titulo = leer.nextLine();

        Pelicula pelicula = null;

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Pelicula) {

                if (m.getTitulo().contains(titulo)) {

                    pelicula = (Pelicula) m;

                }

            }

        }

        if (pelicula == null) {

            System.out.println("Pelicula no encontrada");

        } else {

            System.out.println(pelicula);

        }

    }

    private void reproducirDisco() {

        System.out.println("Ingrese el titulo del disco");
        String titulo = leer.nextLine();

        Disco disco = null;

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Disco) {

                if (m.getTitulo().contains(titulo)) {

                    disco = (Disco) m;
                    break;

                }

            }

        }

        if (disco == null) {

            System.out.println("Disco no encontrado");

        } else {

            disco.reproducir();

        }

    }

    private void reproducirPelicula() {

        System.out.println("Ingrese el titulo de la pelicula");
        String titulo = leer.nextLine();

        Pelicula pelicula = null;

        for (int i = 0, n = this.lista.getTam(); i < n; i++) {

            Multimedia m = this.lista.getMultimedia(i);

            if (m instanceof Pelicula) {

                if (m.getTitulo().contains(titulo)) {

                    pelicula = (Pelicula) m;

                }

            }

        }

        if (pelicula == null) {

            System.out.println("Pelicula no encontrada");

        } else {

            pelicula.reproducir();

        }

    }

    private int menu() {

        System.out.println("-------------------------");
        System.out.println("1. Insertar Disco");
        System.out.println("2. Insertar Pelicula");
        System.out.println("3. Buscar Disco");
        System.out.println("4. Buscar Pelicula");
        System.out.println("5. Listar Discos");
        System.out.println("6. Listar Peliculas");
        System.out.println("7. Reproducir Disco");
        System.out.println("8. Reproducir Pelicula");
        System.out.println("9. Salir");

        return Integer.parseInt(leer.nextLine());

    }

    public void iniciarMenu() {

        for (int opcion = this.menu(); opcion != 9; opcion = this.menu()) {

            switch (opcion) {

                case 1:
                    this.insertarDisco();
                    break;

                case 2:
                    this.insertarPelicula();
                    break;

                case 3:
                    this.buscarDisco();
                    break;

                case 4:
                    this.buscarPelicula();
                    break;

                case 5:
                    this.listarDiscos();
                    break;

                case 6:
                    this.listarPeliculas();
                    break;

                case 7:
                    this.reproducirDisco();
                    break;

                case 8:
                    this.reproducirPelicula();
                    break;

                default:
                    break;
            }

        }

    }

    public static void main(String[] args) {

        TestMenu menu = new TestMenu();

        menu.iniciarMenu();

    }

}
