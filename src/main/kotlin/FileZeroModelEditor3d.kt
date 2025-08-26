package org.lewapnoob.FileZeroModel3DEditor

import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Modality
import javafx.stage.Stage
import kotlin.math.*

data class Vector3d(var x: Double, var y: Double, var z: Double)
data class Edge(val a: Int, val b: Int)
data class Face(val indices: List<Int>)
data class Mesh(val vertices: List<Vector3d>, val faces: List<List<Int>>, val faceUVs: List<List<Vector3d>>, val color: Color)
data class AABB(val min: Vector3d, val max: Vector3d)

class ModelEditor : Application() {
    private val vertices = mutableListOf<Vector3d>()
    private val edges = mutableListOf<Edge>()
    private val faces = mutableListOf<Face>()
    private val blushes = mutableListOf<AABB>()

    private var selectedVertex: Int? = null
    private val groupSelectedVertices = mutableSetOf<Int>()
    private var groupGizmoPosition: Vector3d? = null
    private val selectedForFace = mutableListOf<Int>()

    private var dragStartVertex: Int? = null

    private var angleX = -2.4
    private var angleY = 0.2
    private var zoom = 300.0

    private var deleteMode = false
    private var faceSelectMode = false // tryb Q

    // Gizmo
    private var gizmoAxis: Char? = null
    private var dragStartX: Double = 0.0
    private var dragStartY: Double = 0.0
    private var dragStartVertexPos: Vector3d? = null
    private var dragStartGroupPositions: Map<Int, Vector3d>? = null
    private val gizmoSize = 20.0

