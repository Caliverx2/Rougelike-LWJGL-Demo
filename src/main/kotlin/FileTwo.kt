import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.SystemColor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reprezentuje punkt lub wektor w przestrzeni 3D.
 */
data class Vector3D(var x: Double, var y: Double, var z: Double) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vector3D(x / scalar, y / scalar, z / scalar)
    fun dot(other: Vector3D) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3D) = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized() = if (length() == 0.0) Vector3D(0.0, 0.0, 0.0) else this / length()
}

/**
 * Reprezentuje ścianę w przestrzeni 3D, zdefiniowaną przez listę wierzchołków i kolor.
 */
data class Wall(val vertices: List<Vector3D>, val color: Color)

/**
 * Implementuje podstawowe operacje na macierzach 4x4, używane do transformacji 3D.
 */
class Matrix4x4(private val data: Array<DoubleArray>) {
    init {
        require(data.size == 4 && data.all { it.size == 4 }) { "Macierz musi być 4x4" }
    }
    companion object {
        fun identity(): Matrix4x4 {
            return Matrix4x4(arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun rotationX(angleRad: Double): Matrix4x4 {
            val cos = cos(angleRad)
            val sin = sin(angleRad)
            return Matrix4x4(arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, cos, -sin, 0.0),
                doubleArrayOf(0.0, sin, cos, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun rotationY(angleRad: Double): Matrix4x4 {
            val cos = cos(angleRad)
            val sin = sin(angleRad)
            return Matrix4x4(arrayOf(
                doubleArrayOf(cos, 0.0, sin, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                doubleArrayOf(-sin, 0.0, cos, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun rotationZ(angleRad: Double): Matrix4x4 {
            val cos = cos(angleRad)
            val sin = sin(angleRad)
            return Matrix4x4(arrayOf(
                doubleArrayOf(cos, -sin, 0.0, 0.0),
                doubleArrayOf(sin, cos, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun translation(x: Double, y: Double, z: Double): Matrix4x4 {
            return Matrix4x4(arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0, x),
                doubleArrayOf(0.0, 1.0, 0.0, y),
                doubleArrayOf(0.0, 0.0, 1.0, z),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }
    }

    /**
     * Mnoży dwie macierze 4x4.
     */
    operator fun times(other: Matrix4x4): Matrix4x4 {
        val result = Array(4) { DoubleArray(4) }
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) {
                    sum += this.data[i][k] * other.data[k][j]
                }
                result[i][j] = sum
            }
        }
        return Matrix4x4(result)
    }

    /**
     * Transformuje wektor 3D za pomocą macierzy 4x4.
     */
    fun transform(vector: Vector3D): Vector3D {
        val x = vector.x * data[0][0] + vector.y * data[0][1] + vector.z * data[0][2] + data[0][3]
        val y = vector.x * data[1][0] + vector.y * data[1][1] + vector.z * data[1][2] + data[1][3]
        val z = vector.x * data[2][0] + vector.y * data[2][1] + vector.z * data[2][2] + data[2][3]
        val w = vector.x * data[3][0] + vector.y * data[3][1] + vector.z * data[3][2] + data[3][3]

        return Vector3D(x / w, y / w, z / w)
    }
}

/**
 * Klasa reprezentująca pojedynczą kostkę, składającą się ze ścian.
 */
class Cube(val walls: List<Wall>) {
    /**
     * Tworzy kostkę o określonym rozmiarze i pozycji.
     * @param size Rozmiar kostki (długość krawędzi).
     * @param position Pozycja centralna kostki.
     * @param colors Kolory dla każdej ściany kostki.
     */
    companion object {
        fun create(size: Double, position: Vector3D, colors: List<Color>): Cube {
            val halfSize = size / 2.0
            val walls = mutableListOf<Wall>()

            // Front
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position,
                Vector3D(halfSize, halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, halfSize) + position
            ), colors[0]))
            // Back
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(-halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, -halfSize) + position
            ), colors[1]))
            // Left
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(-halfSize, -halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, -halfSize) + position
            ), colors[2]))
            // Right
            walls.add(Wall(listOf(
                Vector3D(halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position
            ), colors[3]))
            // Bottom
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position,
                Vector3D(-halfSize, -halfSize, halfSize) + position
            ), colors[4]))
            // Top
            walls.add(Wall(listOf(
                Vector3D(-halfSize, halfSize, -halfSize) + position,
                Vector3D(-halfSize, halfSize, halfSize) + position,
                Vector3D(halfSize, halfSize, halfSize) + position,
                Vector3D(halfSize, halfSize, -halfSize) + position
            ), colors[5]))

            return Cube(walls)
        }
    }
}

/**
 * Komponent Swing do renderowania sceny 3D.
 */
class Simple3DRenderer(private val gridMap: Array<Array<Array<Int>>>) : JPanel() {

    private val cubes = mutableListOf<Cube>()
    private var rotationAngleY = 0.5
    private var rotationAngleX = 0.5
    private val cubeSize = 100.0
    private val spacing = 0.0

