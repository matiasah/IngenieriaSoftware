/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vista;

import java.awt.Color;
import java.awt.Point;
import javax.swing.JButton;

/**
 *
 * @author matia
 */
public class Celda extends JButton {

    private static final Color COLORSOLIDO = Color.BLUE;
    private static final Color COLORVACIO = Color.WHITE;
    
    private boolean solido;
    private boolean R1;
    private boolean R2;
    private int energia;
    
    private final Point posicion;

    public Celda(Point posicion) {
        
        this.solido = false;
        this.R1 = false;
        this.R2 = false;
        this.energia = 0;
        this.posicion = posicion;
        this.actualizarTexto();

    }
    
    private void actualizarTexto() {
        
        if ( this.R1 ) {
            
            this.setText(this.energia + " (R1)");
            
        } else if ( this.R2 ) {
            
            this.setText(this.energia + " (R2)");
            
        } else {
            
            this.setText(String.valueOf(this.energia));
            
        }
        
    }

    public void setSolido(boolean solido) {

        this.solido = solido;
        this.setBackground(this.solido ? COLORSOLIDO : COLORVACIO);
        this.setEnergia(0);

    }

    public boolean getSolido() {

        return this.solido;

    }
    
    public void setEnergia(int energia) {
        
        this.energia = energia;
        this.actualizarTexto();
        
    }
    
    public int getEnergia() {
        
        return this.energia;
        
    }
    
    public Point getPosicion() {
        
        return this.posicion;
        
    }
    
    public void setR1(boolean R1) {
        
        this.R1 = R1;
        this.actualizarTexto();
        
    }
    
    public boolean getR1() {
        
        return this.R1;
        
    }
    
    public void setR2(boolean R2) {
        
        this.R2 = R2;
        this.actualizarTexto();
        
    }
    
    public boolean getR2() {
        
        return this.R2;
        
    }

}
