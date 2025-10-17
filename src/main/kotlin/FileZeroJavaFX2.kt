package org.lewapnoob.FileZero2

import javafx.animation.AnimationTimer
import javafx.embed.swing.SwingFXUtils
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.image.PixelFormat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.ImageIO
import java.awt.Robot
import javafx.scene.Cursor
import kotlin.math.*

class DrawingPanel : StackPane() {
    private var lastUpdateTime = System.nanoTime()
    private val physicsTimestep = 1.0 / 120.0
    private var accumulator = 0.0

    private val isRendering = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lightGizmoMeshes = Collections.synchronizedList(mutableListOf<PlacedMesh>())
    private val lightSources = Collections.synchronizedList(mutableListOf<LightSource>())
    private data class OrbitingLight(val light: LightSource, val center: Vector3d, var angle: Double = 0.0)
    private val orbitingLights = Collections.synchronizedList(mutableListOf<OrbitingLight>())

    private data class StaticMeshBatch(
        val mesh: Mesh,
        val texture: Image?,
        val aabb: AABB,
        val faceIndexToOriginal: Map<Int, Pair<PlacedMesh, Int>>
    )

    private data class GI_LightPoint(
        val targetFaceKey: Pair<PlacedMesh, Int>,
        val gridX: Int,
        val gridY: Int,
        val color: Vector3d
    )

    private data class FaceToProcess(
        val source: Any, // PlacedMesh or StaticMeshBatch
        val faceIndex: Int,
        val indices: List<Int>,
        val worldVertices: List<Vector3d>
    )

    private var staticMeshBatches = listOf<StaticMeshBatch>()
    private val textureTransparencyCache = ConcurrentHashMap<Image, Boolean>()

    private val faceLightGrids = ConcurrentHashMap<Pair<PlacedMesh, Int>, Array<Array<Color>>>()
    private val giLightGrids = ConcurrentHashMap<Pair<PlacedMesh, Int>, Array<Array<Color>>>()
    private val lightingUpdateQueue = ConcurrentLinkedQueue<Pair<PlacedMesh, Int>>()
    private val giUpdateQueue = ConcurrentLinkedQueue<Pair<PlacedMesh, Int>>()
    private val giLightPoints = Collections.synchronizedList(mutableListOf<GI_LightPoint>())
    private val lightingExecutor = Executors.newSingleThreadExecutor()
    private val lightingUpdateJobQueue = ConcurrentLinkedQueue<LightSource>()
    private var lightingFuture: Future<*>? = null

    private val cubeSize = 100.0
    private var gridDimension = 9

    private val defaultScalePlayer = 1.25
    private val scalePlayer = defaultScalePlayer
    private val playerHeight = ((cubeSize / 2) - (cubeSize / 20)) * scalePlayer
    private val playerRadius = 0.3 * cubeSize

    private var cameraPosition = Vector3d(0.0, 0.1, 0.0)
    private var cameraYaw = 2.4
    private var cameraPitch = 0.0
    private val baseFov = 90.0
    private val dynamicFov = (baseFov / sqrt(scalePlayer / defaultScalePlayer).coerceAtLeast(0.5)).coerceIn(60.0..120.0)

    private var debugFly = false
    private var debugNoclip = false

    private val fogColor = Color.rgb(180, 180, 180)
    private val fogStartDistance = 1.5 * cubeSize
    private val fogEndDistance = 7.0 * cubeSize
    private val fogDensity = 0.5

    private val ambientIntensity = 0.5
    private val HIGH_QualityRes = 16
    private val LOW_QualityRes = 8
    private val globalLightIntensity = 6.0
    private val GI_LightIntensity = 1.1
    private val GI_SAMPLES = 4 // 4
    private val bouncesLeft = 2 // 2

    private var retroScanLineMode = false
    private val renderDownscaleFactor = 4
    private val renderDistanceBlocks = 24.0 * cubeSize
    private val baseResolutionWidth = 1920
    private val baseResolutionHeight = 1080
    private val virtualWidth = baseResolutionWidth / renderDownscaleFactor
    private val virtualHeight = baseResolutionHeight / renderDownscaleFactor

    private lateinit var depthBuffer: DoubleArray
    private lateinit var pixelBuffer: IntArray
    private var bgColorInt: Int
    private lateinit var backBuffer: WritableImage
    private val imageView = ImageView()
    private val overlayCanvas = Canvas(virtualWidth.toDouble(), virtualHeight.toDouble())
    lateinit var texSkybox: Image
    lateinit var texBlackBricks: Image
    lateinit var texBricks: Image
    lateinit var texCeiling: Image
    lateinit var texFloor: Image
    private val dynamicTextures = ConcurrentHashMap<String, Image>()
    private var modelRegistry: Map<String, Mesh>

    private var isMouseCaptured = false
    private val mouseSensitivity = 0.008
    private var accumulatedMouseDeltaX = 1.0
    private var accumulatedMouseDeltaY = 1.0

    private val robot: Robot by lazy { Robot() }
    private var isRobotCentering = false

    val pressedKeys = Collections.synchronizedSet(mutableSetOf<KeyCode>())

    private val collisionGrid = SpatialGrid<PlacedMesh>(cubeSize * 2)
    private val meshAABBs = ConcurrentHashMap<PlacedMesh, AABB>()
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val bvh = BVH()
    private val renderQueue = ConcurrentLinkedQueue<RenderableFace>()
    private val transparentRenderQueue = ConcurrentLinkedQueue<RenderableFace>()

    private var lastLightCheckTime = 0L
    private val lightCheckInterval = (1_000_000_000.0 / 30.0).toLong() // 30 Hz

    private val clientSocket: DatagramSocket = DatagramSocket()
    private val serverAddress: InetAddress = InetAddress.getByName("lewapnoob.ddns.net")
    private val serverPort: Int = 1027
    private val meshes = mutableListOf<PlacedMesh>()
    private val dynamicMeshes = ConcurrentHashMap<String, PlacedMesh>()

    private val clientId: String = UUID.randomUUID().toString()
    private data class PlayerState(
        var currentPos: Vector3d,
        var currentYaw: Double,
        var targetPos: Vector3d,
        var targetYaw: Double
    )
    private val otherPlayers = ConcurrentHashMap<String, PlayerState>()

