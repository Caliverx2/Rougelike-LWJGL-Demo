import javax.swing.*;
import java.awt.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

// A simple pair class to represent coordinates, as Java doesn't have a built-in Pair like Kotlin
class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) &&
                Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}

class Node implements Comparable<Node> {
    public final int r;
    public final int c;
    public int dist;
    public Node prev;

    public Node(int r, int c) {
        this.r = r;
        this.c = c;
        this.dist = Integer.MAX_VALUE;
        this.prev = null;
    }

    @Override
    public int compareTo(Node other) {
        return Integer.compare(this.dist, other.dist);
    }
}

class MazePathfindingPanel extends JPanel {

    private final int baseCols = 25;
    private final int baseRows = 17;
    private final int cellSize = 20;

    private final boolean[][] gridMap;
    private final boolean[][] visitedCells;
    private final Stack<Pair<Integer, Integer>> stack;
    private Pair<Integer, Integer> currentLogicalCell;
    private boolean generating;

    private List<Pair<Integer, Integer>> path;
    private int agentPositionIndex;
    private Timer agentPathTimer;
    private boolean showAgent;

    public MazePathfindingPanel() {
        gridMap = new boolean[baseRows * 2 + 1][baseCols * 2 + 1];
        visitedCells = new boolean[baseRows][baseCols];
        stack = new Stack<>();

        setPreferredSize(new Dimension((baseCols * 2 + 1) * cellSize, (baseRows * 2 + 1) * cellSize));
        setBackground(Color.DARK_GRAY);
        addKeyListener(new KeyboardListener());
        setFocusable(true);
        startGeneration();
    }

    private void startGeneration() {
        for (int r = 0; r < gridMap.length; r++) {
            for (int c = 0; c < gridMap[0].length; c++) {
                gridMap[r][c] = true;
            }
        }
        for (int r = 0; r < baseRows; r++) {
            for (int c = 0; c < baseCols; c++) {
                visitedCells[r][c] = false;
            }
        }
        stack.clear();
        path = null;
        agentPositionIndex = 0;
        if (agentPathTimer != null) {
            agentPathTimer.stop();
        }
        showAgent = false;

        currentLogicalCell = new Pair<>(0, 0);
        visitedCells[0][0] = true;
        gridMap[1][1] = false;
        stack.push(currentLogicalCell);
        generating = true;

        Timer mazeGenerationTimer = new Timer(10, e -> {
            if (generating) {
                generateStep();
                repaint();
            } else {
                ((Timer) e.getSource()).stop();
                startPathfinding();
            }
        });
        mazeGenerationTimer.start();
    }

    private void generateStep() {
        if (!stack.isEmpty()) {
            currentLogicalCell = stack.peek();
            int r = currentLogicalCell.first;
            int c = currentLogicalCell.second;

            List<Pair<Integer, Integer>> unvisitedNeighbors = getUnvisitedNeighbors(r, c);

            if (!unvisitedNeighbors.isEmpty()) {
                Pair<Integer, Integer> nextCell = unvisitedNeighbors.get(new Random().nextInt(unvisitedNeighbors.size()));
                int nextR = nextCell.first;
                int nextC = nextCell.second;

                visitedCells[nextR][nextC] = true;
                stack.push(nextCell);

                removeWallInGridMap(r, c, nextR, nextC);

                gridMap[nextR * 2 + 1][nextC * 2 + 1] = false;

                currentLogicalCell = nextCell;
            } else {
                stack.pop();
            }
        } else {
            generating = false;
        }
    }

    private List<Pair<Integer, Integer>> getUnvisitedNeighbors(int row, int col) {
        List<Pair<Integer, Integer>> neighbors = new ArrayList<>();

        // Up
        if (row > 0 && !visitedCells[row - 1][col]) {
            neighbors.add(new Pair<>(row - 1, col));
        }
        // Right
        if (col < baseCols - 1 && !visitedCells[row][col + 1]) {
            neighbors.add(new Pair<>(row, col + 1));
        }
        // Down
        if (row < baseRows - 1 && !visitedCells[row + 1][col]) {
            neighbors.add(new Pair<>(row + 1, col));
        }
        // Left
        if (col > 0 && !visitedCells[row][col - 1]) {
            neighbors.add(new Pair<>(row, col - 1));
        }

        return neighbors;
    }

    private void removeWallInGridMap(int r1, int c1, int r2, int c2) {
        int gridR1 = r1 * 2 + 1;
        int gridC1 = c1 * 2 + 1;
        int gridR2 = r2 * 2 + 1;
        int gridC2 = c2 * 2 + 1;

        int wallGridR = (gridR1 + gridR2) / 2;
        int wallGridC = (gridC1 + gridC2) / 2;

        gridMap[wallGridR][wallGridC] = false;
    }

