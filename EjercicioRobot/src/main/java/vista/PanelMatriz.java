package vista;

import java.awt.GridLayout;
import java.awt.Point;
import java.util.ArrayList;
import javax.swing.*;

public class PanelMatriz extends JPanel {
    
    private static final int celdasSolidas = 13;
    private static final int [][] matrizEnergia = new int[][] {
        {7,6,5,6,7},
        {6,3,2,3,6},
        {5,2,0,2,5},
        {6,3,2,3,6},
        {7,6,5,6,7},
    };
    
    private Celda[][] tablero;
    private ArrayList<Celda> celdaSolida;
    private Point posicionR1;
    private Point posicionR2;
    
    public PanelMatriz() {
        
        this.posicionR1 = null;
        this.posicionR2 = null;
        this.celdaSolida = new ArrayList<Celda>();
        
        this.iniciarComponentes();
        
    }
    
    private void iniciarComponentes() {
        
        this.setLayout(new GridLayout(5, 5));
        this.tablero = new Celda[5][5];
        
        for (int x = 0; x < this.tablero.length; x++) {
            
            Celda[] columnaTablero = this.tablero[x];
            
            for (int y = 0; y < columnaTablero.length; y++) {
                
                Celda celda = new Celda(new Point(x, y));
                
                columnaTablero[y] = celda;
                
                this.add(celda);
                
            }
            
        }
        
    }
    
    public void generarBloques() {
        
        ArrayList<Celda> celdas = new ArrayList<>();
        
        for (Celda[] columnaTablero : this.tablero) {
            
            for (Celda celda : columnaTablero) {
                
                celda.setSolido(false);
                celda.setR1(false);
                celda.setR2(false);
                celdas.add(celda);
                
            }
            
        }
        
        for (int i = 0; i < celdasSolidas; i++) {

            // Seleccionar una celda al azar y hacerla solida
            // Quitar de la lista de celdas, para no repetir esta celda
            int indice = (int) (Math.random() * celdas.size());
            Celda celda = celdas.remove(indice);
            
            celda.setSolido(true);
            this.celdaSolida.add(celda);
            
        }
        
    }
    
    public void cargarEnergia() {
        
        for (int x = 0; x < this.tablero.length; x++) {
            
            Celda [] columnaTablero = this.tablero[x];
            
            for (int y = 0; y < columnaTablero.length; y++) {
                
                columnaTablero[y].setEnergia(matrizEnergia[x][y]);
                
            }
            
        }
        
    }
    
    public void posicionarRobot1() {
        
        int     indice  =   (int) ( Math.random() * this.celdaSolida.size() );
        Celda   celda   =   this.celdaSolida.get( indice );
        
        this.posicionR1 = celda.getPosicion();
        celda.setR1(true);
        
    }
    
    public void posicionarRobot2() {
        
        int     indice  =   (int) ( Math.random() * this.celdaSolida.size() );
        Celda   celda   =   this.celdaSolida.get( indice );
        
        this.posicionR2 = celda.getPosicion();
        celda.setR2(true);
        
    }
    
}
