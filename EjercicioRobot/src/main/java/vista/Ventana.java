package vista;

import java.awt.GridLayout;
import javax.swing.*;

public class Ventana extends JFrame {

    private PanelMatriz panelMatriz;
    private PanelBotones panelBotones;

    public Ventana() {
        
        this.iniciarComponentes();
        
    }

    private void iniciarComponentes() {
        
        this.panelMatriz = new PanelMatriz();
        this.panelBotones = new PanelBotones(this);
        
        this.setLayout(new GridLayout(1, 2));
        this.add(this.panelMatriz);
        this.add(this.panelBotones);
        
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(500, 250);
        this.setVisible(true);
        
    }
    
    public PanelMatriz getPanelMatriz() {
        
        return this.panelMatriz;
        
    }

}
