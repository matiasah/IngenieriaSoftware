package reproductormusical;

import java.io.File;
import java.time.Duration;
import javazoom.jlgui.basicplayer.BasicPlayer;
import javazoom.jlgui.basicplayer.BasicPlayerException;

public class Disco extends Multimedia {

    public static BasicPlayer reproductor = new BasicPlayer();

    private final File archivo;
    private final GeneroDisco genero;

    /**
     * Constructor de la clase disco
     *
     * @param archivo
     * @param genero
     * @param titulo
     * @param autor
     * @param duracion
     * @param formato
     */
    public Disco(File archivo, GeneroDisco genero, String titulo, String autor, Duration duracion, FormatoMultimedia formato) {

        super(titulo, autor, duracion, formato);

        this.archivo = archivo;
        this.genero = genero;

    }

    /**
     *
     * @return El genero del disco
     */
    public GeneroDisco getGenero() {
        return this.genero;
    }

    /**
     *
     * @return El archivo del disco
     */
    @Override
    public File getArchivo() {
        return this.archivo;
    }

    /**
     * Método toString
     *
     * @return Información de la clase padre, incluyendo el genero del dísco
     */
    @Override
    public String toString() {

        return super.toString() + "\nGenero: " + ( genero.toString() );
        
    }

    /**
     * Reproduce el disco
     */
    @Override
    public void reproducir() {

        try {
            
            reproductor.stop();
            reproductor.open(this.archivo);
            reproductor.play();
            
        } catch (BasicPlayerException e) {

        }

    }

}
