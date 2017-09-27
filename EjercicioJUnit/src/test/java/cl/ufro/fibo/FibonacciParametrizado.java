/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.ufro.fibo;

import java.util.Arrays;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author Matías
 */
@RunWith(Parameterized.class)
public class FibonacciParametrizado {
    
    private int numero;
    private int valorEsperado;
    
    public FibonacciParametrizado(int input, int output) {
        
        this.numero = input;
        this.valorEsperado = output;
        
    }

    @Parameters
    public static Collection cargarDatos() {
        
        return Arrays.asList(new Object[][] {
            {0,0},
            {1,1},
            {2,1},
            {3,2},
            {4,3},
            {5,5},
            {6,8},
            {7,13},
            {8,21},
        });
        
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testFib() {
        
        assertEquals(this.valorEsperado, Fibonacci.fib(this.numero));
        
    }
    
}
