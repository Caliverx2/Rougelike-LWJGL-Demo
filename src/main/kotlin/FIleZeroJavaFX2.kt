package org.lewapnoob.FileZero2

import javafx.animation.AnimationTimer
import javafx.scene.canvas.Canvas
import javafx.scene.image.ImageView
import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.ArcType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.*

class DrawingPanel : StackPane() {
    private var lastUpdateTime = System.nanoTime()
    private val isRendering = java.util.concurrent.atomic.AtomicBoolean(false)
    private val cubes = mutableListOf<TransformedCube>()
    private val lightSources = mutableListOf<LightSource>()

    private var cameraPosition = Vector3d(0.0, 0.0, 0.0)
    private var cameraYaw = 2.4
    private var cameraPitch = 0.0

    private var debugFly = false
    private var debugNoclip = false

    private val cubeSize = 100.0
    private val spacing = 0.0

    private val playerHeight = 45.0
    private val playerWidth = 45.0
    private val playerHalfWidth = playerWidth / 2.0

    private val fogColor = Color.rgb(180, 180, 180, 180 / 255.0)
    private val fogStartDistance = 150.0
    private val fogEndDistance = 700.0
    private val fogDensity = 0.6

    private val ambientIntensity = 0.5
    private val maxLightContribution = 1.0

    private val virtualWidth = 1920 / 4
    private val virtualHeight = 1080 / 4
    private lateinit var depthBuffer: Array<DoubleArray>
    private lateinit var backBuffer: WritableImage
    private val imageView = ImageView()
    private val overlayCanvas = Canvas(virtualWidth.toDouble(), virtualHeight.toDouble())

    val pressedKeys = Collections.synchronizedSet(mutableSetOf<KeyCode>())

    private data class SubFace(val worldVertices: List<Vector3d>)

    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val renderQueue = ConcurrentLinkedQueue<RenderableFace>()

    init {
        depthBuffer = Array(virtualWidth) { DoubleArray(virtualHeight) { Double.MAX_VALUE } }
        backBuffer = WritableImage(virtualWidth, virtualHeight)
        val pixelWriter = backBuffer.pixelWriter
        val bgColor = Color.rgb(40, 40, 40)
        for (y in 0 until virtualHeight) {
            for (x in 0 until virtualWidth) {
                pixelWriter.setColor(x, y, bgColor)
            }
        }
        imageView.image = backBuffer
        imageView.fitWidthProperty().bind(this.widthProperty())
        imageView.fitHeightProperty().bind(this.heightProperty())

        overlayCanvas.widthProperty().bind(this.widthProperty())
        overlayCanvas.heightProperty().bind(this.heightProperty())
        overlayCanvas.graphicsContext2D.isImageSmoothing = false

        children.addAll(imageView, overlayCanvas)

        cameraPosition = Vector3d(-250.0, -325.0, 150.0)

        val grids = listOf(GRID_1, GRID_2, GRID_3, GRID_4)
        for (x in 0..8) {
            for (y in grids.indices) {
                for (z in 0..8) {
                    when (grids[y][x][z]) {
                        1 -> GRID_MAP[z][y][x] = 1
                        2 -> GRID_MAP[z][y][x] = 2
                        3 -> GRID_MAP[z][y][x] = 3
                    }
                }
            }
        }

        val baseCubeVertices = mutableListOf<Vector3d>()
        val halfSize = cubeSize / 2.0
        baseCubeVertices.addAll(
            listOf(
                Vector3d(-halfSize, -halfSize, halfSize), Vector3d(halfSize, -halfSize, halfSize),
                Vector3d(halfSize, halfSize, halfSize), Vector3d(-halfSize, halfSize, halfSize),
                Vector3d(-halfSize, -halfSize, -halfSize), Vector3d(halfSize, -halfSize, -halfSize),
                Vector3d(halfSize, halfSize, -halfSize), Vector3d(-halfSize, halfSize, -halfSize)
            )
        )

        val blackBricksTexture = loadImage("textures/black_bricks.png")
        val bricksTexture = loadImage("textures/bricks.jpg")

        for (x in 0..8) {
            for (y in 0..8) {
                for (z in 0..8) {
                    val initialPos = Vector3d(
                        (x - 4.5) * (cubeSize + spacing),
                        (y - 4.5) * (cubeSize + spacing),
                        (z - 4.5) * (cubeSize + spacing)
                    )
                    val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)

                    when (GRID_MAP[x][y][z]) {
                        1 -> cubes.add(TransformedCube(Cube(Color.WHITE, baseCubeVertices, null, initialPos.x, initialPos.y, initialPos.z), transformMatrix = translationMatrix, texture = blackBricksTexture))
                        2 -> cubes.add(TransformedCube(Cube(Color.RED, baseCubeVertices, null, initialPos.x, initialPos.y, initialPos.z), transformMatrix = translationMatrix, texture = blackBricksTexture))
                        3 -> cubes.add(TransformedCube(Cube(Color.rgb(188, 188, 188), baseCubeVertices, null, initialPos.x, initialPos.y, initialPos.z), transformMatrix = translationMatrix, collision = false, texture = bricksTexture))
                    }
                }
            }
        }

