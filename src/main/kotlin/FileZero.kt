package org.lewapnoob.FileZero

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(var x: Double, var y: Double, var z: Double)
data class Point2D(var x: Double, var y: Double)

val grid1 = arrayOf(
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)
val grid2 = arrayOf(
    intArrayOf(1, 1, 0, 1, 1, 1, 0, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 0, 1, 1, 1, 0, 1, 1)
)
val grid3 = arrayOf(
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)
val grid4 = arrayOf(
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 0)
)

var distance = 10.0

class DrawingPanel : JPanel() {
    private var angleX = 2.2
    private var angleY = 1.6

    private val modelVertices = mutableListOf<Point3D>()
    private val modelEdges = mutableListOf<Pair<Int, Int>>()

    init {
        buildCompleteModel()
        setBackground(Color.DARK_GRAY)
        addKeyListener(KeyboardListener())
        isFocusable = true

        val timer = Timer(16) {
            repaint()
        }
        timer.start()
    }

    /**
     * Tworzy jeden, duży model 3D składający się ze wszystkich sześcianów z siatki.
     */
    private fun buildCompleteModel() {
        val cubeVertices = arrayOf(
            Point3D(-0.5, -0.5, -0.5), Point3D(0.5, -0.5, -0.5),
            Point3D(0.5, 0.5, -0.5), Point3D(-0.5, 0.5, -0.5),
            Point3D(-0.5, -0.5, 0.5), Point3D(0.5, -0.5, 0.5),
            Point3D(0.5, 0.5, 0.5), Point3D(-0.5, 0.5, 0.5)
        )
        val cubeEdges = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0, // ściana tylna
            4 to 5, 5 to 6, 6 to 7, 7 to 4, // ściana przednia
            0 to 4, 1 to 5, 2 to 6, 3 to 7  // krawędzie łączące
        )

        val gridSpacing = 1.0
        val gridWidth = grid2[0].size
        val gridHeight = grid2.size

