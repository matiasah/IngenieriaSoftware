/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testreproductormusical;

import java.io.File;
import java.time.Duration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import reproductormusical.Disco;
import reproductormusical.FormatoMultimedia;
import reproductormusical.GeneroDisco;
import reproductormusical.ListaMultimedia;
import reproductormusical.Multimedia;

/**
 *
 * @author matia
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestParte6 {

    private static ListaMultimedia lista = new ListaMultimedia();

    public TestParte6() {

    }

    @Test
    public void testA() {

        Disco primerDisco = new Disco(
                new File("Monody.mp3"),
                GeneroDisco.POP,
                "Monody",
                "TheFatRat[1]",
                Duration.ofMinutes(4).plusSeconds(50),
                FormatoMultimedia.MP3
        );
        
        Disco segundoDisco = new Disco(
                new File("Unity.mp3"),
                GeneroDisco.POP,
                "Unity",
                "TheFatRat[2]",
                Duration.ofMinutes(4).plusSeconds(9),
                FormatoMultimedia.MP3
        );
        
        Disco tercerDisco = new Disco(
                new File("Windfall.mp3"),
                GeneroDisco.POP,
                "Windfall",
                "TheFatRat[3]",
                Duration.ofMinutes(3).plusSeconds(48),
                FormatoMultimedia.MP3
        );
        
        lista.agregarMultimedia(primerDisco);
        lista.agregarMultimedia(segundoDisco);
        lista.agregarMultimedia(tercerDisco);

    }
    
    @Test
    public void testB() {
        
        System.out.println(lista);
        
    }
    
    @Test
    public void testCDE() {
        
        Multimedia primerDisco = lista.getMultimedia(1);
        
        assertNotNull(primerDisco);
        
        Disco cuartoDisco = new Disco(
                null,
                GeneroDisco.ROCK,
                primerDisco.getTitulo(),
                primerDisco.getAutor(),
                Duration.ofSeconds(0),
                FormatoMultimedia.WAV
        );
        
        int indice = lista.getIndice(cuartoDisco);
        
        System.out.println(lista.getMultimedia(indice));
        System.out.println(indice);
        
    }

}