    private void startPathfinding() {
        Pair<Integer, Integer> startLogical = new Pair<>(0, 0);
        Pair<Integer, Integer> endLogical = new Pair<>(baseRows - 1, baseCols - 1);

        Pair<Integer, Integer> startGrid = new Pair<>(startLogical.first * 2 + 1, startLogical.second * 2 + 1);
        Pair<Integer, Integer> endGrid = new Pair<>(endLogical.first * 2 + 1, endLogical.second * 2 + 1);

        path = findPathDijkstra(startGrid, endGrid);
        agentPositionIndex = 0;
        showAgent = true;

        agentPathTimer = new Timer(100, e -> {
            if (path != null && agentPositionIndex < path.size()) {
                agentPositionIndex++;
                repaint();
            } else {
                ((Timer) e.getSource()).stop();
                showAgent = false;
                repaint();
            }
        });
        agentPathTimer.start();
    }

    // Dijkstra's algorithm
    private List<Pair<Integer, Integer>> findPathDijkstra(Pair<Integer, Integer> start, Pair<Integer, Integer> end) {
        Node[][] nodes = new Node[gridMap.length][gridMap[0].length];
        for (int r = 0; r < gridMap.length; r++) {
            for (int c = 0; c < gridMap[0].length; c++) {
                nodes[r][c] = new Node(r, c);
            }
        }

        PriorityQueue<Node> pq = new PriorityQueue<>();

        Node startNode = nodes[start.first][start.second];
        startNode.dist = 0;
        pq.add(startNode);

        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        while (!pq.isEmpty()) {
            Node u = pq.poll();

            if (u.r == end.first && u.c == end.second) {
                List<Pair<Integer, Integer>> shortestPath = new ArrayList<>();
                Node curr = u;
                while (curr != null) {
                    shortestPath.add(0, new Pair<>(curr.r, curr.c));
                    curr = curr.prev;
                }
                return shortestPath;
            }

            for (int i = 0; i < 4; i++) {
                int newR = u.r + dr[i];
                int newC = u.c + dc[i];

                if (newR >= 0 && newR < gridMap.length && newC >= 0 && newC < gridMap[0].length && !gridMap[newR][newC]) {
                    Node v = nodes[newR][newC];
                    int newDist = u.dist + 1;

                    if (newDist < v.dist) {
                        v.dist = newDist;
                        v.prev = u;
                        pq.add(v);
                    }
                }
            }
        }
        return null; // No path found
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < gridMap.length; r++) {
            for (int c = 0; c < gridMap[0].length; c++) {
                int x = c * cellSize;
                int y = r * cellSize;

                if (gridMap[r][c]) {
                    // wall
                    g2d.setColor(Color.DARK_GRAY);
                    g2d.fillRect(x, y, cellSize, cellSize);
                } else {
                    // corridor
                    g2d.setColor(Color.lightGray);
                    g2d.fillRect(x, y, cellSize, cellSize);
                }
            }
        }

        // draw path
        if (path != null) {
            for (int i = 0; i < Math.min(agentPositionIndex, path.size()); i++) {
                Pair<Integer, Integer> p = path.get(i);
                g2d.setColor(new Color(0, 150, 0, 150)); // Semi-transparent green
                g2d.fillRect(p.second * cellSize, p.first * cellSize, cellSize, cellSize);
            }
        }

        // draw current logical cell during generation
        if (generating && currentLogicalCell != null) {
            int r = currentLogicalCell.first;
            int c = currentLogicalCell.second;
            int gridR = r * 2 + 1;
            int gridC = c * 2 + 1;
            g2d.setColor(Color.CYAN);
            g2d.fillRect(gridC * cellSize, gridR * cellSize, cellSize, cellSize);
        }

        // draw agent
        if (showAgent && path != null && agentPositionIndex > 0 && agentPositionIndex <= path.size()) {
            Pair<Integer, Integer> agentPos = path.get(agentPositionIndex - 1);
            g2d.setColor(Color.MAGENTA);
            g2d.fillRect(agentPos.second * cellSize, agentPos.first * cellSize, cellSize, cellSize);
        }

        g2d.setColor(Color.GREEN); // start
        g2d.fillRect(1 * cellSize, 1 * cellSize, cellSize, cellSize);

        g2d.setColor(Color.RED); // end
        g2d.fillRect((baseCols * 2 - 1) * cellSize, (baseRows * 2 - 1) * cellSize, cellSize, cellSize);
    }

    private class KeyboardListener extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (!generating) {
                    startPathfinding();
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Maze Generator with Dijkstra agent");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new MazePathfindingPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}