/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

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
