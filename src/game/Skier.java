package game;

import java.awt.Image;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;

public class Skier {

    private Image up1 = new ImageIcon("resources/sprites/skier7.png").getImage();
    private Image up2 = new ImageIcon("resources/sprites/skier8.png").getImage();
    private Image down = new ImageIcon("resources/sprites/skier1.png").getImage();
    private Image left = new ImageIcon("resources/sprites/skier6.png").getImage();
    private Image right = new ImageIcon("resources/sprites/skier7.png").getImage();
    
    byte swifter = 0;
    
    private Image current = left;

    private int dx;
    private int dy;
    private int x;
    private int y;

    public Skier() {
        x = 40;
        y = 60;
    }


    public void move() {
        x += dx;
        y += dy;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Image getImage() {
        return current;
    }

    public void keyPressed(KeyEvent e) {

        int key = e.getKeyCode();

        if (key == KeyEvent.VK_LEFT) {
            dx = -1;
            current = left;
        }

        if (key == KeyEvent.VK_RIGHT) {
            dx = 1;
            current = right;
        }

        if (key == KeyEvent.VK_UP) {
            dy = -1;
            swifter = (byte) ((swifter+1) % 2);
            if (swifter == 0)
            	current = up1;
            else
            	current = up2;
        }

        if (key == KeyEvent.VK_DOWN) {
            dy = 1;
            current = down;
        }
    }

    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyEvent.VK_LEFT) {
            dx = 0;
        }

        if (key == KeyEvent.VK_RIGHT) {
            dx = 0;
        }

        if (key == KeyEvent.VK_UP) {
            dy = 0;
        }

        if (key == KeyEvent.VK_DOWN) {
            dy = 0;
        }
    }
}