        for (row in grid2.indices) {
            for (col in grid2[row].indices) {
                if (grid1[row][col] == 1) {
                    val baseIndex = modelVertices.size

                    // Obliczamy przesunięcie w przestrzeni 3D, centrując całą siatkę wokół (0,0,0)
                    val offsetX = (col - gridWidth / 2.0) * gridSpacing
                    val offsetZ = (row - gridHeight / 2.0) * gridSpacing

                    // Dodajemy 8 wierzchołków tego sześcianu, już przesuniętych na właściwe miejsce
                    for (vertex in cubeVertices) {
                        modelVertices.add(
                            Point3D(vertex.x + offsetX, vertex.y-1, vertex.z + offsetZ)
                        )
                    }

                    // Dodajemy 12 krawędzi, aktualizując ich indeksy
                    for (edge in cubeEdges) {
                        modelEdges.add((edge.first + baseIndex) to (edge.second + baseIndex))
                    }
                }
                if (grid2[row][col] == 1) {
                    val baseIndex = modelVertices.size

                    // Obliczamy przesunięcie w przestrzeni 3D, centrując całą siatkę wokół (0,0,0)
                    val offsetX = (col - gridWidth / 2.0) * gridSpacing
                    val offsetZ = (row - gridHeight / 2.0) * gridSpacing

                    // Dodajemy 8 wierzchołków tego sześcianu, już przesuniętych na właściwe miejsce
                    for (vertex in cubeVertices) {
                        modelVertices.add(
                            Point3D(vertex.x + offsetX, vertex.y, vertex.z + offsetZ)
                        )
                    }

                    // Dodajemy 12 krawędzi, aktualizując ich indeksy
                    for (edge in cubeEdges) {
                        modelEdges.add((edge.first + baseIndex) to (edge.second + baseIndex))
                    }
                }
                if (grid3[row][col] == 1) {
                    val baseIndex = modelVertices.size

                    // Obliczamy przesunięcie w przestrzeni 3D, centrując całą siatkę wokół (0,0,0)
                    val offsetX = (col - gridWidth / 2.0) * gridSpacing
                    val offsetZ = (row - gridHeight / 2.0) * gridSpacing

                    // Dodajemy 8 wierzchołków tego sześcianu, już przesuniętych na właściwe miejsce
                    for (vertex in cubeVertices) {
                        modelVertices.add(
                            Point3D(vertex.x + offsetX, vertex.y+1, vertex.z + offsetZ)
                        )
                    }

                    // Dodajemy 12 krawędzi, aktualizując ich indeksy
                    for (edge in cubeEdges) {
                        modelEdges.add((edge.first + baseIndex) to (edge.second + baseIndex))
                    }
                }
                if (grid4[row][col] == 1) {
                    val baseIndex = modelVertices.size

                    // Obliczamy przesunięcie w przestrzeni 3D, centrując całą siatkę wokół (0,0,0)
                    val offsetX = (col - gridWidth / 2.0) * gridSpacing
                    val offsetZ = (row - gridHeight / 2.0) * gridSpacing

                    // Dodajemy 8 wierzchołków tego sześcianu, już przesuniętych na właściwe miejsce
                    for (vertex in cubeVertices) {
                        modelVertices.add(
                            Point3D(vertex.x + offsetX, vertex.y+2, vertex.z + offsetZ)
                        )
                    }

                    // Dodajemy 12 krawędzi, aktualizując ich indeksy
                    for (edge in cubeEdges) {
                        modelEdges.add((edge.first + baseIndex) to (edge.second + baseIndex))
                    }
                }

            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = Color.BLACK
        g2d.fillRect(0, 0, width, height) // Czyścimy tło

        val projectedPoints = mutableListOf<Point2D>()
        val scale = 125.0 // Ogólna skala rzutowanego obiektu

        // Pętla po wszystkich wierzchołkach całego modelu
        for (vertex in modelVertices) {
            // 1. Obrót punktu (który jest już we właściwym miejscu w siatce 3D)
            var rotated = rotateY(vertex, angleY)
            rotated = rotateX(rotated, angleX)

            // 2. Rzutowanie perspektywiczne
            val zPerspective = 1 / (distance - rotated.z)

            val projectedX = rotated.x * zPerspective * scale
            val projectedY = rotated.y * zPerspective * scale

            // 3. Przesunięcie na środek ekranu
            val screenX = projectedX + width / 2.0
            val screenY = projectedY + height / 2.0

            projectedPoints.add(Point2D(screenX, screenY))
        }

        // 4. Rysowanie wszystkich krawędzi całego modelu
        g2d.color = Color.GREEN
        g2d.stroke = java.awt.BasicStroke(0.5f)
        for (edge in modelEdges) {
            val p1 = projectedPoints[edge.first]
            val p2 = projectedPoints[edge.second]
            g2d.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        }
    }

    // Funkcja do obrotu punktu 3D wokół osi X
    private fun rotateX(p: Point3D, angle: Double): Point3D {
        val sinA = sin(angle)
        val cosA = cos(angle)
        val newY = p.y * cosA - p.z * sinA
        val newZ = p.y * sinA + p.z * cosA
        return Point3D(p.x, newY, newZ)
    }

    // Funkcja do obrotu punktu 3D wokół osi Y
    private fun rotateY(p: Point3D, angle: Double): Point3D {
        val sinA = sin(angle)
        val cosA = cos(angle)
        val newX = p.x * cosA - p.z * sinA
        val newZ = p.x * sinA + p.z * cosA
        return Point3D(newX, p.y, newZ)
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            if (e?.keyCode == KeyEvent.VK_UP) {
                angleX -= 0.1
            }
            if (e?.keyCode == KeyEvent.VK_DOWN) {
                angleX += 0.1
            }
            if (e?.keyCode == KeyEvent.VK_LEFT) {
                angleY += 0.1
            }
            if (e?.keyCode == KeyEvent.VK_RIGHT) {
                angleY -= 0.1
            }
            if (e?.keyCode == KeyEvent.VK_W) {
                distance -= 1
            }
            if (e?.keyCode == KeyEvent.VK_S) {
                distance += 1
            }
        }

        override fun keyReleased(e: KeyEvent?) {
            super.keyReleased(e)
            if (e?.keyCode == KeyEvent.VK_G) {
                println(angleX)
                println(angleY)
            }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("FileZero")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val panel = DrawingPanel()
        panel.preferredSize = java.awt.Dimension(320, 200)
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}