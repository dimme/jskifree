package game;

import javax.swing.ImageIcon;
import javax.swing.JFrame;

public class Game extends JFrame {

	private static final long serialVersionUID = 1L;

	public Game() {

        add(new Board());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(new ImageIcon("resources/icons/new.ico").getImage());
        setSize(1024, 768);
        setLocationRelativeTo(null);
        setTitle("JSkiFree");
        setResizable(true);
        setVisible(true);
    }

    public static void main(String[] args) {
        new Game();
    }
}