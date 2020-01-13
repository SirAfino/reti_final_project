import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class ProfileForm {
    private JPanel mainPanel;
    private JLabel usernameLabel;
    private JButton logoutButton;
    private JList leaderboardList;
    private JLabel scoreLabel;
    private JButton addFriendButton;
    private JButton matchButton;
    private JButton refreshButton;

    public static JFrame frame;
    private static JDialog waitDialog;
    private static boolean closing;
    private static ByteBuffer byteBuffer;

    private void checkMatchRequest() {
        while (true) {
            DatagramPacket receivePacket = SocketInteraction.UDPRead(Client.datagramSocket);
            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
            if (message.startsWith("match_request")) {
                showMatchRequest(message, receivePacket.getPort());
            }
        }
    }

    public void showMatchRequest(String request, int remotePort) {
        String[] params = request.split(" ");
        int n = JOptionPane.showOptionDialog(frame,
                params[1] + " ti ha sfidato!",
                "Match",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Accetta", "Rifiuta"},
                "Rifiuta");
        String message = "match_request_r " + params[2] + " rejected";
        boolean accepted = false;
        if (n == 0) {
            message = "match_request_r " + params[2] + " accepted";
            accepted = true;
        }
        SocketInteraction.UDPWrite(Client.datagramSocket, Client.serverAddress, remotePort, message);
        if (accepted) {
            frame.setVisible(false);
            Client.matchForm = new MatchForm(params[1], frame.getLocation());
        }
    }

    private synchronized void logoutHandler() {
        if (!closing) {
            closing = true;
            SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "logout");
            String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);

            String[] params = response.split(" ");
            boolean okFlag = (response.startsWith("logout_r ok"));
            if (okFlag) {
                Client.username = null;
                Client.loginForm = new LoginForm();
                closing = true;
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            } else {
                JOptionPane.showMessageDialog(null, "Errore logout!\nCodice errore: " + params[2]);
            }
        }
    }

    private void setupWindow(String title, Point position) {
        usernameLabel.setText(Client.username);

        frame = new JFrame(title);
        frame.setContentPane(mainPanel);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setLocation(position);
        frame.setVisible(true);
    }

    private void loadLeaderboard() {
        SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "leaderboard");
        String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);

        leaderboardList.setListData(new String[]{"Caricamento classifica fallito"});

        if (response.startsWith("leaderboard_r")) {
            String result = response.substring(14, 16);
            if (result.equals("ok")) {
                String leaderboardJSON = response.substring(response.indexOf("\n") + 1);
                JSONParser parser = new JSONParser();
                try {
                    Leaderboard leaderboard = new Leaderboard((JSONObject) parser.parse(leaderboardJSON));
                    LeaderboardUser[] users = leaderboard.getUsers();
                    leaderboardList.setListData(users);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        leaderboardList.setSelectedIndex(0);
    }

    private void loadScore() {
        SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "score");
        String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);

        scoreLabel.setText("Punteggio 0");
        String[] params = response.split(" ");
        if (params[0].equals("score_r")) {
            if (params[1].equals("ok")) {
                scoreLabel.setText("Punteggio " + params[2]);
            }
        }
    }

    private void addFriendHandler() {
        String dialogMessage = "Inserisci lo username del tuo amico";
        String dialogTitle = "Aggiungi un amico";
        String friendUsername = (String) JOptionPane.showInputDialog(frame, dialogMessage, dialogTitle, JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (friendUsername != null && !friendUsername.equals("")) {
            SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "add_friend " + friendUsername);
            String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);

            String message = "Errore aggiunta amico!";
            String[] params = response.split(" ");
            if (params[0].equals("add_friend_r")) {
                if (params[1].equals("ok")) {
                    loadLeaderboard();
                    message = "Amico aggiunto con successo!";
                } else {
                    switch (params[2]) {
                        case "WRONG_USERNAME":
                            message = "Questo utente non esiste!";
                            break;
                        case "ALREADY_FRIEND":
                            message = "Sei già amico di questo utente!";
                            break;
                    }
                }
            }
            JOptionPane.showMessageDialog(frame, message);
        }
    }

    private void matchRequestHandler(String other) {
        SocketInteraction.TCPWrite(Client.socketChannel, byteBuffer, "match " + other);
        String response = SocketInteraction.TCPRead(Client.socketChannel, byteBuffer);
        boolean wait = true;
        while (wait) {
            if (waitDialog != null && waitDialog.isVisible()) {
                wait = false;
            } else {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }
            }
        }
        waitDialog.dispose();
        matchResponseHandler(response, other);
    }

    private void matchHandler() {
        String other = ((LeaderboardUser) leaderboardList.getSelectedValue()).getUsername();
        String title = "Match";
        String content = "Richiesta inviata a " + other + "\nIn attesa di risposta...";
        new Thread(() -> matchRequestHandler(other)).start();
        final JOptionPane optionPane = new JOptionPane(content, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        waitDialog = new JDialog();
        waitDialog.setTitle(title);
        waitDialog.setModal(true);
        waitDialog.setContentPane(optionPane);
        waitDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        waitDialog.setLocationRelativeTo(frame);
        waitDialog.pack();
        waitDialog.setVisible(true);
    }

    public void matchResponseHandler(String response, String other) {
        boolean okFlag = false;
        String[] params = response.split(" ");
        String message = "Richiesta fallita!";
        if (params[0].equals("match_r")) {

            if (params[1].equals("ok")) {
                if (params[2].equals("accepted")) {
                    okFlag = true;
                } else {
                    message = other + " ha rifiutato la sfida!";
                }
            } else {
                switch (params[2]) {
                    case "USER_OFFLINE":
                        message = "L'utente è offline!";
                        break;
                    case "USER_BUSY":
                        message = "L'utente è occupato!";
                        break;
                }
            }
        }
        if (okFlag) {
            frame.setVisible(false);
            Client.matchForm = new MatchForm(other, frame.getLocation());
        } else {
            JOptionPane.showMessageDialog(frame, message);
        }
    }

    public void refreshHandler() {
        loadLeaderboard();
        loadScore();
    }

    public ProfileForm(Point position) {
        byteBuffer = ByteBuffer.allocate(1024);
        closing = false;
        setupWindow("Word Quizzle", position);
        refreshHandler();

        new Thread(() -> checkMatchRequest()).start();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                logoutHandler();
            }
        });
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logoutHandler();
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshHandler();
            }
        });
        addFriendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addFriendHandler();
            }
        });
        matchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                matchHandler();
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
        mainPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(25, 25, 25, 25), -1, -1));
        mainPanel.setBackground(new Color(-3790808));
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, -1, 28, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setForeground(new Color(-1));
        label1.setText("Word Quizzle");
        mainPanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.setOpaque(false);
        mainPanel.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(4, 1, new Insets(10, 10, 10, 10), -1, -1));
        panel2.setOpaque(false);
        panel1.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, 1, 1, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), "Profilo", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, this.$$$getFont$$$(null, -1, -1, panel2.getFont()), new Color(-1)));
        addFriendButton = new JButton();
        addFriendButton.setMargin(new Insets(0, 0, 0, 0));
        addFriendButton.setText("Aggiungi un amico");
        panel2.add(addFriendButton, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        logoutButton = new JButton();
        logoutButton.setText("Logout");
        panel2.add(logoutButton, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        refreshButton = new JButton();
        refreshButton.setInheritsPopupMenu(false);
        refreshButton.setLabel("Aggiorna");
        refreshButton.setText("Aggiorna");
        panel2.add(refreshButton, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        panel3.setOpaque(false);
        panel2.add(panel3, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        usernameLabel = new JLabel();
        Font usernameLabelFont = this.$$$getFont$$$(null, -1, 22, usernameLabel.getFont());
        if (usernameLabelFont != null) usernameLabel.setFont(usernameLabelFont);
        usernameLabel.setForeground(new Color(-1));
        usernameLabel.setText("Gab");
        panel3.add(usernameLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scoreLabel = new JLabel();
        Font scoreLabelFont = this.$$$getFont$$$(null, -1, 20, scoreLabel.getFont());
        if (scoreLabelFont != null) scoreLabel.setFont(scoreLabelFont);
        scoreLabel.setForeground(new Color(-1));
        scoreLabel.setText("Punteggio 400");
        panel3.add(scoreLabel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel4.setOpaque(false);
        panel1.add(panel4, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel5.setOpaque(false);
        panel4.add(panel5, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), "Classifica", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, this.$$$getFont$$$(null, -1, -1, panel5.getFont()), new Color(-1)));
        leaderboardList = new JList();
        leaderboardList.setEnabled(true);
        leaderboardList.setOpaque(false);
        panel5.add(leaderboardList, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        matchButton = new JButton();
        matchButton.setText("Sfida");
        panel5.add(matchButton, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
