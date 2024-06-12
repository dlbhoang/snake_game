package src;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;
import javax.swing.*;

public class SnakeGame extends JPanel implements ActionListener, KeyListener {

    private class Tile {
        int x;
        int y;

        Tile(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private class Obstacle {
        int x;
        int y;

        Obstacle(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    int boardWidth;
    int boardHeight;
    int tileSize = 25;

    // Snake
    Tile snakeHead;
    ArrayList<Tile> snakeBody;

    // Food
    Tile food;
    Random random;

    // Obstacles
    ArrayList<Obstacle> obstacles;

    // Game logic
    int velocityX;
    int velocityY;
    Timer gameLoop;

    boolean gameOver = false;

    Connection conn;

    SnakeGame(int boardWidth, int boardHeight) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        setPreferredSize(new Dimension(this.boardWidth, this.boardHeight));
        setBackground(Color.black);
        addKeyListener(this);
        setFocusable(true);

        snakeHead = new Tile(5, 5);
        snakeBody = new ArrayList<Tile>();

        food = new Tile(10, 10);
        random = new Random();

        // Initialize obstacles list before placing food
        obstacles = new ArrayList<>();
        placeFood();
        placeObstacles();

        velocityX = 1;
        velocityY = 0;

        // Game timer
        gameLoop = new Timer(100, this);
        gameLoop.start();

        // Initialize the database
        initDatabase();
    }

    private void initDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:highscores.db");
            Statement stmt = conn.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS highscores " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " score INTEGER NOT NULL)";
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveHighScore(int score) {
        try {
            String sql = "INSERT INTO highscores (score) VALUES (?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, score);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Integer> fetchHighScores() {
        ArrayList<Integer> highScores = new ArrayList<>();
        try {
            String sql = "SELECT score FROM highscores ORDER BY score DESC LIMIT 5";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                highScores.add(rs.getInt("score"));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return highScores;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g) {
        // Grid Lines
        for (int i = 0; i < boardWidth / tileSize; i++) {
            g.drawLine(i * tileSize, 0, i * tileSize, boardHeight);
            g.drawLine(0, i * tileSize, boardWidth, i * tileSize);
        }

        // Food
        g.setColor(Color.red);
        g.fill3DRect(food.x * tileSize, food.y * tileSize, tileSize, tileSize, true);

        // Snake Head
        g.setColor(Color.green);
        g.fill3DRect(snakeHead.x * tileSize, snakeHead.y * tileSize, tileSize, tileSize, true);

        // Snake Body
        for (Tile snakePart : snakeBody) {
            g.fill3DRect(snakePart.x * tileSize, snakePart.y * tileSize, tileSize, tileSize, true);
        }

        // Obstacles
        g.setColor(Color.gray);
        for (Obstacle obs : obstacles) {
            g.fill3DRect(obs.x * tileSize, obs.y * tileSize, tileSize, tileSize, true);
        }

        // Score
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        if (gameOver) {
            g.setColor(Color.red);
            g.drawString("Game Over: " + snakeBody.size(), tileSize - 16, tileSize);
            displayHighScores(g);
        } else {
            g.setColor(Color.white);
            g.drawString("Score: " + snakeBody.size(), tileSize - 16, tileSize);
        }
    }

    private void displayHighScores(Graphics g) {
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(Color.white);
        g.drawString("High Scores:", tileSize - 16, tileSize * 2);

        ArrayList<Integer> highScores = fetchHighScores();
        for (int i = 0; i < highScores.size(); i++) {
            g.drawString((i + 1) + ": " + highScores.get(i), tileSize - 16, tileSize * (3 + i));
        }
    }

    public void placeFood() {
        boolean validPosition = false;
        while (!validPosition) {
            int x = random.nextInt(boardWidth / tileSize);
            int y = random.nextInt(boardHeight / tileSize);
            food = new Tile(x, y);

            validPosition = true;
            // Check collision with snake body
            for (Tile snakePart : snakeBody) {
                if (collision(food, snakePart)) {
                    validPosition = false;
                    break;
                }
            }

            // Check collision with obstacles
            for (Obstacle obs : obstacles) {
                if (collision(food, obs)) {
                    validPosition = false;
                    break;
                }
            }
        }
    }

    public void placeObstacles() {
        obstacles.clear();
        // Place a random number of obstacles
        int numObstacles = random.nextInt(6) + 5;  // Random between 5 to 10 obstacles
        for (int i = 0; i < numObstacles; i++) {
            int obsX = random.nextInt(boardWidth / tileSize);
            int obsY = random.nextInt(boardHeight / tileSize);
            Obstacle obs = new Obstacle(obsX, obsY);
            obstacles.add(obs);
        }
    }

    public void move() {
        if (gameOver) {
            return;
        }

        // Eat food
        if (collision(snakeHead, food)) {
            snakeBody.add(new Tile(food.x, food.y));
            playSound("eat.wav");
            placeFood();
        }

        // Move snake body
        for (int i = snakeBody.size() - 1; i >= 0; i--) {
            Tile snakePart = snakeBody.get(i);
            if (i == 0) {
                snakePart.x = snakeHead.x;
                snakePart.y = snakeHead.y;
            } else {
                Tile prevSnakePart = snakeBody.get(i - 1);
                snakePart.x = prevSnakePart.x;
                snakePart.y = prevSnakePart.y;
            }
        }

        // Move snake head
        snakeHead.x += velocityX;
        snakeHead.y += velocityY;

        // Check collision with obstacles
        for (Obstacle obs : obstacles) {
            if (collision(snakeHead, obs)) {
                gameOver = true;
                saveHighScore(snakeBody.size());
            }
        }

        // Game over conditions
        for (Tile snakePart : snakeBody) {
            if (collision(snakeHead, snakePart)) {
                gameOver = true;
                saveHighScore(snakeBody.size());
            }
        }

        if (snakeHead.x * tileSize < 0 || snakeHead.x * tileSize >= boardWidth ||
                snakeHead.y * tileSize < 0 || snakeHead.y * tileSize >= boardHeight) {
            gameOver = true;
            saveHighScore(snakeBody.size());
        }
    }

    public boolean collision(Tile tile1, Tile tile2) {
        return tile1.x == tile2.x && tile1.y == tile2.y;
    }

    public boolean collision(Tile tile1, Obstacle obs) {
        return tile1.x == obs.x && tile1.y == obs.y;
    }

    public void resetGame() {
        snakeHead = new Tile(5, 5);
        snakeBody.clear();
        placeFood();
        placeObstacles();
        velocityX = 1;
        velocityY = 0;
        gameOver = false;
        gameLoop.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        move();
        repaint();
        if (gameOver) {
            gameLoop.stop();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver) {
            resetGame();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_UP && velocityY != 1) {
            velocityX = 0;
            velocityY = -1;
        } else if (e.getKeyCode() == KeyEvent.VK_DOWN && velocityY != -1) {
            velocityX = 0;
            velocityY = 1;
        } else if (e.getKeyCode() == KeyEvent.VK_LEFT && velocityX != 1) {
            velocityX = -1;
            velocityY = 0;
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT && velocityX != -1) {
            velocityX = 1;
            velocityY = 0;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    // Method to play sound
    public void playSound(String soundFile) {
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(soundFile).getAbsoluteFile());
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
            ex.printStackTrace();
        }
    }

}
