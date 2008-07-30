//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.skins.simple.components;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;

import jd.gui.skins.simple.LocationListener;
import jd.gui.skins.simple.SimpleGUI;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

/**
 * Diese Klasse ist wie die Optionspane mit textfeld nur mit textarea
 * 
 * @author JD-Team
 */
public class TextAreaDialog extends JDialog implements ActionListener {
    /**
     * 
     */
    private static final long serialVersionUID = -655039113948925165L;

    public static String showDialog(JFrame frame, String title, String question, String def) {
        TextAreaDialog tda = new TextAreaDialog(frame, title, question, def);
        return tda.getText();

    }

    private JButton btnCancel;

    private JButton btnOk;

    /**
     * 
     */

    protected Insets insets = new Insets(0, 0, 0, 0);

    protected Logger logger = JDUtilities.getLogger();

    // private JLabel lblText;

    private JScrollPane scrollPane;

    private String text = null;

    private JTextPane textArea;

    private TextAreaDialog(JFrame frame, String title, String question, String def) {
        super(frame);

        setModal(false);
        setLayout(new BorderLayout());
        setName(title);
        btnCancel = new JButton(JDLocale.L("gui.btn_cancel", "Cancel"));
        btnCancel.addActionListener(this);
        btnOk = new JButton(JDLocale.L("gui.btn_ok", "OK"));
        btnOk.addActionListener(this);
        setTitle(title);
        textArea = new JTextPane();
        scrollPane = new JScrollPane(textArea);
        // Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        setResizable(true);
        // int width = screenSize.width;
        // int height = screenSize.height;

        // this.setPreferredSize(new
        // Dimension((int)(width*0.9),(int)(height*0.9)));

        textArea.setEditable(true);
        textArea.requestFocusInWindow();
        if (question != null) {
            this.add(new JLabel(question), BorderLayout.NORTH);
        }
        if (def != null) {
            textArea.setText(def);
        }
        this.add(scrollPane, BorderLayout.CENTER);
        JPanel p = new JPanel();
        p.add(btnOk);
        p.add(btnCancel);
        // this.setVisible(true);
        pack();

        getRootPane().setDefaultButton(btnOk);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.add(p, BorderLayout.SOUTH);
        LocationListener list = new LocationListener();
        addComponentListener(list);
        addWindowListener(list);

        setVisible(true);
        SimpleGUI.restoreWindow(null, null, this);
        setVisible(false);
        setModal(true);
        setVisible(true);

    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnOk) {
            text = textArea.getText();
            dispose();
        } else {
            dispose();
        }
    }

    private String getText() {
        return text;
    }

}
