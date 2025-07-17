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
import kotlin.math.max
import kotlin.math.min
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ConcurrentLinkedQueue
import java.awt.image.BufferedImage

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
        private val IDENTITY_DATA = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        )
        fun identity(): Matrix4x4 = Matrix4x4(IDENTITY_DATA)

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
            val depthRange = near - far

            return Matrix4x4(arrayOf(
                doubleArrayOf(f / aspectRatio, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, f, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, (far + near) / depthRange, (2.0 * far * near) / depthRange),
                doubleArrayOf(0.0, 0.0, -1.0, 0.0)
            ))
        }
    }

    operator fun times(other: Matrix4x4): Matrix4x4 {
        val resultData = Array(4) { DoubleArray(4) }
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) {
                    sum += this.data[i][k] * other.data[k][j]
                }
                resultData[i][j] = sum
            }
        }
        return Matrix4x4(resultData)
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

        return if (w.isCloseToZero()) Vector3d(x, y, z) else Vector3d(x / w, y / w, z / w)
    }
}

class Cube(val color: Color, val vertices: List<Vector3d>) {
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
    var transformMatrix: Matrix4x4 = Matrix4x4.identity()
) {
    fun getTransformedVertices(): List<Vector3d> {
        return cube.vertices.map { transformMatrix.transform(it) }
    }

    fun applyTransform(matrix: Matrix4x4) {
        transformMatrix = matrix * transformMatrix
    }
}

