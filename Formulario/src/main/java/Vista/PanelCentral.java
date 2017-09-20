/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vista;

import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 *
 * @author matia
 */
public class PanelCentral extends JPanel {

    public PanelCentral() {
        
        this.iniciarComponentes();
        
    }
    
    private void iniciarComponentes() {
        
        this.setLayout(new GridLayout(12, 2, 0, 5));
        
        // Nombre
        this.add(new JLabel("Nombre (*)"));
        this.add(new JTextField());
        
        // Apellido
        this.add(new JLabel("Apellido (*)"));
        this.add(new JTextField());
        
        // Correo
        this.add(new JLabel("Correo"));
        this.add(new JTextField());
        
        // Direccion
        this.add(new JLabel("Dirección"));
        this.add(new JTextField());
        
        // Ciudad
        this.add(new JLabel("Ciudad"));
        this.add(new JTextField());
        
        // Estado
        this.add(new JLabel("Estado"));
        this.add(new JTextField());
        
        // ZIP
        this.add(new JLabel("ZIP"));
        this.add(new JTextField());
        
        // Estado
        this.add(new JLabel("Estado"));
        this.add(new JTextField());
        
        // Pais
        this.add(new JLabel("Pais"));
        this.add(new JComboBox());
        
        // Organizacion
        this.add(new JLabel("Organizacion"));
        this.add(new JTextField());
        
        // Kind
        this.add(new JLabel("Kind"));
        this.add(new JComboBox());
        
        // Perfil
        this.add(new JLabel("Profile"));
        this.add(new JComboBox());
        
    }
    
}
