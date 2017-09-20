package Vista;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.*;

public class Ventana extends JFrame {
    
    private MenuVentana menuVentana;
    //private PanelSuperior panelSuperior;
    private PanelCentral panelCentral;
    private PanelInferior panelInferior;

    public Ventana() {

        this.iniciarComponentes();

    }

    private void iniciarComponentes() {

        try {

            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        } catch (Exception e) {

        }

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Instanciar arreglo para paneles
        this.menuVentana = new MenuVentana();
        //this.panelSuperior = new PanelSuperior();
        this.panelCentral = new PanelCentral();
        this.panelInferior = new PanelInferior();
        
        this.add(this.menuVentana, BorderLayout.NORTH);
        //this.add(this.panelSuperior, BorderLayout.NORTH);
        this.add(this.panelCentral, BorderLayout.CENTER);
        this.add(this.panelInferior, BorderLayout.SOUTH);
        
        // Mostrar ventana
        this.setSize(500, 500);
        this.setVisible(true);
        
    }

}
