package vistas;

import controladores.ControladorJV;
import javax.swing.JButton;
import java.awt.Point;
import java.awt.Color;

public class CeldaMatrizJV extends JButton {
    
    private static final Color COLOR_VIVA = Color.WHITE;
    private static final Color COLOR_MUERTA = Color.BLACK;

    private MatrizJV matriz;
    private Point posicion;

    /**
     *
     * @param matriz
     */
    public CeldaMatrizJV(MatrizJV matriz, Point posicion) {
        
        this.matriz = matriz;
        this.posicion = posicion;
        this.setMuerta();
        this.addActionListener( (Event) -> this.presionarCelda() );
        
        matriz.add(this);
        
    }

    private void presionarCelda() {
        
        ControladorJV controlador = this.matriz.getVentana().getControlador();
        
        if ( controlador != null ) {
            
            int x = (int) this.posicion.getX();
            int y = (int) this.posicion.getY();
            
            if ( controlador.getViva(x, y) ) {
                
                controlador.setMuerta(x, y);
                
            } else {
                
                controlador.setViva(x, y);
                
            }
            
        }
        
    }

    public void setViva() {
        
        this.setBackground(COLOR_VIVA);
        
    }

    public void setMuerta() {
        
        this.setBackground(COLOR_MUERTA);
        
    }

}
