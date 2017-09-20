/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vista;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author matia
 */
public class PanelSuperior extends JPanel {
    
    public PanelSuperior() {
        
        this.iniciarComponentes();
        
    }
    
    private void iniciarComponentes() {
        
        this.add(new JLabel("Formulario de Registro"), BorderLayout.CENTER);
        
    }
    
}
