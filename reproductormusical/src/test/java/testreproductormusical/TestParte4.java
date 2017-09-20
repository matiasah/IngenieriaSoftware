/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testreproductormusical;

import java.time.Duration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import reproductormusical.FormatoMultimedia;
import reproductormusical.ListaMultimedia;
import reproductormusical.Pelicula;

/**
 *
 * @author matia
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestParte4 {

    /**
     * La variable 'lista' debe ser estática para poder ser utilizada entre
     * varios tests
     */
    private static ListaMultimedia lista = new ListaMultimedia();

    public TestParte4() {

    }

    @Test
    public void testA() {

        Pelicula primeraPelicula = new Pelicula(
                null,
                null,
                "Teresa",
                "Fiesta en el DIS",
                "Anonimo",
                Duration.ofSeconds(1000),
                FormatoMultimedia.AVI
        );
        Pelicula segundaPelicula = new Pelicula(
                null,
                "Test Actor",
                null,
                "Test titulo",
                "Test autor",
                Duration.ofMinutes(100),
                FormatoMultimedia.MOV
        );
        Pelicula terceraPelicula = new Pelicula(
                null,
                "Test Actor",
                "Test Actriz",
                "Test titulo 2",
                "Test autor 2",
                Duration.ofHours(2),
                FormatoMultimedia.MPG
        );

        assertNotNull(primeraPelicula);
        assertNotNull(segundaPelicula);
        assertNotNull(terceraPelicula);

        lista.agregarMultimedia(primeraPelicula);
        lista.agregarMultimedia(segundaPelicula);
        lista.agregarMultimedia(terceraPelicula);

        assertEquals(lista.getTam(), 3);

    }

    @Test
    public void testB() {

        assertEquals(lista.getTam(), 3);

        System.out.println(lista);

    }

    @Test
    public void testCDE() {

        Pelicula pel = (Pelicula) lista.getMultimedia(1);

        assertNotNull(pel);

        Pelicula cuartaPelicula = new Pelicula(
                null,
                null,
                "",
                pel.getTitulo(),
                pel.getAutor(),
                Duration.ofSeconds(0),
                FormatoMultimedia.AVI
        );

        lista.agregarMultimedia(cuartaPelicula);

        int indice = lista.getIndice(cuartaPelicula);

        System.out.println(lista.getMultimedia(indice));
        System.out.println(indice);

    }

}
