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
    private val isRendering = java.util.concurrent.atomic.AtomicBoolean(false)
    private val meshes = mutableListOf<PlacedMesh>()
    private val lightGizmoMeshes = Collections.synchronizedList(mutableListOf<PlacedMesh>())
    private val playerGizmoMeshes = Collections.synchronizedList(mutableListOf<PlacedMesh>())
    private val lightSources = Collections.synchronizedList(mutableListOf<LightSource>())
    private data class OrbitingLight(val light: LightSource, val center: Vector3d, var angle: Double = 0.0)
    private val orbitingLights = Collections.synchronizedList(mutableListOf<OrbitingLight>())

    private val faceLightGrids = ConcurrentHashMap<Pair<PlacedMesh, Int>, Array<Array<Color>>>()
    private val lightingUpdateQueue = ConcurrentLinkedQueue<Pair<PlacedMesh, Int>>()
    private val lightingExecutor = Executors.newSingleThreadExecutor()
    private val lightingUpdateJobQueue = ConcurrentLinkedQueue<LightSource>()
    private var lightingFuture: Future<*>? = null

    private val cubeSize = 100.0
    private var gridDimension = 9

    private val defaultScalePlayer = 1.25
    private val scalePlayer = defaultScalePlayer
    private val playerHeight = ((cubeSize / 2 ) - (cubeSize / 20)) * scalePlayer
    private val playerWidth = ((cubeSize / 2 ) - (cubeSize / 20)) * scalePlayer
    private val playerHalfWidth = playerWidth / 2.0

    private var cameraPosition = Vector3d(0.0, 0.0, 0.0)
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
    private val dynamicTextures = ConcurrentHashMap<Int, Image>()
    private var modelRegistry: Map<String, Mesh>

    private var isMouseCaptured = false
    private val mouseSensitivity = 0.003
    private var accumulatedMouseDeltaX = 0.0
    private var accumulatedMouseDeltaY = 0.0

    private val robot: Robot by lazy { Robot() }
    private var isRobotCentering = false

    val pressedKeys = Collections.synchronizedSet(mutableSetOf<KeyCode>())

    private val collisionGrid = SpatialGrid<PlacedMesh>(cubeSize * 2)
    private val meshAABBs = ConcurrentHashMap<PlacedMesh, AABB>()
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val bvh = BVH()
    private val renderQueue = ConcurrentLinkedQueue<RenderableFace>()

    private var lastLightCheckTime = 0L
    private val lightCheckInterval = (1_000_000_000.0 / 30.0).toLong() // 30 Hz

    private val clientSocket: DatagramSocket = DatagramSocket()
    private val serverAddress: InetAddress = InetAddress.getByName("lewapnoob.ddns.net")
    private val serverPort: Int = 1027
    private val clientId: String = UUID.randomUUID().toString()
    private val otherPlayers = ConcurrentHashMap<String, Vector3d>()

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
        )

        modelRegistry.values.forEach { mesh ->
            mesh.customTextures.forEach { (id, hexData) ->
                dynamicTextures.computeIfAbsent(id) { createTextureFromHexData(hexData) }
            }
        }

        val pos0 = Vector3d(0.0, 0.0, 0.0)
        meshes.add(PlacedMesh(modelRegistry["skybox"]!!, Matrix4x4.translation(pos0.x, pos0.y, pos0.z), texture = texSkybox, collision=false))

        val pos1 = Vector3d(5.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["pyramid"]!!, Matrix4x4.translation(pos1.x, pos1.y, pos1.z), faceTextures = placedTextures(modelRegistry["pyramid"]!!)))

        val pos2 = Vector3d(7.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["invertedPyramid"]!!, Matrix4x4.translation(pos2.x, pos2.y, pos2.z), faceTextures = placedTextures(modelRegistry["invertedPyramid"]!!)))

        val pos3 = Vector3d(9.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["tower"]!!, Matrix4x4.translation(pos3.x, pos3.y, pos3.z), texture = texBricks))

        val pos4 = Vector3d(11.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["kotlin"]!!, Matrix4x4.translation(pos4.x, pos4.y, pos4.z), texture = texFloor))

        val pos5 = Vector3d(13.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["tank"]!!, Matrix4x4.translation(pos5.x, pos5.y, pos5.z), faceTextures = placedTextures(modelRegistry["tank"]!!)))

        val pos6 = Vector3d(15.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["offroadCar"]!!, Matrix4x4.translation(pos6.x, pos6.y, pos6.z), texture = texBlackBricks))

        val pos7 = Vector3d(17.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["stair"]!!, Matrix4x4.translation(pos7.x, pos7.y, pos7.z), texture = texCeiling, faceTextures = placedTextures(modelRegistry["stair"]!!)))

        val pos8 = Vector3d(19.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["sphere"]!!, Matrix4x4.translation(pos8.x, pos8.y, pos8.z), texture = texCeiling))

        val pos9 = Vector3d(31.0 * cubeSize, -4.0 * cubeSize, 0 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["map"]!!, Matrix4x4.translation(pos9.x, pos9.y, pos9.z), faceTextures = placedTextures(modelRegistry["map"]!!)))

        val pos10 = Vector3d(50.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["colorPalette"]!!, Matrix4x4.translation(pos10.x, pos10.y, pos10.z), faceTextures = placedTextures(modelRegistry["colorPalette"]!!)))

        val pos11 = Vector3d(21.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(modelRegistry["TNT"]!!, Matrix4x4.translation(pos11.x, pos11.y, pos11.z), faceTextures = placedTextures(modelRegistry["TNT"]!!)))

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

        bvh.build(meshes)

        Thread { listenForServerMessages() }.start()

        object : AnimationTimer() {
            override fun handle(now: Long) {
                val deltaTime = (now - lastUpdateTime) / 1_000_000_000.0
                lastUpdateTime = now

                if (now - lastLightCheckTime > lightCheckInterval) {
                    lastLightCheckTime = now
                    updateDynamicLights()
                }

                updateCameraPosition(deltaTime)
                updateGameLogic(deltaTime)
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
                if (parts.size == 4) {
                    val receivedClientId = parts[0]
                    if (receivedClientId != clientId) {
                        val x = parts[1].toDouble()
                        val y = parts[2].toDouble()
                        val z = parts[3].toDouble()
                        otherPlayers[receivedClientId] = Vector3d(x, y, z)
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    private fun placedTextures(mesh: Mesh): Map<Int, Image> {
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
                ?: dynamicTextures[textureName.substringAfter("custom_").toIntOrNull()]
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
            Vector3d(testPosition.x - playerHalfWidth, testPosition.y - playerHeight / 2, testPosition.z - playerHalfWidth),
            Vector3d(testPosition.x + playerHalfWidth, testPosition.y + playerHeight / 2, testPosition.z + playerHalfWidth)
        )

        val potentialColliders = collisionGrid.query(playerAABB)
        for (mesh in potentialColliders) {
            val worldBlushes = mesh.mesh.blushes.map { blush ->
                val transformedCorners = blush.getCorners().map { corner -> mesh.transformMatrix.transform(corner) }
                AABB.fromCube(transformedCorners)
            }
            if (worldBlushes.any { it.contains(testPosition) }) {
                continue
            }

            val meshAABB = AABB.fromCube(mesh.getTransformedVertices())
            if (playerAABB.intersects(meshAABB)) {
                val worldVertices = mesh.getTransformedVertices()
                for (faceIndices in mesh.mesh.faces.filter { it.size >= 3 }) {
                    for (i in 0 until faceIndices.size - 2) {
                        val v0 = worldVertices[faceIndices[0]]
                        val v1 = worldVertices[faceIndices[i + 1]]
                        val v2 = worldVertices[faceIndices[i + 2]]
                        if (CollisionUtils.testAABBTriangle(playerAABB, v0, v1, v2)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
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
            bvh.build(meshes)
            pressedKeys.remove(KeyCode.R)
        }
        if (pressedKeys.contains(KeyCode.O)) {
            val lightRadius = 6.0 * cubeSize
            lightSources.add(LightSource(Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z), lightRadius, Color.rgb(255, 0, 0)))
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
            lightSources.clear()
            faceLightGrids.clear()
        }

        if (cameraYaw > 2 * PI) cameraYaw -= 2 * PI
        if (cameraYaw < -2 * PI) cameraYaw += 2 * PI

        val message = "$clientId,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z}".toByteArray()
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
                bvh.build(meshes)
                GRID_MAP[gridX][gridY][gridZ] = 0
            }
        }

        // Update orbiting lights
        val orbitSpeed = 0.5 * cubeSize
        val orbitRadius = 2.5 * cubeSize
        val angularVelocity = orbitSpeed / orbitRadius // v = ω * r  => ω = v / r

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

        playerGizmoMeshes.clear()
        val playerGizmoBaseMesh = createCapsuleMesh(0.4 * cubeSize, Color.GRAY)
        otherPlayers.forEach { (id, pos) ->
            val gizmoTransform = Matrix4x4.translation(pos.x, pos.y - 0.5 * cubeSize, pos.z)
            val placedGizmo = PlacedMesh(playerGizmoBaseMesh, gizmoTransform, collision = true, texture = texCeiling)
            playerGizmoMeshes.add(placedGizmo)
        }
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

                if (light.type == LightType.RAYTRACED) {
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
                                    val influence = light.intensity * attenuation
                                    val existingColor = finalLightGrid[i_sub][j_sub]
                                    val lightColor = light.color
                                    val newR = (existingColor.red + lightColor.red * influence).coerceIn(0.0, 1.0)
                                    val newG = (existingColor.green + lightColor.green * influence).coerceIn(0.0, 1.0)
                                    val newB = (existingColor.blue + lightColor.blue * influence).coerceIn(0.0, 1.0)
                                    finalLightGrid[i_sub][j_sub] = Color(newR, newG, newB, 1.0)
                                    isFaceLitAtAll = true
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

        val lookDirection = Vector3d(cos(cameraPitch) * sin(cameraYaw), sin(cameraPitch), cos(cameraPitch) * cos(cameraYaw)).normalize()
        val upVector = Vector3d(0.0, 1.0, 0.0)
        val viewMatrix = Matrix4x4.lookAt(cameraPosition, cameraPosition + lookDirection, upVector)
        val projectionMatrix = Matrix4x4.perspective(dynamicFov, virtualWidth.toDouble() / virtualHeight.toDouble(), 0.1, renderDistanceBlocks)
        val combinedMatrix = projectionMatrix * viewMatrix

        val allMeshesToRender = meshes + lightGizmoMeshes + playerGizmoMeshes

        val tasks = mutableListOf<Future<*>>()

        for (mesh in allMeshesToRender) {
            val meshAABB = meshAABBs[mesh]
            if (mesh.texture == texSkybox || meshAABB == null || !isAabbOutsideFrustum(meshAABB, combinedMatrix)) {
                tasks.add(executor.submit {
                    val worldVertices = mesh.mesh.vertices.map { mesh.transformMatrix.transform(it) }
                    for (faceIndex in mesh.mesh.faces.indices) {
                        val faceIndices = mesh.mesh.faces[faceIndex]
                        if (faceIndices.size < 3) continue

                        val p0View = viewMatrix.transform(worldVertices[faceIndices[0]])
                        val p1View = viewMatrix.transform(worldVertices[faceIndices[1]])
                        val p2View = viewMatrix.transform(worldVertices[faceIndices[2]])

                        val normalView = (p1View - p0View).cross(p2View - p0View).normalize()
                        val cameraRayView = (Vector3d(0.0, 0.0, 0.0) - p0View).normalize()

                        if (normalView.dot(cameraRayView) > 0) {
                            val faceWorldVertices = faceIndices.map { worldVertices[it] }
                            val faceTexCoords = mesh.mesh.faceUVs[faceIndex]

                            val triangles = if (faceIndices.size == 4) {
                                listOf(
                                    Pair(listOf(faceWorldVertices[0], faceWorldVertices[1], faceWorldVertices[2]),
                                        listOf(faceTexCoords[0], faceTexCoords[1], faceTexCoords[2])),
                                    Pair(listOf(faceWorldVertices[0], faceWorldVertices[2], faceWorldVertices[3]),
                                        listOf(faceTexCoords[0], faceTexCoords[2], faceTexCoords[3]))
                                )
                            } else {
                                listOf(Pair(faceWorldVertices, faceTexCoords))
                            }

                            val lightGrid = faceLightGrids[Pair(mesh, faceIndex)]
                            val worldBlushes = mesh.mesh.blushes.map { blush ->
                                val transformedCorners = blush.getCorners().map { corner -> mesh.transformMatrix.transform(corner) }
                                AABB.fromCube(transformedCorners)
                            }

                            val blushContainerAABB = if (worldBlushes.isNotEmpty()) {
                                var min = worldBlushes.first().min.copy()
                                var max = worldBlushes.first().max.copy()
                                worldBlushes.forEach {
                                    min = Vector3d(min(min.x, it.min.x), min(min.y, it.min.y), min(min.z, it.min.z))
                                    max = Vector3d(max(max.x, it.max.x), max(max.y, it.max.y), max(max.z, it.max.z))
                                }
                                AABB(min, max)
                            } else {
                                null
                            }

                            for ((triWorld, triUV) in triangles) {
                                val clippedTriangles = clipTriangleAgainstNearPlane(triWorld, triUV, viewMatrix, 0.1)
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
                                        val textureName = mesh.mesh.faceTextureNames[faceIndex]
                                        val faceTexture = if (textureName != null && textureName.startsWith("custom_")) {
                                            val textureId = textureName.substringAfter("custom_").toIntOrNull()
                                            if (textureId != null) {
                                                dynamicTextures[textureId]
                                            } else {
                                                mesh.faceTextures[faceIndex] ?: mesh.texture
                                            }
                                        } else {
                                            mesh.faceTextures[faceIndex] ?: mesh.texture
                                        }
                                        renderQueue.add(RenderableFace(projectedVertices, originalClipW, clippedUVs, mesh.mesh.color, false, faceTexture, clippedW, lightGrid, worldBlushes, blushContainerAABB))
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }

        for (task in tasks) { task.get() }

        while (renderQueue.isNotEmpty()) {
            val renderableFace = renderQueue.poll()
            rasterizeTexturedTriangle(pixelBuffer, renderableFace, virtualWidth, virtualHeight)
        }

        backBuffer.pixelWriter.setPixels(0, 0, virtualWidth, virtualHeight, PixelFormat.getIntArgbInstance(), pixelBuffer, 0, virtualWidth)
        imageView.image = backBuffer
        drawOverlay()
        isRendering.set(false)
    }

    private fun rasterizeTexturedTriangle(pixelBuffer: IntArray, renderableFace: RenderableFace, screenWidth: Int, screenHeight: Int) {
        val (screenVertices, originalClipW, textureVertices, color, _, texture, worldVertices, lightGrid, blushes, blushContainerAABB) = renderableFace
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

                        val isInBlush = if (blushContainerAABB != null && blushContainerAABB.contains(interpolatedWorldPos)) {
                            blushes.any { it.contains(interpolatedWorldPos) }
                        } else false

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

                                val dynamicLight = if (lightGrid != null) {
                                    val lightGridResolution = lightGrid.size
                                    val gridX = (u * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    val gridY = ((1.0 - v) * lightGridResolution).toInt().coerceIn(0, lightGridResolution - 1)
                                    lightGrid[gridX][gridY]
                                } else {
                                    Color.BLACK
                                }

                                val ambientLitR = texColor.red * (ambientR * 2)
                                val ambientLitG = texColor.green * (ambientG * 2)
                                val ambientLitB = texColor.blue * (ambientB * 2)

                                val dynamicLightR = dynamicLight.red * (ambientIntensity * globalLightIntensity)
                                val dynamicLightG = dynamicLight.green * (ambientIntensity * globalLightIntensity)
                                val dynamicLightB = dynamicLight.blue * (ambientIntensity * globalLightIntensity)

                                var r = ambientLitR + dynamicLightR / 4
                                var g = ambientLitG + dynamicLightG / 4
                                var b = ambientLitB + dynamicLightB / 4

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

                                pixelBuffer[pixelIndex] = (0xFF shl 24) or
                                        ((r * 255).toInt().coerceIn(0, 255) shl 16) or
                                        ((g * 255).toInt().coerceIn(0, 255) shl 8) or
                                        ((b * 255).toInt().coerceIn(0, 255))
                                depthBuffer[pixelIndex] = interpolatedZ
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


    private fun Color.interpolate(other: Color, t: Double): Color {
        val tClamped = t.coerceIn(0.0, 1.0)
        val invT = 1.0 - tClamped
        return Color(
            this.red * invT + other.red * tClamped,
            this.green * invT + other.green * tClamped,
            this.blue * invT + other.blue * tClamped,
            this.opacity * invT + other.opacity * tClamped
        )
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