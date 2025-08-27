package org.lewapnoob.FileZero2

import kotlin.math.max
import kotlin.math.min

data class TrianglePrimitive(
    val v0: Vector3d,
    val v1: Vector3d,
    val v2: Vector3d,
    val mesh: PlacedMesh,
    val faceIndex: Int
) {
    val center: Vector3d = (v0 + v1 + v2) / 3.0
    val bounds: AABB by lazy {
        val minX = min(v0.x, min(v1.x, v2.x))
        val minY = min(v0.y, min(v1.y, v2.y))
        val minZ = min(v0.z, min(v1.z, v2.z))
        val maxX = max(v0.x, max(v1.x, v2.x))
        val maxY = max(v0.y, max(v1.y, v2.y))
        val maxZ = max(v0.z, max(v1.z, v2.z))
        AABB(Vector3d(minX, minY, minZ), Vector3d(maxX, maxY, maxZ))
    }
}

class BVHNode {
    lateinit var bounds: AABB
    var left: BVHNode? = null
    var right: BVHNode? = null
    val primitives = mutableListOf<TrianglePrimitive>()

    fun isLeaf(): Boolean = left == null && right == null
}

class BVH {
    var root: BVHNode? = null
    private val maxPrimitivesInNode = 8

    fun build(allMeshes: List<PlacedMesh>) {
        val primitives = mutableListOf<TrianglePrimitive>()
        for (mesh in allMeshes) {
            if (!mesh.collision) continue

            val worldVertices = mesh.getTransformedVertices()
            for (faceIndex in mesh.mesh.faces.indices) {
                val face = mesh.mesh.faces[faceIndex]
                if (face.size < 3) continue

                val v0 = worldVertices[face[0]]
                val v1 = worldVertices[face[1]]
                val v2 = worldVertices[face[2]]
                primitives.add(TrianglePrimitive(v0, v1, v2, mesh, faceIndex))

                if (face.size == 4) {
                    val v3 = worldVertices[face[3]]
                    primitives.add(TrianglePrimitive(v0, v2, v3, mesh, faceIndex))
                }
            }
        }
        root = if (primitives.isNotEmpty()) {
            recursiveBuild(primitives)
        } else {
            null
        }
    }

    private fun recursiveBuild(primitives: List<TrianglePrimitive>): BVHNode {
        val node = BVHNode()
        node.bounds = primitives.drop(1).fold(primitives.first().bounds) { acc, p -> acc.union(p.bounds) }

        if (primitives.size <= maxPrimitivesInNode) {
            node.primitives.addAll(primitives)
            return node
        }

        val centroidBounds = primitives.drop(1).fold(AABB(primitives.first().center, primitives.first().center)) { acc, p -> acc.union(p.center) }
        val dim = centroidBounds.longestAxis()

        val sortedPrimitives = primitives.sortedBy { it.center[dim] }
        val mid = sortedPrimitives.size / 2

        node.left = recursiveBuild(sortedPrimitives.subList(0, mid))
        node.right = recursiveBuild(sortedPrimitives.subList(mid, sortedPrimitives.size))

        return node
    }

    fun intersect(rayOrigin: Vector3d, rayDir: Vector3d, maxDist: Double, ignoreMesh: PlacedMesh, ignoreFaceIndex: Int): Boolean {
        val rootNode = root ?: return false
        val invDir = Vector3d(1.0 / rayDir.x, 1.0 / rayDir.y, 1.0 / rayDir.z)

        val nodesToVisit = ArrayDeque<BVHNode>()
        nodesToVisit.add(rootNode)

        while (nodesToVisit.isNotEmpty()) {
            val currentNode = nodesToVisit.removeLast()

            if (!rayIntersectsAABB(rayOrigin, invDir, maxDist, currentNode.bounds)) {
                continue
            }

            if (currentNode.isLeaf()) {
                for (primitive in currentNode.primitives) {
                    if (primitive.mesh === ignoreMesh && primitive.faceIndex == ignoreFaceIndex) {
                        continue
                    }

                    val intersectionDist = rayIntersectsTriangle(rayOrigin, rayDir, primitive.v0, primitive.v1, primitive.v2)
                    if (intersectionDist != null && intersectionDist > 1e-6 && intersectionDist < maxDist) {
                        val intersectionPoint = rayOrigin + rayDir * intersectionDist
                        val worldBlushes by lazy {
                            primitive.mesh.mesh.blushes.map { blush ->
                                val transformedCorners = blush.getCorners().map { corner -> primitive.mesh.transformMatrix.transform(corner) }
                                AABB.fromCube(transformedCorners)
                            }
                        }

                        if (worldBlushes.isEmpty() || !worldBlushes.any { it.contains(intersectionPoint) }) {
                            return true
                        }
                    }
                }
            } else {
                currentNode.left?.let { nodesToVisit.add(it) }
                currentNode.right?.let { nodesToVisit.add(it) }
            }
        }
        return false
    }

    private fun rayIntersectsTriangle(rayOrigin: Vector3d, rayDir: Vector3d, v0: Vector3d, v1: Vector3d, v2: Vector3d): Double? {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = rayDir.cross(edge2)
        val a = edge1.dot(h)
        if (a > -1e-6 && a < 1e-6) return null
        val f = 1.0 / a
        val s = rayOrigin - v0
        val u = f * s.dot(h)
        if (u < 0.0 || u > 1.0) return null
        val q = s.cross(edge1)
        val v = f * rayDir.dot(q)
        if (v < 0.0 || u + v > 1.0) return null
        val t = f * edge2.dot(q)
        return if (t > 1e-6) t else null
    }

    private fun rayIntersectsAABB(rayOrigin: Vector3d, invDir: Vector3d, maxDist: Double, aabb: AABB): Boolean {
        val tx1 = (aabb.min.x - rayOrigin.x) * invDir.x; val tx2 = (aabb.max.x - rayOrigin.x) * invDir.x
        var tmin = min(tx1, tx2); var tmax = max(tx1, tx2)
        val ty1 = (aabb.min.y - rayOrigin.y) * invDir.y; val ty2 = (aabb.max.y - rayOrigin.y) * invDir.y
        tmin = max(tmin, min(ty1, ty2)); tmax = min(tmax, max(ty1, ty2))
        val tz1 = (aabb.min.z - rayOrigin.z) * invDir.z; val tz2 = (aabb.max.z - rayOrigin.z) * invDir.z
        tmin = max(tmin, min(tz1, tz2)); tmax = min(tmax, max(tz1, tz2))
        return tmax >= max(0.0, tmin) && tmin < maxDist
    }
}

operator fun Vector3d.get(index: Int): Double = when (index) {
    0 -> x; 1 -> y; 2 -> z; else -> throw IndexOutOfBoundsException("Invalid index for Vector3d")
}

fun AABB.longestAxis(): Int {
    val extent = max - min
    return if (extent.x > extent.y && extent.x > extent.z) 0
    else if (extent.y > extent.z) 1
    else 2
}