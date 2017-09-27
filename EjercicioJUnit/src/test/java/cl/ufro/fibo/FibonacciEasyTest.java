/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.ufro.fibo;

import org.easetech.easytest.annotation.DataLoader;
import org.easetech.easytest.annotation.Param;
import org.easetech.easytest.annotation.Report;
import org.easetech.easytest.loader.LoaderType;
import org.easetech.easytest.runner.DataDrivenTestRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Matías
 */
@RunWith(DataDrivenTestRunner.class)
@DataLoader(filePaths = {"ValoresFibo.xls"}, loaderType = LoaderType.EXCEL)
@Report(outputLocation = "file:TestReports")
public class FibonacciEasyTest {

    public FibonacciEasyTest() {

    }

    @Test
    public void testFibo(
            @Param(name = "numero") int numero,
            @Param(name = "valorEsperado") int valorEsperado) {

        assertEquals(valorEsperado, Fibonacci.fib(numero));

    }

}