    init {
        sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) {
                newScene.windowProperty().addListener { _, _, newWindow ->
                    if (newWindow != null) {
                        newWindow.setOnCloseRequest {
                            System.exit(0)
                        }
                    }
                }
            }
        }

        depthBuffer = DoubleArray(virtualWidth * virtualHeight) { Double.MAX_VALUE }
        pixelBuffer = IntArray(virtualWidth * virtualHeight)
        backBuffer = WritableImage(virtualWidth, virtualHeight)

        val bgColor = Color.rgb(40, 40, 40)
        bgColorInt = colorToInt(bgColor)

        imageView.image = backBuffer
        imageView.fitWidthProperty().bind(this.widthProperty())
        imageView.fitHeightProperty().bind(this.heightProperty())

        overlayCanvas.widthProperty().bind(this.widthProperty())
        overlayCanvas.heightProperty().bind(this.heightProperty())
        overlayCanvas.graphicsContext2D.isImageSmoothing = false

        setOnMouseClicked {
            if (!isMouseCaptured) {
                requestFocus()
                scene.cursor = Cursor.NONE
                isMouseCaptured = true
            }
        }

        setOnMouseMoved { event: MouseEvent ->
            if (isMouseCaptured) {
                if (isRobotCentering) {
                    isRobotCentering = false
                    return@setOnMouseMoved
                }

                val window = scene.window
                val centerX = window.x + window.width / 2
                val centerY = window.y + window.height / 2

                val deltaX = event.screenX - centerX
                val deltaY = event.screenY - centerY

                if (deltaX == 0.0 && deltaY == 0.0) {
                    return@setOnMouseMoved
                }

                accumulatedMouseDeltaX += deltaX
                accumulatedMouseDeltaY += deltaY

                // Center the mouse
                isRobotCentering = true
                robot.mouseMove(centerX.toInt(), centerY.toInt())
            }
        }

        children.addAll(imageView, overlayCanvas)

        cameraPosition = Vector3d(-2.5 * cubeSize, (-3.97 * cubeSize) + playerHeight, 1.5 * cubeSize)

        val grids = listOf(GRID_1, GRID_2, GRID_3, GRID_4)
        for (x in 0 until gridDimension) {
            for (y in grids.indices) {
                for (z in 0 until gridDimension) {
                    when (grids[y][x][z]) {
                        1 -> GRID_MAP[z][y][x] = 1
                        2 -> GRID_MAP[z][y][x] = 2
                        3 -> GRID_MAP[z][y][x] = 3
                    }
                }
            }
        }
        texBlackBricks = loadImage("textures/black_bricks.png")
        texBricks = loadImage("textures/bricks.jpg")
        texCeiling = loadImage("textures/ceiling.jpg")
        texFloor = loadImage("textures/floor.jpg")
        texSkybox = loadImage("textures/skybox.png")

        modelRegistry = mapOf(
            "cube" to createCubeMesh(cubeSize, Color.GRAY),
            "cubeRed" to createCubeMesh(cubeSize, Color.RED),
            "cubeGates" to createCubeMesh(cubeSize, Color.rgb(40, 255, 40)),
            "skybox" to createCubeMesh(1000.0 * cubeSize, Color.rgb(80, 80, 80), inverted = true),
            "pyramid" to createPyramidMesh(cubeSize, Color.RED),
            "invertedPyramid" to createInvertedPyramidMesh(cubeSize, Color.DEEPSKYBLUE),
            "tower" to createTowerMesh(cubeSize, Color.WHITE),
            "kotlin" to createKotlinModelMesh(cubeSize, Color.GRAY),
            "tank" to createTankMesh(cubeSize, Color.GREEN),
            "offroadCar" to createOffroadCarMesh(cubeSize, Color.GRAY),
            "stair" to createStairMesh(cubeSize, Color.WHITE),
            "sphere" to createCapsuleMesh(cubeSize, Color.WHITE),
            "map" to createMapMesh(cubeSize, Color.WHITE),
            "colorPalette" to createColorPaletteMesh(cubeSize, Color.WHITE),
            "TNT" to createTNTMesh(cubeSize, Color.WHITE),
            "player" to createPlayerMesh(cubeSize, Color.WHITE),
            "rtMap" to createRayTracingMapMesh(cubeSize, Color.WHITE),
        )

        modelRegistry.forEach { (modelName, mesh) ->
            mesh.customTextures.forEach { (localId, hexData) ->
                val globalId = "${modelName}_$localId"
                dynamicTextures.computeIfAbsent(globalId) { createTextureFromHexData(hexData) }
            }
        }

        val pos0 = Vector3d(0.0, 0.0, 0.0)
        meshes.add(PlacedMesh(modelRegistry["skybox"]!!, Matrix4x4.translation(pos0.x, pos0.y, pos0.z), texture = texSkybox, collision=false))

        val pos1 = Vector3d(5.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["pyramid"]!!, Matrix4x4.translation(pos1.x, pos1.y, pos1.z), faceTextures = placedTextures("pyramid", modelRegistry["pyramid"]!!)))

        val pos2 = Vector3d(7.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["invertedPyramid"]!!, Matrix4x4.translation(pos2.x, pos2.y, pos2.z), faceTextures = placedTextures("invertedPyramid", modelRegistry["invertedPyramid"]!!)))

        val pos3 = Vector3d(9.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["tower"]!!, Matrix4x4.translation(pos3.x, pos3.y, pos3.z) * Matrix4x4.rotationY(PI/4), texture = texBricks))

        val pos4 = Vector3d(11.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["kotlin"]!!, Matrix4x4.translation(pos4.x, pos4.y, pos4.z), texture = texFloor))

        val pos5 = Vector3d(13.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["tank"]!!, Matrix4x4.translation(pos5.x, pos5.y, pos5.z), faceTextures = placedTextures("tank", modelRegistry["tank"]!!)))

        val pos6 = Vector3d(15.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["offroadCar"]!!, Matrix4x4.translation(pos6.x, pos6.y, pos6.z), texture = texBlackBricks))

        val pos7 = Vector3d(17.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["stair"]!!, Matrix4x4.translation(pos7.x, pos7.y, pos7.z), texture = texCeiling, faceTextures = placedTextures("stair", modelRegistry["stair"]!!)))

        val pos8 = Vector3d(19.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["sphere"]!!, Matrix4x4.translation(pos8.x, pos8.y, pos8.z), texture = texCeiling))

        val pos9 = Vector3d(31.0 * cubeSize, -4.0 * cubeSize, 0 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["map"]!!, Matrix4x4.translation(pos9.x, pos9.y, pos9.z), faceTextures = placedTextures("map", modelRegistry["map"]!!)))

        val pos10 = Vector3d(50.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["colorPalette"]!!, Matrix4x4.translation(pos10.x, pos10.y, pos10.z), faceTextures = placedTextures("colorPalette", modelRegistry["colorPalette"]!!)))

        val pos11 = Vector3d(21.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["TNT"]!!, Matrix4x4.translation(pos11.x, pos11.y, pos11.z), faceTextures = placedTextures("TNT", modelRegistry["TNT"]!!)))

        val pos12 = Vector3d(23.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["rtMap"]!!, Matrix4x4.translation(pos12.x, pos12.y, pos12.z), faceTextures = placedTextures("rtMap", modelRegistry["rtMap"]!!)))

        for (x in 0 until gridDimension) {
            for (y in 0 until gridDimension) {
                for (z in 0 until gridDimension) {
                    val offset = (gridDimension) / 2.0
                    val pos = Vector3d(
                        (x - offset) * cubeSize,
                        (y - offset) * cubeSize,
                        (z - offset) * cubeSize
                    )
                    val mat = Matrix4x4.translation(pos.x, pos.y, pos.z)
                    when (GRID_MAP[x][y][z]) {
                        1 -> meshes.add(PlacedMesh(modelRegistry["cube"]!!, mat, texBlackBricks, collisionPos = pos))
                        2 -> meshes.add(PlacedMesh(modelRegistry["cubeRed"]!!, mat, texBlackBricks, collisionPos = pos))
                        3 -> {meshes.add(PlacedMesh(modelRegistry["cubeGates"]!!, mat, texBricks, collision=false, collisionPos = pos))
                            println("${pos.x} ${pos.y} ${pos.z}")}
                    }
                }
            }
        }

        meshes.forEach { mesh ->
            val aabb = AABB.fromCube(mesh.getTransformedVertices())
            meshAABBs[mesh] = aabb
            if (mesh.collision) {
                collisionGrid.add(mesh, aabb)
            }
        }

        rebuildPhysicsStructures()
        rebuildStaticMeshBatches()

        Thread { listenForServerMessages() }.start()

        object : AnimationTimer() {
            override fun handle(now: Long) {
                val rawDeltaTime = (now - lastUpdateTime) / 1_000_000_000.0
                lastUpdateTime = now
                accumulator += rawDeltaTime

                while (accumulator >= physicsTimestep) {
                    updateCameraPosition(physicsTimestep)
                    updateGameLogic(physicsTimestep)
                    accumulator -= physicsTimestep
                }

                if (now - lastLightCheckTime > lightCheckInterval) {
                    lastLightCheckTime = now
                    updateDynamicLights()
                }

                requestRender()
            }
        }.start()
    }

    private fun listenForServerMessages() {
        val buffer = ByteArray(1024)
        while (true) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                clientSocket.receive(packet)
                val message = String(packet.data, 0, packet.length)
                val parts = message.split(",")
                if (parts.size == 5) {
                    val receivedClientId = parts[0]
                    if (receivedClientId != clientId) {
                        val x = parts[1].toDouble()
                        val y = parts[2].toDouble()
                        val z = parts[3].toDouble()
                        val yaw = parts[4].toDouble()
                        val newPos = Vector3d(x, y, z)

                        otherPlayers.compute(receivedClientId) { _, existingState ->
                            if (existingState == null) {
                                PlayerState(newPos, yaw, newPos, yaw)
                            } else {
                                existingState.targetPos = newPos
                                existingState.targetYaw = yaw
                                existingState
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    private fun placedTextures(modelName: String, mesh: Mesh): Map<Int, Image> {
        val loadedTextures = mapOf(
            "blackBricks" to texBlackBricks,
            "bricks" to texBricks,
            "ceiling" to texCeiling,
            "floor" to texFloor,
            "skybox" to texSkybox,
            "red" to createColorTexture(Color.RED),
            "darkRed" to createColorTexture(Color.DARKRED),
            "blue" to createColorTexture(Color.BLUE),
            "darkBlue" to createColorTexture(Color.DARKBLUE),
            "green" to createColorTexture(Color.GREEN),
            "darkGreen" to createColorTexture(Color.DARKGREEN),
            "gray" to createColorTexture(Color.GRAY),
            "darkGray" to createColorTexture(Color.DARKGRAY),
            "orange" to createColorTexture(Color.ORANGE),
            "darkOrange" to createColorTexture(Color.DARKORANGE),
            "yellow" to createColorTexture(Color.YELLOW),
            "darkGoldenrod" to createColorTexture(Color.DARKGOLDENROD),
            "cyan" to createColorTexture(Color.CYAN),
            "darkCyan" to createColorTexture(Color.DARKCYAN),
            "magenta" to createColorTexture(Color.MAGENTA),
            "darkMagenta" to createColorTexture(Color.DARKMAGENTA),
            "brown" to createColorTexture(Color.BROWN),
            "navy" to createColorTexture(Color.NAVY),
            "purple" to createColorTexture(Color.PURPLE),
            "darkViolet" to createColorTexture(Color.DARKVIOLET),
            "olive" to createColorTexture(Color.OLIVE),
            "maroon" to createColorTexture(Color.MAROON),
            "teal" to createColorTexture(Color.TEAL),
            "black" to createColorTexture(Color.web("#000000")),
            "darkPurple" to createColorTexture(Color.web("#7E2553")),
            "lightGray" to createColorTexture(Color.web("#C2C3C7")),
            "white" to createColorTexture(Color.web("#FFF1E8")),
            "indigo" to createColorTexture(Color.web("#83769C")),
            "pink" to createColorTexture(Color.web("#FF77A8")),
            "peach" to createColorTexture(Color.web("#FFCCAA")),
            "silver" to createColorTexture(Color.SILVER),
            "gold" to createColorTexture(Color.GOLD),
            "mintCream" to createColorTexture(Color.MINTCREAM),
            "salmon" to createColorTexture(Color.SALMON),
            "turquoise" to createColorTexture(Color.TURQUOISE),
            "orchid" to createColorTexture(Color.ORCHID)
        )
        return mesh.faceTextureNames.mapValues { (_, textureName) ->
            loadedTextures[textureName]
                ?: dynamicTextures["${modelName}_${textureName.substringAfter("custom_")}"]
                ?: texSkybox
        }
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
        if (code == KeyCode.L) {
            retroScanLineMode = !retroScanLineMode
            println("CRT Monitor Mode: $retroScanLineMode")
        }
        if (code == KeyCode.ESCAPE) {
            isMouseCaptured = false
            scene.cursor = Cursor.DEFAULT
        }
    }

    private fun isCollidingAt(testPosition: Vector3d): Boolean {
        val playerAABB = AABB(
            min = Vector3d(testPosition.x - playerRadius, testPosition.y - playerHeight / 2, testPosition.z - playerRadius),
            max = Vector3d(testPosition.x + playerRadius, testPosition.y + playerHeight / 2, testPosition.z + playerRadius)
        )

        val potentialColliders = collisionGrid.query(playerAABB)
        for (mesh in potentialColliders) {
            val meshAABB = AABB.fromCube(mesh.getTransformedVertices())
            if (playerAABB.intersects(meshAABB)) {
                if (checkCylinderCollision(testPosition, mesh)) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkCylinderCollision(cylinderCenter: Vector3d, mesh: PlacedMesh): Boolean {
        val worldVertices = mesh.getTransformedVertices()
        val playerMinY = cylinderCenter.y - playerHeight / 2
        val playerMaxY = cylinderCenter.y + playerHeight / 2

        val worldBlushes = mesh.mesh.blushes.map { blush ->
            val transformedCorners = blush.getCorners().map { corner -> mesh.transformMatrix.transform(corner) }
            AABB.fromCube(transformedCorners)
        }

        for (faceIndices in mesh.mesh.faces) {
            if (faceIndices.size < 3) continue

            // Triangulate face
            for (i in 0 until faceIndices.size - 2) {
                val v0 = worldVertices[faceIndices[0]]
                val v1 = worldVertices[faceIndices[i + 1]]
                val v2 = worldVertices[faceIndices[i + 2]]

                // Broad-phase check for triangle
                val triMinY = minOf(v0.y, v1.y, v2.y)
                val triMaxY = maxOf(v0.y, v1.y, v2.y)
                if (playerMaxY < triMinY || playerMinY > triMaxY) {
                    continue
                }

                val closestPoint = closestPointOnTriangle(Vector3d(cylinderCenter.x, 0.0, cylinderCenter.z), Vector3d(v0.x, 0.0, v0.z), Vector3d(v1.x, 0.0, v1.z), Vector3d(v2.x, 0.0, v2.z))

                val distSq = (closestPoint.x - cylinderCenter.x).pow(2) + (closestPoint.z - cylinderCenter.z).pow(2)

                if (distSq < playerRadius.pow(2)) {
                    val collisionPointY = (playerMinY + playerMaxY) / 2
                    val collisionPoint = Vector3d(closestPoint.x, collisionPointY, closestPoint.z)
                    if (worldBlushes.any { it.contains(collisionPoint) }) {
                        continue
                    }

                    return true
                }
            }
        }
        return false
    }

    private fun closestPointOnTriangle(p: Vector3d, a: Vector3d, b: Vector3d, c: Vector3d): Vector3d {
        // This is a 2D version for XZ plane
        val ab = b - a
        val ac = c - a
        val ap = p - a

        val d1 = ab.dot(ap)
        val d2 = ac.dot(ap)
        if (d1 <= 0.0 && d2 <= 0.0) return a

        val bp = p - b
        val d3 = ab.dot(bp)
        val d4 = ac.dot(bp)
        if (d3 >= 0.0 && d4 <= d3) return b

        val vc = d1 * d4 - d3 * d2
        if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
            val v = d1 / (d1 - d3)
            return a + ab * v
        }

        val cp = p - c
        val d5 = ab.dot(cp)
        val d6 = ac.dot(cp)
        if (d6 >= 0.0 && d5 <= d6) return c

        val vb = d5 * d2 - d1 * d6
        if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
            val w = d2 / (d2 - d6)
            return a + ac * w
        }

        val va = d3 * d6 - d5 * d4
        if (va <= 0.0 && (d4 - d3) >= 0.0 && (d5 - d6) >= 0.0) {
            val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
            return b + (c - b) * w
        }

        val denom = 1.0 / (va + vb + vc)
        val v = vb * denom
        val w = vc * denom
        return a + ab * v + ac * w
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

        var newCameraPosition = cameraPosition.copy()

        var currentMovementSpeed = 2.8 * cubeSize * scalePlayer
        val playerSprintSpeed = 4.4 * cubeSize * scalePlayer

        if (pressedKeys.contains(KeyCode.SHIFT)) currentMovementSpeed = playerSprintSpeed

        if (pressedKeys.contains(KeyCode.W)) newCameraPosition += forwardVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.S)) newCameraPosition -= forwardVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.D)) newCameraPosition += rightVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.A)) newCameraPosition -= rightVector * currentMovementSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.SPACE) && debugFly) newCameraPosition += Vector3d(0.0, currentMovementSpeed, 0.0) * deltaTime
        if (pressedKeys.contains(KeyCode.CONTROL) && debugFly) newCameraPosition -= Vector3d(0.0, currentMovementSpeed, 0.0) * deltaTime

        val oldCameraPosition = cameraPosition.copy()

        if (debugNoclip || !isCollidingAt(newCameraPosition)) {
            cameraPosition = newCameraPosition
        } else {
            val resolvedPosition = oldCameraPosition.copy()

            val tempPosX = resolvedPosition.copy(x = newCameraPosition.x)
            if (!isCollidingAt(tempPosX)) {
                resolvedPosition.x = newCameraPosition.x
            }

            val tempPosZ = resolvedPosition.copy(z = newCameraPosition.z)
            if (!isCollidingAt(tempPosZ)) {
                resolvedPosition.z = newCameraPosition.z
            }

            val tempPosY = resolvedPosition.copy(y = newCameraPosition.y)
            if (!isCollidingAt(tempPosY)) {
                resolvedPosition.y = newCameraPosition.y
            }

            cameraPosition = resolvedPosition
        }

        if (isMouseCaptured) {
            val rotationSpeed = 1.0
            cameraYaw -= accumulatedMouseDeltaX * mouseSensitivity * rotationSpeed * deltaTime * 60
            cameraPitch -= accumulatedMouseDeltaY * mouseSensitivity * rotationSpeed * deltaTime * 60
            cameraPitch = cameraPitch.coerceIn(-1.5, 1.5)

            accumulatedMouseDeltaX = 0.0
            accumulatedMouseDeltaY = 0.0
        }

        val currentRotationSpeed = 4.0
        if (pressedKeys.contains(KeyCode.LEFT)) cameraYaw += currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.RIGHT)) cameraYaw -= currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.DOWN) && (cameraPitch > -1.3)) cameraPitch -= currentRotationSpeed * deltaTime
        if (pressedKeys.contains(KeyCode.UP) && (cameraPitch < 1.3)) cameraPitch += currentRotationSpeed * deltaTime

        if (pressedKeys.contains(KeyCode.G)) {
            val gridPos = worldToGridCoords(cameraPosition)
            println(
                "X:${gridPos.x.toInt()}" +
                        " Y:${gridPos.y.toInt()}" +
                        " Z:${gridPos.z.toInt()}" +
                        " YAW:${((cameraYaw*10).toInt()/10.0)}" +
                        " PITCH:${((cameraPitch*10).toInt()/10.0)}" +
                        " SPEED:$currentMovementSpeed" +
                        " FOV: $dynamicFov")
        }

        if (pressedKeys.contains(KeyCode.H)) {
            println(
                "X:${cameraPosition.x}" +
                        " Y:${cameraPosition.y}" +
                        " Z:${cameraPosition.z}")
        }

        if (pressedKeys.contains(KeyCode.R)) {
            val cubeMeshGray = createCubeMesh(cubeSize, Color.GRAY)
            val playerGridPos = worldToGridCoords(cameraPosition)
            val gridX = playerGridPos.x.toInt()
            val gridY = playerGridPos.y.toInt()
            val gridZ = playerGridPos.z.toInt()
            val offset = gridDimension / 2.0
            val initialPos = Vector3d(
                (gridX - offset) * cubeSize,
                (gridY - offset) * cubeSize,
                (gridZ - offset) * cubeSize
            )
            val translationMatrix = Matrix4x4.translation(initialPos.x, initialPos.y, initialPos.z)
            val newMesh = PlacedMesh(cubeMeshGray, transformMatrix = translationMatrix, texture = loadImage("textures/black_bricks.png"), collisionPos = initialPos)
            meshes.add(newMesh)

            val aabb = AABB.fromCube(newMesh.getTransformedVertices())
            meshAABBs[newMesh] = aabb

            if (newMesh.collision) {
                collisionGrid.clear()
                meshes.filter { it.collision }.forEach { mesh ->
                    collisionGrid.add(mesh, meshAABBs[mesh]!!)
                }
            }
            rebuildPhysicsStructures()
            pressedKeys.remove(KeyCode.R)
        }
        if (pressedKeys.contains(KeyCode.O)) {
            val lightRadius = 6.0 * cubeSize
            lightSources.add(LightSource(Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z), lightRadius, Color.rgb(160, 160, 160), intensity = 1.0, type = LightType.RAYTRACED_GI))
            pressedKeys.remove(KeyCode.O)
        }
        if (pressedKeys.contains(KeyCode.P)) {
            val lightRadius = 6.0 * cubeSize
            lightSources.add(LightSource(Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z), lightRadius, Color.rgb(0, 255, 0), type = LightType.VERTEX))
            pressedKeys.remove(KeyCode.P)
        }
        if (pressedKeys.contains(KeyCode.COMMA)) { // ','
            val lightRadius = 6.0 * cubeSize
            val initialPos = cameraPosition.copy()
            val newLight = LightSource(
                position = initialPos.copy(),
                radius = lightRadius,
                color = Color.YELLOW,
                type = LightType.VERTEX
            )
            lightSources.add(newLight)
            orbitingLights.add(OrbitingLight(newLight, initialPos))
            pressedKeys.remove(KeyCode.COMMA)
        }
        if (pressedKeys.contains(KeyCode.PERIOD)) { // '.'
            val lightRadius = 6.0 * cubeSize
            val initialPos = cameraPosition.copy()
            val newLight = LightSource(
                position = initialPos.copy(), radius = lightRadius, color = Color.rgb(0, 0, 255), type = LightType.RAYTRACED
            )
            lightSources.add(newLight)
            orbitingLights.add(OrbitingLight(newLight, initialPos))
            pressedKeys.remove(KeyCode.PERIOD)
        }
        if (pressedKeys.contains(KeyCode.X)) {
            lightingFuture?.cancel(true)
            synchronized(lightSources) { lightSources.clear() }
            synchronized(orbitingLights) { orbitingLights.clear() }
            lightingUpdateJobQueue.clear()
            faceLightGrids.clear()
            giLightGrids.clear()
            giLightPoints.clear()
        }

        if (cameraYaw > 2 * PI) cameraYaw -= 2 * PI
        if (cameraYaw < -2 * PI) cameraYaw += 2 * PI

        val message = "$clientId,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z},${cameraYaw - PI}".toByteArray()
        val packet = DatagramPacket(message, message.size, serverAddress, serverPort)
        try {
            clientSocket.send(packet)
        } catch (e: Exception) {
        }
    }

    private fun drawOverlay() {
        val gc = overlayCanvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, overlayCanvas.width, overlayCanvas.height)

        val mapScale = min(overlayCanvas.width, overlayCanvas.height) / 100.0
        val cursorSize = mapScale/5 * 2.5
        val mapOffsetX = 10.0
        val mapOffsetY = 10.0

        gc.stroke = Color.WHITE // cursor
        gc.strokeLine(overlayCanvas.width/2-cursorSize, overlayCanvas.height/2, overlayCanvas.width/2+cursorSize, overlayCanvas.height/2)
        gc.strokeLine(overlayCanvas.width/2, overlayCanvas.height/2-cursorSize, overlayCanvas.width/2, overlayCanvas.height/2+cursorSize)

        for (x in 0 until gridDimension) {
            for (z in 0 until gridDimension) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][z] == 1) {
                        gc.strokeOval(mapOffsetX + z * mapScale, mapOffsetY + x * mapScale + zLevel * (gridDimension + 2) * mapScale, mapScale, mapScale)
                    }
                }
            }
        }
    }

    private fun updateGameLogic(deltaTime: Double) {
        val playerGridPos = worldToGridCoords(cameraPosition)
        val gridX = playerGridPos.x.toInt()
        val gridY = playerGridPos.y.toInt()
        val gridZ = playerGridPos.z.toInt()

        if (gridX in 0 until gridDimension &&
            gridY in 0 until 4 &&
            gridZ in 0 until gridDimension &&
            GRID_MAP[gridX][gridY][gridZ] == 3) {
            val offset = gridDimension / 2.0
            val pos = Vector3d(
                (gridX - offset) * cubeSize,
                (gridY - offset) * cubeSize,
                (gridZ - offset) * cubeSize
            )
            val meshToRemove = meshes.find { it.collisionPos == pos }
            if (meshToRemove != null) {
                meshes.remove(meshToRemove)
                meshAABBs.remove(meshToRemove)
                collisionGrid.clear()
                meshes.filter { it.collision }.forEach { mesh ->
                    val aabb = meshAABBs[mesh]!!
                    collisionGrid.add(mesh, aabb)
                }
                rebuildPhysicsStructures()
                rebuildStaticMeshBatches()
                GRID_MAP[gridX][gridY][gridZ] = 0
            }
        }

        // Update orbiting lights
        val orbitSpeed = 0.5 * cubeSize
        val orbitRadius = 2.5 * cubeSize
        val angularVelocity = orbitSpeed / orbitRadius // v = ω * r  => ω = v / r
        var physicsNeedsRebuild = false

        synchronized(orbitingLights) {
            for (orbitingLight in orbitingLights) {
                orbitingLight.angle += angularVelocity * deltaTime
                val offsetX = sin(orbitingLight.angle) * orbitRadius
                val offsetZ = cos(orbitingLight.angle) * orbitRadius
                orbitingLight.light.position = orbitingLight.center.copy(x = orbitingLight.center.x + offsetX, z = orbitingLight.center.z + offsetZ)
            }
        }

        // Update light gizmos
        lightGizmoMeshes.clear()
        val lightGizmoBaseMesh = createCubeMesh(0.1 * cubeSize, Color.WHITE)
        val currentLightSources = synchronized(lightSources) { lightSources.toList() }
        for (light in currentLightSources) {
            val gizmoTransform = Matrix4x4.translation(light.position.x, light.position.y, light.position.z)
            val coloredGizmoMesh = lightGizmoBaseMesh.copy(color = light.color)
            val placedGizmo = PlacedMesh(coloredGizmoMesh, gizmoTransform, collision = false)
            meshAABBs[placedGizmo] = AABB.fromCube(placedGizmo.getTransformedVertices())
            lightGizmoMeshes.add(placedGizmo)
        }

        // Interpolate other players' positions
        val interpolationFactor = (deltaTime * 10.0).coerceIn(0.0, 1.0) // Adjust the multiplier for faster/slower interpolation
        otherPlayers.values.forEach { state ->
            state.currentPos = state.currentPos.lerp(state.targetPos, interpolationFactor)

            // Smooth yaw interpolation
            var yawDiff = state.targetYaw - state.currentYaw
            while (yawDiff < -PI) yawDiff += 2 * PI
            while (yawDiff > PI) yawDiff -= 2 * PI
            state.currentYaw += yawDiff * interpolationFactor
        }


        // Update dynamic player meshes
        val currentDynamicMeshIds = dynamicMeshes.keys.toSet()
        val activePlayerIds = otherPlayers.keys.toSet()

        val toRemove = currentDynamicMeshIds - activePlayerIds
        val toAdd = activePlayerIds - currentDynamicMeshIds
        val toUpdate = currentDynamicMeshIds.intersect(activePlayerIds)

        toRemove.forEach { id ->
            val meshToRemove = dynamicMeshes.remove(id)
            if (meshToRemove != null) {
                meshAABBs.remove(meshToRemove)
                physicsNeedsRebuild = true
            }
        }

        toAdd.forEach { id ->
            val playerData = otherPlayers[id]
            if (playerData != null) {
                val (pos, yaw) = Pair(playerData.currentPos, playerData.currentYaw)
                val playerGizmoBaseMesh = createPlayerMesh(0.3 * cubeSize, Color.WHITE)
                val gizmoTransform = Matrix4x4.translation(pos.x, pos.y - 0.6 * cubeSize, pos.z) * Matrix4x4.rotationY(yaw)
                val placedGizmo = PlacedMesh(playerGizmoBaseMesh, gizmoTransform, collision = true, texture = texCeiling, faceTextures = placedTextures("player", modelRegistry["player"]!!))
                dynamicMeshes[id] = placedGizmo
                meshAABBs[placedGizmo] = AABB.fromCube(placedGizmo.getTransformedVertices())
                physicsNeedsRebuild = true
            }
        }

        toUpdate.forEach { id ->
            val mesh = dynamicMeshes[id]
            val playerData = otherPlayers[id]
            if (mesh != null && playerData != null) {
                val (pos, yaw) = Pair(playerData.currentPos, playerData.currentYaw)
                mesh.transformMatrix = Matrix4x4.translation(pos.x, pos.y - 0.6 * cubeSize, pos.z) * Matrix4x4.rotationY(yaw)
                meshAABBs[mesh] = AABB.fromCube(mesh.getTransformedVertices())
                physicsNeedsRebuild = true // obiekt się poruszył
            }
        }

        if (physicsNeedsRebuild) {
            rebuildPhysicsStructures()
        }
    }

    private fun rebuildPhysicsStructures() {
        val allMeshes = meshes + dynamicMeshes.values
        collisionGrid.clear()
        allMeshes.filter { it.collision }.forEach { mesh ->
            meshAABBs[mesh]?.let { aabb ->
                collisionGrid.add(mesh, aabb)
            }
        }
        bvh.build(allMeshes)
    }

    private fun rebuildStaticMeshBatches() {
        val newBatches = mutableMapOf<Image?, MutableList<PlacedMesh>>()

        meshes.filter { mesh ->
            !mesh.collision && mesh.texture != null && mesh.texture != texSkybox && !isTextureTransparent(mesh.texture)
        }.forEach { mesh ->
            newBatches.computeIfAbsent(mesh.texture) { mutableListOf() }.add(mesh)
        }

        staticMeshBatches = newBatches.map { (texture, groupedMeshes) ->
            val combinedVertices = mutableListOf<Vector3d>()
            val combinedFaces = mutableListOf<List<Int>>()
            val combinedUVs = mutableListOf<List<Vector3d>>()
            val faceIndexToOriginal = mutableMapOf<Int, Pair<PlacedMesh, Int>>()
            var vertexOffset = 0

            val batchAABBs = mutableListOf<AABB>()

            for (placedMesh in groupedMeshes) {
                val worldVertices = placedMesh.mesh.vertices.map { placedMesh.transformMatrix.transform(it) }
                combinedVertices.addAll(worldVertices)
                meshAABBs[placedMesh]?.let { batchAABBs.add(it) }

                placedMesh.mesh.faces.forEachIndexed { faceIndex, face ->
                    combinedFaces.add(face.map { it + vertexOffset })
                    val newFaceIndex = combinedFaces.size - 1
                    faceIndexToOriginal[newFaceIndex] = Pair(placedMesh, faceIndex)
                }
                combinedUVs.addAll(placedMesh.mesh.faceUVs)

                vertexOffset += worldVertices.size
            }

            val batchedMesh = Mesh(
                vertices = combinedVertices,
                faces = combinedFaces,
                faceUVs = combinedUVs,
                color = Color.WHITE
            )
            val combinedAABB = AABB.fromAABBs(batchAABBs)
            StaticMeshBatch(batchedMesh, texture, combinedAABB, faceIndexToOriginal)
        }
        println("Rebuilt static batches. Found ${staticMeshBatches.size} batches.")
    }



    private fun updateDynamicLights() {
        synchronized(lightSources) {
            for (light in lightSources) {
                val isMoving = orbitingLights.any { it.light === light }

                var currentStateHash = 1
                if (!isMoving) {
                    val radiusVec = Vector3d(light.radius, light.radius, light.radius)
                    val lightAABB = AABB(light.position - radiusVec, light.position + radiusVec)
                    val meshesInRadius = collisionGrid.query(lightAABB)
                    meshesInRadius.sortedBy { System.identityHashCode(it) }.forEach { mesh ->
                        currentStateHash = 31 * currentStateHash + System.identityHashCode(mesh)
                        currentStateHash = 31 * currentStateHash + mesh.transformMatrix.hashCode()
                    }
                }

                if (isMoving || currentStateHash != light.lastKnownMeshStateHash) {
                    light.lastKnownMeshStateHash = currentStateHash
                    if (!lightingUpdateJobQueue.contains(light)) {
                        lightingUpdateJobQueue.add(light)
                    }
                }
            }
        }

        if ((lightingFuture == null || lightingFuture!!.isDone) && lightingUpdateJobQueue.isNotEmpty()) {
            val nextLightToProcess = lightingUpdateJobQueue.poll()
            val isMoving = orbitingLights.any { it.light === nextLightToProcess }
            val resolution = if (isMoving) LOW_QualityRes else HIGH_QualityRes
            lightingFuture = scheduleLightingUpdate(nextLightToProcess, resolution)
        }
    }

    private fun scheduleLightingUpdate(lightToProcess: LightSource, resolution: Int): Future<*> {
        val lightSnapshot = LightSource(
            position = lightToProcess.position.copy(),
            radius = lightToProcess.radius,
            color = lightToProcess.color,
            intensity = lightToProcess.intensity,
            type = lightToProcess.type
        )

        val facesToUpdate = ConcurrentHashMap.newKeySet<Pair<PlacedMesh, Int>>()

        val radiusVec = Vector3d(lightSnapshot.radius, lightSnapshot.radius, lightSnapshot.radius)
        val lightAABB = AABB(lightSnapshot.position - radiusVec, lightSnapshot.position + radiusVec)
        val meshesInRadius = meshes.filter { mesh ->
            val meshAABB = meshAABBs[mesh] ?: return@filter false
            lightAABB.intersects(meshAABB)
        }

        for (mesh in meshesInRadius) {
            if (mesh.mesh.faces.isNotEmpty() && mesh.texture != texSkybox) {
                (0 until mesh.mesh.faces.size).forEach { faceIndex -> facesToUpdate.add(Pair(mesh, faceIndex)) }
            }
        }

        lightingUpdateQueue.clear()
        lightingUpdateQueue.addAll(facesToUpdate)

        return lightingExecutor.submit {
            processLightingQueue(resolution)
        }
    }

    private fun colorsAreSimilar(colors: List<Color>, threshold: Double = 0.1): Boolean {
        if (colors.size < 2) return true
        val first = colors.first()
        for (i in 1 until colors.size) {
            val other = colors[i]
            val dr = abs(first.red - other.red)
            val dg = abs(first.green - other.green)
            val db = abs(first.blue - other.blue)
            if (dr + dg + db > threshold) {
                return false
            }
        }
        return true
    }

    private fun processLightingQueue(resolution: Int) {
        val itemsToProcess = lightingUpdateQueue.toList()
        lightingUpdateQueue.clear()

        val allLightsSnapshot = synchronized(lightSources) {
            lightSources.map { lightToCopy ->
                LightSource(lightToCopy.position.copy(), lightToCopy.radius, lightToCopy.color, lightToCopy.intensity, lightToCopy.type)
            }
        }

        val sortedItems = itemsToProcess.sortedBy { (mesh, faceIndex) ->
            val faceIndices = mesh.mesh.faces.getOrNull(faceIndex)
            if (faceIndices == null || faceIndices.size < 3) {
                Double.MAX_VALUE
            } else {
                val worldVerts = faceIndices.map { vi -> mesh.transformMatrix.transform(mesh.mesh.vertices[vi]) }
                val faceCenter = worldVerts.reduce { acc, vec -> acc + vec } / worldVerts.size.toDouble()
                (faceCenter - cameraPosition).length()
            }
        }

        for (item in sortedItems) {
            if (Thread.currentThread().isInterrupted) {
                break
            }

            val (mesh, faceIndex) = item

            val faceIndices = mesh.mesh.faces[faceIndex]
            if (faceIndices.size < 3) continue

            val worldVerts = faceIndices.map { vi -> mesh.transformMatrix.transform(mesh.mesh.vertices[vi]) }
            val v0 = worldVerts[0]
            val v1 = worldVerts[1]
            val v2 = worldVerts[2]
            val faceNormal = (v1 - v0).cross(v2 - v0).normalize()
            val faceCenter = worldVerts.reduce { acc, vec -> acc + vec } / worldVerts.size.toDouble()

            val subDivisions = resolution
            val finalLightGrid = Array(subDivisions) { Array(subDivisions) { Color.BLACK } }
            var isFaceLitAtAll = false

            for (light in allLightsSnapshot) {
                val toLightDirForNormal = (light.position - faceCenter).normalize()
                if (faceNormal.dot(toLightDirForNormal) <= 0) {
                    continue
                }

                if (light.type == LightType.RAYTRACED || light.type == LightType.RAYTRACED_GI) {
                    val cornerUVs = if (worldVerts.size == 4) listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0)) else listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
                    val cornerPoints = cornerUVs.map { (u, v) ->
                        if (worldVerts.size == 4) {
                            val p1 = worldVerts[0].lerp(worldVerts[1], u)
                            val p2 = worldVerts[3].lerp(worldVerts[2], u)
                            p1.lerp(p2, v)
                        } else {
                            worldVerts[0] * (1.0 - u - v) + worldVerts[1] * u + worldVerts[2] * v
                        }
                    }
                    val cornerColors = cornerPoints.map { pointOnFace ->
                        val rayOrigin = light.position
                        val rayTarget = pointOnFace
                        val rayVector = rayTarget - rayOrigin
                        val dist = rayVector.length()
                        if (dist > light.radius || isOccluded(rayOrigin, rayTarget, mesh, faceIndex)) {
                            Color.BLACK
                        } else {
                            val attenuation = 1.0 - (dist / light.radius).coerceIn(0.0, 10.0)
                            val influence = light.intensity * attenuation
                            Color(
                                (light.color.red * influence).coerceIn(0.0, 1.0),
                                (light.color.green * influence).coerceIn(0.0, 1.0),
                                (light.color.blue * influence).coerceIn(0.0, 1.0),
                                1.0
                            )
                        }
                    }

                    if (worldVerts.size == 4 && colorsAreSimilar(cornerColors)) {
                        val c00 = cornerColors[0]; val c10 = cornerColors[1]; val c11 = cornerColors[2]; val c01 = cornerColors[3]
                        for (i_sub in 0 until subDivisions) {
                            for (j_sub in 0 until subDivisions) {
                                val u = i_sub.toDouble() / (subDivisions - 1)
                                val v = j_sub.toDouble() / (subDivisions - 1)
                                val top = c00.interpolate(c10, u)
                                val bottom = c01.interpolate(c11, u)
                                val finalColor = top.interpolate(bottom, v)

                                val existingColor = finalLightGrid[i_sub][j_sub]
                                val newR = (existingColor.red + finalColor.red).coerceIn(0.0, 1.0)
                                val newG = (existingColor.green + finalColor.green).coerceIn(0.0, 1.0)
                                val newB = (existingColor.blue + finalColor.blue).coerceIn(0.0, 1.0)
                                finalLightGrid[i_sub][j_sub] = Color(newR, newG, newB, 1.0)
                            }
                        }
                        isFaceLitAtAll = true
                    } else {
                        for (i_sub in 0 until subDivisions) {
                            for (j_sub in 0 until subDivisions) {
                                val u = (i_sub + 0.5) / subDivisions
                                val v = (j_sub + 0.5) / subDivisions

                                val pointOnFace = if (worldVerts.size == 4) {
                                    val p1 = worldVerts[0].lerp(worldVerts[1], u)
                                    val p2 = worldVerts[3].lerp(worldVerts[2], u)
                                    p1.lerp(p2, v)
                                } else {
                                    var u_bary = u; var v_bary = v
                                    if (u_bary + v_bary > 1.0) { u_bary = 1.0 - u_bary; v_bary = 1.0 - v_bary }
                                    worldVerts[0] * (1.0 - u_bary - v_bary) + worldVerts[1] * u_bary + worldVerts[2] * v_bary
                                }

                                val rayOrigin = light.position
                                val rayTarget = pointOnFace
                                val dist = (rayTarget - rayOrigin).length()

                                if (dist > light.radius) continue

                                if (!isOccluded(rayOrigin, rayTarget, mesh, faceIndex)) {
                                    val attenuation = 1.0 - (dist / light.radius).coerceIn(0.0, 10.0)
                                    val influence = (light.intensity * attenuation)//.coerceIn(0.0, 1.0)
                                    val existingColor = finalLightGrid[i_sub][j_sub]
                                    val lightColor = light.color
                                    val newR = (existingColor.red + lightColor.red * influence).coerceIn(0.0, 1.0)
                                    val newG = (existingColor.green + lightColor.green * influence).coerceIn(0.0, 1.0)
                                    val newB = (existingColor.blue + lightColor.blue * influence).coerceIn(0.0, 1.0)
                                    finalLightGrid[i_sub][j_sub] = Color(newR, newG, newB, 1.0)
                                    isFaceLitAtAll = true

                                    // --- Global Illumination (odbicia) ---
                                    if (light.type == LightType.RAYTRACED_GI) {
                                        for (i in 0 until GI_SAMPLES) {
                                            val incidentDir = (pointOnFace - rayOrigin).normalize()
                                            traceReflection(
                                                reflectionOrigin = pointOnFace,
                                                incidentDirection = incidentDir,
                                                surfaceNormal = faceNormal,
                                                incomingLight = Vector3d(lightColor.red * influence, lightColor.green * influence, lightColor.blue * influence),
                                                bouncesLeft = bouncesLeft,
                                                sourceMesh = mesh,
                                                sourceFaceIndex = faceIndex,
                                                remainingDistance = light.radius - dist
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else { // LightType.VERTEX
                    val vertexColors = worldVerts.map { vertex ->
                        val dist = (light.position - vertex).length()
                        if (dist > light.radius) {
                            Color.BLACK
                        } else {
                            val isBlocked = isLineOfSightBlockedFast(light.position, vertex, mesh)
                            if (isBlocked) {
                                Color.BLACK
                            } else {
                                val attenuation = 1.0 - (dist / light.radius).coerceIn(0.0, 10.0)
                                val influence = light.intensity * attenuation
                                val lightColor = light.color
                                Color(
                                    (lightColor.red * influence).coerceIn(0.0, 1.0),
                                    (lightColor.green * influence).coerceIn(0.0, 1.0),
                                    (lightColor.blue * influence).coerceIn(0.0, 1.0),
                                    1.0
                                )
                            }
                        }
                    }

                    for (i_sub in 0 until subDivisions) {
                        for (j_sub in 0 until subDivisions) {
                            val u = (i_sub + 0.5) / subDivisions
                            val v = (j_sub + 0.5) / subDivisions

                            val interpolatedColor = if (worldVerts.size == 4) {
                                val c1 = vertexColors[0].interpolate(vertexColors[1], u)
                                val c2 = vertexColors[3].interpolate(vertexColors[2], u)
                                c1.interpolate(c2, v)
                            } else {
                                var u_bary = u
                                var v_bary = v
                                if (u_bary + v_bary > 1.0) {
                                    u_bary = 1.0 - u_bary
                                    v_bary = 1.0 - v_bary
                                }
                                val w_bary = 1.0 - u_bary - v_bary

                                val r = vertexColors[0].red * w_bary + vertexColors[1].red * u_bary + vertexColors[2].red * v_bary
                                val g = vertexColors[0].green * w_bary + vertexColors[1].green * u_bary + vertexColors[2].green * v_bary
                                val b = vertexColors[0].blue * w_bary + vertexColors[1].blue * u_bary + vertexColors[2].blue * v_bary
                                Color(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0), 1.0)
                            }

                            val existingColor = finalLightGrid[i_sub][j_sub]
                            val newR = (existingColor.red + interpolatedColor.red).coerceIn(0.0, 1.0)
                            val newG = (existingColor.green + interpolatedColor.green).coerceIn(0.0, 1.0)
                            val newB = (existingColor.blue + interpolatedColor.blue).coerceIn(0.0, 1.0)
                            finalLightGrid[i_sub][j_sub] = Color(newR, newG, newB, 1.0)
                            isFaceLitAtAll = true
                        }
                    }
                }
            }

            if (isFaceLitAtAll) {
                faceLightGrids[item] = finalLightGrid
            } else {
                faceLightGrids.remove(item)
            }

            try {
                Thread.sleep(1)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        applyGILightPoints(resolution)
    }

    private fun traceReflection(reflectionOrigin: Vector3d, incidentDirection: Vector3d, surfaceNormal: Vector3d, incomingLight: Vector3d, bouncesLeft: Int, sourceMesh: PlacedMesh, sourceFaceIndex: Int, remainingDistance: Double) {
        if (bouncesLeft <= 0 || remainingDistance <= 0) {
            return
        }

        var randomDir: Vector3d
        do {
            randomDir = Vector3d(Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1)
        } while (randomDir.length() > 1.0)
        randomDir = randomDir.normalize()
        if (randomDir.dot(surfaceNormal) < 0) {
            randomDir *= -1.0
        }
        val bounceOrigin = reflectionOrigin + randomDir * 0.1

        val bounceHitResult = bvh.intersectWithDetails(bounceOrigin, randomDir, remainingDistance, sourceMesh, sourceFaceIndex)

        if (bounceHitResult != null) {
            val (finalHitPrimitive, finalHitDist) = bounceHitResult
            val finalHitPoint = bounceOrigin + randomDir * finalHitDist

            // albedo
            val surfaceTexture = sourceMesh.faceTextures[sourceFaceIndex] ?: sourceMesh.texture
            val surfaceAlbedo = if (surfaceTexture != null) {
                val texColor = surfaceTexture.pixelReader.getColor(0, 0)
                Vector3d(texColor.red, texColor.green, texColor.blue)
            } else {
                val meshColor = sourceMesh.mesh.color
                Vector3d(meshColor.red, meshColor.green, meshColor.blue)
            }

            val outgoingLight = Vector3d(
                (incomingLight.x * surfaceAlbedo.x).coerceIn(0.0, 1.0),
                (incomingLight.y * surfaceAlbedo.y).coerceIn(0.0, 1.0),
                (incomingLight.z * surfaceAlbedo.z).coerceIn(0.0, 1.0)
            )

            // dodaj światło do siatki trafionej powierzchni (jeśli to ostatnie odbicie) lub kontynuuj rekurencję
            if (bouncesLeft == 1) {
                // w który piksel siatki światła trafił promień
                val barycentricCoords = finalHitPrimitive.barycentricCoords
                val allFaceUVs = finalHitPrimitive.mesh.mesh.faceUVs[finalHitPrimitive.faceIndex]

                val u: Double
                val v: Double

                if (allFaceUVs.size == 4) {
                    val triUVs: List<Vector3d>
                    val meshFaceIndices = finalHitPrimitive.mesh.mesh.faces[finalHitPrimitive.faceIndex]
                    val meshV1 = finalHitPrimitive.mesh.getTransformedVertices()[meshFaceIndices[1]]

                    triUVs = if (finalHitPrimitive.v1 == meshV1) {
                        // Pierwszy trójkąt (0, 1, 2)
                        listOf(allFaceUVs[0], allFaceUVs[1], allFaceUVs[2])
                    } else {
                        // Drugi trójkąt (0, 2, 3)
                        listOf(allFaceUVs[0], allFaceUVs[2], allFaceUVs[3])
                    }
                    // Interpolacja dla trójkąta
                    u = triUVs[0].x * barycentricCoords.z + triUVs[1].x * barycentricCoords.x + triUVs[2].x * barycentricCoords.y
                    v = triUVs[0].y * barycentricCoords.z + triUVs[1].y * barycentricCoords.x + triUVs[2].y * barycentricCoords.y
                } else {
                    u = allFaceUVs[0].x * barycentricCoords.z + allFaceUVs[1].x * barycentricCoords.x + allFaceUVs[2].x * barycentricCoords.y
                    v = allFaceUVs[0].y * barycentricCoords.z + allFaceUVs[1].y * barycentricCoords.x + allFaceUVs[2].y * barycentricCoords.y
                }


                val gridX = (u * HIGH_QualityRes).toInt().coerceIn(0, HIGH_QualityRes - 1)
                val gridY = ((1.0 - v) * HIGH_QualityRes).toInt().coerceIn(0, HIGH_QualityRes - 1)

                // Zamiast modyfikować mapę, dodaj punkt do kolejki
                giLightPoints.add(GI_LightPoint(
                    targetFaceKey = Pair(finalHitPrimitive.mesh, finalHitPrimitive.faceIndex),
                    gridX = gridX,
                    gridY = gridY,
                    color = outgoingLight
                ))
            } else {
                val finalHitNormal = (finalHitPrimitive.v1 - finalHitPrimitive.v0).cross(finalHitPrimitive.v2 - finalHitPrimitive.v0).normalize()
                traceReflection(finalHitPoint, randomDir, finalHitNormal, outgoingLight, bouncesLeft - 1, finalHitPrimitive.mesh, finalHitPrimitive.faceIndex, remainingDistance - finalHitDist)
            }
        }
    }

    private fun getPointOnFace(mesh: PlacedMesh, faceIndex: Int, u: Double, v: Double): Vector3d {
        val worldVerts = mesh.mesh.faces[faceIndex].map { vi -> mesh.transformMatrix.transform(mesh.mesh.vertices[vi]) }
        return if (worldVerts.size == 4) {
            val p1 = worldVerts[0].lerp(worldVerts[1], u)
            val p2 = worldVerts[3].lerp(worldVerts[2], u)
            p1.lerp(p2, v)
        } else {
            var u_bary = u; var v_bary = v
            if (u_bary + v_bary > 1.0) { u_bary = 1.0 - u_bary; v_bary = 1.0 - v_bary }
            worldVerts[0] * (1.0 - u_bary - v_bary) + worldVerts[1] * u_bary + worldVerts[2] * v_bary
        }
    }

    private fun applyGILightPoints(resolution: Int) {
        synchronized(giLightPoints) {
            if (giLightPoints.isEmpty()) return

            // pogrupuj punkty GI według ściany, na którą padają
            val pointsByFace = giLightPoints.groupBy { it.targetFaceKey }

            // mapa wierzchołków do ścian dla szybkiego wyszukiwania sąsiadów
            val vertexToFacesMap = mutableMapOf<Vector3d, MutableList<Pair<PlacedMesh, Int>>>()
            for (faceKey in pointsByFace.keys) {
                val (mesh, faceIndex) = faceKey
                val worldVerts = mesh.mesh.faces[faceIndex].map { mesh.transformMatrix.transform(mesh.mesh.vertices[it]) }
                worldVerts.forEach { vertex ->
                    vertexToFacesMap.computeIfAbsent(vertex) { mutableListOf() }.add(faceKey)
                }
            }

            for ((faceKey, pointsOnFace) in pointsByFace) {
                if (Thread.currentThread().isInterrupted) {
                    break
                }

                val giGrid = giLightGrids.computeIfAbsent(faceKey) { Array(resolution) { Array(resolution) { Color.BLACK } } }

                // Znajdź sąsiadujące ściany
                val (currentMesh, currentFaceIndex) = faceKey
                val currentWorldVerts = currentMesh.mesh.faces[currentFaceIndex].map { currentMesh.transformMatrix.transform(currentMesh.mesh.vertices[it]) }
                val neighborFaceKeys = currentWorldVerts.flatMap { vertex -> vertexToFacesMap[vertex] ?: emptyList() }
                    .filter { it != faceKey }
                    .distinct()

                val allRelevantPoints = pointsOnFace.toMutableList()
                neighborFaceKeys.forEach { neighborKey ->
                    pointsByFace[neighborKey]?.let { allRelevantPoints.addAll(it) }
                }

                // wypełnij siatkę oświetlenia - metodą IDW
                for (y in 0 until resolution) {
                    for (x in 0 until resolution) {
                        // Oblicz pozycję 3D piksela na siatce
                        val u = (x + 0.5) / resolution
                        val v = 1.0 - ((y + 0.5) / resolution) // Odwrócone V
                        val pointOnFace3D = getPointOnFace(currentMesh, currentFaceIndex, u, v)

                        var totalWeight = 0.0
                        var weightedR = 0.0
                        var weightedG = 0.0
                        var weightedB = 0.0

                        // Zbierz wpływy od wszystkich relevantnych punktów GI (z tej ściany i sąsiadów)
                        for (point in allRelevantPoints) {
                            val (pointMesh, pointFaceIndex) = point.targetFaceKey
                            val pointU = (point.gridX + 0.5) / resolution
                            val pointV = 1.0 - ((point.gridY + 0.5) / resolution)
                            val point3D = getPointOnFace(pointMesh, pointFaceIndex, pointU, pointV)

                            val distanceSq = pointOnFace3D.distanceSquared(point3D)

                            val weight = 1.0 / (distanceSq + 0.01)

                            weightedR += point.color.x * weight
                            weightedG += point.color.y * weight
                            weightedB += point.color.z * weight
                            totalWeight += weight
                        }

                        // Oblicz ostateczny kolor jako średnią ważoną
                        if (totalWeight > 0) {
                            val finalR = (weightedR / totalWeight).coerceIn(0.0, 1.0)
                            val finalG = (weightedG / totalWeight).coerceIn(0.0, 1.0)
                            val finalB = (weightedB / totalWeight).coerceIn(0.0, 1.0)

                            giGrid[x][y] = Color(
                                finalR,
                                finalG,
                                finalB,
                                1.0
                            )
                        }
                    }
                }

                // --- POST-PROCESSING: Wygładzanie oryginalnych punktów odbicia ---
                // Zastąp kolor w miejscu oryginalnego trafienia promienia GI średnią z sąsiadów,
                // aby wtopić go w uśrednione otoczenie.
                for (point in pointsOnFace) { // Iteruj tylko po punktach, które trafiły w TĘ ścianę
                    val px = point.gridX
                    val py = point.gridY

                    var sumR = 0.0
                    var sumG = 0.0
                    var sumB = 0.0
                    var count = 0

                    val neighbors = listOf(
                        Pair(px, py - 1), // Góra
                        Pair(px, py + 1), // Dół
                        Pair(px - 1, py), // Lewo
                        Pair(px + 1, py)  // Prawo
                    )

                    for ((nx, ny) in neighbors) {
                        if (nx >= 0 && nx < resolution && ny >= 0 && ny < resolution) {
                            val neighborColor = giGrid[nx][ny]
                            sumR += neighborColor.red
                            sumG += neighborColor.green
                            sumB += neighborColor.blue
                            count++
                        }
                    }

                    if (count > 0) {
                        giGrid[px][py] = Color(
                            (sumR / count).coerceIn(0.0, 1.0),
                            (sumG / count).coerceIn(0.0, 1.0),
                            (sumB / count).coerceIn(0.0, 1.0),
                            1.0
                        )
                    }
                }

                giUpdateQueue.add(faceKey)
            }
            giLightPoints.clear()
        }
    }

    private fun isOccluded(rayOrigin: Vector3d, rayTarget: Vector3d, ignoreMesh: PlacedMesh, ignoreFaceIndex: Int): Boolean {
        val rayDir = (rayTarget - rayOrigin).normalize()
        val rayLength = (rayTarget - rayOrigin).length()
        if (rayLength < 1e-5) return false

        val biasedRayLength = rayLength - 0.01
        return bvh.intersect(rayOrigin, rayDir, biasedRayLength, ignoreMesh, ignoreFaceIndex)
    }

    private fun isLineOfSightBlockedFast(start: Vector3d, end: Vector3d, ignoreMesh: PlacedMesh): Boolean {
        val direction = (end - start).normalize()
        val distance = (end - start).length()
        if (distance < 1e-2) return false

        val rayAABB = AABB(
            Vector3d(min(start.x, end.x), min(start.y, end.y), min(start.z, end.z)),
            Vector3d(max(start.x, end.x), max(start.y, end.y), max(start.z, end.z))
        )
        val potentialOccluders = collisionGrid.query(rayAABB)

        val numSteps = 5
        val stepSize = distance / numSteps

        for (i in 1 until numSteps) {
            val testPoint = start + direction * (i * stepSize)
            for (mesh in potentialOccluders) {
                if (mesh === ignoreMesh) continue
                val meshAABB = meshAABBs[mesh] ?: continue
                if (meshAABB.contains(testPoint)) {
                    val worldBlushes = mesh.mesh.blushes.map { blush ->
                        val transformedCorners = blush.getCorners().map { corner -> mesh.transformMatrix.transform(corner) }
                        AABB.fromCube(transformedCorners)
                    }
                    if (worldBlushes.isEmpty() || !worldBlushes.any { it.contains(testPoint) }) {
                        return true
                    }
                }
            }
        }
        return false
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
        if (virtualWidth <= 0 || virtualHeight <= 0) {
            isRendering.set(false)
            return
        }

        if (depthBuffer.size != virtualWidth * virtualHeight) {
            depthBuffer = DoubleArray(virtualWidth * virtualHeight) { Double.MAX_VALUE }
            pixelBuffer = IntArray(virtualWidth * virtualHeight)
            backBuffer = WritableImage(virtualWidth, virtualHeight)
            imageView.image = backBuffer
        }

        pixelBuffer.fill(bgColorInt)
        depthBuffer.fill(Double.MAX_VALUE)
        renderQueue.clear()
        transparentRenderQueue.clear()

        val lookDirection = Vector3d(cos(cameraPitch) * sin(cameraYaw), sin(cameraPitch), cos(cameraPitch) * cos(cameraYaw)).normalize()
        val upVector = Vector3d(0.0, 1.0, 0.0)
        val viewMatrix = Matrix4x4.lookAt(cameraPosition, cameraPosition + lookDirection, upVector)
        val projectionMatrix = Matrix4x4.perspective(dynamicFov, virtualWidth.toDouble() / virtualHeight.toDouble(), 0.1, renderDistanceBlocks)
        val combinedMatrix = projectionMatrix * viewMatrix

        val facesToProcess = mutableListOf<FaceToProcess>()

        // add walls from individual objects (dynamic, transparent, special)
        val individualMeshesToRender = (meshes + lightGizmoMeshes + dynamicMeshes.values).filter { mesh ->
            val isBatched = staticMeshBatches.any { batch -> batch.texture == mesh.texture && !mesh.collision }
            !isBatched
        }

        for (mesh in individualMeshesToRender) {
            val meshAABB = meshAABBs[mesh]
            if (mesh.texture == texSkybox || meshAABB == null || !isAabbOutsideFrustum(meshAABB, combinedMatrix)) {
                val worldVertices = mesh.mesh.vertices.map { mesh.transformMatrix.transform(it) }
                mesh.mesh.faces.forEachIndexed { faceIndex, faceIndices ->
                    facesToProcess.add(FaceToProcess(mesh, faceIndex, faceIndices, worldVertices))
                }
            }
        }

        // add walls from batched objects
        for (batch in staticMeshBatches) {
            if (isAabbOutsideFrustum(batch.aabb, combinedMatrix)) continue
            val worldVertices = batch.mesh.vertices
            batch.mesh.faces.forEachIndexed { faceIndex, faceIndices ->
                facesToProcess.add(FaceToProcess(batch, faceIndex, faceIndices, worldVertices))
            }
        }

        val tasks = mutableListOf<Future<*>>()
        val chunkSize = (facesToProcess.size / Runtime.getRuntime().availableProcessors()).coerceAtLeast(100)
        val chunks = facesToProcess.chunked(chunkSize)

        for (chunk in chunks) {
            tasks.add(executor.submit {
                for (faceData in chunk) {
                    if (faceData.indices.size < 3) continue

                    val worldVertices = faceData.worldVertices
                    val p0View = viewMatrix.transform(worldVertices[faceData.indices[0]])
                    val p1View = viewMatrix.transform(worldVertices[faceData.indices[1]])
                    val p2View = viewMatrix.transform(worldVertices[faceData.indices[2]])

                    val normalView = (p1View - p0View).cross(p2View - p0View).normalize()
                    val cameraRayView = (Vector3d(0.0, 0.0, 0.0) - p0View).normalize()

                    if (normalView.dot(cameraRayView) > 0) { // Back-face culling
                        val faceWorldVertices = faceData.indices.map { worldVertices[it] }
                        val faceTexCoords: List<Vector3d>
                        val lightGrid: Array<Array<Color>>?
                        val faceTexture: Image?
                        val giGrid: Array<Array<Color>>?
                        val meshColor: Color
                        val modelName: String?
                        val worldBlushes: List<AABB>
                        val blushContainerAABB: AABB?

                        if (faceData.source is PlacedMesh) {
                            val mesh = faceData.source
                            faceTexCoords = mesh.mesh.faceUVs[faceData.faceIndex]
                            lightGrid = faceLightGrids[Pair(mesh, faceData.faceIndex)]
                            giGrid = giLightGrids[Pair(mesh, faceData.faceIndex)]
                            meshColor = mesh.mesh.color
                            modelName = modelRegistry.entries.find { it.value === mesh.mesh }?.key
                            val textureName = mesh.mesh.faceTextureNames[faceData.faceIndex]
                            faceTexture = if (textureName != null && textureName.startsWith("custom_")) {
                                val textureId = textureName.substringAfter("custom_").toIntOrNull()
                                if (textureId != null && modelName != null) dynamicTextures["${modelName}_$textureId"]
                                else mesh.faceTextures[faceData.faceIndex] ?: mesh.texture
                            } else {
                                mesh.faceTextures[faceData.faceIndex] ?: mesh.texture
                            }
                            worldBlushes = mesh.mesh.blushes.map { blush ->
                                val transformedCorners = blush.getCorners().map { corner -> mesh.transformMatrix.transform(corner) }
                                AABB.fromCube(transformedCorners)
                            }
                            blushContainerAABB = if (worldBlushes.isNotEmpty()) AABB.fromAABBs(worldBlushes) else null

                        } else { // source is StaticMeshBatch
                            val batch = faceData.source as StaticMeshBatch
                            faceTexCoords = batch.mesh.faceUVs[faceData.faceIndex]
                            val originalFaceInfo = batch.faceIndexToOriginal[faceData.faceIndex]
                            lightGrid = if (originalFaceInfo != null) faceLightGrids[originalFaceInfo] else null
                            giGrid = if (originalFaceInfo != null) giLightGrids[originalFaceInfo] else null
                            faceTexture = batch.texture
                            meshColor = batch.mesh.color
                            modelName = null

                            // blushes system
                            if (originalFaceInfo != null) {
                                val (originalMesh, _) = originalFaceInfo
                                worldBlushes = originalMesh.mesh.blushes.map { blush ->
                                    val transformedCorners = blush.getCorners().map { corner -> originalMesh.transformMatrix.transform(corner) }
                                    AABB.fromCube(transformedCorners)
                                }
                                blushContainerAABB = if (worldBlushes.isNotEmpty()) AABB.fromAABBs(worldBlushes) else null
                            } else {
                                worldBlushes = emptyList()
                                blushContainerAABB = null
                            }
                        }

                        // Triangulation
                        val triangles = if (faceData.indices.size == 4) {
                            listOf(
                                Pair(listOf(faceWorldVertices[0], faceWorldVertices[1], faceWorldVertices[2]), listOf(faceTexCoords[0], faceTexCoords[1], faceTexCoords[2])),
                                Pair(listOf(faceWorldVertices[0], faceWorldVertices[2], faceWorldVertices[3]), listOf(faceTexCoords[0], faceTexCoords[2], faceTexCoords[3]))
                            )
                        } else {
                            listOf(Pair(faceWorldVertices, faceTexCoords))
                        }

                        for ((triWorld, triUV) in triangles) {
                            processAndQueueRenderableFace(triWorld, triUV, meshColor, faceTexture, lightGrid, giGrid, worldBlushes, blushContainerAABB, combinedMatrix, viewMatrix)
                        }
                    }
                }
            })
        }

        for (task in tasks) { task.get() }

        // Przetwarzanie ścian zaktualizowanych przez GI
        while (giUpdateQueue.isNotEmpty()) {
            val faceToUpdateKey = giUpdateQueue.poll() ?: continue
            val (mesh, faceIndex) = faceToUpdateKey

            val faceIndices = mesh.mesh.faces.getOrNull(faceIndex) ?: continue
            val worldVertices = faceIndices.map { mesh.transformMatrix.transform(mesh.mesh.vertices[it]) }
            val faceTexCoords = mesh.mesh.faceUVs.getOrNull(faceIndex) ?: continue
            val lightGrid = faceLightGrids[faceToUpdateKey]
            val faceTexture = mesh.faceTextures[faceIndex] ?: mesh.texture
            val giGrid = giLightGrids[faceToUpdateKey]

            // Ponownie trianguluj i dodaj do kolejki renderowania
            val triangles = if (faceIndices.size == 4) {
                listOf(
                    Pair(listOf(worldVertices[0], worldVertices[1], worldVertices[2]), listOf(faceTexCoords[0], faceTexCoords[1], faceTexCoords[2])),
                    Pair(listOf(worldVertices[0], worldVertices[2], worldVertices[3]), listOf(faceTexCoords[0], faceTexCoords[2], faceTexCoords[3]))
                )
            } else {
                listOf(Pair(worldVertices, faceTexCoords))
            }

            triangles.forEach { (triWorld, triUV) -> processAndQueueRenderableFace(triWorld, triUV, mesh.mesh.color, faceTexture, lightGrid, giGrid, emptyList(), null, combinedMatrix, viewMatrix) }
        }

        // Rasteryzuj kolejki
        while (renderQueue.isNotEmpty()) {
            val renderableFace = renderQueue.poll()
            rasterizeTexturedTriangle(pixelBuffer, renderableFace, virtualWidth, virtualHeight)
        }

        val sortedTransparentFaces = transparentRenderQueue.sortedByDescending { face ->
            face.screenVertices.map { it.z }.average()
        }
        for (renderableFace in sortedTransparentFaces) {
            rasterizeTexturedTriangle(pixelBuffer, renderableFace, virtualWidth, virtualHeight)
        }

        backBuffer.pixelWriter.setPixels(0, 0, virtualWidth, virtualHeight, PixelFormat.getIntArgbInstance(), pixelBuffer, 0, virtualWidth)
        imageView.image = backBuffer
        drawOverlay()
        isRendering.set(false)
    }

    private fun rasterizeTexturedTriangle(pixelBuffer: IntArray, renderableFace: RenderableFace, screenWidth: Int, screenHeight: Int) {
        val (screenVertices, originalClipW, textureVertices, color, _, texture, worldVertices, lightGrid, blushes, blushContainerAABB, giGrid) = renderableFace
        if (screenVertices.size != 3 || textureVertices.size != 3 || originalClipW.size != 3) return

        val v0 = screenVertices[0]; val v1 = screenVertices[1]; val v2 = screenVertices[2]
        val wVert0 = worldVertices[0]; val wVert1 = worldVertices[1]; val wVert2 = worldVertices[2]
        val uv0 = textureVertices[0]; val uv1 = textureVertices[1]; val uv2 = textureVertices[2]
        val w0 = originalClipW[0]; val w1 = originalClipW[1]; val w2 = originalClipW[2]

        val minX = max(0, minOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt()))
        val maxX = min(screenWidth - 1, maxOf(v0.x.toInt(), v1.x.toInt(), v2.x.toInt()))
        val minY = max(0, minOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt()))
        val maxY = min(screenHeight - 1, maxOf(v0.y.toInt(), v1.y.toInt(), v2.y.toInt()))

        val A12 = v1.y - v2.y; val B12 = v2.x - v1.x
        val A20 = v2.y - v0.y; val B20 = v0.x - v2.x
        val A01 = v0.y - v1.y; val B01 = v1.x - v0.x
        val C12_base = v1.x * v2.y - v2.x * v1.y
        val C20_base = v2.x * v0.y - v0.x * v2.y
        val C01_base = v0.x * v1.y - v1.x * v0.y

        val totalArea = A12 * v0.x + B12 * v0.y + C12_base
        if (abs(totalArea) < 1e-5) return
        val invTotalArea = 1.0 / totalArea

        val u0_prime = uv0.x / w0; val v0_prime = uv0.y / w0; val z0_inv_prime = 1.0 / w0
        val u1_prime = uv1.x / w1; val v1_prime = uv1.y / w1; val z1_inv_prime = 1.0 / w1
        val u2_prime = uv2.x / w2; val v2_prime = uv2.y / w2; val z2_inv_prime = 1.0 / w2

        val wVert0_prime = wVert0 / w0
        val wVert1_prime = wVert1 / w1
        val wVert2_prime = wVert2 / w2

        val ambientR = color.red * ambientIntensity
        val ambientG = color.green * ambientIntensity
        val ambientB = color.blue * ambientIntensity

        val fogR = fogColor.red; val fogG = fogColor.green; val fogB = fogColor.blue

        var bary_w0_row = A12 * minX + B12 * minY + C12_base
        var bary_w1_row = A20 * minX + B20 * minY + C20_base
        var bary_w2_row = A01 * minX + B01 * minY + C01_base

        for (py in minY..maxY) {
            val rowOffset = py * screenWidth
            var barycentric_w0 = bary_w0_row
            var barycentric_w1 = bary_w1_row
            var barycentric_w2 = bary_w2_row

            for (px in minX..maxX) {
                if ((barycentric_w0 >= 0 && barycentric_w1 >= 0 && barycentric_w2 >= 0) || (barycentric_w0 <= 0 && barycentric_w1 <= 0 && barycentric_w2 <= 0)) {
                    val alpha = barycentric_w0 * invTotalArea
                    val beta = barycentric_w1 * invTotalArea
                    val gamma = 1.0 - alpha - beta

                    val interpolated_z_inv_prime = alpha * z0_inv_prime + beta * z1_inv_prime + gamma * z2_inv_prime
                    if (interpolated_z_inv_prime >= 1e-6) {
                        val inv_z_prime = 1.0 / interpolated_z_inv_prime
                        val interpolatedWorldPos = (wVert0_prime * alpha + wVert1_prime * beta + wVert2_prime * gamma) * inv_z_prime

                        val isInBlush = blushContainerAABB?.contains(interpolatedWorldPos) == true && blushes.any { blush ->
                            blush.contains(interpolatedWorldPos)
                        }

                        if (!isInBlush) {
                            val pixelIndex = px + rowOffset
                            val interpolatedZ = alpha * v0.z + beta * v1.z + gamma * v2.z

                            if (interpolatedZ < depthBuffer[pixelIndex]) {
                                if (texture == null) { // This is a gizmo or untextured face
                                    val finalColor = colorToInt(color)
                                    pixelBuffer[pixelIndex] = finalColor
                                    depthBuffer[pixelIndex] = interpolatedZ
                                    continue
                                }

                                val texReader = texture.pixelReader // This will now only be called if texture is not null
                                val texWidth = texture.width.toInt(); val texHeight = texture.height.toInt()

                                val u = (alpha * u0_prime + beta * u1_prime + gamma * u2_prime) * inv_z_prime
                                val v = (alpha * v0_prime + beta * v1_prime + gamma * v2_prime) * inv_z_prime
                                val texX = (u * texWidth).toInt().coerceIn(0, texWidth - 1)
                                val texY = (v * texHeight).toInt().coerceIn(0, texHeight - 1)

                                val texColor = texReader.getColor(texX, texY)

                                if (texColor.opacity == 0.0) {
                                    barycentric_w0 += A12; barycentric_w1 += A20; barycentric_w2 += A01
                                    continue
                                }

                                val dynamicLight = if (lightGrid != null) {
                                    val lightGridResolution = lightGrid.size
                                    val gridX = (u * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    val gridY = ((1.0 - v) * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    lightGrid[gridX][gridY]
                                } else {
                                    Color.BLACK
                                }

                                val giLight = if (giGrid != null) {
                                    val lightGridResolution = giGrid.size
                                    val gridX = (u * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    val gridY = ((1.0 - v) * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    giGrid[gridX][gridY]
                                } else {
                                    Color.BLACK
                                }

                                val ambientLitR = texColor.red * (ambientR * 2)
                                val ambientLitG = texColor.green * (ambientG * 2)
                                val ambientLitB = texColor.blue * (ambientB * 2)

                                val dynamicLightR = dynamicLight.red * (ambientIntensity * globalLightIntensity)
                                val dynamicLightG = dynamicLight.green * (ambientIntensity * globalLightIntensity)
                                val dynamicLightB = dynamicLight.blue * (ambientIntensity * globalLightIntensity)

                                // Modyfikacja GI: im jaśniejsze światło bezpośrednie, tym słabszy efekt GI.
                                // To sprawia, że GI jest bardziej widoczne w cieniach.
                                val directLightLuminance = (dynamicLight.red + dynamicLight.green + dynamicLight.blue) / 3.0
                                val giModulationFactor = (1.0 - directLightLuminance).coerceIn(0.0, 1.0)

                                // Zastosuj modulację do światła GI
                                val giLightR = giLight.red * (ambientIntensity * globalLightIntensity) * giModulationFactor
                                val giLightG = giLight.green * (ambientIntensity * globalLightIntensity) * giModulationFactor
                                val giLightB = giLight.blue * (ambientIntensity * globalLightIntensity) * giModulationFactor


                                // Zsumuj wszystkie składowe światła
                                var r = ambientLitR + dynamicLightR / 4 + giLightR * GI_LightIntensity * 2.0
                                var g = ambientLitG + dynamicLightG / 4 + giLightG * GI_LightIntensity * 2.0
                                var b = ambientLitB + dynamicLightB / 4 + giLightB * GI_LightIntensity * 2.0

                                if (texture != this.texSkybox) {
                                    val distance = inv_z_prime
                                    val fogFactor = ((distance - fogStartDistance) / (fogEndDistance - fogStartDistance)).coerceIn(0.0, 1.0) * fogDensity

                                    r = r * (1 - fogFactor) + fogR * fogFactor
                                    g = g * (1 - fogFactor) + fogG * fogFactor
                                    b = b * (1 - fogFactor) + fogB * fogFactor
                                }

                                if (retroScanLineMode) {
                                    val screenCenterY = screenHeight / 2.0
                                    val screenCenterX = screenWidth / 2.0
                                    val distFromCenterY = (py - screenCenterY) / screenCenterY
                                    val distFromCenterX = (px - screenCenterX) / screenCenterX
                                    val distSquared = distFromCenterX * distFromCenterX + distFromCenterY * distFromCenterY
                                    val vignetteFactor = 1.0 - distSquared * 0.15

                                    r *= vignetteFactor
                                    g *= vignetteFactor
                                    b *= vignetteFactor

                                    val rShiftFactor = 1.0 + distSquared * 0.05
                                    val bShiftFactor = 1.0 + distSquared * 0.05
                                    val tempR = r * rShiftFactor
                                    val tempB = b * bShiftFactor
                                    val scanlineFactor = 0.80
                                    val bloomFactor = 1.05

                                    when (py % 3) {
                                        0 -> {
                                            r *= scanlineFactor; g *= scanlineFactor; b *= scanlineFactor
                                        }
                                        1 -> {
                                            r *= bloomFactor; g *= bloomFactor; b *= bloomFactor
                                        }
                                    }

                                    val noise = (Math.random() * 0.05) - 0.025
                                    r += noise; g += noise; b += noise

                                    r = tempR
                                    b = tempB
                                }

                                val finalR = (r * 255).toInt().coerceIn(0, 255)
                                val finalG = (g * 255).toInt().coerceIn(0, 255)
                                val finalB = (b * 255).toInt().coerceIn(0, 255)

                                val isOpaque = texColor.opacity >= 1.0
                                if (!isOpaque) {
                                    val srcAlpha = texColor.opacity
                                    val invSrcAlpha = 1.0 - srcAlpha

                                    val dstColorInt = pixelBuffer[pixelIndex]
                                    val dstR = (dstColorInt shr 16) and 0xFF
                                    val dstG = (dstColorInt shr 8) and 0xFF
                                    val dstB = dstColorInt and 0xFF

                                    val blendedR = (finalR * srcAlpha + dstR * invSrcAlpha).toInt()
                                    val blendedG = (finalG * srcAlpha + dstG * invSrcAlpha).toInt()
                                    val blendedB = (finalB * srcAlpha + dstB * invSrcAlpha).toInt()

                                    pixelBuffer[pixelIndex] = (0xFF shl 24) or (blendedR shl 16) or (blendedG shl 8) or blendedB
                                } else {
                                    pixelBuffer[pixelIndex] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB // W pełni nieprzezroczysty piksel
                                    depthBuffer[pixelIndex] = interpolatedZ
                                }
                            }
                        }
                    }
                }
                barycentric_w0 += A12
                barycentric_w1 += A20
                barycentric_w2 += A01
            }
            bary_w0_row += B12
            bary_w1_row += B20
            bary_w2_row += B01
        }
    }

    private fun processAndQueueRenderableFace(worldVerts: List<Vector3d>, texCoords: List<Vector3d>, color: Color, texture: Image?, lightGrid: Array<Array<Color>>?, giGrid: Array<Array<Color>>?, blushes: List<AABB>, blushContainerAABB: AABB?, combinedMatrix: Matrix4x4, viewMatrix: Matrix4x4) {
        val clippedTriangles = clipTriangleAgainstNearPlane(worldVerts, texCoords, viewMatrix, 0.1)

        for ((clippedW, clippedUVs) in clippedTriangles) {
            val projectedVertices = mutableListOf<Vector3d>()
            val originalClipW = mutableListOf<Double>()
            for (vertex in clippedW) {
                val projectedHomogeneous = combinedMatrix.transformHomogeneous(vertex)
                val w = projectedHomogeneous.w
                if (w.isCloseToZero()) continue
                projectedVertices.add(Vector3d((projectedHomogeneous.x / w + 1) * virtualWidth / 2.0, (1 - projectedHomogeneous.y / w) * virtualHeight / 2.0, projectedHomogeneous.z / w))
                originalClipW.add(w)
            }
            if (projectedVertices.size == 3) {
                val isTransparent = isTextureTransparent(texture)
                val face = RenderableFace(projectedVertices, originalClipW, clippedUVs, color, false, texture, clippedW, lightGrid, blushes, blushContainerAABB, giGrid)
                if (isTransparent) {
                    transparentRenderQueue.add(face)
                } else {
                    renderQueue.add(face)
                }
            }
        }
    }

    private fun isAabbOutsideFrustum(aabb: AABB, viewProjMatrix: Matrix4x4): Boolean {
        val corners = aabb.getCorners()
        val clipSpaceCorners = corners.map { viewProjMatrix.transformHomogeneous(it) }

        for (i in 0..5) {
            var allCornersOutside = true
            for (corner in clipSpaceCorners) {
                val isOutside = when (i) {
                    0 -> corner.x < -corner.w // Lewa
                    1 -> corner.x > corner.w  // Prawa
                    2 -> corner.y < -corner.w // Dół
                    3 -> corner.y > corner.w  // Góra
                    4 -> corner.z < -corner.w // Bliska //0
                    5 -> corner.z > corner.w  // Daleka
                    else -> false
                }
                if (!isOutside) {
                    allCornersOutside = false
                    break
                }
            }
            if (allCornersOutside) {
                return true
            }
        }
        return false
    }

    private fun colorToInt(c: Color): Int {
        val r = (c.red * 255).toInt() and 0xFF
        val g = (c.green * 255).toInt() and 0xFF
        val b = (c.blue * 255).toInt() and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun createColorTexture(color: Color): WritableImage {
        val image = WritableImage(8, 8)
        val pixelWriter = image.pixelWriter
        val colorInt = colorToInt(color)
        val pixels = IntArray(8 * 8) { colorInt }

        pixelWriter.setPixels(
            0, 0,
            8, 8,
            PixelFormat.getIntArgbInstance(),
            pixels,
            0,
            8
        )

        return image
    }

    private fun worldToGridCoords(worldPos: Vector3d): Vector3d {
        val offset = gridDimension / 2.0

        val gridX = (worldPos.x / cubeSize + offset).roundToInt()
        val gridY = (worldPos.y / cubeSize + offset).roundToInt()
        val gridZ = (worldPos.z / cubeSize + offset).roundToInt()

        return Vector3d(gridX.toDouble(), gridY.toDouble(), gridZ.toDouble())
    }

    fun loadImage(path: String): Image {
        val stream = DrawingPanel::class.java.classLoader.getResourceAsStream(path)
        val bufferedImage = ImageIO.read(stream)
        return SwingFXUtils.toFXImage(bufferedImage, null)
    }

    private fun isTextureTransparent(texture: Image?): Boolean {
        if (texture == null) return false
        return textureTransparencyCache.computeIfAbsent(texture) {
            val reader = it.pixelReader ?: return@computeIfAbsent false
            for (y in 0 until it.height.toInt()) {
                for (x in 0 until it.width.toInt()) {
                    if (reader.getColor(x, y).opacity < 1.0) return@computeIfAbsent true
                }
            }
            false
        }
    }

    private fun createTextureFromHexData(hexData: List<Int>): WritableImage {
        val width = 16
        val height = 16
        if (hexData.size != width * height) {
            println("Warning: Custom texture data has incorrect size. Expected ${width * height}, got ${hexData.size}")
            return createColorTexture(Color.MAGENTA)
        }
        val image = WritableImage(width, height)
        val pixelWriter = image.pixelWriter
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), hexData.toIntArray(), 0, width)
        return image
    }
}