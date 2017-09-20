package app;

import modelo.Encendido;
import modelo.Lampara;
import modelo.Motor;

public class App {

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        
        Lampara lampara = new Lampara();
        Motor motor = new Motor();
        
        lampara.encender();
        motor.encender();
        
        Encendido encendido = new Encendido() {
            public void encender() { }
            public void apagar() { }
        };
        
    }

}
