package org.lewapnoob.FileZero2

import javafx.animation.AnimationTimer
import javafx.embed.swing.SwingFXUtils
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.image.PixelFormat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.ImageIO
import kotlin.math.*

class DrawingPanel : StackPane() {
    private var lastUpdateTime = System.nanoTime()
    private val isRendering = java.util.concurrent.atomic.AtomicBoolean(false)
    private val meshes = mutableListOf<PlacedMesh>()
    private var lightSources = mutableListOf<LightSource>()

    private val faceLightGrids = ConcurrentHashMap<Pair<PlacedMesh, Int>, Array<Array<Color>>>()
    private val lightingUpdateQueue = ConcurrentLinkedQueue<Pair<PlacedMesh, Int>>()
    private val lightingExecutor = Executors.newSingleThreadExecutor()
    private var lightingFuture: Future<*>? = null

    private var cameraPosition = Vector3d(0.0, 0.0, 0.0)
    private var cameraYaw = 2.4
    private var cameraPitch = 0.0

    private var debugFly = false
    private var debugNoclip = false

    private val cubeSize = 100.0
    private var gridDimension = 9

    private val scalePlayer = 1.25
    private val playerHeight = ((cubeSize / 2 ) - (cubeSize / 20)) * scalePlayer
    private val playerWidth = ((cubeSize / 2 ) - (cubeSize / 20)) * scalePlayer
    private val playerHalfWidth = playerWidth / 2.0

    private val fogColor = Color.rgb(180, 180, 180)
    private val fogStartDistance = 1.5 * cubeSize
    private val fogEndDistance = 7.0 * cubeSize
    private val fogDensity = 0.5

    private val ambientIntensity = 0.5

    private val renderDownscaleFactor = 4
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

    val pressedKeys = Collections.synchronizedSet(mutableSetOf<KeyCode>())

    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val renderQueue = ConcurrentLinkedQueue<RenderableFace>()