val GRID_1 = arrayOf(
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
val GRID_2 = arrayOf(
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
val GRID_3 = arrayOf(
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
val GRID_4 = arrayOf(
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

val GRID_MAP: Array<Array<Array<Int>>> = Array(9) { Array(9) { Array(9) { 0 } } }

data class RenderableFace(
    val screenVertices: List<Vector3d>,
    val color: Color,
    val isEdge: Boolean
)

class DrawingPanel : JPanel() {
    private val cubes = mutableListOf<TransformedCube>()

    private var cameraPosition = Vector3d(0.0, 0.0, -800.0)
    private var cameraYaw = 0.0
    private var cameraPitch = 0.0
    private val movementSpeed = 20.0
    private val rotationSpeed = 0.05

    private val cubeSize = 100.0
    private val spacing = 0.0

    private val virtualWidth = 1920/6 //320
    private val virtualHeight = 1080/6 //180
    private lateinit var depthBuffer: Array<DoubleArray>
    private lateinit var backBuffer: BufferedImage

    private val pressedKeys = Collections.synchronizedSet(mutableSetOf<Int>())

    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val renderQueue = ConcurrentLinkedQueue<RenderableFace>()

    init {
        addKeyListener(KeyboardListener())
        isFocusable = true

        depthBuffer = Array(virtualWidth) { DoubleArray(virtualHeight) { Double.MAX_VALUE } }
        backBuffer = BufferedImage(virtualWidth, virtualHeight, BufferedImage.TYPE_INT_RGB)
        val g2dBack = backBuffer.graphics as Graphics2D
        g2dBack.color = Color(40, 40, 40)
        g2dBack.fillRect(0, 0, virtualWidth, virtualHeight)
        g2dBack.dispose()


        val grids = listOf(GRID_1, GRID_2, GRID_3, GRID_4)
        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until grids.size) {
                    if (grids[zLevel][x][y] == 1) {
                        GRID_MAP[x][zLevel][y] = 1
                    }
                }
            }
        }

        val baseCubeVertices = mutableListOf<Vector3d>()
        val halfSize = cubeSize / 2.0
        baseCubeVertices.addAll(
            listOf(
                Vector3d(-halfSize, -halfSize, halfSize),
                Vector3d(halfSize, -halfSize, halfSize),
                Vector3d(halfSize, halfSize, halfSize),
                Vector3d(-halfSize, halfSize, halfSize),

                Vector3d(-halfSize, -halfSize, -halfSize),
                Vector3d(halfSize, -halfSize, -halfSize),
                Vector3d(halfSize, halfSize, -halfSize),
                Vector3d(-halfSize, halfSize, -halfSize)
            )
        )

        for (x in 0..8) {
            for (y in 0..8) {
                for (z in 0..8) {
                    if (GRID_MAP[x][y][z] == 1) {
                        val initialPos = Vector3d(
                            (x - 4.0) * (cubeSize + spacing),
                            (y - 4.0) * (cubeSize + spacing),
                            (z - 4.0) * (cubeSize + spacing)
                        )
                        val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
                        cubes.add(TransformedCube(Cube(Color.LIGHT_GRAY, baseCubeVertices), transformMatrix = translationMatrix))
                    }
                }
            }
        }

        Timer(16) {
            updateCameraPosition()
            requestRender()
        }.start()
    }

    private fun updateCameraPosition() {
        val lookDirection = Vector3d(
            cos(cameraPitch) * sin(cameraYaw),
            sin(cameraPitch),
            cos(cameraPitch) * cos(cameraYaw)
        ).normalize()

        val upVector = Vector3d(0.0, 1.0, 0.0)
        val rightVector = lookDirection.cross(upVector).normalize()
        val forwardVector = lookDirection

        if (pressedKeys.contains(KeyEvent.VK_W)) cameraPosition += forwardVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_S)) cameraPosition -= forwardVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_D)) cameraPosition += rightVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_A)) cameraPosition -= rightVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_SPACE)) cameraPosition += Vector3d(0.0, movementSpeed, 0.0)
        if (pressedKeys.contains(KeyEvent.VK_CONTROL)) cameraPosition -= Vector3d(0.0, movementSpeed, 0.0)
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) cameraYaw += rotationSpeed*2
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) cameraYaw -= rotationSpeed*2
        if (pressedKeys.contains(KeyEvent.VK_DOWN) && (cameraPitch > -1)) cameraPitch -= rotationSpeed*2
        if (pressedKeys.contains(KeyEvent.VK_UP) && (cameraPitch < 1)) cameraPitch += rotationSpeed*2
    }

    private fun requestRender() {
        if (virtualWidth <= 0 || virtualHeight <= 0) return

        if (depthBuffer.size != virtualWidth || depthBuffer[0].size != virtualHeight) {
            depthBuffer = Array(virtualWidth) { DoubleArray(virtualHeight) { Double.MAX_VALUE } }
            backBuffer = BufferedImage(virtualWidth, virtualHeight, BufferedImage.TYPE_INT_RGB)
        }
        for (x in 0 until virtualWidth) {
            for (y in 0 until virtualHeight) {
                depthBuffer[x][y] = Double.MAX_VALUE
            }
        }
        val g2dBack = backBuffer.graphics as Graphics2D
        g2dBack.color = Color(40, 40, 40)
        g2dBack.fillRect(0, 0, virtualWidth, virtualHeight)
        g2dBack.dispose()


        renderQueue.clear()

        val lookDirection = Vector3d(
            cos(cameraPitch) * sin(cameraYaw),
            sin(cameraPitch),
            cos(cameraPitch) * cos(cameraYaw)
        ).normalize()

        val upVector = Vector3d(0.0, 1.0, 0.0)

        val viewMatrix = Matrix4x4.lookAt(cameraPosition, cameraPosition + lookDirection, upVector)

        val fov = 90.0
        val aspectRatio = virtualWidth.toDouble() / virtualHeight.toDouble()
        val near = 0.1
        val far = 5000.0

        val projectionMatrix = Matrix4x4.perspective(fov, aspectRatio, near, far)

        val combinedMatrix = projectionMatrix * viewMatrix

        val tasks = mutableListOf<Future<*>>()

        for (transformedCube in cubes) {
            tasks.add(executor.submit {
                val transformedVerticesHomogeneous = transformedCube.cube.vertices.map {
                    combinedMatrix.transformHomogeneous(transformedCube.transformMatrix.transform(it))
                }

                for (faceIndices in transformedCube.cube.faces) {
                    val p0Homogeneous = transformedVerticesHomogeneous[faceIndices[0]]
                    val p1Homogeneous = transformedVerticesHomogeneous[faceIndices[1]]
                    val p2Homogeneous = transformedVerticesHomogeneous[faceIndices[2]]

                    if (p0Homogeneous.w.isCloseToZero() || p1Homogeneous.w.isCloseToZero() || p2Homogeneous.w.isCloseToZero()) continue

                    val p0View = viewMatrix.transform(transformedCube.transformMatrix.transform(transformedCube.cube.vertices[faceIndices[0]]))
                    val p1View = viewMatrix.transform(transformedCube.transformMatrix.transform(transformedCube.cube.vertices[faceIndices[1]]))
                    val p2View = viewMatrix.transform(transformedCube.transformMatrix.transform(transformedCube.cube.vertices[faceIndices[2]]))

                    val normalView = (p1View - p0View).cross(p2View - p0View).normalize()
                    val cameraRayView = (Vector3d(0.0, 0.0, 0.0) - p0View).normalize()

                    if (normalView.dot(cameraRayView) > 0) {
                        val projectedVerticesForFace = mutableListOf<Vector3d>()
                        var anyVertexInFrustum = false

                        for (index in faceIndices) {
                            val projectedHomogeneous = transformedVerticesHomogeneous[index]

                            val w = projectedHomogeneous.w
                            if (w.isCloseToZero() || w < near || w > far) {
                                projectedVerticesForFace.add(Vector3d(Double.NaN, Double.NaN, Double.NaN))
                            } else {
                                val xClip = projectedHomogeneous.x / w
                                val yClip = projectedHomogeneous.y / w
                                val zClip = projectedHomogeneous.z / w

                                if (xClip >= -1.0 && xClip <= 1.0 && yClip >= -1.0 && yClip <= 1.0 && zClip >= -1.0 && zClip <= 1.0) {
                                    anyVertexInFrustum = true
                                }

                                projectedVerticesForFace.add(
                                    Vector3d(
                                        (xClip + 1) * virtualWidth / 2.0,
                                        (1 - yClip) * virtualHeight / 2.0,
                                        zClip
                                    )
                                )
                            }
                        }

                        val allVerticesRejected = projectedVerticesForFace.all { it.x.isNaN() }
                        if (!allVerticesRejected && anyVertexInFrustum) {
                            val drawableVertices = projectedVerticesForFace.filter { !it.x.isNaN() }

                            if (drawableVertices.size >= 3) {
                                renderQueue.add(RenderableFace(listOf(drawableVertices[0], drawableVertices[1], drawableVertices[2]), transformedCube.cube.color, false))
                                if (drawableVertices.size == 4) {
                                    renderQueue.add(RenderableFace(listOf(drawableVertices[0], drawableVertices[2], drawableVertices[3]), transformedCube.cube.color, false))
                                }

                                val edgeColor = Color(40, 40, 40)
                                for (i in 0 until drawableVertices.size) {
                                    val p1 = drawableVertices[i]
                                    val p2 = drawableVertices[(i + 1) % drawableVertices.size]
                                    renderQueue.add(RenderableFace(listOf(p1, p2), edgeColor, true))
                                }
                            }
                        }
                    }
                }
            })
        }

        SwingUtilities.invokeLater {
            for (task in tasks) {
                task.get()
            }
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2dBack = backBuffer.graphics as Graphics2D
        g2dBack.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        while (renderQueue.isNotEmpty()) {
            val renderableFace = renderQueue.poll()
            if (renderableFace.isEdge) {
                if (renderableFace.screenVertices.size == 2) {
                    drawLineZBuffered(g2dBack, renderableFace.screenVertices[0].x.toInt(), renderableFace.screenVertices[0].y.toInt(), renderableFace.screenVertices[0].z,
                        renderableFace.screenVertices[1].x.toInt(), renderableFace.screenVertices[1].y.toInt(), renderableFace.screenVertices[1].z,
                        renderableFace.color, virtualWidth, virtualHeight)
                }
            } else {
                rasterizeTriangle(g2dBack, renderableFace.screenVertices, renderableFace.color, virtualWidth, virtualHeight)
            }
        }
        g2dBack.dispose()

        val g2d = g as Graphics2D
        g2d.drawImage(backBuffer, 0, 0, width, height, null)
        g2d.color = Color.WHITE
        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][y] == 1) {
                        g2d.drawOval(x * 10, y * 10 + zLevel * 90 + zLevel * 10, 10, 10)
                    }
                }
            }
        }
    }

    data class ScreenVertex(val x: Int, val y: Int, val z: Double)

    private fun rasterizeTriangle(g2d: Graphics2D, screenVertices: List<Vector3d>, color: Color, screenWidth: Int, screenHeight: Int) {
        if (screenVertices.size != 3) return

        val v0 = ScreenVertex(screenVertices[0].x.toInt(), screenVertices[0].y.toInt(), screenVertices[0].z)
        val v1 = ScreenVertex(screenVertices[1].x.toInt(), screenVertices[1].y.toInt(), screenVertices[1].z)
        val v2 = ScreenVertex(screenVertices[2].x.toInt(), screenVertices[2].y.toInt(), screenVertices[2].z)

        var minX = minOf(v0.x, v1.x, v2.x)
        var maxX = maxOf(v0.x, v1.x, v2.x)
        var minY = minOf(v0.y, v1.y, v2.y)
        var maxY = maxOf(v0.y, v1.y, v2.y)

        minX = max(0, minX)
        maxX = min(screenWidth - 1, maxX)
        minY = max(0, minY)
        maxY = min(screenHeight - 1, maxY)

        g2d.color = color

        val a12 = (v1.y - v2.y).toDouble()
        val b12 = (v2.x - v1.x).toDouble()
        val c12 = (v1.x * v2.y - v2.x * v1.y).toDouble()

        val a20 = (v2.y - v0.y).toDouble()
        val b20 = (v0.x - v2.x).toDouble()
        val c20 = (v2.x * v0.y - v0.x * v2.y).toDouble()

        val a01 = (v0.y - v1.y).toDouble()
        val b01 = (v1.x - v0.x).toDouble()
        val c01 = (v0.x * v1.y - v1.x * v0.y).toDouble()

        val totalArea = a12 * v0.x + b12 * v0.y + c12
        if (totalArea.isCloseToZero()) return

        val invTotalArea = 1.0 / totalArea

        var w0Row = a12 * minX + b12 * minY + c12
        var w1Row = a20 * minX + b20 * minY + c20
        var w2Row = a01 * minX + b01 * minY + c01

        for (py in minY..maxY) {
            var w0 = w0Row
            var w1 = w1Row
            var w2 = w2Row

            for (px in minX..maxX) {
                val epsilon = 0.00001
                val isInside = (w0 >= -epsilon && w1 >= -epsilon && w2 >= -epsilon) ||
                        (w0 <= epsilon && w1 <= epsilon && w2 <= epsilon)

                if (isInside) {
                    val alpha = w0 * invTotalArea
                    val beta = w1 * invTotalArea
                    val gamma = w2 * invTotalArea

                    val interpolatedZ = alpha * v0.z + beta * v1.z + gamma * v2.z

                    if (px in 0 until screenWidth && py in 0 until screenHeight && interpolatedZ < depthBuffer[px][py]) {
                        g2d.fillRect(px, py, 1, 1)
                        depthBuffer[px][py] = interpolatedZ
                    }
                }
                w0 += a12
                w1 += a20
                w2 += a01
            }
            w0Row += b12
            w1Row += b20
            w2Row += b01
        }
    }

    private fun drawLineZBuffered(g2d: Graphics2D, x0: Int, y0: Int, z0: Double, x1: Int, y1: Int, z1: Double, color: Color, screenWidth: Int, screenHeight: Int) {
        var currX0 = x0
        var currY0 = y0
        val dx = abs(x1 - currX0)
        val dy = abs(y1 - currY0)
        val sx = if (currX0 < x1) 1 else -1
        val sy = if (currY0 < y1) 1 else -1

        var err = dx - dy

        g2d.color = color

        val lineLengthSq = (x1 - x0).toDouble() * (x1 - x0) + (y1 - y0).toDouble() * (y1 - y0)
        val invLineLength = if (lineLengthSq == 0.0) 0.0 else 1.0 / sqrt(lineLengthSq)

        while (true) {
            val t = sqrt((currX0 - x0).toDouble() * (currX0 - x0) + (currY0 - y0).toDouble() * (currY0 - y0)) * invLineLength
            val interpolatedZ = z0 + t * (z1 - z0)

            if (currX0 in 0 until screenWidth && currY0 in 0 until screenHeight && interpolatedZ < depthBuffer[currX0][currY0]) {
                g2d.fillRect(currX0, currY0, 1, 1)
                depthBuffer[currX0][currY0] = interpolatedZ
            }

            if (currX0 == x1 && currY0 == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                currX0 += sx
            }
            if (e2 < dx) {
                err += dx
                currY0 += sy
            }
        }
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            e?.keyCode?.let { pressedKeys.add(it) }
        }

        override fun keyReleased(e: KeyEvent?) {
            e?.keyCode?.let { pressedKeys.remove(it) }
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("FileZero.kt").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            val panel = DrawingPanel()
            panel.preferredSize = java.awt.Dimension(1920/2, 1080/2)
            add(panel)
            pack()
            setLocationRelativeTo(null)
            isVisible = true
            iconImage = Toolkit.getDefaultToolkit().getImage(DrawingPanel::class.java.classLoader.getResource("textures/icon.jpeg"))
        }
    }
}