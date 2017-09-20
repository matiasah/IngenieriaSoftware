package vista;

import java.awt.FlowLayout;
import javax.swing.*;

public class PanelBotones extends JPanel {

    private final Ventana ventana;
    private JButton botonGenerarBloques;
    private JButton botonCargarEnergia;
    private JButton botonPosicionarRobot1;
    private JButton botonPosicionarRobot2;

    public PanelBotones(Ventana ventana) {

        this.ventana = ventana;
        this.iniciarComponentes();

    }

    private void iniciarComponentes() {

        this.setLayout(new FlowLayout());

        this.botonGenerarBloques = new JButton("Generar Bloques");
        this.botonCargarEnergia = new JButton("Cargar Energia");
        this.botonPosicionarRobot1 = new JButton("Posicionar Robot 1");
        this.botonPosicionarRobot2 = new JButton("Posicionar Robot 2");
        
        PanelMatriz panelMatriz = this.ventana.getPanelMatriz();

        this.botonGenerarBloques.addActionListener( (ActionListener) -> { panelMatriz.generarBloques(); } );
        this.botonCargarEnergia.addActionListener( (ActionListener) -> { panelMatriz.cargarEnergia(); } );
        this.botonPosicionarRobot1.addActionListener( (ActionListener) -> { panelMatriz.posicionarRobot1(); } );
        this.botonPosicionarRobot2.addActionListener( (ActionListener) -> { panelMatriz.posicionarRobot2(); } );

        this.add(this.botonGenerarBloques);
        this.add(this.botonCargarEnergia);
        this.add(this.botonPosicionarRobot1);
        this.add(this.botonPosicionarRobot2);

    }

}
