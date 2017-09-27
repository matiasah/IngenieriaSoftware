/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package poligonos;

import org.easetech.easytest.annotation.DataLoader;
import org.easetech.easytest.annotation.Param;
import org.easetech.easytest.annotation.Report;
import org.easetech.easytest.loader.LoaderType;
import org.easetech.easytest.runner.DataDrivenTestRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Matías
 */
@RunWith(DataDrivenTestRunner.class)
@DataLoader(filePaths = {"Tests Puntos.xls"}, loaderType = LoaderType.EXCEL)
@Report(outputLocation = "file:TestReports")
public class PuntoTest {

    public PuntoTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testPuntoMedio(
            @Param(name = "x1") double x1,
            @Param(name = "y1") double y1,
            @Param(name = "x2") double x2,
            @Param(name = "y2") double y2,
            @Param(name = "x3") double x3,
            @Param(name = "y3") double y3) {
        Punto primerPunto = new Punto(x1, y1);
        Punto segundoPunto = new Punto(x2, y2);
        Punto medio = primerPunto.getMedio(segundoPunto);

        assertEquals(x3, medio.getX(), 10 ^ -5);
        assertEquals(y3, medio.getY(), 10 ^ -5);

    }

    @Test
    public void testDistancia(
            @Param(name = "x1") double x1,
            @Param(name = "y1") double y1,
            @Param(name = "x2") double x2,
            @Param(name = "y2") double y2,
            @Param(name = "distancia") double dist) {
        Punto primerPunto = new Punto(x1, y1);
        Punto segundoPunto = new Punto(x2, y2);
        double distancia = primerPunto.getDistancia(segundoPunto);

        assertEquals(distancia, dist, 10 ^ -5);

    }

}
