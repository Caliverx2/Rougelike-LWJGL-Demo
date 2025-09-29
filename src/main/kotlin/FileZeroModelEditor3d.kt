package org.lewapnoob.FileZeroModel3DEditor

import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Modality
import javafx.stage.Stage
import kotlin.math.*

data class Vector3d(var x: Double, var y: Double, var z: Double)
fun Vector3d.subtract(other: Vector3d) = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
fun Vector3d.add(other: Vector3d) = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
fun Vector3d.scale(factor: Double) = Vector3d(this.x * factor, this.y * factor, this.z * factor)
fun Vector3d.cross(other: Vector3d): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vector3d.length(): Double = sqrt(x*x + y*y + z*z)
fun Vector3d.normalize(): Vector3d {
    val len = this.length()
    return if (len > 0.00001) Vector3d(x / len, y / len, z / len) else Vector3d(0.0, 0.0, 0.0)
}

data class Edge(val a: Int, val b: Int)
data class Face(val indices: List<Int>)
data class Mesh(val vertices: List<Vector3d>, val faces: List<List<Int>>, val faceUVs: List<List<Vector3d>>, val color: Color)
data class AABB(var min: Vector3d, var max: Vector3d) {
    fun getCorners(): List<Vector3d> {
        return listOf(
            Vector3d(min.x, min.y, min.z),
            Vector3d(max.x, min.y, min.z),
            Vector3d(min.x, max.y, min.z),
            Vector3d(max.x, max.y, min.z),
            Vector3d(min.x, min.y, max.z),
            Vector3d(max.x, min.y, max.z),
            Vector3d(min.x, max.y, max.z),
            Vector3d(max.x, max.y, max.z)
        )
    }
    fun getCenter(): Vector3d = Vector3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
    fun translate(delta: Vector3d) {
        min.x += delta.x; min.y += delta.y; min.z += delta.z
        max.x += delta.x; max.y += delta.y; max.z += delta.z
    }
}
enum class BlushCornerType { MIN, MAX }

class ModelEditor : Application() {
    private val vertices = mutableListOf<Vector3d>()
    private val edges = mutableListOf<Edge>()
    private val faces = mutableListOf<Face>()
    private val faceTextures = mutableMapOf<Int, String>()
    private val blushes = mutableListOf<AABB>()

    private data class CopiedModelPart(
        val vertices: List<Vector3d>,
        val edges: List<Edge>,
        val faces: List<Face>,
        val faceTextures: Map<Int, String>
    )
    private var clipboardModelPart: CopiedModelPart? = null
    private var clipboardBlush: AABB? = null

    private var selectedVertex: Int? = null
    private val groupSelectedVertices = mutableSetOf<Int>()
    private var groupGizmoPosition: Vector3d? = null
    private val selectedForFace = mutableListOf<Int>()
    private var selectedBlushIndex: Int? = null
    private var selectedBlushCorner: BlushCornerType? = null
    private var selectedFaceIndex: Int? = null

    private var dragStartVertex: Int? = null

    private var angleX = -2.4
    private var angleY = 0.2
    private var zoom = 300.0

    private var deleteMode = false
    private var faceSelectMode = false // tryb Q
    private var blushMode = false // tryb B

    // Gizmo
    private var gizmoAxis: Char? = null
    private var dragStartX: Double = 0.0
    private var dragStartY: Double = 0.0
    private var dragStartVertexPos: Vector3d? = null
    private var dragStartGroupPositions: Map<Int, Vector3d>? = null
    private var dragStartBlushCornerPos: Vector3d? = null
    private val gizmoStartOffset = 10.0
    private val gizmoEndOffset = 30.0

    private var isBoxSelecting = false
    private var boxSelectStartX = 0.0
    private var boxSelectStartY = 0.0
    private var boxSelectCurrentX = 0.0
    private var boxSelectCurrentY = 0.0
    private var lastSubdivideTime = 0L

