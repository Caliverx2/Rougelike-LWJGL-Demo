package org.lewapnoob.FileZeroModel3DEditor

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import kotlin.math.*

data class Vector3d(val x: Double, val y: Double, val z: Double)
data class Edge(val a: Int, val b: Int)
data class Face(val indices: List<Int>)
data class Mesh(val vertices: List<Vector3d>, val faces: List<List<Int>>, val faceUVs: List<List<Vector3d>>, val color: Color)

class ModelEditor : Application() {
    private val vertices = mutableListOf<Vector3d>()
    private val edges = mutableListOf<Edge>()
    private val faces = mutableListOf<Face>()

    private var dragStartVertex: Int? = null
    private val selectedForFace = mutableListOf<Int>()
    private var selectedVertex: Int? = null

    private var angleX = -2.4
    private var angleY = 0.2
    private var zoom = 300.0

    private var deleteMode = false
    private var faceSelectMode = false // tryb Q

    override fun start(stage: Stage) {
        val canvas = Canvas((1920.0*2)/3, (1080.0*2)/3)
        val gc = canvas.graphicsContext2D

        canvas.setOnMousePressed { e ->
            val clickedVertex = findClosestVertex(e.x, e.y, canvas.width, canvas.height)

            if (faceSelectMode && e.button == MouseButton.PRIMARY && clickedVertex != null) {
                if (!selectedForFace.contains(clickedVertex)) {
                    selectedForFace.add(clickedVertex)
                }
                draw(gc, canvas.width, canvas.height, e.x, e.y)
                return@setOnMousePressed
            }

            if (deleteMode && e.button == MouseButton.PRIMARY && clickedVertex == null) {
                val faceIndex = findFaceUnderCursor(e.x, e.y, canvas.width, canvas.height)
                if (faceIndex != null) {
                    faces.removeAt(faceIndex)
                }
                draw(gc, canvas.width, canvas.height, e.x, e.y)
                return@setOnMousePressed
            }

            if (e.button == MouseButton.PRIMARY) {
                if (clickedVertex == null) {
                    vertices.add(Vector3d(0.0, 0.0, 0.0))
                    selectedVertex = vertices.size - 1
                } else {
                    selectedVertex = clickedVertex
                    dragStartVertex = clickedVertex
                }
            }
            draw(gc, canvas.width, canvas.height, e.x, e.y)
        }

        canvas.setOnMouseReleased { e ->
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

                KeyCode.UP -> moveSelectedVertex(0.0, 0.0, -5.0)
                KeyCode.DOWN -> moveSelectedVertex(0.0, 0.0, 5.0)
                KeyCode.LEFT -> moveSelectedVertex(-5.0, 0.0, 0.0)
                KeyCode.RIGHT -> moveSelectedVertex(5.0, 0.0, 0.0)
                KeyCode.PLUS, KeyCode.EQUALS -> ifSelectedMoveY(5.0)
                KeyCode.MINUS -> ifSelectedMoveY(-5.0)
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
                KeyCode.E -> exportMeshFunction()
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

    private fun moveSelectedVertex(dx: Double, dy: Double, dz: Double) {
        selectedVertex?.let {
            vertices[it] = vertices[it].copy(
                x = vertices[it].x + dx,
                y = vertices[it].y + dy,
                z = vertices[it].z + dz
            )
        }
    }

    private fun ifSelectedMoveY(dy: Double) {
        selectedVertex?.let {
            vertices[it] = vertices[it].copy(
                y = vertices[it].y + dy
            )
        }
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

    private fun findFaceUnderCursor(mx: Double, my: Double, w: Double, h: Double): Int? {
        for ((i, face) in faces.withIndex()) {
            val pts = face.indices.map { project(vertices[it], w, h) }
            if (pts.any { !it.first.isFinite() || !it.second.isFinite() }) continue
            if (pointInPolygon(mx, my, pts)) return i
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
            newEdges.add(Edge(p3,p4))
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
            newEdges.add(Edge(p3,p4))
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
            newEdges.add(Edge(p1,p3))
            newEdges.add(Edge(p3,p4))
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
        println("fun createCustomMesh(color: Color): Mesh {")
        println("    val vertices = listOf(")
        for (v in vertices) println("        Vector3d(${v.x}, ${v.y}, ${v.z}),")
        println("    )")
        println("    val faces: List<List<Int>> = listOf(")
        for (f in faces) println("        listOf(${f.indices.joinToString(", ")}),")
        println("    )")
        println("    val uvs: List<List<Vector3d>> = faces.map { face ->")
        println("        when(face.size) {")
        println("            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))")
        println("            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))")
        println("            else -> face.map { Vector3d(0.0,0.0,0.0) }")
        println("        }")
        println("    }")
        println("    return Mesh(vertices, faces, uvs, color)")
        println("}")
    }

    private fun draw(gc: GraphicsContext, w: Double, h: Double, mouseX: Double, mouseY: Double) {
        gc.fill = Color.BLACK; gc.fillRect(0.0, 0.0, w, h)
        drawGrid(gc, w, h); drawAxisMarker(gc, w, h)

        if (angleY > PI * 2) angleY -= PI * 2
        if (angleY < -PI * 2) angleY += PI * 2
        if (angleX < -PI * 2) angleX += PI * 2
        if (angleX > PI * 2) angleX -= PI * 2

        gc.globalAlpha = 0.2; gc.fill = Color.GRAY
        for (face in faces.sortedByDescending { f -> f.indices.map { vertices[it].z }.average() }) {
            val pts = face.indices.map { project(vertices[it], w, h) }
            if (pts.any { !it.first.isFinite() || !it.second.isFinite() }) continue
            gc.beginPath()
            gc.moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) gc.lineTo(pts[i].first, pts[i].second)
            gc.closePath(); gc.fill()
            // przekątna
            gc.globalAlpha = 1.0; gc.stroke = Color.RED
            val diagStart = pts[0]
            val diagEnd = pts[pts.size / 2]
            gc.strokeLine(diagStart.first, diagStart.second, diagEnd.first, diagEnd.second)
            gc.globalAlpha = 0.2
        }
        gc.globalAlpha = 1.0

        // Krawędzie
        gc.stroke = Color.LIME
        for (e in edges) {
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
                    selectedVertex == i -> Color.ORANGE
                    selectedForFace.contains(i) -> Color.CYAN
                    else -> Color.WHITE
                }
                gc.fillOval(p.first - 4, p.second - 4, 8.0, 8.0)
            }
        }
        drawCompass(gc, w, h)
        drawVertexInfo(gc, w, h)
    }

    private fun drawClippedLine(gc: GraphicsContext, p1World: Vector3d, p2World: Vector3d, w: Double, h: Double) {
        val zNearClip = -399.0

        fun getZInViewSpace(v: Vector3d): Double {
            val cosY = cos(angleY); val sinY = sin(angleY)
            val cosX = cos(angleX); val sinX = sin(angleX)
            val zAfterYRot = v.x * sinY + v.z * cosY
            return v.y * sinX + zAfterYRot * cosX
        }

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

    private fun drawAxisMarker(gc: GraphicsContext, w: Double, h: Double) {
        val axisLength = 25.0

        val originWorld = Vector3d(0.0, 0.0, 0.0)
        val leftWorld = Vector3d(-axisLength, 0.0, 0.0)
        val upWorld = Vector3d(0.0, axisLength, 0.0)
        val forwardWorld = Vector3d(0.0, 0.0, -axisLength)

        val origin = project(originWorld, w, h)
        val left = project(leftWorld, w, h)
        val up = project(upWorld, w, h)
        val forward = project(forwardWorld, w, h)

        if (origin.first.isFinite() && left.first.isFinite()) {
            gc.stroke = Color.RED
            gc.strokeLine(origin.first, origin.second, left.first, left.second)
        }

        if (origin.first.isFinite() && up.first.isFinite()) {
            gc.stroke = Color.GREEN
            gc.strokeLine(origin.first, origin.second, up.first, up.second)
        }

        if (origin.first.isFinite() && forward.first.isFinite()) {
            gc.stroke = Color.BLUE
            gc.strokeLine(origin.first, origin.second, forward.first, forward.second)
        }
    }

    private fun drawCompass(gc: GraphicsContext, w: Double, h: Double) {
        val radius = 40.0; val cx = w - 60; val cy = h - 60
        gc.stroke = Color.WHITE; gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2)
        val zx = cx + radius * sin(angleY); val zy = cy - radius * cos(angleY)
        gc.stroke = Color.BLUE; gc.strokeLine(cx, cy, zx, zy)
        val xx = cx + radius * sin(angleY + PI / 2); val xy = cy - radius * cos(angleY + PI / 2)
        gc.stroke = Color.YELLOW; gc.strokeLine(cx, cy, xx, xy)
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
        var x = v.x * cosY - v.z * sinY
        var z = v.x * sinY + v.z * cosY
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
}

fun main() {
    Application.launch(ModelEditor::class.java)
}