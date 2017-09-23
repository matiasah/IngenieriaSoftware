package vistas;

import controladores.*;
import java.awt.GridLayout;
import java.awt.Point;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JPanel;

public class MatrizJV extends JPanel implements Observer {

    private final VentanaJV ventana;
    private CeldaMatrizJV [][] celdas;
    
    private int ancho;
    private int largo;

    /**
     *
     * @param ventana
     */
    public MatrizJV(VentanaJV ventana) {
        
        this.ventana = ventana;
        this.celdas = null;
        this.ancho = 0;
        this.largo = 0;
        
        this.ventana.add(this);
        
    }

    /**
     *
     * @param x
     * @param y
     */
    private void setViva(int x, int y) {
        
        this.celdas[x][y].setViva();
        
    }

    /**
     *
     * @param x
     * @param y
     */
    private void setMuerta(int x, int y) {
        
        this.celdas[x][y].setMuerta();
        
    }

    /**
     *
     * @param n
     * @param m
     */
    private void setTamaño(int n, int m) {
        
        if ( this.celdas != null ) {
            
            for (int x = 0; x < this.ancho; x++) {

                CeldaMatrizJV [] columna = this.celdas[x];

                for (int y = 0; y < this.largo; y++) {

                    this.remove(columna[y]);

                }

            }
            
        }
        
        this.ancho = m;
        this.largo = n;
        
        if ( this.ancho > 0 && this.largo > 0 ) {
            
            this.setLayout(new GridLayout(this.largo, this.ancho));
            
        }
        
        this.celdas = new CeldaMatrizJV[this.ancho][this.largo];
        
        for (int y = 0; y < this.largo; y++) {
            
            for (int x = 0; x < this.ancho; x++) {
                
                this.celdas[x][y] = new CeldaMatrizJV(this, new Point(x, y));
                
            }
            
        }
        
    }

    public VentanaJV getVentana() {
        
        return this.ventana;
        
    }

    @Override
    public void update(Observable o, Object arg) {
        
        if ( o instanceof ControladorJV && arg instanceof EventoJV ) {
            
            ControladorJV   controlador = (ControladorJV) o;
            EventoJV        evento      = (EventoJV) arg;
            
            switch ( evento.getTipo() ) {
                
                case CAMBIO_TAMAÑO:
                    
                    int ancho = (Integer) evento.get("ancho");
                    int largo = (Integer) evento.get("largo");
                    
                    this.setTamaño(ancho, largo);
                    
                    break;
                    
                case CAMBIO_CELDA:
                    
                    int x = (Integer) evento.get("x");
                    int y = (Integer) evento.get("y");
                    boolean viva = (Boolean) evento.get("viva");
                    
                    if ( viva ) {
                        
                        this.setViva(x, y);
                        
                    } else {
                        
                        this.setMuerta(x, y);
                        
                    }
                    
                    break;
                    
                default:
                    
                    break;
                    
            }
            
        }
        
    }

}
