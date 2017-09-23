package vistas;

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

public class PanelInterfazJV extends JPanel {

    private VentanaJV ventana;
    private JButton btnGenerar;
    private JButton btnAvanzar;
    private JButton btnDetener;

    /**
     *
     * @param ventana
     */
    public PanelInterfazJV(VentanaJV ventana) {

        this.ventana = ventana;
        this.setLayout(new FlowLayout());
        
        this.btnGenerar = new JButton("Generar Celulas");
        this.btnAvanzar = new JButton("Avanzar");
        this.btnDetener = new JButton("Detener");
        
        this.btnGenerar.addActionListener( (Action) -> this.generarCelulasVivas() );
        this.btnAvanzar.addActionListener( (Action) -> this.avanzar() );
        this.btnDetener.addActionListener( (Action) -> this.detener() );
        
        this.add(this.btnGenerar);
        this.add(this.btnAvanzar);
        this.add(this.btnDetener);
        
        this.ventana.add(this);

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

}