    init {
        sceneProperty().addListener { _, LightSource_, newScene ->
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

        //
        val cubeMesh = createCubeMesh(cubeSize, Color.GRAY)
        val cubeMeshRed = createCubeMesh(cubeSize, Color.RED)
        val cubeMeshGates = createCubeMesh(cubeSize, Color.rgb(40,255,40))

        val skyboxMesh = createCubeMesh(1000.0 * cubeSize, Color.rgb(80,80,80), inverted = true)
        val pyramidMesh = createPyramidMesh(cubeSize, Color.RED)
        val invertedPyramidMesh = createInvertedPyramidMesh(cubeSize, Color.DEEPSKYBLUE)
        val towerMesh = createTowerMesh(cubeSize, Color.WHITE)
        val KotlinModel = createKotlinModelMesh(cubeSize, Color.GRAY)
        val Tank = createTankMesh(cubeSize, Color.GREEN)
        val OffroadCar = createOffroadCarMesh(cubeSize, Color.GRAY)
        val Stair = createStairMesh(cubeSize, Color.WHITE)
        val Sphere = createCapsuleMesh(cubeSize, Color.WHITE)
        val custom = createCustomMesh(cubeSize, Color.WHITE)

        val loadedTextures = mapOf(
            "blackBricks" to texBlackBricks,
            "bricks" to texBricks,
            "ceiling" to texCeiling,
            "floor" to texFloor,
            "skybox" to texSkybox,
            "green" to createColorTexture(Color.GREEN),
            "darkGreen" to createColorTexture(Color.DARKGREEN),
        )

        fun placedTextures(mesh: Mesh): Map<Int, Image> {
            return mesh.faceTextureNames.mapValues { (faceIndex, textureName) ->
                loadedTextures[textureName] ?: run {
                    println("missing texture $textureName for face $faceIndex")
                    texSkybox
                }
            }
        }

        val pos0 = Vector3d(0.0, 0.0, 0.0)
        meshes.add(PlacedMesh(skyboxMesh, Matrix4x4.translation(pos0.x, pos0.y, pos0.z), texture = texSkybox, collision=false))

        val pos1 = Vector3d(5.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(pyramidMesh, Matrix4x4.translation(pos1.x, pos1.y, pos1.z), faceTextures = placedTextures(pyramidMesh)))

        val pos2 = Vector3d(7.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(invertedPyramidMesh, Matrix4x4.translation(pos2.x, pos2.y, pos2.z), faceTextures = placedTextures(invertedPyramidMesh)))

        val pos3 = Vector3d(9.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(towerMesh,Matrix4x4.translation(pos3.x, pos3.y, pos3.z), texture = texBricks))

        val pos4 = Vector3d(11.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(KotlinModel,Matrix4x4.translation(pos4.x, pos4.y, pos4.z), texture = texFloor))

        val pos5 = Vector3d(13.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(Tank,Matrix4x4.translation(pos5.x, pos5.y, pos5.z), faceTextures = placedTextures(Tank)))

        val pos6 = Vector3d(15.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(OffroadCar,Matrix4x4.translation(pos6.x, pos6.y, pos6.z), texture = texBlackBricks))

        val pos7 = Vector3d(17.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(Stair,Matrix4x4.translation(pos7.x, pos7.y, pos7.z), texture = texCeiling))

        val pos8 = Vector3d(19.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(Sphere,Matrix4x4.translation(pos8.x, pos8.y, pos8.z), texture = texCeiling))

        val pos9 = Vector3d(21.5 * cubeSize, -4.0 * cubeSize, 0.5 * cubeSize)
        meshes.add(PlacedMesh(custom,Matrix4x4.translation(pos9.x, pos9.y, pos9.z), texture = texCeiling))
        //

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
                        1 -> meshes.add(PlacedMesh(cubeMesh, mat, texBlackBricks, collisionPos = pos))
                        2 -> meshes.add(PlacedMesh(cubeMeshRed, mat, texBlackBricks, collisionPos = pos))
                        3 -> {meshes.add(PlacedMesh(cubeMeshGates, mat, texBricks, collision=false, collisionPos = pos))
                            println("${pos.x} ${pos.y} ${pos.z}")}
                    }
                }
            }
        }

        object : AnimationTimer() {
            override fun handle(now: Long) {
                val deltaTime = (now - lastUpdateTime) / 1_000_000_000.0
                lastUpdateTime = now

                if (lightingFuture == null || lightingFuture!!.isDone) {
                    lightingFuture = scheduleLightingUpdate()
                }

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

        var currentMovementSpeed = 2.8 * cubeSize * scalePlayer
        val playerSprintSpeed = 4.4 * cubeSize * scalePlayer

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

        for (mesh in meshes) {
            val meshAABB = AABB.fromCube(mesh.getTransformedVertices())
            if (playerAABB.intersects(meshAABB) && mesh.collision) {
                collisionOccurred = true
                break
            }
        }

        if (!collisionOccurred || debugNoclip) {
            cameraPosition = newCameraPosition
        } else {
            val testX = AABB(
                Vector3d(newCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(newCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth)
            )
            var collisionX = false
            for (mesh in meshes) {
                if (testX.intersects(AABB.fromCube(mesh.getTransformedVertices())) && mesh.collision) {
                    collisionX = true
                    break
                }
            }
            if (!collisionX) cameraPosition.x = newCameraPosition.x

            val testY = AABB(
                Vector3d(oldCameraPosition.x - playerHalfWidth, newCameraPosition.y - playerHeight / 2, oldCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, newCameraPosition.y + playerHeight / 2, oldCameraPosition.z + playerHalfWidth)
            )
            var collisionY = false
            for (mesh in meshes) {
                if (testY.intersects(AABB.fromCube(mesh.getTransformedVertices())) && mesh.collision) {
                    collisionY = true
                    break
                }
            }
            if (!collisionY) cameraPosition.y = newCameraPosition.y

            val testZ = AABB(
                Vector3d(oldCameraPosition.x - playerHalfWidth, oldCameraPosition.y - playerHeight / 2, newCameraPosition.z - playerHalfWidth),
                Vector3d(oldCameraPosition.x + playerHalfWidth, oldCameraPosition.y + playerHeight / 2, newCameraPosition.z + playerHalfWidth)
            )
            var collisionZ = false
            for (mesh in meshes) {
                if (testZ.intersects(AABB.fromCube(mesh.getTransformedVertices())) && mesh.collision) {
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

        if (pressedKeys.contains(KeyCode.G)){
            val gridPos = worldToGridCoords(cameraPosition)
            println(
                "X:${gridPos.x.toInt()}" +
                " Y:${gridPos.y.toInt()}" +
                " Z:${gridPos.z.toInt()}" +
                " YAW:${((cameraYaw*10).toInt()/10.0)}" +
                " PITCH:${((cameraPitch*10).toInt()/10.0)}" +
                " SPEED:$currentMovementSpeed")
        }

        if (pressedKeys.contains(KeyCode.H)){
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
            meshes.add(PlacedMesh(cubeMeshGray, transformMatrix = translationMatrix, texture = loadImage("textures/black_bricks.png")))
        }
        if (pressedKeys.contains(KeyCode.O)) {
            val lightRadius = 6.0 * cubeSize
            lightSources.add(LightSource(Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z), lightRadius, Color.rgb(188, 0, 0, 180 / 255.0)))
            pressedKeys.remove(KeyCode.O)
        }

        if (cameraYaw > 2 * PI) cameraYaw -= 2 * PI
        if (cameraYaw < -2 * PI) cameraYaw += 2 * PI
    }

    private fun drawOverlay() {
        val gc = overlayCanvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, overlayCanvas.width, overlayCanvas.height)
        gc.stroke = Color.WHITE
        for (x in 0..8) {
            for (y in 0..8) {
                for (zLevel in 0 until 4) {
                    if (GRID_MAP[x][zLevel][y] == 1) {
                        gc.strokeOval(x * 10.0, y * 10.0 + zLevel * 90 + zLevel * 10, 10.0, 10.0)
                    }
                }
            }
        }
    }

    private fun updateGameLogic() {
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
                println("$meshToRemove X:$gridX, Y:$gridY, Z:$gridZ")
                meshes.remove(meshToRemove)
                GRID_MAP[gridX][gridY][gridZ] = 0
            }
        }
    }

    private fun scheduleLightingUpdate(): Future<*> {
        if (lightSources.isEmpty()) {
            if (faceLightGrids.isNotEmpty()) {
                faceLightGrids.clear()
            }
            return lightingExecutor.submit { }
        }

        lightingUpdateQueue.clear()
        for (mesh in meshes) {
            if (mesh.mesh.faces.isNotEmpty() && mesh.texture != texSkybox) {
                for (faceIndex in mesh.mesh.faces.indices) {
                    lightingUpdateQueue.add(Pair(mesh, faceIndex))
                }
            }
        }

        return lightingExecutor.submit {
            processLightingQueue()
        }
    }

    private fun processLightingQueue() {
        val allMeshes = meshes.toList()
        val batchSize = 1000

        while (lightingUpdateQueue.isNotEmpty()) {
            for (i in 0 until batchSize) {
                val item = lightingUpdateQueue.poll() ?: break
                val (mesh, faceIndex) = item

                val faceIndices = mesh.mesh.faces[faceIndex]
                if (faceIndices.size < 3) continue

                val worldVerts = faceIndices.map { vi -> mesh.transformMatrix.transform(mesh.mesh.vertices[vi]) }
                val v0 = worldVerts[0]
                val v1 = worldVerts[1]
                val v2 = worldVerts[2]
                val faceNormal = (v1 - v0).cross(v2 - v0).normalize()

                val subDivisions = 8
                val lightGrid = Array(subDivisions) { Array(subDivisions) { Color.BLACK } }
                var isFaceLitAtAll = false

                for (light in lightSources) {
                    val faceCenter = worldVerts.reduce { acc, vec -> acc + vec } / worldVerts.size.toDouble()
                    val toLightDirForNormal = (light.position - faceCenter).normalize()
                    if (faceNormal.dot(toLightDirForNormal) <= 0) {
                        continue
                    }

                    for (i_sub in 0 until subDivisions) {
                        for (j_sub in 0 until subDivisions) {
                            val u = (i_sub + 0.5) / subDivisions
                            val v = (j_sub + 0.5) / subDivisions

                            val pointOnFace = if (worldVerts.size == 4) {
                                val p1 = worldVerts[0].lerp(worldVerts[1], u)
                                val p2 = worldVerts[3].lerp(worldVerts[2], u)
                                p1.lerp(p2, v)
                            } else {
                                var u_bary = u
                                var v_bary = v
                                if (u_bary + v_bary > 1.0) {
                                    u_bary = 1.0 - u_bary
                                    v_bary = 1.0 - v_bary
                                }
                                worldVerts[0] * (1.0 - u_bary - v_bary) + worldVerts[1] * u_bary + worldVerts[2] * v_bary
                            }

                            val rayOrigin = light.position
                            val rayTarget = pointOnFace
                            val rayVector = rayTarget - rayOrigin
                            val dist = rayVector.length()

                            if (dist > light.radius) continue

                            if (!isOccluded(rayOrigin, rayTarget, allMeshes, mesh, faceIndex)) {
                                val attenuation = 1.0 - (dist / light.radius).coerceIn(0.0, 1.0)
                                val influence = light.intensity * attenuation

                                val existingColor = lightGrid[i_sub][j_sub]
                                val lightColor = light.color

                                val newR = (existingColor.red + lightColor.red * influence).coerceIn(0.0, 1.0)
                                val newG = (existingColor.green + lightColor.green * influence).coerceIn(0.0, 1.0)
                                val newB = (existingColor.blue + lightColor.blue * influence).coerceIn(0.0, 1.0)

                                lightGrid[i_sub][j_sub] = Color(newR, newG, newB, 1.0)
                                isFaceLitAtAll = true
                            }
                        }
                    }
                }

                if (isFaceLitAtAll) {
                    faceLightGrids[item] = lightGrid
                } else {
                    faceLightGrids.remove(item)
                }
            }

            try {
                Thread.sleep(1)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun isOccluded(rayOrigin: Vector3d, rayTarget: Vector3d, meshes: List<PlacedMesh>, ignoreMesh: PlacedMesh, ignoreFaceIndex: Int): Boolean {
        val rayDir = (rayTarget - rayOrigin).normalize()
        val rayLength = (rayTarget - rayOrigin).length() - 1e-5

        for (mesh in meshes) {
            if (!mesh.collision) continue

            val worldVertices = mesh.mesh.vertices.map { mesh.transformMatrix.transform(it) }

            for (faceIndex in mesh.mesh.faces.indices) {
                if (mesh === ignoreMesh && faceIndex == ignoreFaceIndex) {
                    continue
                }

                val faceIndices = mesh.mesh.faces[faceIndex]
                if (faceIndices.size < 3) continue

                val v0 = worldVertices[faceIndices[0]]
                val v1 = worldVertices[faceIndices[1]]
                val v2 = worldVertices[faceIndices[2]]

                val intersectionDist = rayIntersectsTriangle(rayOrigin, rayDir, v0, v1, v2)
                if (intersectionDist != null && intersectionDist < rayLength) {
                    return true
                }

                if (faceIndices.size == 4) {
                    val v3 = worldVertices[faceIndices[3]]
                    val intersectionDist2 = rayIntersectsTriangle(rayOrigin, rayDir, v0, v2, v3)
                    if (intersectionDist2 != null && intersectionDist2 < rayLength) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun rayIntersectsTriangle(rayOrigin: Vector3d, rayDir: Vector3d, v0: Vector3d, v1: Vector3d, v2: Vector3d): Double? {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = rayDir.cross(edge2)
        val a = edge1.dot(h)
        if (a > -1e-6 && a < 1e-6) {
            return null
        }
        val f = 1.0 / a
        val s = rayOrigin - v0
        val u = f * s.dot(h)
        if (u < 0.0 || u > 1.0) {
            return null
        }
        val q = s.cross(edge1)
        val v = f * rayDir.dot(q)
        if (v < 0.0 || u + v > 1.0) {
            return null
        }
        val t = f * edge2.dot(q)
        return if (t > 1e-6) {
            t
        } else {
            null
        }
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
        val projectionMatrix = Matrix4x4.perspective(90.0, virtualWidth.toDouble() / virtualHeight.toDouble(), 0.1, 1.0)
        val combinedMatrix = projectionMatrix * viewMatrix

        val tasks = mutableListOf<Future<*>>()

        for (mesh in meshes) {
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
                                    val faceTexture = mesh.faceTextures[faceIndex] ?: mesh.texture
                                    renderQueue.add(RenderableFace(projectedVertices, originalClipW, clippedUVs, mesh.mesh.color, false, faceTexture, clippedW, lightGrid))
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
            rasterizeTexturedTriangle(pixelBuffer, renderableFace, virtualWidth, virtualHeight)
        }

        backBuffer.pixelWriter.setPixels(0, 0, virtualWidth, virtualHeight, PixelFormat.getIntArgbInstance(), pixelBuffer, 0, virtualWidth)
        imageView.image = backBuffer
        drawOverlay()
        isRendering.set(false)
    }

    private fun rasterizeTexturedTriangle(pixelBuffer: IntArray, renderableFace: RenderableFace, screenWidth: Int, screenHeight: Int) {
        val (screenVertices, originalClipW, textureVertices, color, _, texture, _, lightGrid) = renderableFace
        if (screenVertices.size != 3 || textureVertices.size != 3 || originalClipW.size != 3 || texture == null) return

        val v0 = screenVertices[0]; val v1 = screenVertices[1]; val v2 = screenVertices[2]
        val uv0 = textureVertices[0]; val uv1 = textureVertices[1]; val uv2 = textureVertices[2]
        val w0 = originalClipW[0]; val w1 = originalClipW[1]; val w2 = originalClipW[2]
        val texReader = texture.pixelReader
        val texWidth = texture.width.toInt(); val texHeight = texture.height.toInt()

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

        val ambientR = color.red * ambientIntensity
        val ambientG = color.green * ambientIntensity
        val ambientB = color.blue * ambientIntensity

        val fogR = fogColor.red; val fogG = fogColor.green; val fogB = fogColor.blue

        for (py in minY..maxY) {
            val rowOffset = py * screenWidth
            for (px in minX..maxX) {
                val barycentric_w0 = A12 * px + B12 * py + C12_base
                val barycentric_w1 = A20 * px + B20 * py + C20_base
                val barycentric_w2 = A01 * px + B01 * py + C01_base

                if ((barycentric_w0 >= 0 && barycentric_w1 >= 0 && barycentric_w2 >= 0) || (barycentric_w0 <= 0 && barycentric_w1 <= 0 && barycentric_w2 <= 0)) {
                    val alpha = barycentric_w0 * invTotalArea
                    val beta = barycentric_w1 * invTotalArea
                    val gamma = 1.0 - alpha - beta

                    val pixelIndex = px + rowOffset
                    val interpolated_z_inv_prime = alpha * z0_inv_prime + beta * z1_inv_prime + gamma * z2_inv_prime
                    val interpolatedZ = alpha * v0.z + beta * v1.z + gamma * v2.z

                    if (interpolatedZ < depthBuffer[pixelIndex]) {
                        if (interpolated_z_inv_prime < 1e-6) continue

                        val inv_z_prime = 1.0 / interpolated_z_inv_prime
                        val u = (alpha * u0_prime + beta * u1_prime + gamma * u2_prime) * inv_z_prime
                        val v = (alpha * v0_prime + beta * v1_prime + gamma * v2_prime) * inv_z_prime
                        val texX = (u * (texWidth - 1)).toInt().coerceIn(0, texWidth - 1)
                        val texY = (v * (texHeight - 1)).toInt().coerceIn(0, texHeight - 1)

                        val texColor = texReader.getColor(texX, texY)

                        val dynamicLight = if (lightGrid != null) {
                            val gridX = (u * 8).toInt().coerceIn(0, 7)
                            val gridY = ((1.0 - v) * 8).toInt().coerceIn(0, 7)
                            lightGrid[gridX][gridY]
                        } else {
                            Color.BLACK
                        }

                        val illR = (ambientR*2 + dynamicLight.red * (ambientIntensity * 6))
                        val illG = (ambientG*2 + dynamicLight.green * (ambientIntensity * 6))
                        val illB = (ambientB*2 + dynamicLight.blue * (ambientIntensity * 6))

                        val litR = texColor.red * illR
                        val litG = texColor.green * illG
                        val litB = texColor.blue * illB

                        var r = litR
                        var g = litG
                        var b = litB

                        if (texture != this.texSkybox) {
                            val distance = inv_z_prime
                            val fogFactor = ((distance - fogStartDistance) / (fogEndDistance - fogStartDistance)).coerceIn(0.0, 1.0) * fogDensity

                            r = litR * (1 - fogFactor) + fogR * fogFactor
                            g = litG * (1 - fogFactor) + fogG * fogFactor
                            b = litB * (1 - fogFactor) + fogB * fogFactor
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

        // Zwracamy wektor z liczbami całkowitymi (Int), bo takie są koordynaty siatki
        return Vector3d(gridX.toDouble(), gridY.toDouble(), gridZ.toDouble())
    }

    fun loadImage(path: String): Image {
        val stream = DrawingPanel::class.java.classLoader.getResourceAsStream(path)
        val bufferedImage = ImageIO.read(stream)
        return SwingFXUtils.toFXImage(bufferedImage, null)
    }
}