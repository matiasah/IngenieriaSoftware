/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controladores;

import java.util.HashMap;

/**
 *
 * @author Matías
 */
public class EventoJV {
    
    private final TipoEventoJV tipo;
    private HashMap datos;
    
    public EventoJV(TipoEventoJV tipo, HashMap datos) {
        
        this.tipo = tipo;
        this.datos = datos;
        
    }
    
    public TipoEventoJV getTipo() {
        
        return this.tipo;
        
    }
    
    public Object get(Object indice) {
        
        return this.datos.get(indice);
        
    }
    
}
