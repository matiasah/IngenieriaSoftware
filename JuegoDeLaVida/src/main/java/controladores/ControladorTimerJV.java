/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controladores;

import java.util.TimerTask;

/**
 *
 * @author Matías
 */
public class ControladorTimerJV extends TimerTask {
    
    private final ControladorJV controlador;
    
    /**
     * Constructor de la clase
     * @param controlador 
     */
    public ControladorTimerJV(ControladorJV controlador) {
        
        this.controlador = controlador;
        
    }
    
    @Override
    public void run() {
        
        this.controlador.run();
        
    }
    
}
