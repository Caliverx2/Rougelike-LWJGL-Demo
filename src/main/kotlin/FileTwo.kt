import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.Double
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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

data class Wall(val vertices: List<Vector3D>, val color: Color)

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

    fun transform(vector: Vector3D): Vector3D {
        val x = vector.x * data[0][0] + vector.y * data[0][1] + vector.z * data[0][2] + data[0][3]
        val y = vector.x * data[1][0] + vector.y * data[1][1] + vector.z * data[1][2] + data[1][3]
        val z = vector.x * data[2][0] + vector.y * data[2][1] + vector.z * data[2][2] + data[2][3]
        val w = vector.x * data[3][0] + vector.y * data[3][1] + vector.z * data[3][2] + data[3][3]

        if (w == 0.0) {
            return Vector3D(x, y, z)
        }
        return Vector3D(x / w, y / w, z / w)
    }
}

class Cube(val walls: List<Wall>) {
    companion object {
        fun create(size: Double, position: Vector3D, colors: List<Color>): Cube {
            val halfSize = size / 2.0
            val walls = mutableListOf<Wall>()

            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position,
                Vector3D(halfSize, halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, halfSize) + position
            ), colors[0]))
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(-halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, -halfSize) + position
            ), colors[1]))
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(-halfSize, -halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, halfSize) + position,
                Vector3D(-halfSize, halfSize, -halfSize) + position
            ), colors[2]))
            walls.add(Wall(listOf(
                Vector3D(halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, -halfSize) + position,
                Vector3D(halfSize, halfSize, halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position
            ), colors[3]))
            walls.add(Wall(listOf(
                Vector3D(-halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, -halfSize) + position,
                Vector3D(halfSize, -halfSize, halfSize) + position,
                Vector3D(-halfSize, -halfSize, halfSize) + position
            ), colors[4]))
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

class Simple3DRenderer(private val gridMap: Array<Array<Array<Int>>>) : JPanel() {

    private val cubes = mutableListOf<Cube>()
    private var rotationAngleY = 0.5
    private var rotationAngleX = 0.5

    private var cameraPosX = 0.0
    private var cameraPosY = 0.0
    private var cameraPosZ = -800.0

    private val cubeSize = 100.0
    private val spacing = 0.0

    private lateinit var depthBuffer: Array<DoubleArray>

    init {
        addKeyListener(KeyboardListener())
        isFocusable = true
        background = Color.DARK_GRAY
        preferredSize = java.awt.Dimension(620, 400)

        val defaultColors = listOf(
            Color.BLUE,
            Color.GREEN,
            Color.RED,
            Color(250,150,0),
            Color.YELLOW,
            Color.WHITE
        )

        val gridWidth = gridMap.size * (cubeSize + spacing) - spacing
        val gridHeight = gridMap[0].size * (cubeSize + spacing) - spacing
        val gridDepth = gridMap[0][0].size * (cubeSize + spacing) - spacing

        val startX = -gridWidth / 2.0 + cubeSize / 2.0
        val startY = -gridHeight / 2.0 + cubeSize / 2.0 + 50.0
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
            val moveSpeed = 20.0
            when (e?.keyCode) {
                KeyEvent.VK_UP -> rotationAngleX -= 0.05
                KeyEvent.VK_DOWN -> rotationAngleX += 0.05
                KeyEvent.VK_LEFT -> rotationAngleY -= 0.05
                KeyEvent.VK_RIGHT -> rotationAngleY += 0.05
                KeyEvent.VK_W -> cameraPosZ += moveSpeed
                KeyEvent.VK_S -> cameraPosZ -= moveSpeed
                KeyEvent.VK_A -> cameraPosX += moveSpeed
                KeyEvent.VK_D -> cameraPosX -= moveSpeed
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        val w = width
        val h = height

        depthBuffer = Array(w) { DoubleArray(h) { Double.MAX_VALUE } }

        val modelMatrix = Matrix4x4.rotationY(rotationAngleY) * Matrix4x4.rotationX(rotationAngleX)
        val viewMatrix = Matrix4x4.translation(cameraPosX, cameraPosY, cameraPosZ)
        val projectionMatrix = createPerspectiveProjectionMatrix(90.0, w.toDouble() / h.toDouble(), 1.0, 2000.0)

        val transformMatrix = projectionMatrix * viewMatrix * modelMatrix

        val allWalls = cubes.flatMap { it.walls }

        for (wall in allWalls) {
            val transformedVerticesNDC = wall.vertices.map { vertex ->
                transformMatrix.transform(vertex)
            }

            if (transformedVerticesNDC.size >= 4) {
                val t1 = listOf(transformedVerticesNDC[0], transformedVerticesNDC[1], transformedVerticesNDC[2])
                val t2 = listOf(transformedVerticesNDC[0], transformedVerticesNDC[2], transformedVerticesNDC[3])

                val isT1InFrustum = t1.any { it.z >= -1.0 && it.z <= 1.0 }
                val isT2InFrustum = t2.any { it.z >= -1.0 && it.z <= 1.0 }

                if (isT1InFrustum) rasterizeTriangle(g2d, t1, wall.color, w, h)
                if (isT2InFrustum) rasterizeTriangle(g2d, t2, wall.color, w, h)
            }
        }
    }

    data class ScreenVertex(val x: Int, val y: Int, val z: Double)

    private fun rasterizeTriangle(g2d: Graphics2D, ndcVertices: List<Vector3D>, color: Color, screenWidth: Int, screenHeight: Int) {
        if (ndcVertices.size != 3) return

        val v0 = ScreenVertex(
            ((ndcVertices[0].x + 1.0) * screenWidth / 2.0).toInt(),
            ((-ndcVertices[0].y + 1.0) * screenHeight / 2.0).toInt(),
            ndcVertices[0].z
        )
        val v1 = ScreenVertex(
            ((ndcVertices[1].x + 1.0) * screenWidth / 2.0).toInt(),
            ((-ndcVertices[1].y + 1.0) * screenHeight / 2.0).toInt(),
            ndcVertices[1].z
        )
        val v2 = ScreenVertex(
            ((ndcVertices[2].x + 1.0) * screenWidth / 2.0).toInt(),
            ((-ndcVertices[2].y + 1.0) * screenHeight / 2.0).toInt(),
            ndcVertices[2].z
        )

        var minX = minOf(v0.x, v1.x, v2.x)
        var maxX = maxOf(v0.x, v1.x, v2.x)
        var minY = minOf(v0.y, v1.y, v2.y)
        var maxY = maxOf(v0.y, v1.y, v2.y)

        minX = max(0, minX)
        maxX = min(screenWidth - 1, maxX)
        minY = max(0, minY)
        maxY = min(screenHeight - 1, maxY)

        g2d.color = color

        val A12 = (v1.y - v2.y).toDouble()
        val B12 = (v2.x - v1.x).toDouble()
        val C12 = (v1.x * v2.y - v2.x * v1.y).toDouble()

        val A20 = (v2.y - v0.y).toDouble()
        val B20 = (v0.x - v2.x).toDouble()
        val C20 = (v2.x * v0.y - v0.x * v2.y).toDouble()

        val A01 = (v0.y - v1.y).toDouble()
        val B01 = (v1.x - v0.x).toDouble()
        val C01 = (v0.x * v1.y - v1.x * v0.y).toDouble()

        val totalArea = A12 * v0.x + B12 * v0.y + C12
        if (totalArea == 0.0) return

        var w0_initial = A12 * minX + B12 * minY + C12
        var w1_initial = A20 * minX + B20 * minY + C20
        var w2_initial = A01 * minX + B01 * minY + C01

        for (py in minY..maxY) {
            var w0 = w0_initial
            var w1 = w1_initial
            var w2 = w2_initial

            for (px in minX..maxX) {
                val epsilon = 0.00001
                val isInside = (w0 >= -epsilon && w1 >= -epsilon && w2 >= -epsilon) ||
                        (w0 <= epsilon && w1 <= epsilon && w2 <= epsilon)

                if (isInside) {
                    val alpha = w0 / totalArea
                    val beta = w1 / totalArea
                    val gamma = w2 / totalArea

                    val interpolatedZ = alpha * v0.z + beta * v1.z + gamma * v2.z

                    if (px >= 0 && px < screenWidth && py >= 0 && py < screenHeight && interpolatedZ < depthBuffer[px][py]) {
                        g2d.fillRect(px, py, 1, 1)
                        depthBuffer[px][py] = interpolatedZ
                    }
                }
                w0 += A12
                w1 += A20
                w2 += A01
            }
            w0_initial += B12
            w1_initial += B20
            w2_initial += B01
        }
    }

    private fun createPerspectiveProjectionMatrix(fovDegrees: Double, aspectRatio: Double, near: Double, far: Double): Matrix4x4 {
        val fovRad = fovDegrees * PI / 180.0
        val tanHalfFov = tan(fovRad / 2.0)

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

    val gridMap: Array<Array<Array<Int>>> = Array(9) {
        Array(4) {
            Array(9) { 0 }
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
        val frame = JFrame("FileTwo.kt")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.add(Simple3DRenderer(gridMap))
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        frame.iconImage = Toolkit.getDefaultToolkit().getImage(Simple3DRenderer::class.java.classLoader.getResource("textures/icon.jpeg"))
    }
}