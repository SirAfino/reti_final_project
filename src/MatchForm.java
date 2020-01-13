import javax.swing.*;
import javax.swing.plaf.synth.SynthMenuBarUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class MatchForm {
    private JTextField translationTextField;
    private JButton sendButton;
    private JLabel wordLabel;
    private JPanel mainPanel;
    private JLabel infoLabel;
    private JButton surrendButton;
    private JLabel timeLabel;
    private JFrame frame;
    private ByteBuffer byteBuffer;

    private long matchTime;
    public boolean matchResultsSent = false;
    private long startTime;

    private void setupWindow(String title, Point position) {
        frame = new JFrame(title);
        frame.setContentPane(mainPanel);
        frame.getRootPane().setDefaultButton(sendButton);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setLocation(position);
        frame.setVisible(true);
    }

    public synchronized void matchResultsHandler(String other) {
        if (!matchResultsSent) {
            matchResultsSent = true;
            SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "match_results");
            String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);
            if (response.startsWith("match_results_r ok")) {
                String[] params = response.split(" ");
                String message = "Risposte corrette: " + params[2]
                        + "\nRisposte errate: " + params[3]
                        + "\nNon risposte: " + params[4]
                        + "\n\nI tuoi punti: " + params[5]
                        + "\nI punti di " + other + ": " + params[6];
                if (!params[7].equals("0")) {
                    message += "\nHai vinto e guadagnato " + params[7] + " punti bonus!";
                }
                JOptionPane.showMessageDialog(frame, message);
                closeWindow();
            }
        }
    }

    private void updateTime() {
        Long remainingTime = 1000L;
        while (remainingTime != 0) {
            remainingTime = (matchTime - (System.currentTimeMillis() - startTime)) / 1000;
            timeLabel.setText(remainingTime.toString());
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }

    private void sendHandler(String other) {
        String translation = translationTextField.getText();
        translationTextField.setText("");
        if (translation != null) {
            SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "translation " + translation);
            String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);
            if (response.startsWith("translation_r ok")) ;
            {
                String params[] = response.split(" ");
                if (params.length == 3) {
                    wordLabel.setText(params[2].toUpperCase());
                } else {
                    infoLabel.setText("In attesa di " + other);
                    sendButton.setEnabled(false);
                    surrendButton.setEnabled(false);
                    wordLabel.setText("");
                    frame.repaint(0);
                    new Thread(() -> matchResultsHandler(other)).start();
                }
            }
        }
    }

    private void closeWindow() {
        Client.profileForm.frame.setVisible(true);
        Client.profileForm.refreshHandler();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }

    private void surrendHandler() {
        int n = JOptionPane.showOptionDialog(frame,
                "Sei sicuro di volerti arrendere?",
                "Match",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Arrenditi", "Continua a giocare"},
                "Continua a giocare");
        if (n == 0) {
            SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "surrend");
            String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);
            if (response.startsWith("surrend_r ok")) {
                closeWindow();
            } else {
                JOptionPane.showMessageDialog(frame, "Errore resa\nCodice errore: " + response.split(" ")[2]);
            }
        }
    }

    public MatchForm(String other, Point position) {
        byteBuffer = ByteBuffer.allocate(1024);
        setupWindow("Match", position);
        SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "match_info");
        String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);
        if (response.startsWith("match_info_r ok")) {
            String[] params = response.split(" ");
            matchTime = Integer.parseInt(params[3]);
            wordLabel.setText(params[4].toUpperCase());
            sendButton.setEnabled(true);
            surrendButton.setEnabled(true);
            Timer timer = new Timer();
            startTime = System.currentTimeMillis();
            new Thread(() -> updateTime()).start();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    matchResultsHandler(other);
                }
            }, matchTime);
        } else {
            JOptionPane.showMessageDialog(frame, "Match error!\n" + response);
            closeWindow();
        }

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendHandler(other);
            }
        });
        surrendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                surrendHandler();
            }
        });
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(6, 1, new Insets(20, 20, 20, 20), -1, -1));
        mainPanel.setBackground(new Color(-3790808));
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, -1, 28, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setForeground(new Color(-1));
        label1.setText("Word Quizzle");
        mainPanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 20, 0), -1, -1));
        panel1.setOpaque(false);
        mainPanel.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        infoLabel = new JLabel();
        Font infoLabelFont = this.$$$getFont$$$(null, -1, 18, infoLabel.getFont());
        if (infoLabelFont != null) infoLabel.setFont(infoLabelFont);
        infoLabel.setForeground(new Color(-1));
        infoLabel.setText("La parola da tradurre è");
        panel1.add(infoLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        wordLabel = new JLabel();
        Font wordLabelFont = this.$$$getFont$$$(null, -1, 24, wordLabel.getFont());
        if (wordLabelFont != null) wordLabel.setFont(wordLabelFont);
        wordLabel.setForeground(new Color(-1));
        wordLabel.setText("____");
        panel1.add(wordLabel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.setOpaque(false);
        mainPanel.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        translationTextField = new JTextField();
        panel2.add(translationTextField, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        sendButton = new JButton();
        sendButton.setDefaultCapable(true);
        sendButton.setEnabled(false);
        sendButton.setText("Invia");
        panel2.add(sendButton, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(20, 0, 0, 0), -1, -1));
        panel3.setOpaque(false);
        mainPanel.add(panel3, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        surrendButton = new JButton();
        surrendButton.setDefaultCapable(true);
        surrendButton.setEnabled(false);
        surrendButton.setText("Arrenditi");
        panel3.add(surrendButton, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel4.setOpaque(false);
        mainPanel.add(panel4, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        timeLabel = new JLabel();
        Font timeLabelFont = this.$$$getFont$$$(null, -1, 23, timeLabel.getFont());
        if (timeLabelFont != null) timeLabel.setFont(timeLabelFont);
        timeLabel.setForeground(new Color(-1));
        timeLabel.setText("_");
        panel4.add(timeLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        return new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
