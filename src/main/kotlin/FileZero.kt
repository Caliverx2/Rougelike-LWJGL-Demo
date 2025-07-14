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
import kotlin.math.tan

data class Vector3d(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3d) = (Vector3d(x + other.x, y + other.y, z + other.z))
    operator fun minus(other: Vector3d) = (Vector3d(x - other.x, y - other.y, z - other.z))
    operator fun times(other: Vector3d) = (Vector3d(x * other.x, y * other.y, z * other.z))
}

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

        fun scale(x: Double, y: Double, z: Double): Matrix4x4 {
            return Matrix4x4(arrayOf(
                doubleArrayOf(x, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, y, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, z, 0.0),
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

    fun transform(vector: Vector3d): Vector3d {
        val x = vector.x * data[0][0] + vector.y * data[0][1] + vector.z * data[0][2] + data[0][3]
        val y = vector.x * data[1][0] + vector.y * data[1][1] + vector.z * data[1][2] + data[1][3]
        val z = vector.x * data[2][0] + vector.y * data[2][1] + vector.z * data[2][2] + data[2][3]
        val w = vector.x * data[3][0] + vector.y * data[3][1] + vector.z * data[3][2] + data[3][3]

        if (w == 0.0) {
            return Vector3d(x, y, z)
        }
        return Vector3d(x / w, y / w, z / w)
    }
}

class Cube(val x: Int, val y: Int, val z: Int, val color: Color) {
    fun getVertices(size: Double, position: Vector3d): List<Vector3d> {
        val halfSize = size / 2.0
        return listOf(
            Vector3d(position.x - halfSize, position.y - halfSize, position.z + halfSize), // 0
            Vector3d(position.x + halfSize, position.y - halfSize, position.z + halfSize), // 1
            Vector3d(position.x + halfSize, position.y + halfSize, position.z + halfSize), // 2
            Vector3d(position.x - halfSize, position.y + halfSize, position.z + halfSize), // 3

            Vector3d(position.x - halfSize, position.y - halfSize, position.z - halfSize), // 4
            Vector3d(position.x + halfSize, position.y - halfSize, position.z - halfSize), // 5
            Vector3d(position.x + halfSize, position.y + halfSize, position.z - halfSize), // 6
            Vector3d(position.x - halfSize, position.y + halfSize, position.z - halfSize)  // 7
        )
    }

    fun getNormals() {

    }
}

class TransformedCube(
    val cube: Cube,
    var transformMatrix: Matrix4x4 = Matrix4x4.identity(),
    val size: Double,
    val initialPosition: Vector3d
) {
    fun getTransformedVertices(): List<Vector3d> {
        val baseVertices = cube.getVertices(size, initialPosition)
        return baseVertices.map { vertex ->
            transformMatrix.transform(vertex)
        }
    }

    fun applyTransform(matrix: Matrix4x4) {
        transformMatrix = matrix * transformMatrix
    }
}

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
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)

var gridMap: Array<Array<Array<Int>>> = Array(9) {
    Array(9) {
        Array(9) { 0 }
    }
}

class DrawingPanel : JPanel() {
    private val cubes = mutableListOf<TransformedCube>()
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

        for (x in 0..8) {
            for (y in 0..8) {
                if (grid1[x][y] == 1) {
                    gridMap[x][0][y] = 1
                }
                if (grid2[x][y] == 1) {
                    gridMap[x][1][y] = 1
                }
                if (grid3[x][y] == 1) {
                    gridMap[x][2][y] = 1
                }
                if (grid4[x][y] == 1) {
                    gridMap[x][3][y] = 1
                }
            }
        }

        for (x in 0..8) {
            for (y in 0..8) {
                for (z in 0..8) {
                    if (gridMap[x][z][y] == 1) {
                        val initialPos = Vector3d(
                            (x - 4.0) * (cubeSize + spacing),
                            (z - 4.0) * (cubeSize + spacing),
                            (y - 4.0) * (cubeSize + spacing)
                        )
                        cubes.add(TransformedCube(Cube(x, z, y, Color.LIGHT_GRAY), initialPosition = initialPos, size = cubeSize))
                    }
                }
            }
        }

        val timer = Timer(16) { repaint() }
        timer.start()
    }


    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = Color(40, 40, 40)
        g2d.fillRect(0, 0, width, height)

        depthBuffer = Array(width) { DoubleArray(height) { Double.MAX_VALUE } }

        val viewMatrix = Matrix4x4.translation(cameraPosX, cameraPosY, cameraPosZ) *
                Matrix4x4.rotationX(rotationAngleX) *
                Matrix4x4.rotationY(rotationAngleY)


        val fov = 90.0
        val aspectRatio = width.toDouble() / height.toDouble()
        val near = 0.1
        val far = 2000.0

        val tanHalfFov = tan(Math.toRadians(fov / 2))

        val projectionMatrix = Matrix4x4(arrayOf(
            doubleArrayOf(1.0 / (aspectRatio * tanHalfFov), 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0 / tanHalfFov, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, -((far + near) / (far - near)), -((2 * far * near) / (far - near))),
            doubleArrayOf(0.0, 0.0, -1.0, 0.0)
        ))

        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0, // Przednia ściana
            4 to 5, 5 to 6, 6 to 7, 7 to 4, // Tylna ściana
            0 to 4, 1 to 5, 2 to 6, 3 to 7  // Krawędzie łączące ściany
        )

        for (transformedCube in cubes) {
            val transformedVertices = transformedCube.getTransformedVertices().map {
                viewMatrix.transform(it)
            }

            val projectedVertices = transformedVertices.map { vertex ->
                val projected = projectionMatrix.transform(vertex)
                Vector3d(
                    (projected.x + 1) * width / 2,
                    (1 - projected.y) * height / 2,
                    projected.z
                )
            }

            g2d.color = transformedCube.cube.color
            for ((startIdx, endIdx) in edges) {
                val p1 = projectedVertices[startIdx]
                val p2 = projectedVertices[endIdx]

                if (p1.z < far && p1.z > near && p2.z < far && p2.z > near) {
                    g2d.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }
            }
        }

        g2d.color = Color.WHITE
        for (x in 0..8) {
            for (y in 0..8) {
                if ((gridMap[x][0][y]) == 1) {
                    g2d.drawOval(x*10, y*10, 10, 10)
                }
                if ((gridMap[x][1][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*1+10, 10, 10)
                }
                if ((gridMap[x][2][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*2+20, 10, 10)
                }
                if ((gridMap[x][3][y]) == 1) {
                    g2d.drawOval(x*10, y*10+90*3+30, 10, 10)
                }
            }
        }
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            when (e?.keyCode) {
                KeyEvent.VK_UP -> cameraPosY += 10.0
                KeyEvent.VK_DOWN -> cameraPosY -= 10.0
                KeyEvent.VK_LEFT -> cameraPosX -= 10.0
                KeyEvent.VK_RIGHT -> cameraPosX += 10.0
                KeyEvent.VK_W -> cameraPosZ += 10.0
                KeyEvent.VK_S -> cameraPosZ -= 10.0
                KeyEvent.VK_A -> rotationAngleY -= 0.05
                KeyEvent.VK_D -> rotationAngleY += 0.05
                KeyEvent.VK_Q -> rotationAngleX -= 0.05
                KeyEvent.VK_E -> rotationAngleX += 0.05
            }
            repaint()
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("FileZero.kt")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val panel = DrawingPanel()
        panel.preferredSize = java.awt.Dimension(620, 400)
        frame.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}