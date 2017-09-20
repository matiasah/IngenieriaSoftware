package app;

import bridge.ControlRemoto;
import bridge.TV;

public class Demo {

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        
        TV tv = new TV();
        
        ControlRemoto cl = new ControlRemoto(tv);
        
        
        
    }

}
