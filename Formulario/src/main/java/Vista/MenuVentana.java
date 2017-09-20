/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vista;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 *
 * @author matia
 */
public class MenuVentana extends JMenuBar {
    
    private JMenu menuArchivo;
    private JMenu menuEditar;
    
    private JMenuItem menuSalir;
    private JMenuItem menuCortar;
    
    public MenuVentana() {
        
        this.iniciarComponentes();
        
    }
    
    private void iniciarComponentes() {
        
        this.menuArchivo = new JMenu("Archivo");
        this.menuEditar = new JMenu("Editar");
        
        this.menuSalir = new JMenuItem("Salir");
        this.menuCortar = new JMenuItem("Cortar");
        
        this.menuArchivo.add(this.menuSalir);
        this.menuEditar.add(this.menuCortar);
        
        this.add(this.menuArchivo);
        this.add(this.menuEditar);
        
    }
    
}
