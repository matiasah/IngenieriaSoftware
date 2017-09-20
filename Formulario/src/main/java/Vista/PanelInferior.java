/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vista;

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 *
 * @author matia
 */
public class PanelInferior extends JPanel {

    public PanelInferior() {
        
        this.iniciarComponentes();
        
    }
    
    private void iniciarComponentes() {
        
        this.add(new JButton("Resetear"), BorderLayout.WEST);
        this.add(new JButton("Registrar"), BorderLayout.EAST);
        
    }
    
}
