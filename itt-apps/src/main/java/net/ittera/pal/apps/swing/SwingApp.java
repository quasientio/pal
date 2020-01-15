package net.ittera.pal.apps.swing;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SwingApp {
  private JPanel panel1;

  public SwingApp() {
    panel1 = new JPanel();
    panel1.add(new JLabel("Just a label"));
    JButton exitButton = new JButton("Hello");
    panel1.add(exitButton);
  }

  public static void main(String[] args) {
    JFrame frame = new JFrame("Swing App");
    final SwingApp app = new SwingApp();

    frame.setSize(200, 100);

    frame.setContentPane(app.panel1);
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }
}