    override fun start(stage: Stage) {
        val canvas = Canvas((1920.0 * 2) / 3, (1080.0 * 2) / 3)
        val gc = canvas.graphicsContext2D

        canvas.setOnMousePressed { e ->
            val w = canvas.width
            val h = canvas.height

            val gizmoOrigin = when {
                blushMode && selectedBlushIndex != null && selectedBlushCorner != null -> {
                    val blush = blushes[selectedBlushIndex!!]
                    if (selectedBlushCorner == BlushCornerType.MIN) blush.min else blush.max
                }
                blushMode && selectedBlushIndex != null -> blushes.getOrNull(selectedBlushIndex!!)?.getCenter()
                groupSelectedVertices.isNotEmpty() -> groupGizmoPosition
                selectedVertex != null -> vertices.getOrNull(selectedVertex!!)
                else -> null
            }

            if (e.button == MouseButton.PRIMARY && gizmoOrigin != null) {
                val axis = findClosestGizmoAxis(e.x, e.y, w, h)
                if (axis != null) {
                    gizmoAxis = axis
                    dragStartX = e.x
                    dragStartY = e.y
                    if (blushMode && selectedBlushIndex != null && selectedBlushCorner != null) {
                        dragStartBlushCornerPos = gizmoOrigin.copy()
                    } else if (blushMode && selectedBlushIndex != null) {
                        dragStartVertexPos = gizmoOrigin.copy()
                    } else if (groupSelectedVertices.isNotEmpty()) {
                        dragStartGroupPositions = groupSelectedVertices.associateWith { vertices[it].copy() }
                        dragStartVertexPos = groupGizmoPosition!!.copy()
                    } else {
                        dragStartVertexPos = vertices[selectedVertex!!].copy()
                    }
                    draw(gc, w, h, e.x, e.y)
                    return@setOnMousePressed
                }
            }

            if (blushMode) {
                val clickedCorner = findBlushCornerUnderCursor(e.x, e.y, w, h)
                if (clickedCorner != null) {
                    selectedBlushIndex = clickedCorner.first
                    selectedBlushCorner = clickedCorner.second
                } else {
                    selectedBlushIndex = findBlushUnderCursor(e.x, e.y, w, h)
                    selectedBlushCorner = null
                }

                selectedVertex = null
                groupSelectedVertices.clear()
                draw(gc, w, h, e.x, e.y)
                return@setOnMousePressed
            }

            val clickedVertex = findClosestVertex(e.x, e.y, w, h)

            if (faceSelectMode && e.button == MouseButton.PRIMARY && clickedVertex != null) {
                if (!selectedForFace.contains(clickedVertex)) {
                    selectedForFace.add(clickedVertex)
                }
                draw(gc, w, h, e.x, e.y)
                return@setOnMousePressed
            }

            if (deleteMode && e.button == MouseButton.PRIMARY && clickedVertex == null) {
                val faceResult = findFaceUnderCursor(e.x, e.y, w, h)
                if (faceResult != null) {
                    faces.removeAt(faceResult.first)
                }
                draw(gc, w, h, e.x, e.y)
                return@setOnMousePressed
            }

            if (e.button == MouseButton.PRIMARY) {
                selectedVertex = null
                groupSelectedVertices.clear()
                groupGizmoPosition = null
                selectedBlushIndex = null
                selectedBlushCorner = null
                dragStartVertex = null
                selectedFaceIndex = null

                if (clickedVertex != null) {
                    selectedVertex = clickedVertex
                    dragStartVertex = clickedVertex
                } else {
                    val closestFaceResult = findFaceUnderCursor(e.x, e.y, w, h)
                    val closestEdgeResult = findClosestEdge(e.x, e.y, w, h)

                    val faceDepth = closestFaceResult?.second ?: Double.MAX_VALUE
                    val edgeDepth = closestEdgeResult?.second ?: Double.MAX_VALUE

                    if (faceDepth < edgeDepth && faceDepth != Double.MAX_VALUE) {
                        selectedFaceIndex = closestFaceResult!!.first
                        val face = faces[selectedFaceIndex!!]
                        groupSelectedVertices.addAll(face.indices)
                    } else if (edgeDepth != Double.MAX_VALUE) {
                        val edge = edges[closestEdgeResult!!.first]
                        groupSelectedVertices.add(edge.a)
                        groupSelectedVertices.add(edge.b)
                    }

                    if (groupSelectedVertices.isNotEmpty()) {
                        var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
                        groupSelectedVertices.forEach { val v = vertices[it]; sumX += v.x; sumY += v.y; sumZ += v.z }
                        val count = groupSelectedVertices.size
                        groupGizmoPosition = Vector3d(sumX / count, sumY / count, sumZ / count)
                    } else {
                        isBoxSelecting = true
                        boxSelectStartX = e.x
                        boxSelectStartY = e.y
                        boxSelectCurrentX = e.x
                        boxSelectCurrentY = e.y
                    }
                }
            }
            draw(gc, w, h, e.x, e.y)
        }

        canvas.setOnMouseDragged { e ->
            if (gizmoAxis != null) {
                val w = canvas.width
                val h = canvas.height

                val originPos = dragStartBlushCornerPos ?: dragStartVertexPos ?: return@setOnMouseDragged

                val axisUnitVector = when(gizmoAxis) {
                    'X' -> Vector3d(-1.0, 0.0, 0.0)
                    'Y' -> Vector3d(0.0, 1.0, 0.0)
                    'Z' -> Vector3d(0.0, 0.0, -1.0)
                    else -> return@setOnMouseDragged
                }
                val axisEndPoint = Vector3d(originPos.x + axisUnitVector.x, originPos.y + axisUnitVector.y, originPos.z + axisUnitVector.z)

                val originProjected = project(originPos, w, h)
                val axisEndProjected = project(axisEndPoint, w, h)

                if (!originProjected.first.isFinite() || !axisEndProjected.first.isFinite()) return@setOnMouseDragged

                val screenAxisVx = axisEndProjected.first - originProjected.first
                val screenAxisVy = axisEndProjected.second - originProjected.second

                val mouseDx = e.x - dragStartX
                val mouseDy = e.y - dragStartY

                val screenAxisLenSq = screenAxisVx * screenAxisVx + screenAxisVy * screenAxisVy
                if (screenAxisLenSq < 1e-6) return@setOnMouseDragged

                val dotProduct = mouseDx * screenAxisVx + mouseDy * screenAxisVy
                val movementScale = dotProduct / screenAxisLenSq

                val sensitivity = 1.0
                val moveAmount = movementScale * sensitivity

                val moveVector = Vector3d(axisUnitVector.x * moveAmount, axisUnitVector.y * moveAmount, axisUnitVector.z * moveAmount)

                if (blushMode && selectedBlushIndex != null && selectedBlushCorner != null && dragStartBlushCornerPos != null) {
                    val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                    corner.x = dragStartBlushCornerPos!!.x + moveVector.x
                    corner.y = dragStartBlushCornerPos!!.y + moveVector.y
                    corner.z = dragStartBlushCornerPos!!.z + moveVector.z
                } else if (blushMode && selectedBlushIndex != null) {
                    val blush = blushes[selectedBlushIndex!!]
                    val center = blush.getCenter()
                    val newCenter = Vector3d(originPos.x + moveVector.x, originPos.y + moveVector.y, originPos.z + moveVector.z)
                    blush.translate(Vector3d(newCenter.x - center.x, newCenter.y - center.y, newCenter.z - center.z))
                } else if (groupSelectedVertices.isNotEmpty() && dragStartGroupPositions != null) {
                    groupSelectedVertices.forEach { index ->
                        val startPos = dragStartGroupPositions!![index]
                        if (startPos != null) {
                            vertices[index] = Vector3d(startPos.x + moveVector.x, startPos.y + moveVector.y, startPos.z + moveVector.z)
                        }
                    }
                    groupGizmoPosition = Vector3d(originPos.x + moveVector.x, originPos.y + moveVector.y, originPos.z + moveVector.z)
                } else if (selectedVertex != null) {
                    vertices[selectedVertex!!] = Vector3d(originPos.x + moveVector.x, originPos.y + moveVector.y, originPos.z + moveVector.z)
                }

                draw(gc, canvas.width, canvas.height, e.x, e.y)
            } else if (isBoxSelecting) {
                boxSelectCurrentX = e.x
                boxSelectCurrentY = e.y
                draw(gc, canvas.width, canvas.height, e.x, e.y)
            }
        }

        canvas.setOnMouseReleased { e ->
            if (gizmoAxis != null) {
                if (blushMode && selectedBlushIndex != null && selectedBlushCorner != null) {
                    val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                    corner.x = round(corner.x)
                    corner.y = round(corner.y)
                    corner.z = round(corner.z)
                } else if (blushMode && selectedBlushIndex != null) {
                    blushes.getOrNull(selectedBlushIndex!!)?.let { blush ->
                        blush.min.x = round(blush.min.x)
                        blush.min.y = round(blush.min.y)
                        blush.min.z = round(blush.min.z)
                        blush.max.x = round(blush.max.x)
                        blush.max.y = round(blush.max.y)
                        blush.max.z = round(blush.max.z)
                    }
                } else if (groupSelectedVertices.isNotEmpty()) {
                    groupSelectedVertices.forEach { index ->
                        vertices.getOrNull(index)?.let { v ->
                            v.x = round(v.x)
                            v.y = round(v.y)
                            v.z = round(v.z)
                        }
                    }
                    groupGizmoPosition?.let {
                        it.x = round(it.x)
                        it.y = round(it.y)
                        it.z = round(it.z)
                    }
                } else if (selectedVertex != null) {
                    vertices.getOrNull(selectedVertex!!)?.let { v ->
                        v.x = round(v.x); v.y = round(v.y); v.z = round(v.z)
                    }
                }
            } else if (isBoxSelecting) {
                isBoxSelecting = false
                val dragDistance = hypot(e.x - boxSelectStartX, e.y - boxSelectStartY)
                val clickThreshold = 5.0

                if (dragDistance < clickThreshold) {
                    vertices.add(Vector3d(0.0, 0.0, 0.0))
                    selectedVertex = vertices.size - 1
                    groupSelectedVertices.clear()
                    groupGizmoPosition = null
                    selectedFaceIndex = null
                } else {
                    val x1 = min(boxSelectStartX, boxSelectCurrentX)
                    val y1 = min(boxSelectStartY, boxSelectCurrentY)
                    val x2 = max(boxSelectStartX, boxSelectCurrentX)
                    val y2 = max(boxSelectStartY, boxSelectCurrentY)

                    val w = canvas.width
                    val h = canvas.height

                    val selectedInBox = vertices.withIndex().filter { (_, v) ->
                        val p = project(v, w, h)
                        p.first.isFinite() && p.second.isFinite() &&
                                p.first >= x1 && p.first <= x2 &&
                                p.second >= y1 && p.second <= y2
                    }.map { it.index }

                    groupSelectedVertices.addAll(selectedInBox)

                    if (groupSelectedVertices.isNotEmpty()) {
                        var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
                        groupSelectedVertices.forEach { val v = vertices[it]; sumX += v.x; sumY += v.y; sumZ += v.z }
                        val count = groupSelectedVertices.size
                        groupGizmoPosition = Vector3d(sumX / count, sumY / count, sumZ / count)
                    }
                }
            }

            gizmoAxis = null
            dragStartVertexPos = null
            dragStartGroupPositions = null
            dragStartBlushCornerPos = null

            val releasedVertex = findClosestVertex(e.x, e.y, canvas.width, canvas.height)
            if (dragStartVertex != null && releasedVertex != null && releasedVertex != dragStartVertex) {
                if (edges.none { (it.a == dragStartVertex && it.b == releasedVertex) || (it.a == releasedVertex && it.b == dragStartVertex) }) {
                    edges.add(Edge(dragStartVertex!!, releasedVertex))
                }
            }
            dragStartVertex = null
            draw(gc, canvas.width, canvas.height, e.x, e.y)
        }

        val scene = Scene(StackPane(canvas))
        scene.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.W -> angleX -= 0.1
                KeyCode.S -> angleX += 0.1
                KeyCode.A -> angleY -= 0.1
                KeyCode.D -> angleY += 0.1

                KeyCode.PERIOD -> if (zoom < 1000) zoom += 25
                KeyCode.COMMA -> if (zoom > 25) zoom -= 25
                KeyCode.DIGIT1 -> addCubeAtOrigin()
                KeyCode.DIGIT2 -> addPyramidAtOrigin()
                KeyCode.DIGIT3 -> addPlaneAtOrigin()
                KeyCode.DIGIT4 -> addCapsuleAtOrigin()

                KeyCode.B -> {
                    blushMode = !blushMode
                    faceSelectMode = false
                    deleteMode = false
                    selectedVertex = null
                    groupSelectedVertices.clear()
                    if (!blushMode) {
                        selectedBlushIndex = null
                    }
                }

                KeyCode.Q -> {
                    faceSelectMode = !faceSelectMode
                    blushMode = false
                    deleteMode = false
                    selectedVertex = null
                    if (!faceSelectMode) {
                        selectedForFace.clear()
                    }
                }

                KeyCode.F -> {
                    if (selectedForFace.size >= 3) {
                        faces.add(Face(selectedForFace.toList()))
                        selectedForFace.clear()
                    }
                }
                KeyCode.DELETE -> {
                    if (blushMode && selectedBlushIndex != null) {
                        blushes.removeAt(selectedBlushIndex!!)
                        selectedBlushIndex = null
                        selectedBlushCorner = null
                    } else if (selectedFaceIndex != null) {
                        val removedIndex = selectedFaceIndex
                        faces.removeAt(removedIndex!!)

                        val newFaceTextures = mutableMapOf<Int, String>()
                        faceTextures.forEach { (index, name) ->
                            if (index > removedIndex) {
                                newFaceTextures[index - 1] = name
                            } else if (index < removedIndex) {
                                newFaceTextures[index] = name
                            }
                        }
                        faceTextures.clear()
                        faceTextures.putAll(newFaceTextures)

                        selectedFaceIndex = null
                        groupSelectedVertices.clear()
                        groupGizmoPosition = null

                    } else if (selectedVertex != null) {
                        deleteSelectedVertex()
                    } else {
                        deleteMode = !deleteMode
                        faceSelectMode = false
                        blushMode = false
                    }
                }
                KeyCode.R -> {
                    val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                    verticesToTransform.forEach { index ->
                        val v = vertices[index]
                        v.x = v.x.roundToInt().toDouble()
                        v.y = v.y.roundToInt().toDouble()
                        v.z = v.z.roundToInt().toDouble()
                    }
                }

                KeyCode.T -> showTexturePalette(gc)

                KeyCode.O -> {
                    val newBlush = AABB(min = Vector3d(-25.0, -25.0, -25.0), max = Vector3d(25.0, 25.0, 25.0))
                    blushes.add(newBlush)
                    selectedBlushIndex = blushes.lastIndex
                    selectedBlushCorner = null
                    blushMode = true
                    deleteMode = false
                    faceSelectMode = false
                }

                KeyCode.UP -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.z -= 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(0.0, 0.0, -10.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].z -= 10.0 }
                        groupGizmoPosition?.let { it.z -= 10.0 }
                    }
                }
                KeyCode.DOWN -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.z += 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(0.0, 0.0, 10.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].z += 10.0 }
                        groupGizmoPosition?.let { it.z += 10.0 }
                    }
                }
                KeyCode.LEFT -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.x -= 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(-10.0, 0.0, 0.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].x -= 10.0 }
                        groupGizmoPosition?.let { it.x -= 10.0 }
                    }
                }
                KeyCode.RIGHT -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.x += 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(10.0, 0.0, 0.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].x += 10.0 }
                        groupGizmoPosition?.let { it.x += 10.0 }
                    }
                }
                KeyCode.EQUALS -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.y += 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(0.0, 10.0, 0.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].y += 10.0 }
                        groupGizmoPosition?.let { it.y += 10.0 }
                    }
                }
                KeyCode.MINUS -> {
                    if (blushMode && selectedBlushIndex != null) {
                        if (selectedBlushCorner != null) {
                            val corner = if (selectedBlushCorner == BlushCornerType.MIN) blushes[selectedBlushIndex!!].min else blushes[selectedBlushIndex!!].max
                            corner.y -= 10.0
                        } else {
                            blushes[selectedBlushIndex!!].translate(Vector3d(0.0, -10.0, 0.0))
                        }
                    } else {
                        val verticesToTransform = if (groupSelectedVertices.isNotEmpty()) groupSelectedVertices else selectedVertex?.let { setOf(it) } ?: emptySet()
                        verticesToTransform.forEach { index -> vertices[index].y -= 10.0 }
                        groupGizmoPosition?.let { it.y -= 10.0 }
                    }
                }

                KeyCode.E -> {
                    if (e.isControlDown) {
                        exportMeshFunction()
                    } else {
                        if (selectedFaceIndex != null) {
                            extrudeSelectedFace(10.0)
                        }
                    }
                }
                KeyCode.I -> showImportDialog(gc)
                KeyCode.C -> {
                    if (e.isControlDown) {
                        copySelection()
                    }
                }
                KeyCode.V -> {
                    if (e.isControlDown) {
                        pasteSelection()
                    }
                }
                KeyCode.U -> {
                    subdivideSelection()
                }
                KeyCode.G -> println("$angleX $angleY")
                else -> {}
            }
            draw(gc, canvas.width, canvas.height, 0.0, 0.0)
        }

        stage.setOnCloseRequest {
            System.exit(0)
        }

        stage.title = "FileZero.kt Model 3D Editor"
        stage.icons.add(Image(ModelEditor::class.java.classLoader.getResourceAsStream("textures/icon.jpeg")))
        stage.scene = scene
        stage.show()
        draw(gc, canvas.width, canvas.height, 0.0, 0.0)
    }

    private fun extrudeSelectedFace(distance: Double) {
        val faceIndex = selectedFaceIndex ?: return
        val faceToExtrude = faces.getOrNull(faceIndex) ?: return
        if (faceToExtrude.indices.size < 3) return

        val v0 = vertices[faceToExtrude.indices[0]]
        val v1 = vertices[faceToExtrude.indices[1]]
        val v2 = vertices[faceToExtrude.indices[2]]
        val normal = (v1.subtract(v0)).cross(v2.subtract(v0)).normalize()
        val extrudeVector = normal.scale(distance)

        val oldToNewVertexMap = mutableMapOf<Int, Int>()
        val newVertexIndices = faceToExtrude.indices.map { oldIndex ->
            val oldVertex = vertices[oldIndex]
            val newVertex = oldVertex.add(extrudeVector)
            val newIndex = vertices.size
            vertices.add(newVertex)
            oldToNewVertexMap[oldIndex] = newIndex
            edges.add(Edge(oldIndex, newIndex))

            newIndex
        }

        for (i in faceToExtrude.indices.indices) {
            val p1Old = faceToExtrude.indices[i]
            val p2Old = faceToExtrude.indices[(i + 1) % faceToExtrude.indices.size]
            val p1New = oldToNewVertexMap[p1Old]!!
            val p2New = oldToNewVertexMap[p2Old]!!
            faces.add(Face(listOf(p1Old, p2Old, p2New, p1New)))
        }

        for (i in newVertexIndices.indices) {
            edges.add(Edge(newVertexIndices[i], newVertexIndices[(i + 1) % newVertexIndices.size]))
        }

        faces[faceIndex] = Face(newVertexIndices)

        selectedVertex = null
        groupSelectedVertices.clear()
        groupSelectedVertices.addAll(newVertexIndices)
        val count = groupSelectedVertices.size
        groupGizmoPosition = newVertexIndices.map { vertices[it] }.fold(Vector3d(0.0,0.0,0.0)) { acc, v -> acc.add(v) }.scale(1.0/count)
    }

    private fun deleteSelectedVertex() {
        selectedVertex?.let { index ->
            edges.removeIf { it.a == index || it.b == index }
            faces.removeIf { it.indices.contains(index) }
            vertices.removeAt(index)
            fun fixIndex(oldIdx: Int) = if (oldIdx > index) oldIdx - 1 else oldIdx
            for (i in edges.indices) edges[i] = Edge(fixIndex(edges[i].a), fixIndex(edges[i].b))
            for (i in faces.indices) faces[i] = Face(faces[i].indices.map { fixIndex(it) })
            selectedVertex = null
        }
    }

    private fun getZInViewSpace(v: Vector3d): Double {
        val cosY = cos(angleY); val sinY = sin(angleY)
        val cosX = cos(angleX); val sinX = sin(angleX)
        val zAfterYRot = v.x * sinY + v.z * cosY
        return v.y * sinX + zAfterYRot * cosX
    }

    private fun findClosestVertex(mouseX: Double, mouseY: Double, w: Double, h: Double): Int? {
        var minDist = Double.MAX_VALUE
        var closestIdx: Int? = null
        for ((i, v) in vertices.withIndex()) {
            val p = project(v, w, h)
            if (p.first.isFinite() && p.second.isFinite()) {
                val dist = hypot(mouseX - p.first, mouseY - p.second)
                if (dist < minDist && dist < 10.0) {
                    minDist = dist
                    closestIdx = i
                }
            }
        }
        return closestIdx
    }

    private fun findClosestEdge(mouseX: Double, mouseY: Double, w: Double, h: Double): Pair<Int, Double>? {
        var closestEdgeIndex: Int? = null
        var minAvgZ = Double.MAX_VALUE
        val clickThreshold = 10.0

        for ((i, edge) in edges.withIndex()) {
            if (edge.a >= vertices.size || edge.b >= vertices.size) continue

            val v1 = vertices[edge.a]
            val v2 = vertices[edge.b]

            val p1 = project(v1, w, h)
            val p2 = project(v2, w, h)

            if (p1.first.isFinite() && p1.second.isFinite() && p2.first.isFinite() && p2.second.isFinite()) {
                val dist = pointToLineSegmentDistance(mouseX, mouseY, p1.first, p1.second, p2.first, p2.second)
                if (dist < clickThreshold) {
                    val z1 = getZInViewSpace(v1)
                    val z2 = getZInViewSpace(v2)
                    val avgZ = (z1 + z2) / 2.0

                    if (avgZ < minAvgZ) {
                        minAvgZ = avgZ
                        closestEdgeIndex = i
                    }
                }
            }
        }
        return if (closestEdgeIndex != null) Pair(closestEdgeIndex, minAvgZ) else null
    }

    private fun findFaceUnderCursor(mx: Double, my: Double, w: Double, h: Double): Pair<Int, Double>? {
        var closestFace: Pair<Int, Double>? = null
        faces.forEachIndexed { index, face ->
            if (face.indices.any { it >= vertices.size }) return@forEachIndexed
            val pts = face.indices.map { project(vertices[it], w, h) }
            if (pts.any { !it.first.isFinite() || !it.second.isFinite() }) return@forEachIndexed

            if (calculateSignedPolygonArea(pts) > 0) {
                return@forEachIndexed
            }

            if (pointInPolygon(mx, my, pts)) {
                val avgZ = face.indices.map { getZInViewSpace(vertices[it]) }.average()
                if (closestFace == null || avgZ < closestFace!!.second) {
                    closestFace = Pair(index, avgZ)
                }
            }
        }
        return closestFace
    }

    private fun calculateSignedPolygonArea(polygon: List<Pair<Double, Double>>): Double {
        var area = 0.0
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]
            area += (p1.first * p2.second - p2.first * p1.second)
        }
        return area / 2.0
    }

    private fun findBlushUnderCursor(mx: Double, my: Double, w: Double, h: Double): Int? {
        var closestBlush: Pair<Int, Double>? = null

        blushes.forEachIndexed { index, aabb ->
            val corners = aabb.getCorners()
            val projectedCorners = corners.map { project(it, w, h) }

            if (projectedCorners.any { !it.first.isFinite() || !it.second.isFinite() }) {
                return@forEachIndexed
            }

            val minX = projectedCorners.minOf { it.first }
            val maxX = projectedCorners.maxOf { it.first }
            val minY = projectedCorners.minOf { it.second }
            val maxY = projectedCorners.maxOf { it.second }

            if (mx in minX..maxX && my in minY..maxY) {
                val avgZ = corners.map { getZInViewSpace(it) }.average()
                if (closestBlush == null || avgZ < closestBlush!!.second) {
                    closestBlush = Pair(index, avgZ)
                }
            }
        }
        return closestBlush?.first
    }

    private fun findBlushCornerUnderCursor(mx: Double, my: Double, w: Double, h: Double): Pair<Int, BlushCornerType>? {
        if (selectedBlushIndex == null) return null

        val blush = blushes[selectedBlushIndex!!]
        val cornersToTest = mapOf(
            BlushCornerType.MIN to blush.min,
            BlushCornerType.MAX to blush.max
        )

        for ((type, corner) in cornersToTest) {
            val p = project(corner, w, h)
            if (p.first.isFinite() && p.second.isFinite()) {
                val dist = hypot(mx - p.first, my - p.second)
                if (dist < 10.0) {
                    return Pair(selectedBlushIndex!!, type)
                }
            }
        }
        return null
    }

    private fun pointInPolygon(px: Double, py: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first
            val yi = polygon[i].second
            val xj = polygon[j].first
            val yj = polygon[j].second
            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun addCubeAtOrigin() {
        val baseIndex = vertices.size
        val size = 100.0

        val newVertices = listOf(
            Vector3d(-size / 2, 0.0, -size / 2),
            Vector3d( size / 2, 0.0, -size / 2),
            Vector3d( size / 2, 0.0,  size / 2),
            Vector3d(-size / 2, 0.0,  size / 2),
            Vector3d(-size / 2,  size, -size / 2),
            Vector3d( size / 2,  size, -size / 2),
            Vector3d( size / 2,  size,  size / 2),
            Vector3d(-size / 2,  size,  size / 2)
        )
        vertices.addAll(newVertices)

        val newEdges = listOf(
            Edge(0, 1), Edge(1, 2), Edge(2, 3), Edge(3, 0),
            Edge(4, 5), Edge(5, 6), Edge(6, 7), Edge(7, 4),
            Edge(0, 4), Edge(1, 5), Edge(2, 6), Edge(3, 7)
        ).map { Edge(it.a + baseIndex, it.b + baseIndex) }
        edges.addAll(newEdges)

        val newFaces = listOf(
            Face(listOf(3, 2, 6, 7)),
            Face(listOf(1, 0, 4, 5)),
            Face(listOf(2, 1, 5, 6)),
            Face(listOf(0, 3, 7, 4)),
            Face(listOf(7, 6, 5, 4)),
            Face(listOf(0, 1, 2, 3)),
        ).map { Face(it.indices.map { idx -> idx + baseIndex }) }
        faces.addAll(newFaces)
    }

    private fun addPyramidAtOrigin() {
        val baseIndex = vertices.size
        val size = 100.0

        val newVertices = listOf(
            Vector3d(-size / 2, 0.0, -size / 2),
            Vector3d(size / 2, 0.0, -size / 2),
            Vector3d(size / 2, 0.0, size / 2),
            Vector3d(-size / 2, 0.0, size / 2),
            Vector3d(0.0, size, 0.0),
        )
        vertices.addAll(newVertices)

        val newEdges = listOf(
            Edge(0, 1), Edge(1, 2), Edge(2, 3), Edge(3, 0),
            Edge(0, 4), Edge(1, 4), Edge(2, 4), Edge(3, 4),
        ).map { Edge(it.a + baseIndex, it.b + baseIndex) }
        edges.addAll(newEdges)

        val newFaces = listOf(
            Face(listOf(4, 2, 1)),
            Face(listOf(4, 1, 0)),
            Face(listOf(4, 0, 3)),
            Face(listOf(4, 3, 2)),
            Face(listOf(0, 1, 2, 3)),
        ).map { Face(it.indices.map { idx -> idx + baseIndex }) }
        faces.addAll(newFaces)
    }

    private fun addPlaneAtOrigin() {
        val baseIndex = vertices.size
        val size = 100.0

        val newVertices = listOf(
            Vector3d(-size / 2, 0.0, -size / 2),
            Vector3d( size / 2, 0.0, -size / 2),
            Vector3d( size / 2, 0.0,  size / 2),
            Vector3d(-size / 2, 0.0,  size / 2),
        )
        vertices.addAll(newVertices)

        val newEdges = listOf(
            Edge(0, 1), Edge(1, 2), Edge(2, 3), Edge(3, 0),
        ).map { Edge(it.a + baseIndex, it.b + baseIndex) }
        edges.addAll(newEdges)

        val newFaces = listOf(
            Face(listOf(0, 1, 2, 3)),
            Face(listOf(3, 2, 1, 0)),
        ).map { Face(it.indices.map { idx -> idx + baseIndex }) }
        faces.addAll(newFaces)
    }

    private fun addCapsuleAtOrigin() {
        val height = 125.0
        val radius = 50.0
        val segments = 16
        val rings = 4

        val yOffset = radius + height / 2.0

        val baseIndex = vertices.size
        val newVertices = mutableListOf<Vector3d>()
        val newEdges = mutableListOf<Edge>()
        val newFaces = mutableListOf<Face>()

        newVertices.add(Vector3d(0.0, height + 2 * radius, 0.0))
        val topPoleIndex = 0

        // Pierścienie górnej półsfery (górny)
        for (j in 1 until rings) {
            val phi = PI / 2.0 * (1.0 - j.toDouble() / rings)
            val y = height / 2 + radius * sin(phi) + yOffset
            val ringRadius = radius * cos(phi)
            for (i in 0 until segments) {
                val theta = 2.0 * PI * i / segments
                val x = ringRadius * cos(theta)
                val z = ringRadius * sin(theta)
                newVertices.add(Vector3d(x, y, z))
            }
        }

        // Pierścień na styku z cylindrem (górny)
        val topCylinderRingStartIndex = newVertices.size
        for (i in 0 until segments) {
            val angle = 2.0 * PI * i / segments
            newVertices.add(Vector3d(radius * cos(angle), height / 2 + yOffset, radius * sin(angle)))
        }

        // Pierścień na styku z cylindrem (dolny)
        val bottomCylinderRingStartIndex = newVertices.size
        for (i in 0 until segments) {
            val angle = 2.0 * PI * i / segments
            newVertices.add(Vector3d(radius * cos(angle), -height / 2 + yOffset, radius * sin(angle)))
        }

        // Pierścienie dolnej półsfery (dolne)
        for (j in 1 until rings) {
            val phi = -PI / 2.0 * (j.toDouble() / rings)
            val y = -height / 2 + radius * sin(phi) + yOffset
            val ringRadius = radius * cos(phi)
            for (i in 0 until segments) {
                val theta = 2.0 * PI * i / segments
                val x = ringRadius * cos(theta)
                val z = ringRadius * sin(theta)
                newVertices.add(Vector3d(x, y, z))
            }
        }

        newVertices.add(Vector3d(0.0, 0.0, 0.0))
        val bottomPoleIndex = newVertices.size - 1

        vertices.addAll(newVertices)

        // Łączenie "czubka" z pierwszym pierścieniem
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            val p1 = 1 + i
            val p2 = 1 + nextI
            newFaces.add(Face(listOf(topPoleIndex, p2, p1)))
            newEdges.add(Edge(topPoleIndex, p1))
            newEdges.add(Edge(p1, p2))
        }

        // Łączenie pierścieni górnej półsfery
        for (j in 0 until rings - 2) {
            val ringStart1 = 1 + j * segments
            val ringStart2 = 1 + (j + 1) * segments
            for (i in 0 until segments) {
                val nextI = (i + 1) % segments
                val p1 = ringStart1 + i
                val p2 = ringStart1 + nextI
                val p3 = ringStart2 + i
                val p4 = ringStart2 + nextI
                newFaces.add(Face(listOf(p1, p2, p4, p3)))
                newEdges.add(Edge(p1, p3))
            }
        }

        // Łączenie ostatniego pierścienia półsfery z pierścieniem cylindra
        val lastRingStart = 1 + (rings - 2) * segments
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            val p1 = lastRingStart + i
            val p2 = lastRingStart + nextI
            val p3 = topCylinderRingStartIndex + i
            val p4 = topCylinderRingStartIndex + nextI
            newFaces.add(Face(listOf(p1, p2, p4, p3)))
            newEdges.add(Edge(p1, p3))
            newEdges.add(Edge(p3, p4))
        }

        // Ściany cylindra
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            val p1 = topCylinderRingStartIndex + i
            val p2 = topCylinderRingStartIndex + nextI
            val p3 = bottomCylinderRingStartIndex + i
            val p4 = bottomCylinderRingStartIndex + nextI
            newFaces.add(Face(listOf(p2, p4, p3, p1)))
            newEdges.add(Edge(p1, p3))
            newEdges.add(Edge(p3, p4))
        }

        // Łączenie pierścienia cylindra z pierwszym pierścieniem dolnej półsfery
        val firstBottomRingStart = bottomCylinderRingStartIndex + segments
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            val p1 = bottomCylinderRingStartIndex + i
            val p2 = bottomCylinderRingStartIndex + nextI
            val p3 = firstBottomRingStart + i
            val p4 = firstBottomRingStart + nextI
            newFaces.add(Face(listOf(p2, p4, p3, p1)))
            newEdges.add(Edge(p1, p3))
            newEdges.add(Edge(p3, p4))
        }

        // Łączenie pierścieni dolnej półsfery
        for (j in 0 until rings - 2) {
            val ringStart1 = firstBottomRingStart + j * segments
            val ringStart2 = firstBottomRingStart + (j + 1) * segments
            for (i in 0 until segments) {
                val nextI = (i + 1) % segments
                val p1 = ringStart1 + i
                val p2 = ringStart1 + nextI
                val p3 = ringStart2 + i
                val p4 = ringStart2 + nextI
                newFaces.add(Face(listOf(p2, p4, p3, p1)))
                newEdges.add(Edge(p1, p3))
                newEdges.add(Edge(p1, p2))
            }
        }

        // Łączenie ostatniego pierścienia z "dołem"
        val lastBottomRingStart = firstBottomRingStart + (rings - 2) * segments
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            val p1 = lastBottomRingStart + i
            val p2 = lastBottomRingStart + nextI
            newFaces.add(Face(listOf(p1, p2, bottomPoleIndex)))
            newEdges.add(Edge(p1, bottomPoleIndex))
            newEdges.add(Edge(p1, p2))
        }

        edges.addAll(newEdges.map { Edge(it.a + baseIndex, it.b + baseIndex) })
        faces.addAll(newFaces.map { Face(it.indices.map { idx -> idx + baseIndex }) })
    }

    private fun getEditorTexturePalette(): Map<String, Color> {
        return mapOf(
            "blackBricks" to Color.web("#1D2B53"),
            "bricks" to Color.web("#A34900"),
            "ceiling" to Color.web("#C2C3C7"),
            "floor" to Color.web("#5F574F"),
            "skybox" to Color.web("#29ADFF"),
            "red" to Color.RED,
            "darkRed" to Color.DARKRED,
            "pink" to Color.web("#FF77A8"),
            "salmon" to Color.SALMON,
            "maroon" to Color.MAROON,
            "orange" to Color.ORANGE,
            "darkOrange" to Color.DARKORANGE,
            "peach" to Color.web("#FFCCAA"),
            "brown" to Color.BROWN,
            "yellow" to Color.YELLOW,
            "darkGoldenrod" to Color.DARKGOLDENROD,
            "gold" to Color.GOLD,
            "green" to Color.GREEN,
            "darkGreen" to Color.DARKGREEN,
            "olive" to Color.OLIVE,
            "teal" to Color.TEAL,
            "mintCream" to Color.MINTCREAM,
            "cyan" to Color.CYAN,
            "darkCyan" to Color.DARKCYAN,
            "turquoise" to Color.TURQUOISE,
            "blue" to Color.BLUE,
            "darkBlue" to Color.DARKBLUE,
            "navy" to Color.NAVY,
            "indigo" to Color.web("#83769C"),
            "purple" to Color.PURPLE,
            "darkViolet" to Color.DARKVIOLET,
            "magenta" to Color.MAGENTA,
            "darkMagenta" to Color.DARKMAGENTA,
            "orchid" to Color.ORCHID,
            "darkPurple" to Color.web("#7E2553"),
            "white" to Color.web("#FFF1E8"),
            "lightGray" to Color.web("#C2C3C7"),
            "silver" to Color.SILVER,
            "gray" to Color.GRAY,
            "darkGray" to Color.DARKGRAY,
            "black" to Color.web("#000000")
        )
    }

    private fun showTexturePalette(gc: GraphicsContext) {
        val paletteStage = Stage()
        paletteStage.title = "Texture/Material Palette"

        val grid = GridPane()
        grid.hgap = 10.0
        grid.vgap = 5.0
        grid.padding = Insets(10.0)

        val texturePalette = getEditorTexturePalette()
        var row = 0
        texturePalette.entries.forEach { (name, color) ->
            val swatch = javafx.scene.shape.Rectangle(20.0, 20.0, color)
            swatch.stroke = Color.WHITE
            val nameLabel = Label(name)
            nameLabel.textFill = Color.WHITE
            val assignButton = Button("Assign")
            assignButton.setOnAction {
                if (this.selectedFaceIndex != null) {
                    faceTextures[this.selectedFaceIndex!!] = name
                    draw(gc, gc.canvas.width, gc.canvas.height, 0.0, 0.0)
                } else {
                    val alert = Alert(Alert.AlertType.WARNING, "No face selected to assign texture to.")
                    alert.showAndWait()
                }
            }
            grid.addRow(row++, swatch, nameLabel, assignButton)
        }

        val scrollPane = ScrollPane(grid)
        scrollPane.style = "-fx-background: #333; -fx-background-color: #333;"
        paletteStage.scene = Scene(scrollPane, 220.0, ((1080.0 * 2) / 3) - 40.0)

        paletteStage.x = 1920/2.0 + 400
        paletteStage.y = 1080/2.0 - (((1080.0 * 2) / 2) - 240.0)/2

        paletteStage.focusedProperty().addListener { _, _, isFocused ->
            if (!isFocused) {
                paletteStage.close()
            }
        }
        paletteStage.showAndWait()
    }

    private fun exportMeshFunction() {
        println("\nfun createCustomMesh(size: Double, color: Color): Mesh {")
        println("    val hs = size / 100.0")

        println("\n    val vertices = listOf(")
        for (v in vertices) println("        Vector3d(${v.x} * hs, ${v.y} * hs, ${v.z} * hs),")
        println("    )")

        println("\n    val faces: List<List<Int>> = listOf(")
        for (f in faces) println("        listOf(${f.indices.joinToString(", ")}),")
        println("    )")

        println("\n    data class Edge(val a: Int, val b: Int)")
        println("    val edges: List<Edge> = listOf(")
        for (e in edges) println("        $e,")
        println("    )")

        println("\n    val textureMapping = mapOf(")
        faceTextures.entries.sortedBy { it.key }.forEach { (index, name) ->
            println("        $index to \"$name\",")
        }
        println("    )")

        println("\n    val blushes = listOf(")
        blushes.forEach { blush ->
            println("        AABB(min = Vector3d(${blush.min.x} * hs, ${blush.min.y} * hs, ${blush.min.z} * hs), max = Vector3d(${blush.max.x} * hs, ${blush.max.y} * hs, ${blush.max.z} * hs)),")
        }
        println("    )")

        println("\n    val uvs: List<List<Vector3d>> = faces.map { face ->")
        println("        when(face.size) {")
        println("            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))")
        println("            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))")
        println("            else -> face.map { Vector3d(0.0,0.0,0.0) }")
        println("        }")
        println("    }\n    return Mesh(vertices, faces, uvs, color, blushes = blushes, faceTextureNames = textureMapping)")
        println("}")
        println("\n")
    }

    private fun showImportDialog(gc: GraphicsContext) {
        val dialogStage = Stage()
        dialogStage.initModality(Modality.APPLICATION_MODAL)
        dialogStage.title = "Importuj Model"

        val textArea = TextArea()
        textArea.text = "Wklej tutaj wyeksportowany kod funkcji create...Mesh"
        textArea.isWrapText = true
        textArea.prefRowCount = 25
        textArea.prefColumnCount = 90

        val importButton = Button("Importuj")
        importButton.setOnAction {
            parseAndLoadModel(textArea.text)
            val canvas = gc.canvas
            draw(gc, canvas.width, canvas.height, 0.0, 0.0)
            dialogStage.close()
        }

        val layout = VBox(10.0)
        layout.padding = Insets(10.0)
        layout.children.addAll(Label("Wklej kod modelu:"), textArea, importButton)

        val dialogScene = Scene(layout)
        dialogStage.scene = dialogScene
        dialogStage.showAndWait()
    }

    private fun parseVector3dFromString(vecContent: String): Vector3d? {
        val cleanedContent = vecContent.replace(Regex("[^\\d.,\\s-]"), "")
        val parts = cleanedContent.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 3) Vector3d(parts[0], parts[1], parts[2]) else null
    }

    private fun parseAndLoadModel(code: String) {
        println("--- Rozpoczynam import modelu ---")
        try {
            val newVertices = mutableListOf<Vector3d>()
            val newFaces = mutableListOf<Face>()
            val newEdges = mutableListOf<Edge>()
            val newFaceTextures = mutableMapOf<Int, String>()
            val newBlushes = mutableListOf<AABB>()
            var blushBuffer = ""

            var inVerticesBlock = false
            var inFacesBlock = false
            var inEdgesBlock = false
            var inTextureMappingBlock = false
            var inBlushesBlock = false

            for (line in code.lines()) {
                val trimmedLine = line.trim()

                if (trimmedLine.startsWith("val vertices")) {
                    println("Znaleziono blok 'vertices'.")
                    inVerticesBlock = true; inFacesBlock = false; inEdgesBlock = false; inTextureMappingBlock = false; inBlushesBlock = false
                    continue
                }
                if ((trimmedLine.startsWith("val faces")) or (trimmedLine.startsWith("var faces"))) {
                    println("Zakończono 'vertices'. Znaleziono: ${newVertices.size}.")
                    inVerticesBlock = false; inFacesBlock = true; inEdgesBlock = false; inTextureMappingBlock = false; inBlushesBlock = false
                    continue
                }
                if (trimmedLine.startsWith("val edges")) {
                    println("Zakończono 'faces'. Znaleziono: ${newFaces.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = true; inTextureMappingBlock = false; inBlushesBlock = false
                    continue
                }
                if (trimmedLine.startsWith("val textureMapping")) {
                    if (inEdgesBlock) println("Zakończono 'edges'. Znaleziono: ${newEdges.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = false; inTextureMappingBlock = true; inBlushesBlock = false
                    println("DEBUG: Rozpoczęto parsowanie bloku 'textureMapping'.")
                    continue
                }
                if (trimmedLine.startsWith("val blushes")) {
                    if (inTextureMappingBlock) println("Zakończono 'textureMapping'. Znaleziono: ${newFaceTextures.size}.")
                    else if (inEdgesBlock) println("Zakończono 'edges'. Znaleziono: ${newEdges.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = false; inTextureMappingBlock = false; inBlushesBlock = true
                    blushBuffer = ""
                    println("Znaleziono blok 'blushes'.")
                    continue
                }

                if (trimmedLine.startsWith("val uvs") || trimmedLine.startsWith("return Mesh") || trimmedLine.startsWith("}")) {
                    if (inEdgesBlock) println("Zakończono 'edges'. Znaleziono: ${newEdges.size}.")
                    if (inTextureMappingBlock) println("Zakończono 'textureMapping'. Znaleziono: ${newFaceTextures.size}.")
                    if (inBlushesBlock) println("Zakończono 'blushes'. Znaleziono: ${newBlushes.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = false; inTextureMappingBlock = false; inBlushesBlock = false
                }

                if (inVerticesBlock && trimmedLine.contains("Vector3d(")) {
                    val content = trimmedLine.substringAfter('(').substringBeforeLast(')')
                    val cleanedContent = content.replace(Regex("[^\\d.,\\s-]"), "")
                    val parts = cleanedContent.split(',').mapNotNull { it.trim().ifEmpty { null } }
                    if (parts.size == 3) {
                        try {
                            val x = parts[0].toDouble()
                            val y = parts[1].toDouble()
                            val z = parts[2].toDouble()
                            newVertices.add(Vector3d(x, y, z))
                        } catch (e: NumberFormatException) {
                            println(" > Ostrzeżenie: Nie udało się sparsować liczb w linii: $trimmedLine")
                        }
                    }
                } else if (inFacesBlock && trimmedLine.contains("listOf(")) {
                    val content = trimmedLine.substringAfter('(').substringBeforeLast(')')
                    if (content.all { it.isDigit() || it.isWhitespace() || it == ',' }) {
                        val indices = content.split(',').mapNotNull { it.trim().toIntOrNull() }
                        if (indices.isNotEmpty()) newFaces.add(Face(indices))
                    }
                } else if (inEdgesBlock && trimmedLine.contains("Edge(")) {
                    val content = trimmedLine.substringAfter('(').substringBeforeLast(')')
                    val cleanedContent = content.replace(Regex("[^\\d,]"), "")
                    val parts = cleanedContent.split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size == 2) {
                        newEdges.add(Edge(parts[0], parts[1]))
                    }
                } else if (inTextureMappingBlock && trimmedLine.contains(" to ")) {
                    val parts = trimmedLine.split(" to ")
                    if (parts.size == 2) {
                        val faceIndex = parts[0].trim().toIntOrNull()
                        val textureName = parts[1].trim().removeSuffix(",").removeSurrounding("\"")
                        if (faceIndex != null) {
                            newFaceTextures[faceIndex] = textureName
                            println("DEBUG: Sparsowano mapowanie: $faceIndex -> \"$textureName\"")
                        }
                    }
                } else if (inBlushesBlock) {
                    if (trimmedLine.startsWith("listOf(") || trimmedLine == ")" || trimmedLine.isEmpty()) {
                    } else {
                        blushBuffer += " " + trimmedLine

                        val openParenCount = blushBuffer.count { it == '(' }
                        val closeParenCount = blushBuffer.count { it == ')' }

                        if (blushBuffer.contains("AABB(") && openParenCount > 0 && openParenCount == closeParenCount) {
                            val parseableString = blushBuffer.trim()

                            val minRegex = Regex("""min\s*=\s*Vector3d\s*\(([^)]+)\)""")
                            val maxRegex = Regex("""max\s*=\s*Vector3d\s*\(([^)]+)\)""")

                            val minMatch = minRegex.find(parseableString)
                            val maxMatch = maxRegex.find(parseableString)

                            if (minMatch != null && maxMatch != null) {
                                val minContent = minMatch.groupValues[1]
                                val maxContent = maxMatch.groupValues[1]
                                val minVec = parseVector3dFromString(minContent)
                                val maxVec = parseVector3dFromString(maxContent)

                                if (minVec != null && maxVec != null) {
                                    newBlushes.add(AABB(minVec, maxVec))
                                } else {
                                    println(" > Ostrzeżenie: Nie udało się sparsować wektorów w AABB z bufora: $blushBuffer")
                                }
                            } else {
                                println(" > Ostrzeżenie: Nie udało się znaleźć min/max w AABB z bufora: $blushBuffer")
                            }
                            blushBuffer = ""
                        }
                    }
                }
            }
            println("--- Zakończono parsowanie pliku ---")

            println("DEBUG: newFaceTextures przed putAll: $newFaceTextures")
            if (newVertices.isEmpty()) {
                println("BŁĄD KRYTYCZNY: Nie udało się sparsować żadnych wierzchołków.")
                return
            }

            vertices.clear()
            edges.clear()
            faces.clear()
            blushes.clear()
            faceTextures.clear()
            selectedForFace.clear()
            selectedVertex = null
            groupSelectedVertices.clear()
            groupGizmoPosition = null
            selectedBlushIndex = null
            selectedBlushCorner = null

            vertices.addAll(newVertices)
            edges.addAll(newEdges)
            faces.addAll(newFaces)
            blushes.addAll(newBlushes)
            faceTextures.putAll(newFaceTextures)
            println("DEBUG: faceTextures po putAll: $faceTextures")
            println("Załadowano model: ${vertices.size} wierzchołków, ${edges.size} krawędzi, ${faces.size} ścian, ${faceTextures.size} mapowań, ${blushes.size} blushes.")

        } catch (e: Exception) {
            println("KRYTYCZNY BŁĄD podczas parsowania modelu: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun copySelection() {
        clipboardModelPart = null
        clipboardBlush = null

        if (blushMode && selectedBlushIndex != null) {
            val blushToCopy = blushes.getOrNull(selectedBlushIndex!!) ?: return
            clipboardBlush = blushToCopy.copy(
                min = blushToCopy.min.copy(),
                max = blushToCopy.max.copy()
            )
            println("Skopiowano blush $selectedBlushIndex.")
            return
        }

        val indicesToCopy: Set<Int> = when {
            selectedFaceIndex != null && groupSelectedVertices.isNotEmpty() -> groupSelectedVertices
            groupSelectedVertices.isNotEmpty() -> groupSelectedVertices
            selectedVertex != null -> setOf(selectedVertex!!)
            else -> emptySet()
        }

        if (indicesToCopy.isEmpty()) return

        val copiedVerts = indicesToCopy.mapNotNull { vertices.getOrNull(it)?.copy() }
        val oldToNewIndexMap = indicesToCopy.toList().withIndex().associate { (newIdx, oldIdx) -> oldIdx to newIdx }

        val copiedEdges = edges.mapNotNull { edge ->
            if (edge.a in oldToNewIndexMap && edge.b in oldToNewIndexMap) {
                Edge(oldToNewIndexMap[edge.a]!!, oldToNewIndexMap[edge.b]!!)
            } else {
                null
            }
        }

        val copiedFaces = mutableListOf<Face>()
        val copiedFaceTextures = mutableMapOf<Int, String>()
        faces.withIndex().forEach { (originalFaceIndex, face) ->
            if (face.indices.all { it in oldToNewIndexMap }) {
                val newRelativeFace = Face(face.indices.map { oldToNewIndexMap[it]!! })
                copiedFaces.add(newRelativeFace)

                faceTextures[originalFaceIndex]?.let { textureName ->
                    val newRelativeFaceIndex = copiedFaces.lastIndex
                    copiedFaceTextures[newRelativeFaceIndex] = textureName
                }
            }
        }

        clipboardModelPart = CopiedModelPart(copiedVerts, copiedEdges, copiedFaces, copiedFaceTextures)
        println("Skopiowano ${copiedVerts.size} wierzchołków, ${copiedEdges.size} krawędzi, ${copiedFaces.size} ścian (w tym ${copiedFaceTextures.size} z teksturami).")
    }

    private fun pasteSelection() {
        if (clipboardBlush != null) {
            val newBlush = clipboardBlush!!.copy(
                min = clipboardBlush!!.min.copy(),
                max = clipboardBlush!!.max.copy()
            )
            blushes.add(newBlush)

            selectedBlushIndex = blushes.lastIndex
            selectedBlushCorner = null
            selectedVertex = null
            groupSelectedVertices.clear()
            groupGizmoPosition = null
            selectedFaceIndex = null

            println("Wklejono blush. Nowa ilość: ${blushes.size}")
            return
        }

        clipboardModelPart?.let { part ->
            if (part.vertices.isEmpty()) return

            val vertexBaseIndex = vertices.size
            val faceBaseIndex = faces.size

            val newVertices = part.vertices.map { it.copy() }
            vertices.addAll(newVertices)

            val newEdges = part.edges.map { Edge(it.a + vertexBaseIndex, it.b + vertexBaseIndex) }
            edges.addAll(newEdges)

            val newFaces = part.faces.map { Face(it.indices.map { idx -> idx + vertexBaseIndex }) }
            faces.addAll(newFaces)

            part.faceTextures.forEach { (relativeFaceIndex, textureName) ->
                val newGlobalFaceIndex = faceBaseIndex + relativeFaceIndex
                faceTextures[newGlobalFaceIndex] = textureName
            }

            updateSelectionForPastedPart(vertexBaseIndex)

            println("Wklejono część modelu. Nowe sumy: ${vertices.size}V, ${edges.size}E, ${faces.size}F")
        }
    }

    private fun updateSelectionForPastedPart(baseIndex: Int) {
        selectedVertex = null; groupSelectedVertices.clear(); groupGizmoPosition = null
        selectedFaceIndex = null; selectedBlushIndex = null; selectedBlushCorner = null
        blushMode = false

        val newIndices = (baseIndex until vertices.size).toList()
        if (newIndices.size == 1) {
            selectedVertex = newIndices.first()
        } else if (newIndices.isNotEmpty()) {
            groupSelectedVertices.addAll(newIndices)
            var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
            groupSelectedVertices.forEach { val v = vertices[it]; sumX += v.x; sumY += v.y; sumZ += v.z }
            val count = groupSelectedVertices.size
            groupGizmoPosition = Vector3d(sumX / count, sumY / count, sumZ / count)
        }
    }

    private fun rebuildEdgesFromFaces() {
        val allEdges = mutableSetOf<Pair<Int, Int>>()
        for (face in faces) {
            for (i in face.indices.indices) {
                if (face.indices[i] >= vertices.size || face.indices[(i + 1) % face.indices.size] >= vertices.size) continue
                val idx1 = face.indices[i]
                val idx2 = face.indices[(i + 1) % face.indices.size]
                val edgeKey = if (idx1 < idx2) Pair(idx1, idx2) else Pair(idx2, idx1)
                allEdges.add(edgeKey)
            }
        }
        edges.clear()
        edges.addAll(allEdges.map { Edge(it.first, it.second) })
    }

    private fun subdivideSelection() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSubdivideTime < 500) { // 500ms delay
            return
        }
        lastSubdivideTime = currentTime

        val facesToSubdivideIndices = if (selectedFaceIndex != null) {
            val initialSet = mutableSetOf(selectedFaceIndex!!)

            val selectedFace = faces.getOrNull(selectedFaceIndex!!)
            if (selectedFace != null) {
                val selectedIndicesSet = selectedFace.indices.toSet()

                faces.withIndex()
                    .find { (index, face) ->
                        index != selectedFaceIndex!! &&
                                face.indices.size == selectedFace.indices.size &&
                                face.indices.toSet() == selectedIndicesSet
                    }?.let { (oppositeIndex, _) ->
                        initialSet.add(oppositeIndex)
                    }
            }
            initialSet
        } else if (groupSelectedVertices.isNotEmpty()) {
            faces.withIndex()
                .filter { (_, face) -> face.indices.all { it in groupSelectedVertices } }
                .map { it.index }
                .toSet()
        } else {
            emptySet<Int>()
        }

        if (facesToSubdivideIndices.isEmpty()) {
            println("No faces selected to subdivide.")
            return
        }

        val edgeMidpointCache = mutableMapOf<Pair<Int, Int>, Int>()

        fun getOrCreateMidpoint(idx1: Int, idx2: Int): Int {
            val edgeKey = if (idx1 < idx2) Pair(idx1, idx2) else Pair(idx2, idx1)
            return edgeMidpointCache.getOrPut(edgeKey) {
                val v1 = vertices[idx1]
                val v2 = vertices[idx2]
                val midpoint = v1.add(v2).scale(0.5)
                vertices.add(midpoint)
                vertices.lastIndex
            }
        }

        val facesToKeepWithTextures = faces.withIndex()
            .filter { it.index !in facesToSubdivideIndices }
            .map { it.value to faceTextures[it.index] }

        val newFacesWithTextures = mutableListOf<Pair<Face, String?>>()

        for (faceIndex in facesToSubdivideIndices) {
            val face = faces[faceIndex]
            val originalTexture = faceTextures[faceIndex]

            val indices = face.indices
            if (indices.size < 3) continue

            val centerPos = indices.map { vertices[it] }
                .fold(Vector3d(0.0, 0.0, 0.0)) { acc, v -> acc.add(v) }
                .scale(1.0 / indices.size)
            vertices.add(centerPos)
            val centerIndex = vertices.lastIndex

            val midpoints = indices.mapIndexed { i, _ ->
                val idx1 = indices[i]
                val idx2 = indices[(i + 1) % indices.size]
                getOrCreateMidpoint(idx1, idx2)
            }

            for (i in indices.indices) {
                val currentVertexIndex = indices[i]
                val nextMidpointIndex = midpoints[i]
                val prevMidpointIndex = midpoints[(i + indices.size - 1) % indices.size]

                val baseOrder = listOf(nextMidpointIndex, centerIndex, prevMidpointIndex, currentVertexIndex)

                val shiftAmount = if (indices.size == 4) (3 - i + 4) % 4 else 0

                val finalOrder = if (shiftAmount > 0) {
                    List(baseOrder.size) { j -> baseOrder[(j + shiftAmount) % baseOrder.size] }
                } else {
                    baseOrder
                }
                newFacesWithTextures.add(Face(finalOrder) to originalTexture)
            }
        }

        faces.clear()
        faceTextures.clear()

        facesToKeepWithTextures.forEach { (face, texture) ->
            val newIndex = faces.size
            faces.add(face)
            if (texture != null) {
                faceTextures[newIndex] = texture
            }
        }

        newFacesWithTextures.forEach { (face, texture) ->
            val newIndex = faces.size
            faces.add(face)
            if (texture != null) {
                faceTextures[newIndex] = texture
            }
        }

        rebuildEdgesFromFaces()

        selectedVertex = null; selectedFaceIndex = null; groupSelectedVertices.clear(); groupGizmoPosition = null
        selectedBlushIndex = null; selectedBlushCorner = null; selectedForFace.clear()

        println("Subdivided ${facesToSubdivideIndices.size} faces. New total: ${vertices.size}V, ${edges.size}E, ${faces.size}F")
    }

    private fun draw(gc: GraphicsContext, w: Double, h: Double, mouseX: Double, mouseY: Double) {
        gc.fill = Color.BLACK; gc.fillRect(0.0, 0.0, w, h)
        drawGrid(gc, w, h); drawBlushes(gc, w, h)

        if (angleY > PI * 2) angleY -= PI * 2
        if (angleY < -PI * 2) angleY += PI * 2
        if (angleX < -PI * 2) angleX += PI * 2
        if (angleX > PI * 2) angleX -= PI * 2

        val visibleFaces = faces.withIndex().filter { (_, f) ->
            if (f.indices.any { it >= vertices.size }) return@filter false
            val projectedPoints = f.indices.map { project(vertices[it], w, h) }
            if (projectedPoints.any { !it.first.isFinite() || !it.second.isFinite() }) return@filter false
            calculateSignedPolygonArea(projectedPoints) <= 0
        }

        val sortedFacesWithIndex = visibleFaces.sortedByDescending { (_, f) ->
            f.indices.map { getZInViewSpace(vertices[it]) }.average()
        }

        val texturePalette = getEditorTexturePalette()

        for ((originalFaceIndex, face) in sortedFacesWithIndex) {
            val pts = face.indices.map { project(vertices[it], w, h) }

            val textureName = faceTextures[originalFaceIndex]
            val faceColor = if (textureName != null && texturePalette.containsKey(textureName)) {
                texturePalette[textureName]!!
            } else {
                Color.GRAY
            }
            gc.globalAlpha = if (blushMode) 0.2 else 0.8
            gc.fill = faceColor

            gc.beginPath()
            gc.moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) gc.lineTo(pts[i].first, pts[i].second)
            gc.closePath(); gc.fill()

            if (textureName != null) {
                drawTextOnFace(gc, textureName, pts)
            }
        }

        gc.globalAlpha = 1.0

        // Krawędzie
        gc.stroke = Color.LIME
        gc.globalAlpha = if (blushMode) 0.2 else 1.0
        for (e in edges) {
            if (e.a >= vertices.size || e.b >= vertices.size) continue
            val p1 = project(vertices[e.a], w, h)
            val p2 = project(vertices[e.b], w, h)
            if (p1.first.isFinite() && p1.second.isFinite() && p2.first.isFinite() && p2.second.isFinite()) {
                gc.strokeLine(p1.first, p1.second, p2.first, p2.second)
            }
        }
        gc.globalAlpha = 1.0

        // Punkty
        for ((i, v) in vertices.withIndex()) {
            val p = project(v, w, h)
            if (p.first.isFinite() && p.second.isFinite()) {
                var pointColor = when {
                    groupSelectedVertices.contains(i) -> Color.PURPLE
                    selectedVertex == i -> Color.ORANGE
                    selectedForFace.contains(i) -> Color.CYAN
                    else -> Color.WHITE
                }
                if (blushMode) {
                    pointColor = pointColor.desaturate().interpolate(Color.BLACK, 0.5)
                }
                gc.fill = pointColor
                gc.fillOval(p.first - 4, p.second - 4, 8.0, 8.0)
            }
        }
        if (isBoxSelecting) {
            val x = min(boxSelectStartX, boxSelectCurrentX)
            val y = min(boxSelectStartY, boxSelectCurrentY)
            val width = abs(boxSelectStartX - boxSelectCurrentX)
            val height = abs(boxSelectStartY - boxSelectCurrentY)

            gc.fill = Color.CYAN.deriveColor(0.0, 1.0, 1.0, 0.3)
            gc.fillRect(x, y, width, height)

            gc.stroke = Color.WHITE
            gc.lineWidth = 1.0
            gc.setLineDashes(4.0, 4.0)
            gc.strokeRect(x, y, width, height)
            gc.setLineDashes()
        }
        drawOrientationGizmo(gc, w, h)
        drawVertexInfo(gc, w, h)
        drawGizmo(gc, w, h)
    }

    private fun drawTextOnFace(gc: GraphicsContext, text: String, polygonPoints: List<Pair<Double, Double>>) {
        if (polygonPoints.isEmpty()) return
        val centerX = polygonPoints.map { it.first }.average()
        val centerY = polygonPoints.map { it.second }.average()

        gc.save()
        gc.globalAlpha = 1.0
        gc.font = javafx.scene.text.Font.font("Monospaced", 12.0)
        gc.textAlign = javafx.scene.text.TextAlignment.CENTER
        gc.textBaseline = javafx.geometry.VPos.CENTER
        gc.stroke = Color.BLACK
        gc.fill = Color.WHITE
        gc.strokeText(text, centerX, centerY)
        gc.fillText(text, centerX, centerY)
        gc.restore()
    }

    private fun drawClippedLine(gc: GraphicsContext, p1World: Vector3d, p2World: Vector3d, w: Double, h: Double) {
        val zNearClip = -399.0

        val z1 = getZInViewSpace(p1World)
        val z2 = getZInViewSpace(p2World)

        val p1Visible = z1 > zNearClip
        val p2Visible = z2 > zNearClip

        when {
            p1Visible && p2Visible -> {
                val proj1 = project(p1World, w, h)
                val proj2 = project(p2World, w, h)
                gc.strokeLine(proj1.first, proj1.second, proj2.first, proj2.second)
            }
            p1Visible && !p2Visible -> {
                val t = (zNearClip - z1) / (z2 - z1)
                val intersect = Vector3d(
                    p1World.x + t * (p2World.x - p1World.x),
                    p1World.y + t * (p2World.y - p1World.y),
                    p1World.z + t * (p2World.z - p1World.z)
                )
                val proj1 = project(p1World, w, h)
                val proj2 = project(intersect, w, h)
                gc.strokeLine(proj1.first, proj1.second, proj2.first, proj2.second)
            }
            !p1Visible && p2Visible -> {
                val t = (zNearClip - z1) / (z2 - z1)
                val intersect = Vector3d(
                    p1World.x + t * (p2World.x - p1World.x),
                    p1World.y + t * (p2World.y - p1World.y),
                    p1World.z + t * (p2World.z - p1World.z)
                )
                val proj1 = project(p2World, w, h)
                val proj2 = project(intersect, w, h)
                gc.strokeLine(proj1.first, proj1.second, proj2.first, proj2.second)
            }
        }
    }

    private fun drawGrid(gc: GraphicsContext, w: Double, h: Double) {
        gc.stroke = Color.rgb(50, 50, 50)
        val numLines = 8
        val step = 50.0
        val gridSize = numLines * step

        for (i in -numLines..numLines) {
            val x = i * step
            val p1 = Vector3d(x, 0.0, -gridSize)
            val p2 = Vector3d(x, 0.0, gridSize)
            drawClippedLine(gc, p1, p2, w, h)
        }
        for (i in -numLines..numLines) {
            val z = i * step
            val p1 = Vector3d(-gridSize, 0.0, z)
            val p2 = Vector3d(gridSize, 0.0, z)
            drawClippedLine(gc, p1, p2, w, h)
        }
    }

    private fun drawBlushes(gc: GraphicsContext, w: Double, h: Double) {
        if (!blushMode) return

        blushes.forEachIndexed { index, aabb ->
            gc.globalAlpha = 0.2
            gc.lineWidth = 1.5
            gc.stroke = if (index == selectedBlushIndex) Color.YELLOW else Color.ORANGE

            val corners = aabb.getCorners()
            val blushEdges = listOf(
                0 to 1, 1 to 3, 3 to 2, 2 to 0, // Dolna podstawa
                4 to 5, 5 to 7, 7 to 6, 6 to 4, // Górna podstawa
                0 to 4, 1 to 5, 2 to 6, 3 to 7  // Krawędzie boczne
            )
            for ((i, j) in blushEdges) {
                drawClippedLine(gc, corners[i], corners[j], w, h)
            }

            if (index == selectedBlushIndex) {
                gc.globalAlpha = 1.0
                val minP = project(aabb.min, w, h)
                val maxP = project(aabb.max, w, h)
                gc.fill = if (selectedBlushCorner == BlushCornerType.MIN) Color.WHITE else Color.RED
                gc.fillOval(minP.first - 5, minP.second - 5, 10.0, 10.0)
                gc.fill = if (selectedBlushCorner == BlushCornerType.MAX) Color.WHITE else Color.BLUE
                gc.fillOval(maxP.first - 5, maxP.second - 5, 10.0, 10.0)
            }
        }
        gc.globalAlpha = 1.0
    }

    private fun drawOrientationGizmo(gc: GraphicsContext, w: Double, h: Double) {
        val cx = w - 80.0
        val cy = 80.0
        val axisLength = 40.0

        val xAxis = Vector3d(-1.0, 0.0, 0.0)
        val yAxis = Vector3d(0.0, 1.0, 0.0)
        val zAxis = Vector3d(0.0, 0.0, -1.0)

        val axes = listOf(
            'X' to xAxis,
            'Y' to yAxis,
            'Z' to zAxis
        ).map { (name, vec) ->
            val rotatedZ = getRotatedZ(vec)
            val projected = projectAxis(vec)
            val color = when(name) {
                'X' -> Color.RED
                'Y' -> Color.GREEN
                else -> Color.BLUE
            }
            Triple(rotatedZ, color, projected)
        }.sortedBy { it.first }

        gc.lineWidth = 2.5

        for ((_, color, p) in axes) {
            gc.stroke = color
            gc.strokeLine(cx, cy, cx + p.first * axisLength, cy + p.second * axisLength)
        }

        gc.lineWidth = 1.0
    }

    private fun getRotatedZ(v: Vector3d): Double {
        val cosY = cos(angleY); val sinY = sin(angleY)
        val cosX = cos(angleX); val sinX = sin(angleX)
        val z1 = v.x * sinY + v.z * cosY
        return v.y * sinX + z1 * cosX
    }

    private fun projectAxis(v: Vector3d): Pair<Double, Double> {
        val cosY = cos(angleY); val sinY = sin(angleY)
        val cosX = cos(angleX); val sinX = sin(angleX)
        val x1 = v.x * cosY - v.z * sinY
        val z1 = v.x * sinY + v.z * cosY
        val y2 = v.y * cosX - z1 * sinX
        return Pair(x1, y2)
    }

    private fun drawVertexInfo(gc: GraphicsContext, w: Double, h: Double) {
        selectedVertex?.let { index ->
            val vertex = vertices.getOrNull(index) ?: return
            val text = String.format("V[%d]:  X: %.2f, Y: %.2f, Z: %.2f", index, vertex.x, vertex.y, vertex.z)

            gc.font = javafx.scene.text.Font.font("Monospaced", 14.0)
            gc.fill = Color.WHITE

            val padding = 15.0
            val xPos = padding
            val yPos = h - padding

            gc.fillText(text, xPos, yPos)
        }

        selectedBlushIndex?.let { index ->
            val blush = blushes.getOrNull(index) ?: return
            val min = blush.min
            val max = blush.max
            val text = String.format("B[%d]: MIN(%.2f, %.2f, %.2f)  MAX(%.2f, %.2f, %.2f)", index, min.x, min.y, min.z, max.x, max.y, max.z)

            gc.font = javafx.scene.text.Font.font("Monospaced", 15.0)
            gc.fill = Color.WHITE

            val padding = 15.0
            val xPos = padding
            val yPos = h - padding

            gc.fillText(text, xPos, yPos)
        }
    }

    private fun project(v: Vector3d, w: Double, h: Double): Pair<Double, Double> {
        val cosY = cos(angleY); val sinY = sin(angleY)
        val cosX = cos(angleX); val sinX = sin(angleX)
        val x = v.x * cosY - v.z * sinY
        val z = v.x * sinY + v.z * cosY
        var y = v.y
        val y2 = y * cosX - z * sinX
        val z2 = y * sinX + z * cosX

        val denominator = z2 / 400 + 1
        if (denominator <= 0) {
            return Pair(Double.NaN, Double.NaN)
        }

        y = y2;
        val sx = w / 2 + x / denominator * (zoom / 100)
        val sy = h / 2 + y / denominator * (zoom / 100)
        return Pair(sx, sy)
    }

    private fun pointToLineSegmentDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        if (l2 == 0.0) return hypot(px - x1, py - y1)
        var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
        t = max(0.0, min(1.0, t))
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return hypot(px - projX, py - projY)
    }

    private fun findClosestGizmoAxis(mouseX: Double, mouseY: Double, w: Double, h: Double): Char? {
        val gizmoOrigin = when {
            blushMode && selectedBlushIndex != null && selectedBlushCorner != null -> {
                val blush = blushes[selectedBlushIndex!!]
                if (selectedBlushCorner == BlushCornerType.MIN) blush.min else blush.max
            }
            blushMode && selectedBlushIndex != null -> blushes.getOrNull(selectedBlushIndex!!)?.getCenter()
            groupSelectedVertices.isNotEmpty() -> groupGizmoPosition
            selectedVertex != null -> vertices.getOrNull(selectedVertex!!)
            else -> null
        } ?: return null

        val clickThreshold = 10.0

        val axes = mapOf(
            'X' to Pair(
                project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoStartOffset), w, h),
                project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoEndOffset), w, h)
            ),
            'Y' to Pair(
                project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoStartOffset), w, h),
                project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoEndOffset), w, h)
            ),
            'Z' to Pair(
                project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoStartOffset), w, h),
                project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoEndOffset), w, h)
            )
        )

        for ((axis, points) in axes) {
            val (startPoint, endPoint) = points
            if (!startPoint.first.isFinite() || !startPoint.second.isFinite() || !endPoint.first.isFinite() || !endPoint.second.isFinite()) continue
            val dist = pointToLineSegmentDistance(mouseX, mouseY, startPoint.first, startPoint.second, endPoint.first, endPoint.second)
            if (dist < clickThreshold) {
                return axis
            }
        }
        return null
    }

    private fun drawGizmo(gc: GraphicsContext, w: Double, h: Double) {
        val gizmoOrigin = when {
            blushMode && selectedBlushIndex != null && selectedBlushCorner != null -> {
                val blush = blushes[selectedBlushIndex!!]
                if (selectedBlushCorner == BlushCornerType.MIN) blush.min else blush.max
            }
            blushMode && selectedBlushIndex != null -> blushes.getOrNull(selectedBlushIndex!!)?.getCenter()
            groupSelectedVertices.isNotEmpty() -> groupGizmoPosition
            selectedVertex != null -> vertices.getOrNull(selectedVertex!!)
            else -> null
        } ?: return

        val xAxisStart = project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoStartOffset), w, h)
        val xAxisEnd = project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoEndOffset), w, h)
        val yAxisStart = project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoStartOffset), w, h)
        val yAxisEnd = project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoEndOffset), w, h)
        val zAxisStart = project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoStartOffset), w, h)
        val zAxisEnd = project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoEndOffset), w, h)

        gc.stroke = Color.RED
        gc.lineWidth = if (gizmoAxis == 'X') 3.0 else 1.5
        if (xAxisStart.first.isFinite() && xAxisStart.second.isFinite() && xAxisEnd.first.isFinite() && xAxisEnd.second.isFinite()) {
            gc.strokeLine(xAxisStart.first, xAxisStart.second, xAxisEnd.first, xAxisEnd.second)
        }

        gc.stroke = Color.GREEN
        gc.lineWidth = if (gizmoAxis == 'Y') 3.0 else 1.5
        if (yAxisStart.first.isFinite() && yAxisStart.second.isFinite() && yAxisEnd.first.isFinite() && yAxisEnd.second.isFinite()) {
            gc.strokeLine(yAxisStart.first, yAxisStart.second, yAxisEnd.first, yAxisEnd.second)
        }

        gc.stroke = Color.BLUE
        gc.lineWidth = if (gizmoAxis == 'Z') 3.0 else 1.5
        if (zAxisStart.first.isFinite() && zAxisStart.second.isFinite() && zAxisEnd.first.isFinite() && zAxisEnd.second.isFinite()) {
            gc.strokeLine(zAxisStart.first, zAxisStart.second, zAxisEnd.first, zAxisEnd.second)
        }
        gc.lineWidth = 1.0
    }
}

fun main() {
    Application.launch(ModelEditor::class.java)
}