    override fun start(stage: Stage) {
        val canvas = Canvas((1920.0 * 2) / 3, (1080.0 * 2) / 3)
        val gc = canvas.graphicsContext2D

        canvas.setOnMousePressed { e ->
            val w = canvas.width
            val h = canvas.height

            val gizmoOrigin = if (groupSelectedVertices.isNotEmpty()) groupGizmoPosition else if (selectedVertex != null) vertices.getOrNull(selectedVertex!!) else null
            if (e.button == MouseButton.PRIMARY && gizmoOrigin != null) {
                val axis = findClosestGizmoAxis(e.x, e.y, w, h)
                if (axis != null) {
                    gizmoAxis = axis
                    dragStartX = e.x
                    dragStartY = e.y
                    if (groupSelectedVertices.isNotEmpty()) {
                        dragStartGroupPositions = groupSelectedVertices.associateWith { vertices[it].copy() }
                        dragStartVertexPos = groupGizmoPosition!!.copy()
                    } else {
                        dragStartVertexPos = vertices[selectedVertex!!].copy()
                    }
                    draw(gc, w, h, e.x, e.y)
                    return@setOnMousePressed
                }
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
                dragStartVertex = null

                if (clickedVertex != null) {
                    selectedVertex = clickedVertex
                    dragStartVertex = clickedVertex
                } else {
                    val closestFaceResult = findFaceUnderCursor(e.x, e.y, w, h)
                    val closestEdgeResult = findClosestEdge(e.x, e.y, w, h)

                    val faceDepth = closestFaceResult?.second ?: Double.MAX_VALUE
                    val edgeDepth = closestEdgeResult?.second ?: Double.MAX_VALUE

                    if (faceDepth < edgeDepth && faceDepth != Double.MAX_VALUE) {
                        val face = faces[closestFaceResult!!.first]
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
                        vertices.add(Vector3d(0.0, 0.0, 0.0))
                        selectedVertex = vertices.size - 1
                    }
                }
            }
            draw(gc, w, h, e.x, e.y)
        }

        canvas.setOnMouseDragged { e ->
            if (gizmoAxis != null) {
                val w = canvas.width
                val h = canvas.height

                val originPos = dragStartVertexPos ?: return@setOnMouseDragged

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

                if (groupSelectedVertices.isNotEmpty() && dragStartGroupPositions != null) {
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
            }
        }

        canvas.setOnMouseReleased { e ->
            gizmoAxis = null
            dragStartVertexPos = null
            dragStartGroupPositions = null

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

                KeyCode.Q -> {
                    faceSelectMode = !faceSelectMode
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
                    if (selectedVertex != null) {
                        deleteSelectedVertex()
                    } else {
                        deleteMode = !deleteMode
                    }
                }
                KeyCode.R -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).x = vertices.get(index).x.toInt() + 0.0
                            vertices.get(index).y = vertices.get(index).y.toInt() + 0.0
                            vertices.get(index).z = vertices.get(index).z.toInt() + 0.0
                        }
                    }
                }

                KeyCode.UP -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).z -= 10.0
                        }
                    }
                }
                KeyCode.DOWN -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).z += 10.0
                        }
                    }
                }
                KeyCode.LEFT -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).x -= 10.0
                        }
                    }
                }
                KeyCode.RIGHT -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).x += 10.0
                        }
                    }
                }
                KeyCode.EQUALS -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).y -= 10.0
                        }
                    }
                }
                KeyCode.MINUS -> {
                    if (selectedVertex != null) {
                        selectedVertex?.let { index ->
                            vertices.get(index).y += 10.0
                        }
                    }
                }

                KeyCode.E -> exportMeshFunction()
                KeyCode.I -> showImportDialog(gc)
                KeyCode.G -> println("$angleX $angleY")
                else -> {}
            }
            draw(gc, canvas.width, canvas.height, 0.0, 0.0)
        }

        stage.setOnCloseRequest {
            System.exit(0)
        }

        stage.title = "FileZero.kt Model 3D Editor"
        stage.scene = scene
        stage.show()
        draw(gc, canvas.width, canvas.height, 0.0, 0.0)
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

            if (pointInPolygon(mx, my, pts)) {
                val avgZ = face.indices.map { getZInViewSpace(vertices[it]) }.average()
                if (closestFace == null || avgZ < closestFace!!.second) {
                    closestFace = Pair(index, avgZ)
                }
            }
        }
        return closestFace
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
        println("    )")

        println("\n    val blushes = listOf(")
        println("    )")

        println("\n    val uvs: List<List<Vector3d>> = faces.map { face ->")
        println("        when(face.size) {")
        println("            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))")
        println("            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))")
        println("            else -> face.map { Vector3d(0.0,0.0,0.0) }")
        println("        }")
        println("    }")
        println("    return Mesh(vertices, faces, uvs, color)")
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

    private fun parseAndLoadModel(code: String) {
        println("--- Rozpoczynam import modelu ---")
        try {
            val newVertices = mutableListOf<Vector3d>()
            val newFaces = mutableListOf<Face>()
            val newEdges = mutableListOf<Edge>()

            var inVerticesBlock = false
            var inFacesBlock = false
            var inEdgesBlock = false

            for (line in code.lines()) {
                val trimmedLine = line.trim()

                if (trimmedLine.startsWith("val vertices")) {
                    println("Znaleziono blok 'vertices'.")
                    inVerticesBlock = true; inFacesBlock = false; inEdgesBlock = false
                    continue
                }
                if (trimmedLine.startsWith("val faces")) {
                    println("Zakończono 'vertices'. Znaleziono: ${newVertices.size}.")
                    inVerticesBlock = false; inFacesBlock = true; inEdgesBlock = false
                    continue
                }
                if (trimmedLine.startsWith("val edges")) {
                    println("Zakończono 'faces'. Znaleziono: ${newFaces.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = true
                    continue
                }
                if (trimmedLine.startsWith("val uvs") || trimmedLine.startsWith("return Mesh")) {
                    if (inEdgesBlock) println("Zakończono 'edges'. Znaleziono: ${newEdges.size}.")
                    inVerticesBlock = false; inFacesBlock = false; inEdgesBlock = false
                }

                if (inVerticesBlock && trimmedLine.contains("Vector3d(")) {
                    val content = trimmedLine.substringAfter('(').substringBeforeLast(')')
                    val cleanedContent = content.replace(Regex("[^\\d.,\\s-]"), "")
                    val parts = cleanedContent.split(',').map { it.trim() }
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
                }
            }
            println("--- Zakończono parsowanie pliku ---")

            if (newVertices.isEmpty()) {
                println("BŁĄD KRYTYCZNY: Nie udało się sparsować żadnych wierzchołków.")
                return
            }

            vertices.clear()
            edges.clear()
            faces.clear()
            blushes.clear()
            selectedForFace.clear()
            selectedVertex = null

            vertices.addAll(newVertices)
            edges.addAll(newEdges)
            faces.addAll(newFaces)

            println("Załadowano model: ${vertices.size} wierzchołków, ${edges.size} krawędzi, ${faces.size} ścian.")

        } catch (e: Exception) {
            println("KRYTYCZNY BŁĄD podczas parsowania modelu: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun draw(gc: GraphicsContext, w: Double, h: Double, mouseX: Double, mouseY: Double) {
        gc.fill = Color.BLACK; gc.fillRect(0.0, 0.0, w, h)
        drawGrid(gc, w, h)

        if (angleY > PI * 2) angleY -= PI * 2
        if (angleY < -PI * 2) angleY += PI * 2
        if (angleX < -PI * 2) angleX += PI * 2
        if (angleX > PI * 2) angleX -= PI * 2

        gc.globalAlpha = 0.2; gc.fill = Color.GRAY
        val sortedFaces = try {
            faces.sortedByDescending { f ->
                if (f.indices.any { it >= vertices.size }) 0.0
                else f.indices.map { getZInViewSpace(vertices[it]) }.average()
            }
        } catch (e: Exception) {
            faces
        }

        for (face in sortedFaces) {
            if (face.indices.any { it >= vertices.size }) continue
            val pts = face.indices.map { project(vertices[it], w, h) }
            if (pts.any { !it.first.isFinite() || !it.second.isFinite() }) continue
            gc.beginPath()
            gc.moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) gc.lineTo(pts[i].first, pts[i].second)
            gc.closePath(); gc.fill()
        }
        gc.globalAlpha = 1.0

        // Krawędzie
        gc.stroke = Color.LIME
        for (e in edges) {
            if (e.a >= vertices.size || e.b >= vertices.size) continue
            val p1 = project(vertices[e.a], w, h)
            val p2 = project(vertices[e.b], w, h)
            if (p1.first.isFinite() && p1.second.isFinite() && p2.first.isFinite() && p2.second.isFinite()) {
                gc.strokeLine(p1.first, p1.second, p2.first, p2.second)
            }
        }

        // Punkty
        for ((i, v) in vertices.withIndex()) {
            val p = project(v, w, h)
            if (p.first.isFinite() && p.second.isFinite()) {
                gc.fill = when {
                    groupSelectedVertices.contains(i) -> Color.PURPLE
                    selectedVertex == i -> Color.ORANGE
                    selectedForFace.contains(i) -> Color.CYAN
                    else -> Color.WHITE
                }
                gc.fillOval(p.first - 4, p.second - 4, 8.0, 8.0)
            }
        }
        drawOrientationGizmo(gc, w, h)
        drawVertexInfo(gc, w, h)
        drawGizmo(gc, w, h)
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
        val range = -8..8
        val step = 50.0
        val gridSize = range.last * step + step

        for (i in range) {
            val p1z = Vector3d(i * step, 0.0, -gridSize)
            val p2z = Vector3d(i * step, 0.0, gridSize)
            drawClippedLine(gc, p1z, p2z, w, h)

            val p1x = Vector3d(-gridSize, 0.0, i * step)
            val p2x = Vector3d(gridSize, 0.0, i * step)
            drawClippedLine(gc, p1x, p2x, w, h)
        }
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
            groupSelectedVertices.isNotEmpty() -> groupGizmoPosition
            selectedVertex != null -> vertices.getOrNull(selectedVertex!!)
            else -> null
        } ?: return null

        val origin = project(gizmoOrigin, w, h)
        if (!origin.first.isFinite()) return null

        val clickThreshold = 10.0

        val axes = mapOf(
            'X' to project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoSize), w, h),
            'Y' to project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoSize), w, h),
            'Z' to project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoSize), w, h)
        )

        for ((axis, endPoint) in axes) {
            if (!endPoint.first.isFinite() || !endPoint.second.isFinite()) continue
            val dist = pointToLineSegmentDistance(mouseX, mouseY, origin.first, origin.second, endPoint.first, endPoint.second)
            if (dist < clickThreshold) {
                return axis
            }
        }
        return null
    }

    private fun drawGizmo(gc: GraphicsContext, w: Double, h: Double) {
        val gizmoOrigin = when {
            groupSelectedVertices.isNotEmpty() -> groupGizmoPosition
            selectedVertex != null -> vertices.getOrNull(selectedVertex!!)
            else -> null
        } ?: return

        val origin = project(gizmoOrigin, w, h)
        if (!origin.first.isFinite() || !origin.second.isFinite()) return

        val xAxisEnd = project(gizmoOrigin.copy(x = gizmoOrigin.x - gizmoSize), w, h)
        val yAxisEnd = project(gizmoOrigin.copy(y = gizmoOrigin.y + gizmoSize), w, h)
        val zAxisEnd = project(gizmoOrigin.copy(z = gizmoOrigin.z - gizmoSize), w, h)

        gc.stroke = Color.RED
        gc.lineWidth = if (gizmoAxis == 'X') 3.0 else 1.5
        if (xAxisEnd.first.isFinite() && xAxisEnd.second.isFinite()) {
            gc.strokeLine(origin.first, origin.second, xAxisEnd.first, xAxisEnd.second)
        }

        gc.stroke = Color.GREEN
        gc.lineWidth = if (gizmoAxis == 'Y') 3.0 else 1.5
        if (yAxisEnd.first.isFinite() && yAxisEnd.second.isFinite()) {
            gc.strokeLine(origin.first, origin.second, yAxisEnd.first, yAxisEnd.second)
        }

        gc.stroke = Color.BLUE
        gc.lineWidth = if (gizmoAxis == 'Z') 3.0 else 1.5
        if (zAxisEnd.first.isFinite() && zAxisEnd.second.isFinite()) {
            gc.strokeLine(origin.first, origin.second, zAxisEnd.first, zAxisEnd.second)
        }
        gc.lineWidth = 1.0
    }
}

fun main() {
    Application.launch(ModelEditor::class.java)
}
