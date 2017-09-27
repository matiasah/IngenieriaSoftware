package vistas;

import controladores.*;
import java.awt.BorderLayout;
import javax.swing.JFrame;

public class VentanaJV extends JFrame {

    private MatrizJV panelMatriz;
    private ControladorJV controlador;
    private PanelInterfazJV panelInterfaz;

    public VentanaJV() {
        
        this.panelMatriz = new MatrizJV(this);
        this.controlador = new ControladorJV();
        this.panelInterfaz = new PanelInterfazJV(this);
        
        this.controlador.addObserver(this.panelMatriz);
        this.controlador.setTamaño(50, 50);
        
        this.add(this.panelMatriz, BorderLayout.CENTER);
        this.add(this.panelInterfaz, BorderLayout.SOUTH);
        
        this.setSize(300, 410);
        this.setVisible(true);
        
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
    }

    public ControladorJV getControlador() {
        
        return this.controlador;
        
    }

}
