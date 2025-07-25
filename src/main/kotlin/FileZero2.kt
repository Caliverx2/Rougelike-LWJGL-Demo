package org.lewapnoob.FileZero

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DrawingPanel : JPanel() {
    private val cubes = mutableListOf<TransformedCube>()
    private val lightSources = mutableListOf<LightSource>()

    private var cameraPosition = Vector3d(0.0, 0.0, 0.0)
    private var cameraYaw = 2.4
    private var cameraPitch = 0.0
    private var movementSpeed = 20.0
    private val rotationSpeed = 0.05

    private var debugFly = false
    private var debugNoclip = false

    private val cubeSize = 100.0
    private val spacing = 0.0

    private val playerHeight = 45.0
    private val playerWidth = 45.0
    private val playerHalfWidth = playerWidth / 2.0

    private val fogColor = Color(180, 180, 180, 180)
    private val fogStartDistance = 150.0
    private val fogEndDistance = 700.0
    private val fogDensity = 0.6

    private val ambientIntensity = 0.5
    private val maxLightContribution = 1.0//255.0

    private val virtualWidth = 1920/4//(50 * 1.77).toInt()
    private val virtualHeight = 1080/4//(50).toInt()
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
        cameraPosition = Vector3d(-250.0, -325.0, 150.0)

        val grids = listOf(GRID_1, GRID_2, GRID_3, GRID_4)
        for (x in 0..8) {
            for (y in 0..grids.size-1) {
                for (z in 0..8) {
                    if (grids[y][x][z] == 1) {
                        GRID_MAP[z][y][x] = 1
                    }
                    if (grids[y][x][z] == 2) {
                        GRID_MAP[z][y][x] = 2
                    }
                    if (grids[y][x][z] == 3) {
                        GRID_MAP[z][y][x] = 3
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
                            (x - 4.5) * (cubeSize + spacing),
                            (y - 4.5) * (cubeSize + spacing),
                            (z - 4.5) * (cubeSize + spacing)
                        )
                        val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
                        cubes.add(TransformedCube(Cube(Color(255, 255, 255), baseCubeVertices, null, initialPos.x, initialPos.y, initialPos. z), transformMatrix = translationMatrix))
                    }
                    if (GRID_MAP[x][y][z] == 2) {
                        val initialPos = Vector3d(
                            (x - 4.5) * (cubeSize + spacing),
                            (y - 4.5) * (cubeSize + spacing),
                            (z - 4.5) * (cubeSize + spacing)
                        )
                        val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
                        cubes.add(TransformedCube(Cube(Color(255, 0, 0), baseCubeVertices, null, initialPos.x, initialPos.y, initialPos. z), transformMatrix = translationMatrix))
                    }
                    if (GRID_MAP[x][y][z] == 3) {
                        val initialPos = Vector3d(
                            (x - 4.5) * (cubeSize + spacing),
                            (y - 4.5) * (cubeSize + spacing),
                            (z - 4.5) * (cubeSize + spacing)
                        )
                        val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
                        cubes.add(TransformedCube(Cube(Color(188, 188, 188), baseCubeVertices, null, initialPos.x, initialPos.y, initialPos. z), transformMatrix = translationMatrix, collision = false, texture = ImageIO.read(DrawingPanel::class.java.classLoader.getResource("textures/bricks.jpg"))))
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

        movementSpeed = 20.0
        if (pressedKeys.contains(KeyEvent.VK_SHIFT)) movementSpeed = 30.0
        if (pressedKeys.contains(KeyEvent.VK_W)) newCameraPosition += forwardVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_S)) newCameraPosition -= forwardVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_D)) newCameraPosition += rightVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_A)) newCameraPosition -= rightVector * movementSpeed
        if (pressedKeys.contains(KeyEvent.VK_SPACE) and debugFly) newCameraPosition += Vector3d(0.0, movementSpeed, 0.0)
        if (pressedKeys.contains(KeyEvent.VK_CONTROL) and debugFly) newCameraPosition -= Vector3d(0.0, movementSpeed, 0.0)

        val oldCameraPosition = Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z)

        val playerMin = Vector3d(newCameraPosition.x - playerHalfWidth, newCameraPosition.y - playerHeight / 2, newCameraPosition.z - playerHalfWidth)
        val playerMax = Vector3d(newCameraPosition.x + playerHalfWidth, newCameraPosition.y + playerHeight / 2, newCameraPosition.z + playerHalfWidth)
        val playerAABB = AABB(playerMin, playerMax)

        var collisionOccurred = false

        for (transformedCube in cubes) {
            val cubeAABB = AABB.fromCube(transformedCube.getTransformedVertices())

            if (playerAABB.intersects(cubeAABB) and (transformedCube.collision)) {
                collisionOccurred = true
                break
            }
        }

        if (!collisionOccurred) {
            cameraPosition = newCameraPosition
        } else if (debugNoclip) {
            cameraPosition = newCameraPosition
        } else {
            val testX = AABB(Vector3d(newCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(newCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth))
            var collisionX = false
            for (transformedCube in cubes) {
                if (testX.intersects(AABB.fromCube(transformedCube.getTransformedVertices()))) {
                    collisionX = true
                    break
                }
            }
            if (!collisionX) cameraPosition.x = newCameraPosition.x


            val testY = AABB(Vector3d(oldCameraPosition.x - playerHalfWidth, newCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, newCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth))
            var collisionY = false
            for (transformedCube in cubes) {
                if (testY.intersects(AABB.fromCube(transformedCube.getTransformedVertices()))) {
                    collisionY = true
                    break
                }
            }
            if (!collisionY) cameraPosition.y = newCameraPosition.y


            val testZ = AABB(Vector3d(oldCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, newCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, newCameraPosition.z + playerHalfWidth))
            var collisionZ = false
            for (transformedCube in cubes) {
                if (testZ.intersects(AABB.fromCube(transformedCube.getTransformedVertices()))) {
                    collisionZ = true
                    break
                }
            }
            if (!collisionZ) cameraPosition.z = newCameraPosition.z
        }

        if (pressedKeys.contains(KeyEvent.VK_LEFT)) cameraYaw += rotationSpeed * 2
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) cameraYaw -= rotationSpeed * 2
        if (pressedKeys.contains(KeyEvent.VK_DOWN) && (cameraPitch > -1.3)) cameraPitch -= rotationSpeed * 2
        if (pressedKeys.contains(KeyEvent.VK_UP) && (cameraPitch < 1.3)) cameraPitch += rotationSpeed * 2
        if (pressedKeys.contains(KeyEvent.VK_G)) println("X:${((cameraPosition.x+500)/100).toInt()} Y:${((cameraPosition.y+500)/100).toInt()} Z:${((cameraPosition.z+500)/100).toInt()} YAW:${((cameraYaw*10).toInt()/10.0)} PITCH:${((cameraPitch*10).toInt()/10.0)} SPEED:$movementSpeed")
        if (pressedKeys.contains(KeyEvent.VK_R)) {
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
            val initialPos = Vector3d(
                (((cameraPosition.x.toInt()) / 100) + 0.5) * (cubeSize + spacing).toInt().toDouble(),
                (((cameraPosition.y.toInt()) / 100) - 0.5) * (cubeSize + spacing).toInt().toDouble(),
                (((cameraPosition.z.toInt()) / 100) - 0.5) * (cubeSize + spacing).toInt().toDouble()
            )
            val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
            cubes.add(TransformedCube(Cube(Color.LIGHT_GRAY, baseCubeVertices, null, initialPos.x, initialPos.y, initialPos. z), transformMatrix = translationMatrix))
        }
        if (pressedKeys.contains(KeyEvent.VK_O)) {
            val lightRadius = 5.0 * cubeSize
            val lightX = (cameraPosition.x / cubeSize).toInt() * cubeSize + cubeSize / 2
            val lightY = (cameraPosition.y / cubeSize).toInt() * cubeSize + cubeSize / 2
            val lightZ = (cameraPosition.z / cubeSize).toInt() * cubeSize + cubeSize / 2
            lightSources.add(LightSource(Vector3d(lightX, lightY-cubeSize/2, lightZ), lightRadius, Color(188, 0, 0, 180)))
            pressedKeys.remove(KeyEvent.VK_O)
        }

        if (cameraYaw > 6.0) cameraYaw = 0.0
        if (cameraYaw < -6.0) cameraYaw = 0.0
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
                rasterizeTexturedTriangle(g2dBack, renderableFace, virtualWidth, virtualHeight)
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

        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][y] == 3) {

                        if ((((cameraPosition.x+500)/100).toInt() == x) and
                            (((cameraPosition.y+500)/100).toInt() == zLevel) and
                            (((cameraPosition.z+500)/100).toInt() == y)) {

                            val blockx = (x - 4.5) * (cubeSize + spacing)
                            val blocky = (zLevel - 4.5) * (cubeSize + spacing)
                            val blockz = (y - 4.5) * (cubeSize + spacing)

                            GRID_MAP[x][zLevel][y] = 0
                            println(cubes.find { it.cube.x == blockx && it.cube.y == blocky && it.cube.z == blockz })
                            cubes.removeAt(cubes.indexOf(cubes.find { it.cube.x == blockx && it.cube.y == blocky && it.cube.z == blockz }))
                        }
                    }
                }
            }
        }
    }

    private fun calculateIlluminatedFaces(
        cubes: List<TransformedCube>,
        lightSources: List<LightSource>,
        cameraPosition: Vector3d,
        viewMatrix: Matrix4x4,
        projectionMatrix: Matrix4x4
    ): Map<RenderableFace, Color> {
        val illuminatedFaceColors = ConcurrentHashMap<RenderableFace, Color>()

        val allFacesWithWorldVertices = cubes.flatMap { transformedCube ->
            val worldVertices = transformedCube.cube.vertices.map { transformedCube.transformMatrix.transform(it) }
            transformedCube.cube.faces.mapIndexed { faceIndex, faceIndices ->
                val faceWorldVertices = faceIndices.map { worldVertices[it] }
                Triple(transformedCube, faceIndex, faceWorldVertices)
            }
        }

        val lightTasks = lightSources.map { light ->
            executor.submit {
                for ((transformedCube, faceIndex, faceWorldVertices) in allFacesWithWorldVertices) {
                    if (faceWorldVertices.size < 3) continue

                    val faceNormal = (faceWorldVertices[1] - faceWorldVertices[0]).cross(faceWorldVertices[2] - faceWorldVertices[0]).normalize()

                    val samplingPoints = mutableListOf<Vector3d>()
                    samplingPoints.add(faceWorldVertices.reduce { acc, vec -> acc + vec } / faceWorldVertices.size.toDouble())

                    if (faceWorldVertices.size == 4) {
                        samplingPoints.add(faceWorldVertices[0])
                        samplingPoints.add(faceWorldVertices[1])
                        samplingPoints.add(faceWorldVertices[2])
                        samplingPoints.add(faceWorldVertices[3])
                        samplingPoints.add((faceWorldVertices[0] + faceWorldVertices[1]) / 2.0)
                        samplingPoints.add((faceWorldVertices[1] + faceWorldVertices[2]) / 2.0)
                        samplingPoints.add((faceWorldVertices[2] + faceWorldVertices[3]) / 2.0)
                        samplingPoints.add((faceWorldVertices[3] + faceWorldVertices[0]) / 2.0)
                    }

                    var totalLightInfluence = 0.0
                    var hitCount = 0

                    for (point in samplingPoints) {
                        val lightToFaceVector = point - light.position
                        val distanceToFace = lightToFaceVector.length()

                        if (distanceToFace > light.radius) {
                            continue
                        }

                        val lightDirection = lightToFaceVector.normalize()

                        val dotProduct = faceNormal.dot(lightDirection)
                        if (dotProduct > 0.0) {
                            continue
                        }

                        if (!isOccludedByGrid(light.position, point)) {
                            val attenuation = 1.0 - (distanceToFace / light.radius).coerceIn(0.0, 1.0)
                            totalLightInfluence += light.intensity * attenuation
                            hitCount++
                        }
                    }

                    if (hitCount > 0) {
                        val averageLightInfluence = totalLightInfluence / hitCount

                        val baseColor = transformedCube.cube.color

                        val dynamicLightR = (baseColor.red * (light.color.red / 255.0) * averageLightInfluence * maxLightContribution)
                        val dynamicLightG = (baseColor.green * (light.color.green / 255.0) * averageLightInfluence * maxLightContribution)
                        val dynamicLightB = (baseColor.blue * (light.color.blue / 255.0) * averageLightInfluence * maxLightContribution)

                        val dummyRenderableFace = RenderableFace(
                            screenVertices = listOf(),
                            originalClipW = listOf(),
                            textureVertices = listOf(),
                            color = transformedCube.cube.color,
                            isEdge = false,
                            texture = null,
                            worldVertices = faceWorldVertices
                        )

                        val currentIlluminatedColor = illuminatedFaceColors.getOrDefault(dummyRenderableFace, Color(0,0,0))

                        val combinedR = (currentIlluminatedColor.red + dynamicLightR).coerceIn(0.0, 255.0).toInt()
                        val combinedG = (currentIlluminatedColor.green + dynamicLightG).coerceIn(0.0, 255.0).toInt()
                        val combinedB = (currentIlluminatedColor.blue + dynamicLightB).coerceIn(0.0, 255.0).toInt()

                        illuminatedFaceColors[dummyRenderableFace] = Color(combinedR, combinedG, combinedB)
                    }
                }
            }
        }

        lightTasks.forEach { it.get() }
        return illuminatedFaceColors
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
        val near = 0.01
        val far = 1200.0

        val projectionMatrix = Matrix4x4.perspective(fov, aspectRatio, near, far)
        val combinedMatrix = projectionMatrix * viewMatrix
        val illuminatedFacesMap = calculateIlluminatedFaces(cubes, lightSources, cameraPosition, viewMatrix, projectionMatrix)
        val tasks = mutableListOf<Future<*>>()

        for (transformedCube in cubes) {
            tasks.add(executor.submit {
                val transformedVerticesHomogeneous = transformedCube.cube.vertices.map {
                    combinedMatrix.transformHomogeneous(transformedCube.transformMatrix.transform(it))
                }

                val worldVertices = transformedCube.cube.vertices.map { transformedCube.transformMatrix.transform(it) }

                for (faceIndex in transformedCube.cube.faces.indices) {
                    val faceIndices = transformedCube.cube.faces[faceIndex]
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
                        val textureVerticesForFace = mutableListOf<Vector3d>()
                        val originalClipWForFace = mutableListOf<Double>()
                        val worldVerticesForFace = mutableListOf<Vector3d>()
                        var anyVertexInFrustum = false

                        val faceTexCoords = transformedCube.cube.faceTextureCoords[faceIndex]

                        for (i in faceIndices.indices) {
                            val index = faceIndices[i]
                            val projectedHomogeneous = transformedVerticesHomogeneous[index]
                            val originalTextureCoord = faceTexCoords[i]
                            val originalWorldVertex = worldVertices[index]

                            val w = projectedHomogeneous.w
                            if (w.isCloseToZero() || w < near || w > far) {
                                projectedVerticesForFace.add(Vector3d(Double.NaN, Double.NaN, Double.NaN))
                                textureVerticesForFace.add(Vector3d(Double.NaN, Double.NaN, Double.NaN))
                                originalClipWForFace.add(Double.NaN)
                                worldVerticesForFace.add(Vector3d(Double.NaN, Double.NaN, Double.NaN))
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
                                textureVerticesForFace.add(originalTextureCoord)
                                originalClipWForFace.add(w)
                                worldVerticesForFace.add(originalWorldVertex)
                            }
                        }

                        val allVerticesRejected = projectedVerticesForFace.all { it.x.isNaN() }
                        if (!allVerticesRejected && anyVertexInFrustum) {
                            val drawableVertices = mutableListOf<Vector3d>()
                            val drawableTextureCoords = mutableListOf<Vector3d>()
                            val drawableOriginalClipW = mutableListOf<Double>()
                            val drawableWorldVertices = mutableListOf<Vector3d>()

                            for (i in projectedVerticesForFace.indices) {
                                if (!projectedVerticesForFace[i].x.isNaN()) {
                                    drawableVertices.add(projectedVerticesForFace[i])
                                    drawableTextureCoords.add(textureVerticesForFace[i])
                                    drawableOriginalClipW.add(originalClipWForFace[i])
                                    drawableWorldVertices.add(worldVerticesForFace[i])
                                }
                            }

                            if (drawableVertices.size >= 3) {
                                val originalFaceWorldVerticesForIdentification = faceIndices.map { transformedCube.transformMatrix.transform(transformedCube.cube.vertices[it]) }

                                val faceIdentifier = RenderableFace(
                                    screenVertices = listOf(),
                                    originalClipW = listOf(),
                                    textureVertices = listOf(),
                                    color = transformedCube.cube.color,
                                    isEdge = false,
                                    texture = null,
                                    worldVertices = originalFaceWorldVerticesForIdentification
                                )
                                val dynamicLightColor = illuminatedFacesMap[faceIdentifier]

                                var finalCalculatedColor: Color
                                if (dynamicLightColor != null) {
                                    val finalR =
                                        (dynamicLightColor.red + transformedCube.cube.color.red * ambientIntensity).toInt()
                                            .coerceIn(0, 255)
                                    val finalG =
                                        (dynamicLightColor.green + transformedCube.cube.color.green * ambientIntensity).toInt()
                                            .coerceIn(0, 255)
                                    val finalB =
                                        (dynamicLightColor.blue + transformedCube.cube.color.blue * ambientIntensity).toInt()
                                            .coerceIn(0, 255)
                                    finalCalculatedColor = Color(finalR, finalG, finalB)
                                } else {
                                    val ambientR =
                                        (transformedCube.cube.color.red * ambientIntensity).toInt().coerceIn(0, 255)
                                    val ambientG =
                                        (transformedCube.cube.color.green * ambientIntensity).toInt().coerceIn(0, 255)
                                    val ambientB =
                                        (transformedCube.cube.color.blue * ambientIntensity).toInt().coerceIn(0, 255)
                                    finalCalculatedColor = Color(ambientR, ambientG, ambientB)
                                }

                                renderQueue.add(
                                    RenderableFace(
                                        listOf(drawableVertices[0], drawableVertices[1], drawableVertices[2]),
                                        listOf(
                                            drawableOriginalClipW[0],
                                            drawableOriginalClipW[1],
                                            drawableOriginalClipW[2]
                                        ),
                                        listOf(
                                            drawableTextureCoords[0],
                                            drawableTextureCoords[1],
                                            drawableTextureCoords[2]
                                        ),
                                        transformedCube.cube.color,
                                        false,
                                        transformedCube.texture,
                                        listOf(
                                            drawableWorldVertices[0],
                                            drawableWorldVertices[1],
                                            drawableWorldVertices[2]
                                        ),
                                        finalCalculatedColor
                                    )
                                )
                                if (drawableVertices.size == 4) {
                                    renderQueue.add(
                                        RenderableFace(
                                            listOf(drawableVertices[0], drawableVertices[2], drawableVertices[3]),
                                            listOf(
                                                drawableOriginalClipW[0],
                                                drawableOriginalClipW[2],
                                                drawableOriginalClipW[3]
                                            ),
                                            listOf(
                                                drawableTextureCoords[0],
                                                drawableTextureCoords[2],
                                                drawableTextureCoords[3]
                                            ),
                                            transformedCube.cube.color,
                                            false,
                                            transformedCube.texture,
                                            listOf(
                                                drawableWorldVertices[0],
                                                drawableWorldVertices[2],
                                                drawableWorldVertices[3]
                                            ),
                                            finalCalculatedColor
                                        )
                                    )
                                }

                                val edgeColor = Color(40, 40, 40)
                                for (i in 0 until drawableVertices.size) {
                                    val p1 = drawableVertices[i]
                                    val p2 = drawableVertices[(i + 1) % drawableVertices.size]
                                    renderQueue.add(RenderableFace(listOf(p1, p2), listOf(), listOf(), edgeColor, true, null, listOf()))
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

    private fun rasterizeTexturedTriangle(g2d: Graphics2D, renderableFace: RenderableFace, screenWidth: Int, screenHeight: Int) {
        val screenVertices = renderableFace.screenVertices
        val originalClipW = renderableFace.originalClipW
        val textureVertices = renderableFace.textureVertices
        val texture = renderableFace.texture
        val worldVertices = renderableFace.worldVertices
        val illuminatedColor = renderableFace.illuminatedColor ?: renderableFace.color

        if (screenVertices.size != 3 || textureVertices.size != 3 || originalClipW.size != 3 || worldVertices.size != 3 || texture == null) return

        val v0 = screenVertices[0]
        val v1 = screenVertices[1]
        val v2 = screenVertices[2]

        val uv0 = textureVertices[0]
        val uv1 = textureVertices[1]
        val uv2 = textureVertices[2]

        val w0 = originalClipW[0]
        val w1 = originalClipW[1]
        val w2 = originalClipW[2]

        var minX = minOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt())
        var maxX = maxOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt())
        var minY = minOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt())
        var maxY = maxOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt())

        minX = max(0, minX)
        maxX = min(screenWidth - 1, maxX)
        minY = max(0, minY)
        maxY = min(screenHeight - 1, maxY)

        val A12 = (v1.y - v2.y)
        val B12 = (v2.x - v1.x)
        val C12 = (v1.x * v2.y - v2.x * v1.y)

        val A20 = (v2.y - v0.y)
        val B20 = (v0.x - v2.x)
        val C20 = (v2.x * v0.y - v0.x * v2.y)

        val A01 = (v0.y - v1.y)
        val B01 = (v1.x - v0.x)
        val C01 = (v0.x * v1.y - v1.x * v0.y)

        val totalArea = A12 * v0.x + B12 * v0.y + C12
        if (totalArea.isCloseToZero()) return

        val invTotalArea = 1.0 / totalArea

        val u0_prime = uv0.x / w0
        val v0_prime = uv0.y / w0
        val z0_inv_prime = 1.0 / w0

        val u1_prime = uv1.x / w1
        val v1_prime = uv1.y / w1
        val z1_inv_prime = 1.0 / w1

        val u2_prime = uv2.x / w2
        val v2_prime = uv2.y / w2
        val z2_inv_prime = 1.0 / w2


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

                        val interpolated_u_prime = alpha * u0_prime + beta * u1_prime + gamma * u2_prime
                        val interpolated_v_prime = alpha * v0_prime + beta * v1_prime + gamma * v2_prime

                        val u = interpolated_u_prime / interpolated_z_inv_prime
                        val v = interpolated_v_prime / interpolated_z_inv_prime

                        val texX = (u * (texture.width - 1)).toInt().coerceIn(0, texture.width - 1)
                        val texY = (v * (texture.height - 1)).toInt().coerceIn(0, texture.height - 1)

                        val finalFaceIlluminationColor = renderableFace.illuminatedColor ?: renderableFace.color

                        var finalPixelColor = Color(texture.getRGB(texX, texY))

                        val litR = (finalPixelColor.red * (finalFaceIlluminationColor.red / 255.0)).toInt()
                        val litG = (finalPixelColor.green * (finalFaceIlluminationColor.green / 255.0)).toInt()
                        val litB = (finalPixelColor.blue * (finalFaceIlluminationColor.blue / 255.0)).toInt()
                        finalPixelColor = Color(litR.coerceIn(0, 255), litG.coerceIn(0, 255), litB.coerceIn(0, 255))

                        val distance = 1.0 / interpolated_z_inv_prime
                        var linearFogFactor = (distance - fogStartDistance) / (fogEndDistance - fogStartDistance)
                        linearFogFactor = linearFogFactor.coerceIn(0.0, 1.0)
                        val fogFactor = linearFogFactor * fogDensity.coerceIn(0.0, 1.0)

                        val r = ((finalPixelColor.red * (1 - fogFactor) + fogColor.red * fogFactor)).toInt().coerceIn(0, 255)
                        val g = ((finalPixelColor.green * (1 - fogFactor) + fogColor.green * fogFactor)).toInt().coerceIn(0, 255)
                        val b = ((finalPixelColor.blue * (1 - fogFactor) + fogColor.blue * fogFactor)).toInt().coerceIn(0, 255)

                        finalPixelColor = Color(r, g, b)

                        g2d.color = finalPixelColor
                        g2d.fillRect(px, py, 1, 1)
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

        val dx = (targetX - currentX).toDouble()
        val dy = (targetY - currentY).toDouble()
        val dz = (targetZ - currentZ).toDouble()

        val steps = maxOf(abs(dx), abs(dy), abs(dz)).toInt()
        if (steps == 0) return false

        for (i in 1..steps) {
            val t = i.toDouble() / steps.toDouble()
            val interpolatedX = currentX + (dx * t)
            val interpolatedY = currentY + (dy * t)
            val interpolatedZ = currentZ + (dz * t)

            val gridX = interpolatedX.toInt().coerceIn(0, 8)
            val gridY = interpolatedY.toInt().coerceIn(0, 8)
            val gridZ = interpolatedZ.toInt().coerceIn(0, 8)

            if (GRID_MAP[gridX][gridY][gridZ] == 1) {
                val blockWorldMin = Vector3d((gridX - 4.5) * (cubeSize + spacing), (gridY - 4.5) * (cubeSize + spacing), (gridZ - 4.5) * (cubeSize + spacing))
                val blockWorldMax = Vector3d(blockWorldMin.x + cubeSize, blockWorldMin.y + cubeSize, blockWorldMin.z + cubeSize)
                val blockCenter = Vector3d(blockWorldMin.x + cubeSize / 2, blockWorldMin.y + cubeSize / 2, blockWorldMin.z + cubeSize / 2)

                val distStartToBlock = (blockCenter - start).length()
                val distStartToEnd = (end - start).length()

                if (distStartToBlock < distStartToEnd - 10) {
                    return true
                }
            }
        }
        return false
    }

    private fun drawLineZBuffered(g2d: Graphics2D, x0: Int, y0: Int, z0: Double, x1: Int, y1: Int, z1: Double, color: Color, screenWidth: Int, screenHeight: Int) {
        var currX0 = x0
        var currY0 = y0
        val dx = abs(x1 - currX0)
        val dy = abs(y1 - currY0)
        val sx = if (currX0 < x1) 1 else -1
        val sy = if (currY0 < y1) 1 else -1

        var err = dx - dy

        g2d.color = Color(40, 40, 40)

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
            if (pressedKeys.contains(KeyEvent.VK_9)) debugFly = !debugFly
            if (pressedKeys.contains(KeyEvent.VK_8)) debugNoclip = !debugNoclip
            e?.keyCode?.let { pressedKeys.remove(it) }
        }
    }
}

private fun MutableList<TransformedCube>.removeAt(index: TransformedCube?) {}
