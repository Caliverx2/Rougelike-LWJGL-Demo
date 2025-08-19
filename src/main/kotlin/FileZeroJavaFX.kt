package org.lewapnoob.FileZero2

import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.stage.Stage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vector3d(var x: Double, var y: Double, var z: Double) {
    operator fun plus(other: Vector3d) = Vector3d(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3d) = Vector3d(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vector3d(x / scalar, y / scalar, z / scalar)

    fun lerp(other: Vector3d, alpha: Double): Vector3d {
        return this * (1.0 - alpha) + other * alpha
    }
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

data class RenderableFace(
    val screenVertices: List<Vector3d>,
    val originalClipW: List<Double>,
    val textureVertices: List<Vector3d>,
    val color: Color,
    val isEdge: Boolean,
    val texture: Image? = null,
    val worldVertices: List<Vector3d> = listOf(),
    val lightGrid: Array<Array<Color>>? = null
)

data class Mesh(
    val vertices: List<Vector3d>,
    val faces: List<List<Int>>,
    val faceUVs: List<List<Vector3d>>,
    val color: Color,
    val faceTextureNames: Map<Int, String> = emptyMap()
)

class PlacedMesh(
    val mesh: Mesh,
    var transformMatrix: Matrix4x4 = Matrix4x4.identity(),
    val texture: Image? = null,
    val faceTextures: Map<Int, Image> = emptyMap(),
    var collision: Boolean = true,
    var collisionPos: Vector3d = Vector3d(0.0,0.0,0.0)
) {
    fun getTransformedVertices(): List<Vector3d> =
        mesh.vertices.map { transformMatrix.transform(it) }

    fun applyTransform(matrix: Matrix4x4) {
        transformMatrix = matrix * transformMatrix
    }
}

data class AABB(val min: Vector3d, val max: Vector3d) {
    companion object {
        fun fromCube(cubeVertices: List<Vector3d>): AABB {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var minZ = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            var maxZ = Double.NEGATIVE_INFINITY

            for (v in cubeVertices) {
                minX = minOf(minX, v.x)
                minY = minOf(minY, v.y)
                minZ = minOf(minZ, v.z)
                maxX = maxOf(maxX, v.x)
                maxY = maxOf(maxY, v.y)
                maxZ = maxOf(maxZ, v.z)
            }
            return AABB(Vector3d(minX, minY, minZ), Vector3d(maxX, maxY, maxZ))
        }
    }

    fun intersects(other: AABB): Boolean {
        return (this.min.x <= other.max.x && this.max.x >= other.min.x) &&
                (this.min.y <= other.max.y && this.max.y >= other.min.y) &&
                (this.min.z <= other.max.z && this.max.z >= other.min.z)
    }
}

object CollisionUtils {
    private fun getInterval(aabb: AABB, axis: Vector3d): Pair<Double, Double> {
        val center = (aabb.min + aabb.max) * 0.5
        val extents = (aabb.max - aabb.min) * 0.5

        val c = center.dot(axis)
        val e = extents.x * abs(axis.x) + extents.y * abs(axis.y) + extents.z * abs(axis.z)

        return Pair(c - e, c + e)
    }

    private fun getInterval(v0: Vector3d, v1: Vector3d, v2: Vector3d, axis: Vector3d): Pair<Double, Double> {
        val d0 = v0.dot(axis)
        val d1 = v1.dot(axis)
        val d2 = v2.dot(axis)

        return Pair(minOf(d0, d1, d2), maxOf(d0, d1, d2))
    }

    fun testAABBTriangle(aabb: AABB, v0: Vector3d, v1: Vector3d, v2: Vector3d): Boolean {
        // Separating Axis Theorem
        val axes = mutableListOf<Vector3d>()

        // axes world
        axes.add(Vector3d(1.0, 0.0, 0.0))
        axes.add(Vector3d(0.0, 1.0, 0.0))
        axes.add(Vector3d(0.0, 0.0, 1.0))

        val edge0 = v1 - v0
        val edge1 = v2 - v1
        val triNormal = edge0.cross(edge1).normalize()
        axes.add(triNormal)

        val triEdges = listOf(edge0, edge1, v0 - v2)
        val boxAxes = listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))

        for (edge in triEdges) {
            for (boxAxis in boxAxes) {
                val crossProduct = edge.cross(boxAxis).normalize()
                if (crossProduct.length() > 1e-6) {
                    axes.add(crossProduct)
                }
            }
        }

        for (axis in axes) {
            val aabbInterval = getInterval(aabb, axis)
            val triInterval = getInterval(v0, v1, v2, axis)

            if (aabbInterval.second < triInterval.first || triInterval.second < aabbInterval.first) {
                return false
            }
        }

        return true
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
    intArrayOf(1, 1, 0, 1, 2, 1, 0, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 0, 1, 3, 1, 0, 1, 1)
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
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 1),
    intArrayOf(1, 2, 0, 0, 0, 0, 0, 2, 1),
    intArrayOf(1, 2, 0, 0, 0, 0, 0, 2, 1),
    intArrayOf(1, 2, 0, 0, 3, 0, 0, 2, 1),
    intArrayOf(1, 2, 0, 0, 0, 0, 0, 2, 1),
    intArrayOf(1, 2, 0, 0, 0, 0, 0, 2, 1),
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
)

val GRID_MAP: Array<Array<Array<Int>>> = Array(18) { Array(18) { Array(18) { 0 } } }

enum class LightType {
    RAYTRACED,
    VERTEX
}

data class LightSource(
    val position: Vector3d,
    val radius: Double,
    val color: Color,
    val intensity: Double = 1.0,
    val type: LightType = LightType.RAYTRACED
)

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        primaryStage.title = "FileZero.kt"
        val panel = DrawingPanel()
        val scene = Scene(panel, 1920.0/2, 1080.0/2)

        scene.setOnKeyPressed { event ->
            panel.pressedKeys.add(event.code)
        }
        scene.setOnKeyReleased { event ->
            panel.handleKeyRelease(event.code)
            panel.pressedKeys.remove(event.code)
        }

        primaryStage.scene = scene
        primaryStage.icons.add(Image(DrawingPanel::class.java.classLoader.getResourceAsStream("textures/icon.jpeg")))
        primaryStage.show()
    }
}

fun main(args: Array<String>) {
    Application.launch(MainApp::class.java, *args)
}