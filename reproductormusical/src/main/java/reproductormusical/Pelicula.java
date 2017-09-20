package reproductormusical;

import java.io.File;
import java.time.Duration;

public class Pelicula extends Multimedia {

    /**
     * Constructor de la clase pelicula
     * @param archivo Parametro no utilizando (pesando para implementar el uso
     * de archivos en futuro)
     * @param actorPrincipal
     * @param actrizPrincipal
     * @param titulo
     * @param autor
     * @param duracion
     * @param formato
     */
    public Pelicula(File archivo, String actorPrincipal, String actrizPrincipal, String titulo, String autor, Duration duracion, FormatoMultimedia formato) {

        super(titulo, autor, duracion, formato);

        if (actorPrincipal == null && actrizPrincipal == null) {

            throw new NullPointerException("Se esperaba actor o actriz");

        }

        this.actorPrincipal = actorPrincipal;
        this.actrizPrincipal = actrizPrincipal;

    }

    private String actorPrincipal;
    private String actrizPrincipal;

    /**
     * 
     * @return El nombre del actor principal
     */
    public String getActorPrincipal() {
        return this.actorPrincipal;
    }

    /**
     * 
     * @return El nombre de la actriz principal
     */
    public String getActrizPrincipal() {
        return this.actrizPrincipal;
    }

    /**
     * Método toString (muestra informacion de clase padre y actriz/actor
     *
     * @return Información de clase padre, actriz y a actor
     */
    public String toString() {
        if (this.actorPrincipal == null) {
            return super.toString() + "\nActriz: " + this.actrizPrincipal;
        } else if (this.actrizPrincipal == null) {
            return super.toString() + "\nActor: " + this.actorPrincipal;
        }
        return super.toString() + "\nActriz: " + this.actrizPrincipal + "\nActor: " + this.actorPrincipal;
    }

    /**
     * Metodo no implementado, pensado para reproducir la pelicula en el futuro.
     */
    @Override
    public void reproducir() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Metodo no implementado, pensado para devolver el archivo de la pelicula
     * en el futuro.
     *
     * @return El archivo de la pelicula
     */
    @Override
    public File getArchivo() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
