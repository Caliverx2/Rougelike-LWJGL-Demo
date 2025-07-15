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
import java.awt.Toolkit
import kotlin.math.sqrt
import kotlin.math.abs

data class Vector3d(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3d) = Vector3d(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3d) = Vector3d(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vector3d(x / scalar, y / scalar, z / scalar)

    fun dot(other: Vector3d): Double = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3d): Vector3d = Vector3d(
        this.y * other.z - this.z * other.y,
        this.z * other.x - this.x * other.z,
        this.x * other.y - this.y * other.x
    )
    fun normalize(): Vector3d {
        val length = length()
        return if (length != 0.0) Vector3d(x / length, y / length, z / length) else Vector3d(0.0, 0.0, 0.0)
    }
    fun length(): Double = sqrt(x * x + y * y + z * z)
}

data class Vector4d(val x: Double, val y: Double, val z: Double, val w: Double)

fun Double.isCloseToZero(epsilon: Double = 1e-9): Boolean = abs(this) < epsilon

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

        fun lookAt(eye: Vector3d, target: Vector3d, up: Vector3d): Matrix4x4 {
            val zAxis = (eye - target).normalize()
            val xAxis = up.cross(zAxis).normalize()
            val yAxis = zAxis.cross(xAxis)

            return Matrix4x4(arrayOf(
                doubleArrayOf(xAxis.x, xAxis.y, xAxis.z, -xAxis.dot(eye)),
                doubleArrayOf(yAxis.x, yAxis.y, yAxis.z, -yAxis.dot(eye)),
                doubleArrayOf(zAxis.x, zAxis.y, zAxis.z, -zAxis.dot(eye)),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun perspective(fovY: Double, aspectRatio: Double, near: Double, far: Double): Matrix4x4 {
            val tanHalfFovY = tan(Math.toRadians(fovY / 2.0))
            val f = 1.0 / tanHalfFovY

            return Matrix4x4(arrayOf(
                doubleArrayOf(f / aspectRatio, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, f, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, (far + near) / (near - far), (2.0 * far * near) / (near - far)),
                doubleArrayOf(0.0, 0.0, -1.0, 0.0)
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

    fun transformHomogeneous(vector: Vector3d): Vector4d {
        val x = vector.x * data[0][0] + vector.y * data[0][1] + vector.z * data[0][2] + data[0][3]
        val y = vector.x * data[1][0] + vector.y * data[1][1] + vector.z * data[1][2] + data[1][3]
        val z = vector.x * data[2][0] + vector.y * data[2][1] + vector.z * data[2][2] + data[2][3]
        val w = vector.x * data[3][0] + vector.y * data[3][1] + vector.z * data[3][2] + data[3][3]
        return Vector4d(x, y, z, w)
    }

    fun transform(vector: Vector3d): Vector3d {
        val x = vector.x * data[0][0] + vector.y * data[0][1] + vector.z * data[0][2] + data[0][3]
        val y = vector.x * data[1][0] + vector.y * data[1][1] + vector.z * data[1][2] + data[1][3]
        val z = vector.x * data[2][0] + vector.y * data[2][1] + vector.z * data[2][2] + data[2][3]
        val w = vector.x * data[3][0] + vector.y * data[3][1] + vector.z * data[3][2] + data[3][3]

        if (w.isCloseToZero()) {
            return Vector3d(x, y, z)
        }
        return Vector3d(x / w, y / w, z / w)
    }
}

class Cube(val x: Int, val y: Int, val z: Int, val color: Color) {
    fun getVertices(size: Double, position: Vector3d): List<Vector3d> {
        val halfSize = size / 2.0
        return listOf(
            Vector3d(position.x - halfSize, position.y - halfSize, position.z + halfSize),
            Vector3d(position.x + halfSize, position.y - halfSize, position.z + halfSize),
            Vector3d(position.x + halfSize, position.y + halfSize, position.z + halfSize),
            Vector3d(position.x - halfSize, position.y + halfSize, position.z + halfSize),

            Vector3d(position.x - halfSize, position.y - halfSize, position.z - halfSize),
            Vector3d(position.x + halfSize, position.y - halfSize, position.z - halfSize),
            Vector3d(position.x + halfSize, position.y + halfSize, position.z - halfSize),
            Vector3d(position.x - halfSize, position.y + halfSize, position.z - halfSize)
        )
    }

    val faces = listOf(
        listOf(0, 1, 2, 3),
        listOf(4, 7, 6, 5),
        listOf(3, 2, 6, 7),
        listOf(0, 4, 5, 1),
        listOf(1, 5, 6, 2),
        listOf(4, 0, 3, 7)
    )
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
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 1, 1),
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

    private var cameraPosition = Vector3d(0.0, 0.0, -800.0)
    private var cameraYaw = 0.0
    private var cameraPitch = 0.0
    private val movementSpeed = 20.0
    private val rotationSpeed = 0.05

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

        val lookDirection = Vector3d(
            cos(cameraPitch) * sin(cameraYaw),
            sin(cameraPitch),
            cos(cameraPitch) * cos(cameraYaw)
        ).normalize()

        val upVector = Vector3d(0.0, 1.0, 0.0)
        val rightVector = lookDirection.cross(upVector).normalize()
        val forwardVector = lookDirection

        val viewMatrix = Matrix4x4.lookAt(cameraPosition, cameraPosition + lookDirection, upVector)

        val fov = 90.0
        val aspectRatio = width.toDouble() / height.toDouble()
        val near = 0.1
        val far = 5000.0

        val projectionMatrix = Matrix4x4.perspective(fov, aspectRatio, near, far)

        val trianglesToRender = mutableListOf<Triple<Color, List<Vector3d>, Double>>()

        for (transformedCube in cubes) {
            val modelViewVertices = transformedCube.getTransformedVertices().map {
                viewMatrix.transform(it)
            }

            for (faceIndices in transformedCube.cube.faces) {
                val p0View = modelViewVertices[faceIndices[0]]
                val p1View = modelViewVertices[faceIndices[1]]
                val p2View = modelViewVertices[faceIndices[2]]

                val normalView = (p1View - p0View).cross(p2View - p0View).normalize()
                val cameraRayView = (Vector3d(0.0, 0.0, 0.0) - p0View).normalize()

                if (normalView.dot(cameraRayView) > 0) {
                    val projectedVerticesForFace = mutableListOf<Vector3d>()
                    var anyVertexInFrustum = false

                    for (index in faceIndices) {
                        val vertexInView = modelViewVertices[index]
                        val projectedHomogeneous = projectionMatrix.transformHomogeneous(vertexInView)

                        if (projectedHomogeneous.w < 0 || projectedHomogeneous.w.isCloseToZero()) {
                            projectedVerticesForFace.add(Vector3d(Double.NaN, Double.NaN, Double.NaN))
                        } else {
                            val xClip = projectedHomogeneous.x / projectedHomogeneous.w
                            val yClip = projectedHomogeneous.y / projectedHomogeneous.w
                            val zClip = projectedHomogeneous.z / projectedHomogeneous.w

                            if (xClip >= -1.0 && xClip <= 1.0 && yClip >= -1.0 && yClip <= 1.0 && zClip >= -1.0 && zClip <= 1.0) {
                                anyVertexInFrustum = true
                            }

                            projectedVerticesForFace.add(
                                Vector3d(
                                    (xClip + 1) * width / 2,
                                    (1 - yClip) * height / 2,
                                    zClip
                                )
                            )
                        }
                    }

                    val allVerticesRejected = projectedVerticesForFace.all { it.x.isNaN() }
                    if (!allVerticesRejected && anyVertexInFrustum) {
                        val drawableVertices = projectedVerticesForFace.filter { !it.x.isNaN() }

                        if (drawableVertices.size >= 3) {
                            val avgZ = faceIndices.map { index ->
                                val vertexInView = modelViewVertices[index]
                                val projectedHomogeneous = projectionMatrix.transformHomogeneous(vertexInView)
                                if (projectedHomogeneous.w.isCloseToZero()) Double.MAX_VALUE else projectedHomogeneous.z / projectedHomogeneous.w
                            }.average()

                            trianglesToRender.add(Triple(transformedCube.cube.color, listOf(drawableVertices[0], drawableVertices[1], drawableVertices[2]), avgZ))
                            if (drawableVertices.size == 4) {
                                trianglesToRender.add(Triple(transformedCube.cube.color, listOf(drawableVertices[0], drawableVertices[2], drawableVertices[3]), avgZ))
                            }
                        }
                    }
                }
            }
        }

        trianglesToRender.sortByDescending { it.third }

        for ((color, triangleVertices, _) in trianglesToRender) {
            val xPoints = IntArray(3)
            val yPoints = IntArray(3)

            for (i in 0 until 3) {
                xPoints[i] = triangleVertices[i].x.toInt()
                yPoints[i] = triangleVertices[i].y.toInt()
            }

            g2d.color = color
            g2d.fillPolygon(xPoints, yPoints, 3)
            g2d.color = Color(40, 40, 40)
            g2d.drawPolygon(xPoints, yPoints, 3)
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
            val lookDirection = Vector3d(
                cos(cameraPitch) * sin(cameraYaw),
                sin(cameraPitch),
                cos(cameraPitch) * cos(cameraYaw)
            ).normalize()

            val upVector = Vector3d(0.0, 1.0, 0.0)
            val rightVector = lookDirection.cross(upVector).normalize()
            val forwardVector = lookDirection

            when (e?.keyCode) {
                KeyEvent.VK_W -> cameraPosition += forwardVector * movementSpeed
                KeyEvent.VK_S -> cameraPosition -= forwardVector * movementSpeed
                KeyEvent.VK_D -> cameraPosition += rightVector * movementSpeed
                KeyEvent.VK_A -> cameraPosition -= rightVector * movementSpeed
                KeyEvent.VK_SPACE -> cameraPosition += Vector3d(0.0, movementSpeed, 0.0)
                KeyEvent.VK_CONTROL -> cameraPosition -= Vector3d(0.0, movementSpeed, 0.0)
                KeyEvent.VK_LEFT -> cameraYaw += rotationSpeed
                KeyEvent.VK_RIGHT -> cameraYaw -= rotationSpeed
                KeyEvent.VK_DOWN -> cameraPitch -= rotationSpeed
                KeyEvent.VK_UP -> cameraPitch += rotationSpeed
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
        frame.iconImage = Toolkit.getDefaultToolkit().getImage(DrawingPanel::class.java.classLoader.getResource("textures/icon.jpeg"))
    }
}