    init {
        addKeyListener(KeyboardListener())
        isFocusable = true
        background = Color.DARK_GRAY
        preferredSize = java.awt.Dimension(620, 400)

        val defaultColors = listOf(
            Color.BLUE,      // Front
            Color.GREEN,     // Back
            Color.RED,       // Left
            Color(250,150,0),// Right
            Color.YELLOW,    // Bottom
            Color.WHITE      // Top
        )

        val gridWidth = gridMap.size * (cubeSize + spacing) - spacing
        val gridHeight = gridMap[0].size * (cubeSize + spacing) - spacing
        val gridDepth = gridMap[0][0].size * (cubeSize + spacing) - spacing

        val startX = -gridWidth / 2.0 + cubeSize / 2.0
        val startY = -gridHeight / 2.0 + cubeSize / 2.0
        val startZ = -gridDepth / 2.0 + cubeSize / 2.0

        for (x in gridMap.indices) {
            for (y in gridMap[x].indices) {
                for (z in gridMap[x][y].indices) {
                    if (gridMap[x][y][z] == 1) {
                        val posX = startX + x * (cubeSize + spacing)
                        val posY = startY + y * (cubeSize + spacing)
                        val posZ = startZ + z * (cubeSize + spacing)
                        cubes.add(Cube.create(cubeSize, Vector3D(posX, posY, posZ), defaultColors))
                    }
                }
            }
        }

        Timer(16) {
            repaint()
        }.start()
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            when (e?.keyCode) {
                KeyEvent.VK_UP -> rotationAngleX -= 0.1
                KeyEvent.VK_DOWN -> rotationAngleX += 0.1
                KeyEvent.VK_LEFT -> rotationAngleY += 0.1
                KeyEvent.VK_RIGHT -> rotationAngleY -= 0.1
            }
        }

        override fun keyReleased(e: KeyEvent?) {
            if (e?.keyCode == KeyEvent.VK_G) {
                println("angleX")
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        val width = width
        val height = height

        val modelMatrix = Matrix4x4.rotationY(rotationAngleY) * Matrix4x4.rotationX(rotationAngleX)
        val viewMatrix = Matrix4x4.translation(0.0, 0.0, -800.0)
        val projectionMatrix = createPerspectiveProjectionMatrix(90.0, width.toDouble() / height.toDouble(), 1.0, 2000.0)

        val allWalls = cubes.flatMap { it.walls }

        val sortedWalls = allWalls.sortedByDescending { wall ->
            wall.vertices.map { v -> (projectionMatrix * viewMatrix * modelMatrix).transform(v).z }.average()
        }

        for (wall in sortedWalls) {
            val transformedVertices = wall.vertices.map { vertex ->
                modelMatrix.transform(vertex)
            }

            val v0 = transformedVertices[0]
            val v1 = transformedVertices[1]
            val v2 = transformedVertices[2]
            val edge1 = (v1 - v0)
            val edge2 = (v2 - v0)
            val normal = edge1.cross(edge2).normalized()


            val cameraVector = Vector3D(0.0, 0.0, 1.0).normalized()


            if (normal.dot(cameraVector) > 0) {
                val finalTransformedVertices = wall.vertices.map { vertex ->
                    projectionMatrix.transform(viewMatrix.transform(modelMatrix.transform(vertex)))
                }

                val screenPoints = finalTransformedVertices.map { v ->
                    java.awt.Point(
                        (v.x * width / 2.0 + width / 2.0).toInt(),
                        (-v.y * height / 2.0 + height / 2.0).toInt()
                    )
                }

                g2d.color = wall.color
                val xPoints = screenPoints.map { it.x }.toIntArray()
                val yPoints = screenPoints.map { it.y }.toIntArray()
                g2d.fillPolygon(xPoints, yPoints, screenPoints.size)

                g2d.color = Color.BLACK
                g2d.drawPolygon(xPoints, yPoints, screenPoints.size)
            }
        }
    }

    private fun createPerspectiveProjectionMatrix(fovDegrees: Double, aspectRatio: Double, near: Double, far: Double): Matrix4x4 {
        val fovRad = fovDegrees * PI / 180.0
        val tanHalfFov = kotlin.math.tan(fovRad / 2.0)

        val m00 = 1.0 / (aspectRatio * tanHalfFov)
        val m11 = 1.0 / tanHalfFov
        val m22 = -(far + near) / (far - near)
        val m23 = -2.0 * far * near / (far - near)
        val m32 = -1.0
        val m33 = 0.0

        return Matrix4x4(arrayOf(
            doubleArrayOf(m00, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, m11, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, m22, m23),
            doubleArrayOf(0.0, 0.0, m32, m33)
        ))
    }
}

/**
 * Główna funkcja aplikacji, tworząca okno Swing i dodająca do niego renderer.
 */
fun main() {
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
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(1, 1, 0, 0, 1, 0, 0, 1, 1),
        intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
    )

    val gridMap: Array<Array<Array<Int>>> = Array(9) { // X
        Array(4) { // Y (poziomy)
            Array(9) { 0 } // Z
        }
    }

    for (x in 0..8) {
        for (z in 0..8) {
            if (grid1[x][z] == 1) {
                gridMap[x][0][z] = 1
            }
            if (grid2[x][z] == 1) {
                gridMap[x][1][z] = 1
            }
            if (grid3[x][z] == 1) {
                gridMap[x][2][z] = 1
            }
            if (grid4[x][z] == 1) {
                gridMap[x][3][z] = 1
            }
        }
    }

    SwingUtilities.invokeLater {
        val frame = JFrame("Prosty Renderer 3D w Kotlin/Swing - Mapa Kostek")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.add(Simple3DRenderer(gridMap))
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}