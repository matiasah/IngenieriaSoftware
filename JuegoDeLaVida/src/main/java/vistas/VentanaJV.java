package vistas;

import controladores.*;
import java.awt.GridLayout;
import javax.swing.JFrame;

public class VentanaJV extends JFrame {

    private MatrizJV panelMatriz;
    private ControladorJV controlador;
    private PanelInterfazJV panelInterfaz;

    public VentanaJV() {
        
        this.setLayout(new GridLayout(2, 1));
        
        this.panelMatriz = new MatrizJV(this);
        this.controlador = new ControladorJV();
        this.panelInterfaz = new PanelInterfazJV(this);
        
        this.controlador.addObserver(this.panelMatriz);
        this.controlador.setTamaño(20, 20);
        
        this.setSize(300, 600);
        this.setVisible(true);
        
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
    }

    public ControladorJV getControlador() {
        
        return this.controlador;
        
    }

}
