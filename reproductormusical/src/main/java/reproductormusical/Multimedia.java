package reproductormusical;

import java.io.File;
import java.time.Duration;

public abstract class Multimedia {
    
    private String titulo;
    private String autor;
    private Duration duracion;
    private FormatoMultimedia formato;

    /**
     * Constructor de la clase multimedia
     * @param titulo
     * @param autor
     * @param formato
     */
    public Multimedia(String titulo, String autor, Duration duracion, FormatoMultimedia formato) {
        this.titulo = titulo;
        this.autor = autor;
        this.duracion = duracion;
        this.formato = formato;
    }

    /**
     * 
     * @return El titulo del objeto multimedia
     */
    public String getTitulo() {
        return this.titulo;
    }

    /**
     * 
     * @return El autor del objeto multimedia
     */
    public String getAutor() {
        return this.autor;
    }
    
    /**
     * 
     * @return Duracion del objeto multimedia
     */
    public Duration getDuracion() {
        return this.duracion;
    }

    /**
     * 
     * @return El formato del objeto multimedia
     */
    public FormatoMultimedia getFormato() {
        return this.formato;
    }

    /**
     * Método toString
     * @return Informacion relacionada al objeto multimedia: Título, autor, formato
     */
    public String toString() {
        return "Titulo: " + this.titulo + "\nAutor: " + this.autor + "\nFormato: " + this.formato;
    }

    /**
     *
     * @param obj
     */
    public boolean equals(Object obj) {
        if ( obj instanceof Multimedia ) {
            Multimedia objMult = (Multimedia) obj;
            return objMult.getAutor().equals(this.autor) && objMult.getTitulo().equals(this.titulo);
        }
        return false;
    }

    /**
     * Reproduce el objeto multimedia
     */
    public abstract void reproducir();
    
    /**
     * 
     * @return El archivo en el que se guarda el objeto multimedia
     */
    public abstract File getArchivo();

}
