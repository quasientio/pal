package com.ittera.cometa.apps;

import javax.swing.*;

public class SwingApp {
    private JPanel panel1 = new JPanel();
    private JButton exitButton = new JButton("Hello");
    private static JFrame frame;

    public SwingApp() {
        panel1 = new JPanel();
        panel1.add(new JLabel("Just a label"));
        panel1.add(exitButton);
    }

    public static void main(String[] args) {
        frame = new JFrame("Swing App");
        final SwingApp app = new SwingApp();

        frame.setSize(200,100);

        frame.setContentPane(app.panel1);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}
