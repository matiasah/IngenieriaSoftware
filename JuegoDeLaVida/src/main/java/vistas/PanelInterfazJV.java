package vistas;

import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

public class PanelInterfazJV extends JPanel {

    private VentanaJV ventana;
    private JButton btnGenerar;
    private JButton btnAvanzar;
    private JButton btnDetener;
    private JButton btnLimpiar;

    /**
     *
     * @param ventana
     */
    public PanelInterfazJV(VentanaJV ventana) {

        this.ventana = ventana;
        this.setLayout(new GridLayout(4, 1));
        
        this.btnGenerar = new JButton("Generar Celulas");
        this.btnAvanzar = new JButton("Avanzar");
        this.btnDetener = new JButton("Detener");
        this.btnLimpiar = new JButton("Limpiar");
        
        this.btnGenerar.addActionListener( (Action) -> this.generarCelulasVivas() );
        this.btnAvanzar.addActionListener( (Action) -> this.avanzar() );
        this.btnDetener.addActionListener( (Action) -> this.detener() );
        this.btnLimpiar.addActionListener( (Action) -> this.limpiar() );
        
        this.add(this.btnGenerar);
        this.add(this.btnAvanzar);
        this.add(this.btnDetener);
        this.add(this.btnLimpiar);

    }

    private void generarCelulasVivas() {
        
        this.ventana.getControlador().generar();
        
    }

    private void avanzar() {
        
        this.ventana.getControlador().avanzar();
        
    }

    private void detener() {
        
        this.ventana.getControlador().detener();
        
    }
    
    private void limpiar() {
        
        this.ventana.getControlador().limpiar();
        
    }

}
