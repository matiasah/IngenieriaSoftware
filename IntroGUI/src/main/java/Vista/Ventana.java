package Vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
//import java.awt.FlowLayout;
import javax.swing.*;

public class Ventana extends JFrame {

    public Ventana() throws Exception {

        this.inicializarComponentes();

    }

    private void inicializarComponentes() throws Exception {

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        //FlowLayout layout = new FlowLayout();
        //this.setLayout(layout);
        
        /**
        JButton boton = new JButton("Norte");
        this.add(boton, BorderLayout.NORTH);

        JButton boton2 = new JButton("Sur");
        this.add(boton2, BorderLayout.SOUTH);

        JButton boton3 = new JButton("Oeste");
        this.add(boton3, BorderLayout.WEST);

        JButton boton4 = new JButton("Este");
        this.add(boton4, BorderLayout.EAST);
        
        JButton boton5 = new JButton("Centro");
        this.add(boton5, BorderLayout.CENTER);
        **/
        
        GridLayout distribucion = new GridLayout(5, 6, 10, 15);
        this.setLayout(distribucion);
        
        for (int x = 0; x < 5; x++) {
            
            for (int y = 0; y < 6; y++) {
                
                this.add(new JButton("("+x+","+y+")"));
                
            }
            
        }

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.getContentPane().setBackground(Color.PINK);
        this.setSize(500, 500);
        this.setVisible(true);

    }

}