        object : AnimationTimer() {
            override fun handle(now: Long) {
                val deltaTime = (now - lastUpdateTime) / 1_000_000_000.0
                lastUpdateTime = now
                updateCameraPosition(deltaTime)
                updateGameLogic()
                requestRender()
            }
        }.start()
    }

    fun handleKeyRelease(code: KeyCode) {
        if (code == KeyCode.DIGIT9 || code == KeyCode.NUMPAD9) {
            debugFly = !debugFly
            println("debugFly: $debugFly")
        }
        if (code == KeyCode.DIGIT8 || code == KeyCode.NUMPAD8) {
            debugNoclip = !debugNoclip
            println("debugNoclip: $debugNoclip")
        }
    }

    private fun updateCameraPosition(deltaTime: Double) {
        var lookDirection = Vector3d(
            cos(cameraPitch) * sin(cameraYaw),
            0.0,
            cos(cameraPitch) * cos(cameraYaw)
        ).normalize()

        if (debugFly) {
            lookDirection = Vector3d(
                cos(cameraPitch) * sin(cameraYaw),
                sin(cameraPitch),
                cos(cameraPitch) * cos(cameraYaw)
            ).normalize()
        }

        val upVector = Vector3d(0.0, 1.0, 0.0)
        val rightVector = lookDirection.cross(upVector).normalize()
        val forwardVector = lookDirection

        var newCameraPosition = Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z)

        var currentMovementSpeed = 350.0
        val playerSprintSpeed = 550.0

        if (pressedKeys.contains(KeyCode.SHIFT)) currentMovementSpeed = playerSprintSpeed

        if (pressedKeys.contains(KeyCode.W)) newCameraPosition += forwardVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.S)) newCameraPosition -= forwardVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.D)) newCameraPosition += rightVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.A)) newCameraPosition -= rightVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.SPACE) && debugFly) newCameraPosition += Vector3d(0.0, currentMovementSpeed, 0.0) * deltaTime
        if (pressedKeys.contains(KeyCode.CONTROL) && debugFly) newCameraPosition -= Vector3d(0.0, currentMovementSpeed, 0.0) * deltaTime

        val oldCameraPosition = Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z)

        val playerMin = Vector3d(newCameraPosition.x - playerHalfWidth, newCameraPosition.y - playerHeight / 2, newCameraPosition.z - playerHalfWidth)
        val playerMax = Vector3d(newCameraPosition.x + playerHalfWidth, newCameraPosition.y + playerHeight / 2, newCameraPosition.z + playerHalfWidth)
        val playerAABB = AABB(playerMin, playerMax)

        var collisionOccurred = false

        for (transformedCube in cubes) {
            val cubeAABB = AABB.fromCube(transformedCube.getTransformedVertices())
            if (playerAABB.intersects(cubeAABB) && transformedCube.collision) {
                collisionOccurred = true
                break
            }
        }

        if (!collisionOccurred || debugNoclip) {
            cameraPosition = newCameraPosition
        } else {
            val testX = AABB(Vector3d(newCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(newCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth))
            var collisionX = false
            for (transformedCube in cubes) {
                if (testX.intersects(AABB.fromCube(transformedCube.getTransformedVertices())) && transformedCube.collision) {
                    collisionX = true
                    break
                }
            }
            if (!collisionX) cameraPosition.x = newCameraPosition.x


            val testY = AABB(Vector3d(oldCameraPosition.x - playerHalfWidth, newCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, newCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth))
            var collisionY = false
            for (transformedCube in cubes) {
                if (testY.intersects(AABB.fromCube(transformedCube.getTransformedVertices())) && transformedCube.collision) {
                    collisionY = true
                    break
                }
            }
            if (!collisionY) cameraPosition.y = newCameraPosition.y


            val testZ = AABB(Vector3d(oldCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, newCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, newCameraPosition.z + playerHalfWidth))
            var collisionZ = false
            for (transformedCube in cubes) {
                if (testZ.intersects(AABB.fromCube(transformedCube.getTransformedVertices())) && transformedCube.collision) {
                    collisionZ = true
                    break
                }
            }
            if (!collisionZ) cameraPosition.z = newCameraPosition.z
        }

        val currentRotationSpeed = 4.0
        if (pressedKeys.contains(KeyCode.LEFT)) cameraYaw += currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.RIGHT)) cameraYaw -= currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.DOWN) && (cameraPitch > -1.3)) cameraPitch -= currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.UP) && (cameraPitch < 1.3)) cameraPitch += currentRotationSpeed * deltaTime

        if (pressedKeys.contains(KeyCode.G)) println("X:${((cameraPosition.x+500)/100).toInt()} Y:${((cameraPosition.y+500)/100).toInt()} Z:${((cameraPosition.z+500)/100).toInt()} YAW:${((cameraYaw*10).toInt()/10.0)} PITCH:${((cameraPitch*10).toInt()/10.0)} SPEED:$currentMovementSpeed")
        if (pressedKeys.contains(KeyCode.R)) {
            val baseCubeVertices = mutableListOf<Vector3d>()
            val halfSize = cubeSize / 2.0
            baseCubeVertices.addAll(
                listOf(
                    Vector3d(-halfSize, -halfSize, halfSize), Vector3d(halfSize, -halfSize, halfSize),
                    Vector3d(halfSize, halfSize, halfSize), Vector3d(-halfSize, halfSize, halfSize),
                    Vector3d(-halfSize, -halfSize, -halfSize), Vector3d(halfSize, -halfSize, -halfSize),
                    Vector3d(halfSize, halfSize, -halfSize), Vector3d(-halfSize, halfSize, -halfSize)
                )
            )
            val initialPos = Vector3d(
                (((cameraPosition.x.toInt()) / 100) + 0.5) * (cubeSize + spacing),
                (((cameraPosition.y.toInt()) / 100) - 0.5) * (cubeSize + spacing),
                (((cameraPosition.z.toInt()) / 100) - 0.5) * (cubeSize + spacing)
            )
            val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
            cubes.add(TransformedCube(Cube(Color.LIGHTGRAY, baseCubeVertices, null, initialPos.x, initialPos.y, initialPos.z), transformMatrix = translationMatrix, texture = loadImage("textures/black_bricks.png")))
        }
        if (pressedKeys.contains(KeyCode.O)) {
            val lightRadius = 5.0 * cubeSize
            lightSources.add(LightSource(Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z), lightRadius, Color.rgb(188, 0, 0, 180 / 255.0)))
            pressedKeys.remove(KeyCode.O)
        }

        if (cameraYaw > 2 * PI) cameraYaw -= 2 * PI
        if (cameraYaw < -2 * PI) cameraYaw += 2 * PI
    }

    private fun drawOverlay() {
        val gc = overlayCanvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, overlayCanvas.width, overlayCanvas.height)
        gc.fill = Color.WHITE
        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][y] == 1) {
                        gc.fillOval(x * 10.0, y * 10.0 + zLevel * 90 + zLevel * 10, 10.0, 10.0)
                    }
                }
            }
        }
    }

    private fun updateGameLogic() {
        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][y] == 3) {
                        if ((((cameraPosition.x + 500) / 100).toInt() == x) &&
                            (((cameraPosition.y + 500) / 100).toInt() == zLevel) &&
                            (((cameraPosition.z + 500) / 100).toInt() == y)) {

                            val blockx = (x - 4.5) * (cubeSize + spacing)
                            val blocky = (zLevel - 4.5) * (cubeSize + spacing)
                            val blockz = (y - 4.5) * (cubeSize + spacing)

                            GRID_MAP[x][zLevel][y] = 0
                            val cubeToRemove = cubes.find { abs(it.cube.x - blockx) < 1 && abs(it.cube.y - blocky) < 1 && abs(it.cube.z - blockz) < 1 }
                            if (cubeToRemove != null) {
                                println("$cubeToRemove X:${((cameraPosition.x+500)/100).toInt()} Y:${((cameraPosition.y+500)/100).toInt()} Z:${((cameraPosition.z+500)/100).toInt()}")
                                cubes.remove(cubeToRemove)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateIlluminatedFaces(cubes: List<TransformedCube>, lightSources: List<LightSource>): Map<SubFace, Color> {
        val illuminatedSubFaceColors = ConcurrentHashMap<SubFace, Color>()
        val allFacesWithWorldVertices = cubes.flatMap { transformedCube ->
            val worldVertices = transformedCube.cube.vertices.map { transformedCube.transformMatrix.transform(it) }
            transformedCube.cube.faces.map { faceIndices ->
                val faceWorldVertices = faceIndices.map { worldVertices[it] }
                Pair(transformedCube, faceWorldVertices)
            }
        }

        val lightTasks = lightSources.map { light ->
            executor.submit {
                for ((transformedCube, faceWorldVertices) in allFacesWithWorldVertices) {
                    if (faceWorldVertices.size != 4) continue

                    val v0 = faceWorldVertices[0]; val v1 = faceWorldVertices[1]; val v2 = faceWorldVertices[2]; val v3 = faceWorldVertices[3]
                    val center = (v0 + v1 + v2 + v3) / 4.0

                    val subTriangles = listOf(listOf(v0, v1, center), listOf(v1, v2, center), listOf(v2, v3, center), listOf(v3, v0, center))

                    for (subTriangleVertices in subTriangles) {
                        val subFace = SubFace(subTriangleVertices)
                        val subFaceCenter = (subTriangleVertices[0] + subTriangleVertices[1] + subTriangleVertices[2]) / 3.0
                        val subFaceNormal = (subTriangleVertices[1] - subTriangleVertices[0]).cross(subTriangleVertices[2] - subTriangleVertices[0]).normalize()
                        val lightToFaceVector = subFaceCenter - light.position
                        val distanceToFace = lightToFaceVector.length()

                        if (distanceToFace > light.radius) continue

                        val lightDirection = lightToFaceVector.normalize()
                        val dotProduct = subFaceNormal.dot(lightDirection)

                        if (dotProduct <= 0.0 && !isOccludedByGrid(light.position, subFaceCenter)) {
                            val attenuation = 1.0 - (distanceToFace / light.radius).coerceIn(0.0, 1.0)
                            val lightInfluence = light.intensity * attenuation

                            val baseColor = transformedCube.cube.color
                            val dynamicLightR = (baseColor.red * light.color.red * lightInfluence * maxLightContribution)
                            val dynamicLightG = (baseColor.green * light.color.green * lightInfluence * maxLightContribution)
                            val dynamicLightB = (baseColor.blue * light.color.blue * lightInfluence * maxLightContribution)

                            val currentIlluminatedColor = illuminatedSubFaceColors.getOrDefault(subFace, Color.BLACK)
                            val combinedR = currentIlluminatedColor.red + dynamicLightR
                            val combinedG = currentIlluminatedColor.green + dynamicLightG
                            val combinedB = currentIlluminatedColor.blue + dynamicLightB

                            illuminatedSubFaceColors[subFace] = Color(combinedR, combinedG, combinedB, 1.0).saturate()
                        }
                    }
                }
            }
        }
        lightTasks.forEach { it.get() }
        return illuminatedSubFaceColors
    }

    private fun clipTriangleAgainstNearPlane(worldVertices: List<Vector3d>, uvs: List<Vector3d>, viewMatrix: Matrix4x4, nearPlane: Double): List<Pair<List<Vector3d>, List<Vector3d>>> {
        val viewVertices = worldVertices.map { viewMatrix.transform(it) }
        val insidePoints = mutableListOf<Vector3d>(); val insideUvs = mutableListOf<Vector3d>()
        val outsidePoints = mutableListOf<Vector3d>(); val outsideUvs = mutableListOf<Vector3d>()
        val clipZ = -nearPlane

        for (i in viewVertices.indices) {
            if (viewVertices[i].z < clipZ) {
                insidePoints.add(worldVertices[i]); insideUvs.add(uvs[i])
            } else {
                outsidePoints.add(worldVertices[i]); outsideUvs.add(uvs[i])
            }
        }

        when (insidePoints.size) {
            0 -> return emptyList()
            3 -> return listOf(Pair(worldVertices, uvs))
            1 -> {
                val pIn = insidePoints[0]; val uvIn = insideUvs[0]
                val pOut1 = outsidePoints[0]; val uvOut1 = outsideUvs[0]
                val pOut2 = outsidePoints[1]; val uvOut2 = outsideUvs[1]
                val vInView = viewMatrix.transform(pIn); val vOut1View = viewMatrix.transform(pOut1); val vOut2View = viewMatrix.transform(pOut2)
                val t1 = (clipZ - vInView.z) / (vOut1View.z - vInView.z)
                val newPoint1 = pIn + (pOut1 - pIn) * t1; val newUv1 = uvIn + (uvOut1 - uvIn) * t1
                val t2 = (clipZ - vInView.z) / (vOut2View.z - vInView.z)
                val newPoint2 = pIn + (pOut2 - pIn) * t2; val newUv2 = uvIn + (uvOut2 - uvIn) * t2
                return listOf(Pair(listOf(pIn, newPoint1, newPoint2), listOf(uvIn, newUv1, newUv2)))
            }
            2 -> {
                val pIn1 = insidePoints[0]; val uvIn1 = insideUvs[0]
                val pIn2 = insidePoints[1]; val uvIn2 = insideUvs[1]
                val pOut = outsidePoints[0]; val uvOut = outsideUvs[0]
                val vIn1View = viewMatrix.transform(pIn1); val vIn2View = viewMatrix.transform(pIn2); val vOutView = viewMatrix.transform(pOut)
                val t1 = (clipZ - vIn1View.z) / (vOutView.z - vIn1View.z)
                val newPoint1 = pIn1 + (pOut - pIn1) * t1; val newUv1 = uvIn1 + (uvOut - uvIn1) * t1
                val t2 = (clipZ - vIn2View.z) / (vOutView.z - vIn2View.z)
                val newPoint2 = pIn2 + (pOut - pIn2) * t2; val newUv2 = uvIn2 + (uvOut - uvIn2) * t2
                return listOf(Pair(listOf(pIn1, pIn2, newPoint1), listOf(uvIn1, uvIn2, newUv1)), Pair(listOf(newPoint1, pIn2, newPoint2), listOf(newUv1, uvIn2, newUv2)))
            }
        }
        return emptyList()
    }

    private fun requestRender() {
        if (isRendering.getAndSet(true)) return
        if (virtualWidth <= 0 || virtualHeight <= 0) { isRendering.set(false); return }

        if (depthBuffer.size != virtualWidth || depthBuffer[0].size != virtualHeight) {
            depthBuffer = Array(virtualWidth) { DoubleArray(virtualHeight) { Double.MAX_VALUE } }
            backBuffer = WritableImage(virtualWidth, virtualHeight)
        }
        for (x in 0 until virtualWidth) { for (y in 0 until virtualHeight) { depthBuffer[x][y] = Double.MAX_VALUE } }

        val pixelWriter = backBuffer.pixelWriter
        val bgColor = Color.rgb(40, 40, 40)
        for (y in 0 until virtualHeight) {
            for (x in 0 until virtualWidth) {
                pixelWriter.setColor(x, y, bgColor)
            }
        }

        renderQueue.clear()

        val lookDirection = Vector3d(cos(cameraPitch) * sin(cameraYaw), sin(cameraPitch), cos(cameraPitch) * cos(cameraYaw)).normalize()
        val upVector = Vector3d(0.0, 1.0, 0.0)
        val viewMatrix = Matrix4x4.lookAt(cameraPosition, cameraPosition + lookDirection, upVector)
        val projectionMatrix = Matrix4x4.perspective(90.0, virtualWidth.toDouble() / virtualHeight.toDouble(), 0.1, 1200.0)
        val combinedMatrix = projectionMatrix * viewMatrix
        val illuminatedFacesMap = calculateIlluminatedFaces(cubes, lightSources)
        val tasks = mutableListOf<Future<*>>()

        for (transformedCube in cubes) {
            tasks.add(executor.submit {
                val worldVertices = transformedCube.cube.vertices.map { transformedCube.transformMatrix.transform(it) }
                for (faceIndex in transformedCube.cube.faces.indices) {
                    val faceIndices = transformedCube.cube.faces[faceIndex]
                    val p0View = viewMatrix.transform(worldVertices[faceIndices[0]]); val p1View = viewMatrix.transform(worldVertices[faceIndices[1]]); val p2View = viewMatrix.transform(worldVertices[faceIndices[2]])
                    val normalView = (p1View - p0View).cross(p2View - p0View).normalize()
                    val cameraRayView = (Vector3d(0.0, 0.0, 0.0) - p0View).normalize()

                    if (normalView.dot(cameraRayView) > 0) {
                        val originalFaceWorldVertices = faceIndices.map { worldVertices[it] }
                        if (originalFaceWorldVertices.size != 4) continue
                        val v0_world = originalFaceWorldVertices[0]; val v1_world = originalFaceWorldVertices[1]; val v2_world = originalFaceWorldVertices[2]; val v3_world = originalFaceWorldVertices[3]
                        val center_world = (v0_world + v1_world + v2_world + v3_world) / 4.0
                        val subTrianglesWorld = listOf(listOf(v0_world, v1_world, center_world), listOf(v1_world, v2_world, center_world), listOf(v2_world, v3_world, center_world), listOf(v3_world, v0_world, center_world))
                        val faceTexCoords = transformedCube.cube.faceTextureCoords[faceIndex]
                        val uv0 = faceTexCoords[0]; val uv1 = faceTexCoords[1]; val uv2 = faceTexCoords[2]; val uv3 = faceTexCoords[3]
                        val uv_center = (uv0 + uv1 + uv2 + uv3) / 4.0
                        val subTrianglesUVs = listOf(listOf(uv0, uv1, uv_center), listOf(uv1, uv2, uv_center), listOf(uv2, uv3, uv_center), listOf(uv3, uv0, uv_center))

                        for (i in subTrianglesWorld.indices) {
                            val subTriangleWorldVertices = subTrianglesWorld[i]
                            val subTriangleUVsAsVec3 = subTrianglesUVs[i].map { Vector3d(it.x, it.y, 0.0) }
                            val clippedTriangles = clipTriangleAgainstNearPlane(subTriangleWorldVertices, subTriangleUVsAsVec3, viewMatrix, 0.1)

                            for ((clippedWorldVertices, clippedUVs) in clippedTriangles) {
                                val projectedVertices = mutableListOf<Vector3d>()
                                val originalClipW = mutableListOf<Double>()
                                for (vertex in clippedWorldVertices) {
                                    val projectedHomogeneous = combinedMatrix.transformHomogeneous(vertex); val w = projectedHomogeneous.w
                                    if (w.isCloseToZero()) continue
                                    projectedVertices.add(Vector3d((projectedHomogeneous.x / w + 1) * virtualWidth / 2.0, (1 - projectedHomogeneous.y / w) * virtualHeight / 2.0, projectedHomogeneous.z / w))
                                    originalClipW.add(w)
                                }
                                if (projectedVertices.size == 3) {
                                    val subFaceIdentifier = SubFace(clippedWorldVertices)
                                    val dynamicLightColor = illuminatedFacesMap[subFaceIdentifier]
                                    val finalCalculatedColor = if (dynamicLightColor != null) {
                                        val finalR = dynamicLightColor.red + transformedCube.cube.color.red * ambientIntensity
                                        val finalG = dynamicLightColor.green + transformedCube.cube.color.green * ambientIntensity
                                        val finalB = dynamicLightColor.blue + transformedCube.cube.color.blue * ambientIntensity
                                        Color(finalR, finalG, finalB, 1.0).saturate()
                                    } else {
                                        Color(transformedCube.cube.color.red * ambientIntensity, transformedCube.cube.color.green * ambientIntensity, transformedCube.cube.color.blue * ambientIntensity, 1.0).saturate()
                                    }
                                    renderQueue.add(RenderableFace(projectedVertices, originalClipW, clippedUVs, transformedCube.cube.color, false, transformedCube.texture, clippedWorldVertices, finalCalculatedColor))
                                }
                            }
                        }
                    }
                }
            })
        }

        for (task in tasks) { task.get() }

        while (renderQueue.isNotEmpty()) {
            val renderableFace = renderQueue.poll()
            if (renderableFace.isEdge) {
                if (renderableFace.screenVertices.size == 2) {
                    drawLineZBuffered(pixelWriter, renderableFace.screenVertices[0].x.toInt(), renderableFace.screenVertices[0].y.toInt(), renderableFace.screenVertices[0].z,
                        renderableFace.screenVertices[1].x.toInt(), renderableFace.screenVertices[1].y.toInt(), renderableFace.screenVertices[1].z,
                        renderableFace.color, virtualWidth, virtualHeight)
                }
            } else {
                rasterizeTexturedTriangle(pixelWriter, renderableFace, virtualWidth, virtualHeight)
            }
        }

        imageView.image = backBuffer
        drawOverlay()
        isRendering.set(false)
    }

    private fun rasterizeTexturedTriangle(pixelWriter: PixelWriter, renderableFace: RenderableFace, screenWidth: Int, screenHeight: Int) {
        val (screenVertices, originalClipW, textureVertices, _, _, texture, _, illuminatedColor) = renderableFace
        if (screenVertices.size != 3 || textureVertices.size != 3 || originalClipW.size != 3 || texture == null) return

        val v0 = screenVertices[0]; val v1 = screenVertices[1]; val v2 = screenVertices[2]
        val uv0 = textureVertices[0]; val uv1 = textureVertices[1]; val uv2 = textureVertices[2]
        val w0 = originalClipW[0]; val w1 = originalClipW[1]; val w2 = originalClipW[2]
        val texReader = texture.pixelReader

        val minX = max(0, minOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt()))
        val maxX = min(screenWidth - 1, maxOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt()))
        val minY = max(0, minOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt()))
        val maxY = min(screenHeight - 1, maxOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt()))

        val A12 = v1.y - v2.y; val B12 = v2.x - v1.x; val C12 = v1.x * v2.y - v2.x * v1.y
        val A20 = v2.y - v0.y; val B20 = v0.x - v2.x; val C20 = v2.x * v0.y - v0.x * v2.y
        val A01 = v0.y - v1.y; val B01 = v1.x - v0.x; val C01 = v0.x * v1.y - v1.x * v0.y

        val totalArea = A12 * v0.x + B12 * v0.y + C12
        if (totalArea.isCloseToZero()) return
        val invTotalArea = 1.0 / totalArea

        val u0_prime = uv0.x / w0; val v0_prime = uv0.y / w0; val z0_inv_prime = 1.0 / w0
        val u1_prime = uv1.x / w1; val v1_prime = uv1.y / w1; val z1_inv_prime = 1.0 / w1
        val u2_prime = uv2.x / w2; val v2_prime = uv2.y / w2; val z2_inv_prime = 1.0 / w2

        for (py in minY..maxY) {
            for (px in minX..maxX) {
                val barycentric_w0 = A12 * px + B12 * py + C12
                val barycentric_w1 = A20 * px + B20 * py + C20
                val barycentric_w2 = A01 * px + B01 * py + C01

                val epsilon = 0.01
                val isInside = (barycentric_w0 >= -epsilon && barycentric_w1 >= -epsilon && barycentric_w2 >= -epsilon) ||
                        (barycentric_w0 <= epsilon && barycentric_w1 <= epsilon && barycentric_w2 <= epsilon)

                if (isInside) {
                    val alpha = barycentric_w0 * invTotalArea
                    val beta = barycentric_w1 * invTotalArea
                    val gamma = barycentric_w2 * invTotalArea
                    val interpolatedZ = alpha * v0.z + beta * v1.z + gamma * v2.z

                    if (px in 0 until screenWidth && py in 0 until screenHeight && interpolatedZ < depthBuffer[px][py]) {
                        val interpolated_z_inv_prime = alpha * z0_inv_prime + beta * z1_inv_prime + gamma * z2_inv_prime
                        if (interpolated_z_inv_prime.isCloseToZero()) continue

                        val u = (alpha * u0_prime + beta * u1_prime + gamma * u2_prime) / interpolated_z_inv_prime
                        val v = (alpha * v0_prime + beta * v1_prime + gamma * v2_prime) / interpolated_z_inv_prime
                        val texX = (u * (texture.width - 1)).toInt().coerceIn(0, texture.width.toInt() - 1)
                        val texY = (v * (texture.height - 1)).toInt().coerceIn(0, texture.height.toInt() - 1)

                        val finalFaceIlluminationColor = illuminatedColor ?: renderableFace.color
                        val texColor = texReader.getColor(texX, texY)

                        val litR = texColor.red * finalFaceIlluminationColor.red
                        val litG = texColor.green * finalFaceIlluminationColor.green
                        val litB = texColor.blue * finalFaceIlluminationColor.blue
                        var finalPixelColor = Color(litR, litG, litB, 1.0).saturate()

                        val distance = 1.0 / interpolated_z_inv_prime
                        var linearFogFactor = ((distance - fogStartDistance) / (fogEndDistance - fogStartDistance)).coerceIn(0.0, 1.0)
                        val fogFactor = linearFogFactor * fogDensity.coerceIn(0.0, 1.0)

                        val r = finalPixelColor.red * (1 - fogFactor) + fogColor.red * fogFactor
                        val g = finalPixelColor.green * (1 - fogFactor) + fogColor.green * fogFactor
                        val b = finalPixelColor.blue * (1 - fogFactor) + fogColor.blue * fogFactor
                        finalPixelColor = Color(r,g,b,1.0).saturate()

                        pixelWriter.setColor(px, py, finalPixelColor)
                        depthBuffer[px][py] = interpolatedZ
                    }
                }
            }
        }
    }

    private fun isOccludedByGrid(start: Vector3d, end: Vector3d): Boolean {
        val currentX = ((start.x + cubeSize / 2) / cubeSize).toInt() + 4
        val currentY = ((start.y + cubeSize / 2) / cubeSize).toInt() + 4
        val currentZ = ((start.z + cubeSize / 2) / cubeSize).toInt() + 4
        val targetX = ((end.x + cubeSize / 2) / cubeSize).toInt() + 4
        val targetY = ((end.y + cubeSize / 2) / cubeSize).toInt() + 4
        val targetZ = ((end.z + cubeSize / 2) / cubeSize).toInt() + 4

        val dx = (targetX - currentX).toDouble(); val dy = (targetY - currentY).toDouble(); val dz = (targetZ - currentZ).toDouble()
        val steps = maxOf(abs(dx), abs(dy), abs(dz)).toInt()
        if (steps == 0) return false

        for (i in 1..steps) {
            val t = i.toDouble() / steps.toDouble()
            val gridX = (currentX + (dx * t)).toInt().coerceIn(0, 8)
            val gridY = (currentY + (dy * t)).toInt().coerceIn(0, 8)
            val gridZ = (currentZ + (dz * t)).toInt().coerceIn(0, 8)

            if (GRID_MAP[gridX][gridY][gridZ] == 1) {
                val blockWorldMin = Vector3d((gridX - 4.5) * (cubeSize + spacing), (gridY - 4.5) * (cubeSize + spacing), (gridZ - 4.5) * (cubeSize + spacing))
                val blockCenter = Vector3d(blockWorldMin.x + cubeSize / 2, blockWorldMin.y + cubeSize / 2, blockWorldMin.z + cubeSize / 2)
                if ((blockCenter - start).length() < (end - start).length() - 10) {
                    return true
                }
            }
        }
        return false
    }

    private fun drawLineZBuffered(pixelWriter: PixelWriter, x0: Int, y0: Int, z0: Double, x1: Int, y1: Int, z1: Double, color: Color, screenWidth: Int, screenHeight: Int) {
        var currX0 = x0; var currY0 = y0
        val dx = abs(x1 - currX0); val dy = abs(y1 - currY0)
        val sx = if (currX0 < x1) 1 else -1; val sy = if (currY0 < y1) 1 else -1
        var err = dx - dy
        val lineColor = Color.rgb(40,40,40)

        val lineLengthSq = (x1 - x0).toDouble().pow(2) + (y1 - y0).toDouble().pow(2)
        val invLineLength = if (lineLengthSq == 0.0) 0.0 else 1.0 / sqrt(lineLengthSq)

        while (true) {
            val t = sqrt((currX0 - x0).toDouble().pow(2) + (currY0 - y0).toDouble().pow(2)) * invLineLength
            val interpolatedZ = z0 + t * (z1 - z0)

            if (currX0 in 0 until screenWidth && currY0 in 0 until screenHeight && interpolatedZ < depthBuffer[currX0][currY0]) {
                pixelWriter.setColor(currX0, currY0, lineColor)
                depthBuffer[currX0][currY0] = interpolatedZ
            }
            if (currX0 == x1 && currY0 == y1) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; currX0 += sx }
            if (e2 < dx) { err += dx; currY0 += sy }
        }
    }
}